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

package network.balanced.score.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;

public class TestBNUSD extends TestBase {
    public static ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    public static final Account admin = sm.createAccount();
    public static final Account governance = sm.createAccount();
    public static Score bnUSDScore;


    @BeforeAll
    static void setup() throws Exception {
        bnUSDScore = sm.deploy(owner, BalancedDollar.class, governance.getAddress());
        bnUSDScore.invoke(governance, "setAdmin", owner.getAddress());
    }

    @Test
    void testGovernance() {
        // test initial governance setup
        assertEquals(
                governance.getAddress(),
                (Address) bnUSDScore.call("getGovernance")
        );

        Account account = Account.newScoreAccount(0);
        bnUSDScore.invoke(owner, "setGovernance", account.getAddress());
        assertEquals(
                account.getAddress(),
                bnUSDScore.call("getGovernance")
        );
    }

    @Test
    void testAdmin() {
        // test initial admin setup
        assertEquals(
                owner.getAddress(),
                (Address) bnUSDScore.call("getAdmin")
        );

        Account account = Account.newScoreAccount(0);
        bnUSDScore.invoke(governance, "setAdmin", account.getAddress());
        assertEquals(
                account.getAddress(),
                bnUSDScore.call("getAdmin")
        );
    }

    @Test
    void testOracle() {
        Account account = Account.newScoreAccount(0);
        String oracleName = "Hello there!";
        bnUSDScore.invoke(governance, "setOracle", account.getAddress());
        bnUSDScore.invoke(governance, "setOracleName", oracleName);

        // assert oracle address
        assertEquals(
                account.getAddress(),
                bnUSDScore.call("getOracle")
        );

        // assert oracle name
        assertEquals(
                oracleName,
                bnUSDScore.call("getOracleName")
        );
    }

    public static BigInteger pow(BigInteger base, int exponent){
        BigInteger res = BigInteger.ONE;

        for(int i = 1; i <= exponent; i++){
            res = res.multiply(base);
        }

        return res;
    }


    @Test
    void testMint() {
        // Mint test
        BigInteger mintAmount = BigInteger.TEN.multiply(pow(BigInteger.TEN, 18));
        String data = "";
        bnUSDScore.invoke(owner, "mint", mintAmount, data.getBytes());
        assertEquals(
                mintAmount,
                bnUSDScore.call("totalSupply")
        );
        assertEquals(
                mintAmount,
                bnUSDScore.call("balanceOf", owner.getAddress())
        );
    }

    @Test
    void testBurn() {
        // Mint test
        BigInteger burnAmount = BigInteger.TEN.multiply(pow(BigInteger.TEN, 18));
        String data = "";
        bnUSDScore.invoke(owner, "mint", burnAmount, data.getBytes());
        bnUSDScore.invoke(owner, "burn", burnAmount);
        assertEquals(
                BigInteger.ZERO,
                bnUSDScore.call("totalSupply")
        );
        assertEquals(
                BigInteger.ZERO,
                bnUSDScore.call("balanceOf", owner.getAddress())
        );
    }

    @Test
    void testGovTransfer() {
        // Mint test
        BigInteger mintAmount = BigInteger.TEN.multiply(pow(BigInteger.TEN, 18));
        String data = "";
        bnUSDScore.invoke(owner, "mint", mintAmount, data.getBytes());
        Account acc = sm.createAccount();
        bnUSDScore.invoke(governance, "govTransfer", owner.getAddress(), acc.getAddress(), mintAmount, data.getBytes());
        assertEquals(
                mintAmount,
                bnUSDScore.call("balanceOf", acc.getAddress())
        );
    }


    @Test
    void testMinInterval() {
        BigInteger interval = BigInteger.TEN;
        bnUSDScore.invoke(governance, "setMinInterval", interval);
        assertEquals(
                interval,
                bnUSDScore.call("getMinInterval")
        );
    }

    @Test
    void testPriceInLoop() throws Exception {
        Score oracle = sm.deploy(owner, DummyOracle.class);
        String oracleName = "Hello there!";
        bnUSDScore.invoke(governance, "setOracle", oracle.getAddress());
        BigInteger result = (BigInteger) bnUSDScore.call("lastPriceInLoop");
        assertEquals(
                result,
                BigInteger.valueOf(597955725813433531L)
        );
    }

    @Test
    void testPrice() throws Exception {
        Score oracle = sm.deploy(owner, DummyOracle.class);
        String oracleName = "Hello there!";
        bnUSDScore.invoke(governance, "setOracle", oracle.getAddress());
        BigInteger result;
        Score testContract = sm.deploy(owner, DummyContract.class);
        result = (BigInteger) testContract.call( "testPriceInLoop", bnUSDScore.getAddress());

        assertEquals(
                BigInteger.valueOf(Context.getBlockTimestamp()),
                bnUSDScore.call("getPriceUpdateTime")
        );

        assertEquals(
                result,
                BigInteger.valueOf(597955725813433531L)
        );
    }

}
