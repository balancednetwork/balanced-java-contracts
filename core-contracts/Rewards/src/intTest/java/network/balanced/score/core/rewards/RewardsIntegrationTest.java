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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;

class RewardsIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static String dexJavaPath;
    private static String loansJavaPath;

    @BeforeAll    
    static void setup() throws Exception {
        dexJavaPath = System.getProperty("Dex");
        loansJavaPath = System.getProperty("Loans");

        System.setProperty("Rewards", System.getProperty("python"));
        System.setProperty("Dex", System.getProperty("dexPython"));
        System.setProperty("Loans", System.getProperty("loansPython"));
        
        balanced = new Balanced();
        balanced.deployBalanced();
        owner = balanced.ownerClient;
        
        balanced.increaseDay(1);
        owner.baln.toggleEnableSnapshot();

        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));
    }

    @Test
    void update() throws Exception {
        BalancedClient tester1 = balanced.newClient();
        BalancedClient tester2 = balanced.newClient();
        BalancedClient testerWithTooSmallLoan = balanced.newClient();
        BigInteger tester1Loan = BigInteger.TEN.pow(21);
        BigInteger tester2Loan = BigInteger.TEN.pow(21);
        tester1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester1Loan, null, null);        
        tester2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester2Loan, null, null);
        testerWithTooSmallLoan.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(19), null, null);
        
        BigInteger tester1SicxDeposit = BigInteger.TEN.pow(22);
        joinsICXBnusdLP(tester1, tester1SicxDeposit, tester1Loan);

        // Do one distribtion
        balanced.increaseDay(1);
        balanced.syncDistributions();

        //verify dao/reserve/bwt
        verifyRewards(tester1);

        // update Rewards contract
        balanced.rewards._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));
        owner.rewards.addDataProvider(balanced.stakedLp._address());
        owner.rewards.addDataProvider(balanced.dex._address());
        owner.rewards.addDataProvider(balanced.loans._address());

        // try reclaim after upgrade
        verifyNoRewards(tester1);

        // claim unclaimed
        verifyRewards(tester2);        
        verifyNoRewards(testerWithTooSmallLoan);

        //take loans and lp and verify rewards
        BalancedClient tester3 = balanced.newClient();
        BigInteger tester3Loan = BigInteger.TEN.pow(21);
        BigInteger tester3IcxDeposit = BigInteger.TEN.pow(22);

        tester3.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester3Loan, null, null);        
        joinsICXBnusdLP(tester3, tester3IcxDeposit, tester3Loan);

        balanced.increaseDay(1);
        balanced.syncDistributions();

        // Verify rewards after stability fund
        BigInteger tester1Rewards = verifyRewards(tester1);
        BigInteger tester2Rewards = verifyRewards(tester2);
        verifyRewards(tester3);

        depositToStabilityContract(tester2, BigInteger.TEN.pow(22));
        
        balanced.increaseDay(1);
        balanced.syncDistributions();

        // loans rewards stay the same
        assertEquals(tester1Rewards, verifyRewards(tester1));
        assertEquals(tester2Rewards, verifyRewards(tester2));
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
        verifyNoRewards(testerWithTooSmallLoan);
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

        verifyContinousRewards(tester1);
        verifyContinousRewards(tester2);
        verifyContinousRewards(tester3);
        verifyContinousRewards(tester4);
        verifyContinousRewards(testerWithTooSmallLoan);
        verifyContinousRewards(clientWithStakedLp);
        verifyNoRewards(clientWithoutStakedLp);
        
        testLpStakingAfterMigration(clientWithoutStakedLp);
        verifyStabilityContractHasNoEffectOnLoansRewards(tester2);
        closeLoansPostionAndVerifyNoRewards(tester2);
        openNewPostionsAndVerifyRewards();
    }
    
    private void verifyStabilityContractHasNoEffectOnLoansRewards(BalancedClient client) throws Exception {
        client.rewards.claimRewards();
        Thread.sleep(1000);
        BigInteger rewards = verifyContinousRewards(client);

        BigInteger totalBnusdSupply = owner.bnUSD.totalSupply();
        depositToStabilityContract(client, totalBnusdSupply);
        client.rewards.claimRewards();
        Thread.sleep(1400);
        //would be false if we used total supply
        assertTrue(rewards.compareTo(verifyContinousRewards(client)) < 0);
    }
    
    private void closeLoansPostionAndVerifyNoRewards(BalancedClient client) throws Exception {
        Map<String, String> assets = (Map<String, String>) client.loans.getAccountPositions(client.getAddress()).get("assets");
        BigInteger debt = Balanced.hexObjectToInt(assets.get("bnUSD"));
        BigInteger balance = client.bnUSD.balanceOf(client.getAddress());
        BigInteger bnusdNeeded = debt.subtract(balance);
        if (bnusdNeeded.compareTo(BigInteger.ZERO) > 0) {
            client.staking.stakeICX(bnusdNeeded.multiply(BigInteger.TWO), null, null);
            client.sicx.transfer(balanced.stability._address(), client.sicx.balanceOf(client.getAddress()), null);
        }

        client.loans.returnAsset("bnUSD", debt, true);
        client.rewards.claimRewards();
        Thread.sleep(100);
        verifyNoRewards(client);
    }

    private void openNewPostionsAndVerifyRewards() throws Exception {
        BalancedClient LoanTaker = balanced.newClient();
        BigInteger Loan = BigInteger.TEN.pow(21).multiply(BigInteger.valueOf(2));
        LoanTaker.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", Loan, null, null);        
        
        BalancedClient liquidityProvider = balanced.newClient();
        BigInteger bnusdDeposit = BigInteger.TEN.pow(21);
        BigInteger icxDeposit = BigInteger.TEN.pow(22);
        LoanTaker.bnUSD.transfer(liquidityProvider.getAddress(), bnusdDeposit, null); 
        joinsICXBnusdLP(liquidityProvider, icxDeposit, bnusdDeposit);
        stakeICXBnusdLP(liquidityProvider);

        Thread.sleep(100);
        verifyContinousRewards(LoanTaker);
        verifyContinousRewards(liquidityProvider);
    }

    private void testLpStakingAfterMigration(BalancedClient client) throws Exception {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        stakeICXBnusdLP(client);

        BigInteger unstakedBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        assertEquals(BigInteger.ZERO, unstakedBalance);
        
        Thread.sleep(100);
        verifyContinousRewards(client);

        BigInteger stakedBalance = client.stakedLp.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.stakedLp.unstake(icxBnusdPoolId, stakedBalance);
        unstakedBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);

        client.rewards.claimRewards();
        Thread.sleep(100);
        verifyNoRewards(client);
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

    private void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }
    
    private BigInteger verifyRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);

        return balancePostClaim.subtract(balancePreClaim);
    }

    private BigInteger verifyContinousRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);
        return balancePostClaim.subtract(balancePreClaim);
    }

    private void verifyNoRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }
}