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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.router.Router.MAX_NUMBER_OF_ITERATIONS;
import static network.balanced.score.core.router.Router.TAG;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


class RouterTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score routerScore;
    private static final Account adminAccount = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private static final Account sicxScore = Account.newScoreAccount(scoreCount++);
    private static final Account stakingScore = Account.newScoreAccount(scoreCount++);
    private static final Account dexScore = Account.newScoreAccount(scoreCount++);

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void deploy() throws Exception {
        routerScore = sm.deploy(owner, Router.class, governanceScore.getAddress());
    }

    private void setup() {
        routerScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        routerScore.invoke(adminAccount, "setSicx", sicxScore.getAddress());
        routerScore.invoke(adminAccount, "setDex", dexScore.getAddress());
        routerScore.invoke(adminAccount, "setStaking", stakingScore.getAddress());
    }

    @Test
    void name() {
        assertEquals("Balanced Router", routerScore.call("name"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(routerScore, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(routerScore, governanceScore, adminAccount);
    }

    @Test
    void setAndGetDex() {
        testContractSettersAndGetters(routerScore, governanceScore, adminAccount, "setDex", dexScore.getAddress(),
                "getDex");
    }

    @Test
    void setAndGetSicx() {
        testContractSettersAndGetters(routerScore, governanceScore, adminAccount, "setSicx", sicxScore.getAddress(),
                "getSicx");
    }

    @Test
    void setAndGetStaking() {
        testContractSettersAndGetters(routerScore, governanceScore, adminAccount, "setStaking",
                stakingScore.getAddress(), "getStaking");
    }

    @Test
    void route() {
        setup();

        BigInteger icxToTrade = BigInteger.TEN.multiply(ICX);

        Account balnToken = Account.newScoreAccount(scoreCount++);
        Address[] pathWithNonSicx = new Address[]{balnToken.getAddress()};
        Executable nonSicxTrade = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route", pathWithNonSicx,
                BigInteger.ZERO);
        String expectedErrorMessage = TAG + ": ICX can only be traded for sICX";
        expectErrorMessage(nonSicxTrade, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            pathWithMoreHops[i] = Account.newScoreAccount(scoreCount++).getAddress();
        }

        Executable maxTradeHops = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                pathWithMoreHops, BigInteger.ZERO);
        expectedErrorMessage = TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        contextMock.reset();
        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        contextMock.when(() -> Context.call(sicxScore.getAddress(), "balanceOf", routerScore.getAddress())).thenReturn(icxToTrade);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class), any(byte[].class))).thenReturn(null);
        Address[] path = new Address[]{sicxScore.getAddress()};

        Executable lessThanMinimumReceivable = () -> sm.call(owner, icxToTrade, routerScore.getAddress(), "route",
                path, icxToTrade.multiply(BigInteger.TWO));
        expectedErrorMessage = TAG + ": Below minimum receive amount of " + icxToTrade.multiply(BigInteger.TWO);
        expectErrorMessage(lessThanMinimumReceivable, expectedErrorMessage);

        sm.call(owner, icxToTrade, routerScore.getAddress(), "route", path, BigInteger.ZERO);
        contextMock.verify(() -> Context.call(sicxScore.getAddress(), "transfer", owner.getAddress(), icxToTrade,
                new byte[0]));

        Executable negativeMinimumBalance = () -> sm.call(owner, icxToTrade, routerScore.getAddress(),
                "route", path, icxToTrade.negate());
        expectedErrorMessage = TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumBalance, expectedErrorMessage);
    }

    @Test
    void tokenFallback() {
        // perform trade between arbitrary tokens and having destination as icx.
        // have receiver, minimum receive options
        setup();

        String expectedErrorMessage = "Token Fallback: Data can't be empty";
        Executable noDataTransfer = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, "".getBytes());
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        noDataTransfer = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, new byte[0]);
        expectErrorMessage(noDataTransfer, expectedErrorMessage);

        byte[] data = tokenData("hello", Map.of());
        Executable noSwapMethod = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, data);
        expectedErrorMessage = TAG + ": Fallback directly not allowed.";
        expectErrorMessage(noSwapMethod, expectedErrorMessage);

        byte[] negativeData = tokenData("_swap", Map.of("minimumReceive", -123L));
        Executable negativeMinimumReceive = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, negativeData);
        expectedErrorMessage = TAG + ": Must specify a positive number for minimum to receive";
        expectErrorMessage(negativeMinimumReceive, expectedErrorMessage);

        byte[] negativeStringNumber = tokenData("_swap", Map.of("minimumReceive", "-123"));
        Executable negativeMinimumReceiveAsString = () -> routerScore.invoke(sicxScore, "tokenFallback",
                owner.getAddress(),
                BigInteger.TEN, negativeStringNumber);
        expectErrorMessage(negativeMinimumReceiveAsString, expectedErrorMessage);

        byte[] invalidNumberFormat = tokenData("_swap", Map.of("minimumReceive", "123abcd"));
        Executable hexNumber = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidNumberFormat);
        expectedErrorMessage = "Invalid numeric value: " + "123abcd";
        expectErrorMessage(hexNumber, expectedErrorMessage);

        byte[] booleanDataInMinimumReceive = tokenData("_swap", Map.of("minimumReceive", true));
        Executable booleanInNumber = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, booleanDataInMinimumReceive);
        expectedErrorMessage = TAG + ": Invalid value format for minimum receive amount";
        expectErrorMessage(booleanInNumber, expectedErrorMessage);

        Address[] pathWithMoreHops = new Address[MAX_NUMBER_OF_ITERATIONS + 1];
        for (int i = 0; i < MAX_NUMBER_OF_ITERATIONS + 1; i++) {
            pathWithMoreHops[i] = Account.newScoreAccount(scoreCount++).getAddress();
        }
        byte[] pathWithMaxHops = tokenData("_swap", Map.of("path", pathWithMoreHops));
        Executable maxTradeHops = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, pathWithMaxHops);
        expectedErrorMessage = TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS;
        expectErrorMessage(maxTradeHops, expectedErrorMessage);

        contextMock.reset();
        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), eq(routerScore.getAddress()))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class), any(byte[].class))).thenReturn(null);
        contextMock.when(() -> Context.getBalance(routerScore.getAddress())).thenReturn(BigInteger.TEN);

        Account balnToken = Account.newScoreAccount(scoreCount++);
        byte[] invalidPathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{balnToken.getAddress().toString(), null}));
        Executable nonSicxIcxTrade = () -> routerScore.invoke(sicxScore, "tokenFallback", owner.getAddress(),
                BigInteger.TEN, invalidPathWithSicxTerminalToken);
        expectedErrorMessage = TAG + ": ICX can only be traded with sICX token";
        expectErrorMessage(nonSicxIcxTrade, expectedErrorMessage);

        Address newReceiver = sm.createAccount().getAddress();
        byte[] pathWithSicxTerminalToken = tokenData("_swap", Map.of("path",
                new Object[]{sicxScore.getAddress().toString(), null}, "receiver", newReceiver.toString()));
        routerScore.invoke(balnToken, "tokenFallback", owner.getAddress(), BigInteger.TEN, pathWithSicxTerminalToken);
        contextMock.verify(() -> Context.transfer(newReceiver, BigInteger.TEN));
    }

    @AfterEach
    void contextClose() {
        contextMock.close();
    }
}
