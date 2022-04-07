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
        sm.call(owner, icxToTrade, routerScore.getAddress(), "route", path, BigInteger.ZERO);
        contextMock.verify(() -> Context.call(sicxScore.getAddress(), "transfer", owner.getAddress(), icxToTrade,
                new byte[0]));
    }

    @Test
    void testRoute() {
//        Account account1 = Account.newScoreAccount(3);
//        // cannot transfer balance into score create by using Account.newScoreAccount()
//        // so we need to use a score created by us
//        // hence we use routerScore
////        Account token = Account.newScoreAccount(4);
//        Score sicxScore = sm.deploy(owner, Token.class);
//        Address[] addresses = new Address[1];
//        addresses[0] = sicxScore.getAddress();
//
//        account1.addBalance("TestToken", BigInteger.TEN);
//
//        Score stakingScore = sm.deploy(owner, Token.class);
//        routerScore.invoke(governanceScore, "setStaking", stakingScore.getAddress());
//
//        routerScore.invoke(
//                governanceScore,
//                "setSicx",
//                sicxScore.getAddress()
//        );
//
//        BigInteger minReceive = BigInteger.ZERO;
//        routerScore.invoke(adminAccount, "route", addresses, minReceive);
    }

    @AfterEach
    void contextClose() {
        contextMock.close();
    }
}
