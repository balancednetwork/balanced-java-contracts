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

package network.balanced.score.core.rewards;


import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static String dexJavaPath;
    private static String loansJavaPath;

    @BeforeAll    
    static void setup() throws Exception {
        dexJavaPath = System.getProperty("Dex");
        loansJavaPath = System.getProperty("Loans");

        // System.setProperty("Rewards", System.getProperty("python"));
        System.setProperty("Dex", System.getProperty("dexPython"));
        System.setProperty("Loans", System.getProperty("loansPython"));
        
        balanced = new Balanced();
        balanced.deployBalanced();
        owner = balanced.ownerClient;
        //day 1
        balanced.increaseDay(1);

        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setQuorum(BigInteger.ONE);
        owner.baln.toggleEnableSnapshot();

    }

    @Test
    void update() throws Exception {

        BalancedClient tester1 = balanced.newClient();
        BalancedClient tester2 = balanced.newClient();
        
        BigInteger tester1Loan = BigInteger.TEN.pow(21);
        BigInteger tester2Loan = BigInteger.TEN.pow(21);
        tester1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester1Loan, null, null);        
        tester2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester2Loan, null, null);
        
        BigInteger tester1IcxDeposit = BigInteger.TEN.pow(22);
        joinsICXBnusdLP(tester1, tester1IcxDeposit, tester1Loan);

        // Do one distribtion
        // day 2
        balanced.increaseDay(1);
        balanced.syncDistributions();
       
        //verify dao/reserver/bwt
        verifyRewards(tester1);

        //updateRewards
        // balanced.rewards._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));

        // try reclaim after upgrade
        verifyNoRewards(tester1);

        // claim unclaimed
        verifyRewards(tester2);

        //take loans and lp and verify rewards
        BalancedClient tester3 = balanced.newClient();
        BigInteger tester3Loan = BigInteger.TEN.pow(21);
        BigInteger tester3IcxDeposit = BigInteger.TEN.pow(22);

        tester3.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester3Loan, null, null);        
        joinsICXBnusdLP(tester3, tester3IcxDeposit, tester3Loan);

        balanced.increaseDay(1);
        balanced.syncDistributions();

        verifyRewards(tester1);
        verifyRewards(tester2);
        verifyRewards(tester3);

        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));
        
        owner.governance.setContinuousRewardsDay(owner.governance.getDay().add(BigInteger.valueOf(3)));
        balanced.increaseDay(1);
        balanced.syncDistributions();

        verifyRewards(tester1);
        verifyRewards(tester2);
        verifyRewards(tester3);

        BalancedClient tester4 = balanced.newClient();
        BigInteger tester4Loan = BigInteger.TEN.pow(21).multiply(BigInteger.valueOf(3));
        BigInteger tester4BnusdDeposit = BigInteger.TEN.pow(21);
        BigInteger tester4IcxDeposit = BigInteger.TEN.pow(22);
        tester4.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester4Loan, null, null);        
        joinsICXBnusdLP(tester4, tester4IcxDeposit, tester4BnusdDeposit);

        BalancedClient clientWithStakedLp = balanced.newClient();
        BalancedClient clientWithoutStakedLp = balanced.newClient();
        
        BigInteger bnusdDeposit = BigInteger.TEN.pow(21);
        tester4.bnUSD.transfer(clientWithStakedLp.getAddress(), bnusdDeposit, null); 
        tester4.bnUSD.transfer(clientWithoutStakedLp.getAddress(), bnusdDeposit, null); 
        BigInteger icxDeposit = BigInteger.TEN.pow(22);
        joinsICXBnusdLP(clientWithStakedLp, icxDeposit, bnusdDeposit);
        stakeICXBnusdLP(clientWithStakedLp);

        joinsICXBnusdLP(clientWithoutStakedLp, icxDeposit, bnusdDeposit);

        //day before ContinuousRewards
        balanced.increaseDay(1);
        balanced.syncDistributions();

        verifyRewards(tester1);
        verifyRewards(tester2);
        verifyRewards(tester3);
        verifyRewards(tester4);
        verifyRewards(clientWithStakedLp);
        verifyRewards(clientWithoutStakedLp);

        // day of migration 
        balanced.increaseDay(1);
        balanced.syncDistributions();

        verifyRewards(tester1);
        verifyRewards(tester2);
        verifyRewards(tester3);
        verifyRewards(tester4);
        verifyRewards(clientWithStakedLp);
        verifyRewards(clientWithoutStakedLp);

        Thread.sleep(1000);

        verifyRewards(tester1);
        verifyRewards(tester2);
        verifyRewards(tester3);
        verifyRewards(tester4);
        verifyRewards(clientWithStakedLp);
        verifyNoRewards(clientWithoutStakedLp);
        
        stakeICXBnusdLP(clientWithoutStakedLp);

        Thread.sleep(1000);
        verifyRewards(clientWithoutStakedLp);
    }


    private void joinsICXBnusdLP(BalancedClient client, BigInteger icxAmount, BigInteger bnusdAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.bnUSD.transfer(balanced.dex._address(), bnusdAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(balanced.sicx._address(), balanced.bnusd._address(), sicxDeposit, bnusdAmount, false);

    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(),icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }
    
    private void verifyRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);
    }

    private void verifyContinousRewards(BalancedClient client, BigInteger time) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);
    }

    private void verifyNoRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }


}
