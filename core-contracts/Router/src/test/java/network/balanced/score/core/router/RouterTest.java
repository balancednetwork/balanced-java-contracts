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

import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.router.RouterImpl.MAX_NUMBER_OF_ITERATIONS;
import static network.balanced.score.core.router.RouterImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class RouterTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score routerScore;
    private static final Account adminAccount = sm.createAccount();

    protected static MockBalanced mockBalanced;
    protected static MockContract<Governance> governance;
    protected static MockContract<Dex> dex;
    protected static MockContract<Sicx> sicx;
    protected static MockContract<BalancedToken> baln;


    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        dex = mockBalanced.dex;
        sicx = mockBalanced.sicx;
        baln = mockBalanced.baln;
        routerScore = sm.deploy(owner, RouterImpl.class, governance.getAddress());
    }

    @Test
    void name() {
        assertEquals("Balanced Router", routerScore.call("name"));
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(routerScore, governance.account, adminAccount);
    }

    @Test
    void route() {
        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);

        Address[] pathWithNonSicx = new Address[]{baln.getAddress()};
        Executable nonSicxTrade = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route", pathWithNonSicx,
                BigInteger.ZERO);
        String expectedErrorMessage = "Reverted(0): " + TAG + ": ICX can only be traded for sICX";
        expectErrorMessage(nonSicxTrade, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            pathWithMoreHops[i] = Account.newScoreAccount(scoreCount++).getAddress();
        }

        Executable maxTradeHops = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                pathWithMoreHops, BigInteger.ZERO);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        when(sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(icxToTrade);
        Address[] path = new Address[]{sicx.getAddress()};

        Executable lessThanMinimumReceivable = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                path, icxToTrade.multiply(BigInteger.TWO));
        expectedErrorMessage = "Reverted(0): " + TAG + ": Below minimum receive amount of " + icxToTrade.multiply(BigInteger.TWO);
        expectErrorMessage(lessThanMinimumReceivable, expectedErrorMessage);

        sm.call(owner, icxToTrade, routerScore.getAddress(), "route", path, BigInteger.ZERO);
        verify(sicx.mock).transfer( owner.getAddress(), icxToTrade, null);
        Executable negativeMinimumBalance = () -> sm.call(owner, icxToTrade, routerScore.getAddress(),
                "route", path, icxToTrade.negate());
        expectedErrorMessage = "Reverted(0): " + TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumBalance, expectedErrorMessage);
    }

    @Test
    void tokenFallback() {
        // perform trade between arbitrary tokens and having destination as icx.
        // have receiver, minimum receive options
        String expectedErrorMessage = "Reverted(0): Token Fallback: Data can't be empty";
        Executable noDataTransfer = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, "".getBytes());
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        noDataTransfer = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, new byte[0]);
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        byte[] data = tokenData("hello", Map.of());
        Executable noSwapMethod = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, data);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Fallback directly not allowed.";
        expectErrorMessage(noSwapMethod, expectedErrorMessage);

        byte[] negativeData = tokenData("_swap", Map.of("minimumReceive", -123L));
        Executable negativeMinimumReceive = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, negativeData);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumReceive, expectedErrorMessage);

        byte[] negativeStringNumber = tokenData("_swap", Map.of("minimumReceive", "-123"));
        Executable negativeMinimumReceiveAsString = () -> routerScore.invoke(sicx.account, "tokenFallback",
                owner.getAddress(),
                BigInteger.TEN, negativeStringNumber);
        expectErrorMessage(negativeMinimumReceiveAsString, expectedErrorMessage);

        byte[] invalidNumberFormat = tokenData("_swap", Map.of("minimumReceive", "123abcd"));
        Executable hexNumber = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidNumberFormat);
        expectedErrorMessage = "Invalid numeric value: " + "123abcd";
        expectErrorMessage(hexNumber, expectedErrorMessage);

        byte[] booleanDataInMinimumReceive = tokenData("_swap", Map.of("minimumReceive", true));
        Executable booleanInNumber = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, booleanDataInMinimumReceive);
        expectedErrorMessage = TAG + ": Invalid value format for minimum receive amount";
        expectErrorMessage(booleanInNumber, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            pathWithMoreHops[i] = Account.newScoreAccount(scoreCount++).getAddress();
        }
        byte[] pathWithMaxHops = tokenData("_swap", Map.of("path", pathWithMoreHops));
        Executable maxTradeHops = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, pathWithMaxHops);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        when(sicx.mock.balanceOf(routerScore.getAddress())).thenReturn(BigInteger.TEN);
        when(baln.mock.balanceOf(routerScore.getAddress())).thenReturn(BigInteger.TEN);
        routerScore.getAccount().addBalance("ICX", BigInteger.TEN);
        byte[] invalidPathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{baln.getAddress().toString(), null}));
        Executable nonSicxIcxTrade = () -> routerScore.invoke(sicx.account, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidPathWithSicxTerminalToken);
        expectedErrorMessage = "Reverted(0): " + TAG + ": ICX can only be traded with sICX token";
        expectErrorMessage(nonSicxIcxTrade, expectedErrorMessage);

        Account newReceiver = sm.createAccount();
        byte[] pathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{sicx.getAddress().toString(), null}, "receiver", newReceiver.getAddress().toString()));
        routerScore.invoke(baln.account, "tokenFallback", owner.getAddress(), BigInteger.TEN, pathWithSicxTerminalToken);
        assertEquals(newReceiver.getBalance(), BigInteger.TEN);
    }

    @Test
    void fallback() {
        Account nonDex = sm.createAccount();
        nonDex.addBalance("ICX", BigInteger.TEN);
        Executable nonDexCall = () -> sm.transfer(nonDex, routerScore.getAddress(), BigInteger.TEN);
        String expectedErrorMessage = "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonDex.getAddress() +
                " Authorized Caller: " + dex.getAddress();
        expectErrorMessage(nonDexCall, expectedErrorMessage);

        dex.account.addBalance("ICX", BigInteger.TEN);
        sm.transfer(dex.account, routerScore.getAddress(), BigInteger.TEN);
    }
}
