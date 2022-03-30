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
import org.junit.jupiter.api.*;
import score.Address;
import score.ArrayDB;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkerTokenTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account admin = sm.createAccount();
    private static Address governance = owner.getAddress();
    private static Score tokenScore;
    private static Account governanceScore = Account.newScoreAccount(1);
    private static Account balnScoreAccount = Account.newScoreAccount(9);


    @BeforeAll
    static void setup() throws Exception {
        tokenScore = sm.deploy(owner, WorkerToken.class, governanceScore.getAddress());
        tokenScore.invoke(governanceScore,"setAdmin", admin.getAddress());
        tokenScore.invoke(admin, "setBaln", tokenScore.getAddress());
    }

    @Test
    void testGovernance(){
        //test for if condition for isContract is satisfied
        //tokenScore.invoke(owner, "setGovernance", admin.getAddress());

        //test for if for only owner is satisfied
        //tokenScore.invoke(governanceScore, "setGovernance", admin.getAddress());

        assertEquals(tokenScore.call("getGovernance"), governanceScore.getAddress());
        assertEquals(
                tokenScore.call("balanceOf", owner.getAddress()),
                tokenScore.call("totalSupply")
                );
        assertEquals(
                tokenScore.call("balanceOf", governanceScore.getAddress()),
                (BigInteger) governanceScore.getBalance("BALW")
        );
    }

    @Test
    void testAdmin(){
        assertEquals(tokenScore.call("getAdmin"), admin.getAddress());
    }

    @Test
    void transferTest(){
        Account testAccount = sm.createAccount();
        var ownerBalance = BigInteger.valueOf(100).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        var testBalance = BigInteger.valueOf(0);
        testAccount.addBalance("BALW", testBalance);

        var transferAmount = BigInteger.valueOf(50).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        // test 1
        String info = "Hello there";
        tokenScore.invoke(admin, "adminTransfer", owner.getAddress(), testAccount.getAddress(), transferAmount, info.getBytes());
        assertEquals(ownerBalance.subtract(transferAmount), tokenScore.call("balanceOf", owner.getAddress()));
        assertEquals(testBalance.add(transferAmount), tokenScore.call("balanceOf", testAccount.getAddress()));

        // test 2
        testBalance = (BigInteger) tokenScore.call("balanceOf", testAccount.getAddress());
        var test_balance_2 = (BigInteger) tokenScore.call("balanceOf", tokenScore.getAddress());
        byte[] bytes = new byte[10];
        transferAmount = BigInteger.valueOf(25).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        tokenScore.invoke(admin, "adminTransfer", testAccount.getAddress(), tokenScore.getAddress(), transferAmount, info.getBytes());
        assertEquals(testBalance.subtract(transferAmount), tokenScore.call("balanceOf", testAccount.getAddress()));
        assertEquals(test_balance_2.add(transferAmount), tokenScore.call("balanceOf", tokenScore.getAddress()));
    }

    public static boolean arrayDbContains(ArrayDB<Address> arrayDB, Address address){
        final int size =  arrayDB.size();
        for (int i = 0; i < size; i++){
            if (arrayDB.get(i).equals(address)){
                return true;
            }
        }

        return false;
    }

    @Test
    void distributeTest(){
        Account testAccount = sm.createAccount();
        var ownerBalance = (BigInteger) tokenScore.call("balanceOf", owner.getAddress());


        BigInteger tokenScoreBalance = (BigInteger) tokenScore.call("balanceOf", tokenScore.getAddress());
        var transferAmount = BigInteger.valueOf(90).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        String info = "Hello there";
        tokenScore.invoke(admin, "adminTransfer", owner.getAddress(), tokenScore.getAddress(), transferAmount, info.getBytes());

        BigInteger baln_token_balance = (BigInteger) tokenScore.call(
                "balanceOf",
                (Address ) tokenScore.call("getBaln")
                );

        ownerBalance = (BigInteger) tokenScore.call("balanceOf", owner.getAddress());
        tokenScoreBalance = (BigInteger) tokenScore.call("balanceOf", tokenScore.getAddress());
        BigInteger totalSupply = (BigInteger) tokenScore.call("totalSupply");
        BigInteger ownerBalanceNew  = (BigInteger ) tokenScore.call("balanceOf", owner.getAddress());

        tokenScore.call("distribute");

        BigInteger amount = ownerBalanceNew.multiply(baln_token_balance).divide(totalSupply);
        assertEquals(
                ownerBalanceNew.add(amount),
                tokenScore.call("balanceOf", owner.getAddress())
                );

    }

}
