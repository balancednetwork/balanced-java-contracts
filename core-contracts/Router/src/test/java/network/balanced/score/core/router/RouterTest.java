/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.router;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.interfaces.tokens.SpokeToken;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenScoreInterface;
import network.balanced.score.lib.structs.Route;
import network.balanced.score.lib.structs.RouteAction;
import network.balanced.score.lib.structs.RouteData;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.router.RouterImpl.*;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouterTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score routerScore;
    private MockBalanced balanced;
    private MockContract<Sicx> sicxScore;

    @BeforeEach
    void deploy() throws Exception {
        balanced = new MockBalanced(sm, owner);
        routerScore = sm.deploy(owner, RouterImpl.class, balanced.governance.getAddress());
        sicxScore = balanced.sicx;
        when(balanced.xCall.mock.getNetworkId()).thenReturn("0x1.icon");
    }

    @Test
    void name() {
        assertEquals("Balanced Router", routerScore.call("name"));
    }

    @Test
    void route() {
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);

        Account balnToken = Account.newScoreAccount(scoreCount++);
        Address[] pathWithNonSicx = new Address[]{balnToken.getAddress()};
        Executable nonSicxTrade = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route", pathWithNonSicx,
                BigInteger.ZERO, "");
        String expectedErrorMessage = "Reverted(0): " + TAG + ": ICX can only be traded for sICX";
        expectErrorMessage(nonSicxTrade, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            pathWithMoreHops[i] = Account.newScoreAccount(scoreCount++).getAddress();
        }

        Executable maxTradeHops = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                pathWithMoreHops, BigInteger.ZERO, "");
        expectedErrorMessage = "Reverted(0): " + TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(icxToTrade);
        Address[] path = new Address[]{sicxScore.getAddress()};
        routerScore.getAccount().addBalance("ICX", icxToTrade);

        Executable lessThanMinimumReceivable = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                path, icxToTrade.multiply(BigInteger.TWO), "");
        expectedErrorMessage = "Reverted(0): " + TAG + ": Below minimum receive amount of "
                + icxToTrade.multiply(BigInteger.TWO);
        expectErrorMessage(lessThanMinimumReceivable, expectedErrorMessage);

        routerScore.getAccount().addBalance("ICX", icxToTrade);
        sm.call(owner, icxToTrade, routerScore.getAddress(), "route", path, BigInteger.ZERO,
                owner.getAddress().toString());
        verify(sicxScore.mock).transfer(owner.getAddress(), icxToTrade, EMPTY_DATA);

        Executable negativeMinimumBalance = () -> sm.call(owner, icxToTrade, routerScore.getAddress(),
                "route", path, icxToTrade.negate(), "");
        expectedErrorMessage = "Reverted(0): " + TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumBalance, expectedErrorMessage);
    }

    @Test
    void tokenFallback() throws Exception {
        // perform trade between arbitrary tokens and having destination as icx.
        // have receiver, minimum receive options

        String expectedErrorMessage = "Reverted(0): Token Fallback: Data can't be empty";
        Executable noDataTransfer = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, "".getBytes());
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        noDataTransfer = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, new byte[0]);
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        byte[] data = tokenData("hello", Map.of());
        Executable noSwapMethod = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, data);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Fallback directly not allowed.";
        expectErrorMessage(noSwapMethod, expectedErrorMessage);

        byte[] negativeData = tokenData("_swap", Map.of("minimumReceive", -123L));
        Executable negativeMinimumReceive = () -> routerScore.invoke(sicxScore.account, "tokenFallback",
                owner.getAddress(),
                BigInteger.TEN, negativeData);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumReceive, expectedErrorMessage);

        byte[] negativeStringNumber = tokenData("_swap", Map.of("minimumReceive", "-123"));
        Executable negativeMinimumReceiveAsString = () -> routerScore.invoke(sicxScore.account, "tokenFallback",
                owner.getAddress(),
                BigInteger.TEN, negativeStringNumber);
        expectErrorMessage(negativeMinimumReceiveAsString, expectedErrorMessage);

        byte[] invalidNumberFormat = tokenData("_swap", Map.of("minimumReceive", "123abcd"));
        Executable hexNumber = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidNumberFormat);
        expectedErrorMessage = "Invalid numeric value: " + "123abcd";
        expectErrorMessage(hexNumber, expectedErrorMessage);

        byte[] booleanDataInMinimumReceive = tokenData("_swap", Map.of("minimumReceive", true));
        Executable booleanInNumber = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, booleanDataInMinimumReceive);
        expectedErrorMessage = TAG + ": Invalid value format for minimum receive amount";
        expectErrorMessage(booleanInNumber, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
            when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(BigInteger.TEN);
            pathWithMoreHops[i] = token.getAddress();
        }
        byte[] pathWithMaxHops = tokenData("_swap", Map.of("path", pathWithMoreHops));
        Executable maxTradeHops = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, pathWithMaxHops);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        routerScore.getAccount().addBalance("ICX", BigInteger.TEN);

        when(balanced.baln.mock.balanceOf(routerScore.getAddress())).thenReturn(BigInteger.TEN);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(BigInteger.TEN);

        byte[] invalidPathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{balanced.baln.getAddress().toString(), null}));
        Executable nonSicxIcxTrade = () -> routerScore.invoke(sicxScore.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidPathWithSicxTerminalToken);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Native swaps not available to icon from " + balanced.baln.getAddress();
        expectErrorMessage(nonSicxIcxTrade, expectedErrorMessage);

        Account newReceiver = sm.createAccount();
        byte[] pathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{sicxScore.getAddress().toString(), null}, "receiver",
                newReceiver.getAddress().toString()));
        routerScore.invoke(balanced.baln.account, "tokenFallback", owner.getAddress(), BigInteger.TEN,
                pathWithSicxTerminalToken);
        assertEquals(newReceiver.getBalance(), BigInteger.TEN);
    }

    @Test
    void xTrade_WithdrawToken() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);
        when(balanced.daofund.mock.claimXCallFee(net, true)).thenReturn(fee);
        when(balanced.assetManager.mock.getNativeAssetAddress(token.getAddress(), net))
                .thenReturn(new NetworkAddress(net, "cx3").toString());

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString()}, "receiver", receiver.toString()));
        routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);

        // Assert
        verify(balanced.assetManager.mock).withdrawTo(token.getAddress(), receiver.toString(), amount);
    }

    @Test
    void xTrade_WithdrawNotAllowed() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(false);

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString()}, "receiver", receiver.toString()));
        routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);

        // Assert
        verify(token.mock).hubTransfer(receiver.toString(), amount, new byte[0]);
    }

    @Test
    void xTrade_ToNetworkAddressWithDifferentNet() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        String net2 = "0x3.bsc";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);
        when(balanced.assetManager.mock.getNativeAssetAddress(token.getAddress(), net2))
                .thenReturn(new NetworkAddress(net2, "cx3").toString());

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString()}, "receiver", receiver.toString()));
        routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);

        // Assert
        verify(token.mock).hubTransfer(receiver.toString(), amount, new byte[0]);
    }

    @Test
    void xTradeNative_ToNetworkAddressWithDifferentNet() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        String net2 = "0x3.bsc";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);
        when(balanced.assetManager.mock.getNativeAssetAddress(token.getAddress(), net2))
                .thenReturn(new NetworkAddress(net2, "cx3").toString());

        // Act & Assert
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString(), null}, "receiver", receiver.toString()));
        Executable nativeToWrongNet = () -> routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Native swaps are not supported to other networks";
        expectErrorMessage(nativeToWrongNet, expectedErrorMessage);
    }

    @Test
    void xTradeNative_cannotWithdraw() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(false);
        when(balanced.assetManager.mock.getNativeAssetAddress(token.getAddress(), net))
                .thenReturn(new NetworkAddress(net, "cx3").toString());

        // Act & Assert
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString(), null}, "receiver", receiver.toString()));
        Executable nativeToWrongNet = () -> routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Native swaps are not supported for this network";
        expectErrorMessage(nativeToWrongNet, expectedErrorMessage);
    }

    @Test
    void xTrade_toNative() throws Exception {
        // Arrange
        MockContract<SpokeToken> token = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm,
                owner);
        String net = "0x1.eth";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);
        when(balanced.assetManager.mock.getNativeAssetAddress(token.getAddress(), net))
                .thenReturn(new NetworkAddress(net, "cx3").toString());

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{token.getAddress().toString(), null}, "receiver", receiver.toString()));
        routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);

        // Assert
        verify(balanced.assetManager.mock).withdrawNativeTo(token.getAddress(), receiver.toString(), amount);
    }

    @Test
    void xTrade_ToBnUSD() throws Exception {
        // Arrange
        String net = "0x1.eth";
        BigInteger fee = BigInteger.TEN;
        NetworkAddress receiver = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.bnUSD.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);
        when(balanced.daofund.mock.claimXCallFee(net, true)).thenReturn(fee);

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{balanced.bnUSD.getAddress().toString()}, "receiver", receiver.toString()));
        routerScore.invoke(balanced.sicx.account, "tokenFallback", owner.getAddress(), amount, path);

        // Assert
        verify(balanced.bnUSD.mock).crossTransfer(receiver.toString(), amount, new byte[0]);
    }

    @Test
    void xTrade_toIRC20() throws Exception {
        // Arrange
        String net = "0x1.eth";

        NetworkAddress user = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.bnUSD.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.daofund.mock.getXCallFeePermission(routerScore.getAddress(), net)).thenReturn(true);

        // Act & Assert
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{balanced.sicx.getAddress().toString()}));
        Executable tradeToIRC2WithNetworkAddress = () -> routerScore.invoke(balanced.bnUSD.account, "xTokenFallback",
                user.toString(), amount, path);
        expectErrorMessage(tradeToIRC2WithNetworkAddress, "hubTransfer");
    }

    @Test
    void xTrade_toICX() throws Exception {
        // Arrange
        String net = "0x1.eth";
        NetworkAddress user = new NetworkAddress(net, "cx1");
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.bnUSD.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);

        // Act & Assert
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{balanced.sicx.getAddress().toString(), null}));
        assertThrows(AssertionError.class,
                () -> routerScore.invoke(balanced.bnUSD.account, "xTokenFallback", user.toString(), amount, path));
    }

    @Test
    void xTrade_toNativeICX() throws Exception {
        // Arrange
        String net = "0x1.eth";
        NetworkAddress user = new NetworkAddress(net, "cx1");
        Account receiver = sm.createAccount();
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.bnUSD.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{balanced.sicx.getAddress().toString(), null}, "receiver",
                receiver.getAddress().toString()));
        routerScore.getAccount().addBalance("ICX", amount);
        routerScore.invoke(balanced.bnUSD.account, "xTokenFallback", user.toString(), amount, path);

        // Assert
        assertEquals(amount, receiver.getBalance());
        assertEquals(BigInteger.ZERO, routerScore.getAccount().getBalance());
    }

    @Test
    void xTrade_toNativeIRC20() throws Exception {
        // Arrange
        String net = "0x1.eth";
        NetworkAddress user = new NetworkAddress(net, "cx1");
        Account receiver = sm.createAccount();
        BigInteger amount = BigInteger.TEN.multiply(ICX);
        when(balanced.bnUSD.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(amount);

        // Act
        byte[] path = tokenData("_swap", Map.of("path",
                new Object[]{balanced.sicx.getAddress().toString()}, "receiver", receiver.getAddress().toString()));
        routerScore.getAccount().addBalance("ICX", amount);
        routerScore.invoke(balanced.bnUSD.account, "xTokenFallback", user.toString(), amount, path);

        // Assert
        verify(balanced.sicx.mock).transfer(receiver.getAddress(), amount, EMPTY_DATA);
    }

    @Test
    void routeV2_swap_icxSicx() {
        // Arrange
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);
        Account balnToken = Account.newScoreAccount(scoreCount++);
        List<RouteAction> actions = new ArrayList<>(1);
        actions.add(new RouteAction(SWAP, balnToken.getAddress()));
        Route route = new Route(actions);
        byte[] pathWithNonSicx = route.toBytes();

        // Act
        Executable nonSicxTrade = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "routeV2", pathWithNonSicx,
                BigInteger.ZERO, "");

        // Assert
        String expectedErrorMessage = "Reverted(0): " + TAG + ": ICX can only be traded for sICX";
        expectErrorMessage(nonSicxTrade, expectedErrorMessage);
    }

    @Test
    void routeV2_swap_pathWithMoreHops() {
        // Arrange
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);
        List<RouteAction> actions = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS + 1);
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            actions.add(new RouteAction(SWAP, Account.newScoreAccount(scoreCount++).getAddress()));
        }
        Route route = new Route(actions);
        byte[] pathWithMoreHops = route.toBytes();

        // Act
        Executable maxTradeHops = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "routeV2",
                pathWithMoreHops, BigInteger.ZERO, "");

        // Assert
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);
    }

    @Test
    void routeV2_swap_belowMinimumReceive() {
        // Arrange
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);
        when(balanced.sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(icxToTrade);
        routerScore.getAccount().addBalance("ICX", icxToTrade);
        List<RouteAction> actions = new ArrayList<>(1);
        actions.add(new RouteAction(SWAP, sicxScore.getAddress()));
        Route route = new Route(actions);
        byte[] path = route.toBytes();

        // Act
        Executable lessThanMinimumReceivable = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "routeV2",
                path, icxToTrade.multiply(BigInteger.TWO), "");

        //Assert
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Below minimum receive amount of "
                + icxToTrade.multiply(BigInteger.TWO);
        expectErrorMessage(lessThanMinimumReceivable, expectedErrorMessage);
    }

    @Test
    void routeV2_swap_positiveMinimumReceive() {
        // Arrange
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);
        List<RouteAction> actions = new ArrayList<>(1);
        actions.add(new RouteAction(SWAP, sicxScore.getAddress()));
        Route route = new Route(actions);
        byte[] path = route.toBytes();

        // Act
        Executable negativeMinimumBalance = () -> sm.call(owner, icxToTrade, routerScore.getAddress(),
                "routeV2", path, icxToTrade.negate(), "");

        // Assert
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumBalance, expectedErrorMessage);
    }

    @Test
    void routeV2_swapStable() throws Exception {
        // Arrange
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);
        List<RouteAction> actions = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS);
        routerScore.getAccount().addBalance("ICX", icxToTrade);
        List<MockContract<IRC2>> tokens = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS - 1);
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS; i++) {
            if (i == 0) {
                actions.add(new RouteAction(SWAP, balanced.sicx.getAddress()));
                continue;
            }
            MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
            when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(icxToTrade);
            actions.add(new RouteAction(STABILITY_SWAP, token.getAddress()));
            tokens.add(token);
        }
        Route route = new Route(actions);
        byte[] pathWithMoreHops = route.toBytes();

        // Act
        sm.call(owner, icxToTrade, routerScore.getAddress(), "routeV2",
                pathWithMoreHops, BigInteger.ZERO, "");

        // Assert
        int i = 0;
        for (MockContract<IRC2> token : tokens) {
            if (i < tokens.size() - 1) {
                byte[] data = tokens.get(i + 1).getAddress().toString().getBytes();
                verify(token.mock).transfer(balanced.stability.getAddress(), icxToTrade, data);
            }
            i++;
        }
    }

    @Test
    void tokenFallback_swapStable() throws Exception {
        // Arrange
        BigInteger balnToSwap = BigInteger.TEN.multiply(ICX);
        List<RouteAction> actions = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS);
        List<MockContract<IRC2>> tokens = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS - 1);
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS; i++) {
            if (i == 0) {
                actions.add(new RouteAction(STABILITY_SWAP, balanced.sicx.getAddress()));
                continue;
            }
            MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
            when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(balnToSwap);
            actions.add(new RouteAction(STABILITY_SWAP, token.getAddress()));
            tokens.add(token);
        }

        Account newReceiver = sm.createAccount();
        byte[] data = new RouteData("_swapV2", newReceiver.getAddress().toString(), BigInteger.ZERO, actions).toBytes();

        // Act
        routerScore.invoke(balanced.baln.account, "tokenFallback", owner.getAddress(), balnToSwap,
                data);

        // Assert
        int i = 0;
        for (MockContract<IRC2> token : tokens) {
            if (i < tokens.size() - 1) {
                byte[] d = tokens.get(i + 1).getAddress().toString().getBytes();
                verify(token.mock).transfer(balanced.stability.getAddress(), balnToSwap, d);
            }
            i++;
        }
    }

    @Test
    void tokenFallback_swapStableWithoutOptField() throws Exception {
        // Arrange
        BigInteger balnToSwap = BigInteger.TEN.multiply(ICX);
        List<RouteAction> actions = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS);
        List<MockContract<IRC2>> tokens = new ArrayList<>(MAX_NUMBER_OF_ITERATIONS - 1);
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS; i++) {
            if (i == 0) {
                actions.add(new RouteAction(STABILITY_SWAP, balanced.sicx.getAddress()));
                continue;
            }
            MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
            when(token.mock.balanceOf(routerScore.getAddress())).thenReturn(balnToSwap);
            actions.add(new RouteAction(STABILITY_SWAP, token.getAddress()));
            tokens.add(token);
        }

        Account newReceiver = sm.createAccount();
        byte[] data = new RouteData("_swapV2", actions).toBytes();

        // Act
        routerScore.invoke(balanced.baln.account, "tokenFallback", owner.getAddress(), balnToSwap,
                data);

        // Assert
        int i = 0;
        for (MockContract<IRC2> token : tokens) {
            if (i < tokens.size() - 1) {
                byte[] d = tokens.get(i + 1).getAddress().toString().getBytes();
                verify(token.mock).transfer(balanced.stability.getAddress(), balnToSwap, d);
            }
            i++;
        }
    }


}
