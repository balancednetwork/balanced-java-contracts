/*
 * Copyright (c) 2022 Balanced.network.
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RouterTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score routerScore;
    private static final Account adminAccount = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(0);

    @BeforeAll
    static void setup() throws Exception {
        routerScore = sm.deploy(owner, Router.class, governanceScore.getAddress());
    }

    @Test
    void testAdmin() {
        // initial constructor test
        assertEquals(
                routerScore.call("getAdmin"),
                governanceScore.getAddress()
        );
        Account testScore = governanceScore;
        routerScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        assertEquals(
                routerScore.call("getAdmin"),
                adminAccount.getAddress()
        );
    }

    @Test
    void testGovernance() {
        // initial constructor test
        assertEquals(
                routerScore.call("getGovernance"),
                governanceScore.getAddress()
        );
        Account governanceTestScore = Account.newScoreAccount(20);
        routerScore.invoke(owner, "setGovernance", governanceTestScore.getAddress());
        assertEquals(
                routerScore.call("getGovernance"),
                governanceTestScore.getAddress()
        );
    }

    @Test
    void testDex(){
        Account dexAccount = sm.createAccount();
        routerScore.invoke(
                governanceScore,
                "setDex",
                dexAccount.getAddress()
        );
        assertEquals(
                routerScore.call("getDex"),
                dexAccount.getAddress()
        );
    }

    @Test
    void testSicx(){
        Account sicxAccount = Account.newScoreAccount(1);
        Account testScore = governanceScore;
        routerScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        routerScore.invoke(
                adminAccount,
                "setSicx",
                sicxAccount.getAddress()
        );
        assertEquals(
                routerScore.call("getSicx"),
                sicxAccount.getAddress()
        );
    }


    @Test
    void testRoute() throws Exception {
        Account account1 = Account.newScoreAccount(3);
        // cannot transfer balance into score create by using Account.newScoreAccount()
        // so we need to use a score created by us
        // hence we use routerScore
//        Account token = Account.newScoreAccount(4);
        Score sicxScore = sm.deploy(owner, Token.class);
        Address[] addresses = new Address[1];
        addresses[0] = sicxScore.getAddress();

        account1.addBalance("TestToken", BigInteger.TEN);

        Score stakingScore = sm.deploy(owner, Token.class);
        routerScore.invoke(governanceScore, "setStaking", stakingScore.getAddress());

        routerScore.invoke(
                governanceScore,
                "setSicx",
                sicxScore.getAddress()
        );

        BigInteger minReceive = BigInteger.ZERO;
        routerScore.invoke(adminAccount, "route", addresses, minReceive);

    }
}
