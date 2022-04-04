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

package network.balanced.score.tokens.workertoken;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkerTokenTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score workerToken;
    private static final Account admin = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private static final Account balnScore = Account.newScoreAccount(scoreCount++);


    @BeforeAll
    static void setup() throws Exception {
        workerToken = sm.deploy(owner, WorkerToken.class, governanceScore.getAddress());
//        workerToken.invoke(governanceScore,"setAdmin", admin.getAddress());
//        workerToken.invoke(admin, "setBaln", workerToken.getAddress());
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(workerToken, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(workerToken, governanceScore, admin);
    }

    @Test
    void setAndGetBaln() {
        testContractSettersAndGetters(workerToken, governanceScore, admin, "setBaln", balnScore.getAddress(),
                "getBaln");
    }

    @Test
    void transferTest() {
        Account testAccount = sm.createAccount();
        var ownerBalance = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(6));
        var testBalance = BigInteger.ZERO;
        testAccount.addBalance("BALW", testBalance);

        var transferAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(6));
        // test 1
        String info = "Hello there";
        workerToken.invoke(admin, "adminTransfer", owner.getAddress(), testAccount.getAddress(), transferAmount,
                info.getBytes());
        assertEquals(ownerBalance.subtract(transferAmount), workerToken.call("balanceOf", owner.getAddress()));
        assertEquals(testBalance.add(transferAmount), workerToken.call("balanceOf", testAccount.getAddress()));

        // test 2
        testBalance = (BigInteger) workerToken.call("balanceOf", testAccount.getAddress());
        var test_balance_2 = (BigInteger) workerToken.call("balanceOf", workerToken.getAddress());
        byte[] bytes = new byte[10];
        transferAmount = BigInteger.valueOf(25).multiply(BigInteger.TEN.pow(6));
        workerToken.invoke(admin, "adminTransfer", testAccount.getAddress(), workerToken.getAddress(), transferAmount
                , info.getBytes());
        assertEquals(testBalance.subtract(transferAmount), workerToken.call("balanceOf", testAccount.getAddress()));
        assertEquals(test_balance_2.add(transferAmount), workerToken.call("balanceOf", workerToken.getAddress()));
    }

    @Test
    void distributeTest(){
        Account testAccount = sm.createAccount();
        var ownerBalance = (BigInteger) workerToken.call("balanceOf", owner.getAddress());


        BigInteger tokenScoreBalance = (BigInteger) workerToken.call("balanceOf", workerToken.getAddress());
        var transferAmount = BigInteger.valueOf(90).multiply(BigInteger.TEN.pow(6));
        String info = "Hello there";
        workerToken.invoke(admin, "adminTransfer", owner.getAddress(), workerToken.getAddress(), transferAmount,
                info.getBytes());

        BigInteger baln_token_balance = (BigInteger) workerToken.call(
                "balanceOf",
                workerToken.call("getBaln")
        );

        ownerBalance = (BigInteger) workerToken.call("balanceOf", owner.getAddress());
        tokenScoreBalance = (BigInteger) workerToken.call("balanceOf", workerToken.getAddress());
        BigInteger totalSupply = (BigInteger) workerToken.call("totalSupply");
        BigInteger ownerBalanceNew = (BigInteger) workerToken.call("balanceOf", owner.getAddress());

        workerToken.call("distribute");

        BigInteger amount = ownerBalanceNew.multiply(baln_token_balance).divide(totalSupply);
        assertEquals(
                ownerBalanceNew.add(amount),
                workerToken.call("balanceOf", owner.getAddress())
        );

    }

}
