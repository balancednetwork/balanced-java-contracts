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

package network.balanced.score.core.systemtest;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.valueOf;

import java.math.BigInteger;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import foundation.icon.score.client.RevertedException;
import javafx.scene.effect.Light.Point;

import org.junit.jupiter.api.Order;

import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

class Systemtest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static String dexJavaPath;
    private static String loansJavaPath;
    private static String rewardsJavaPath;
    private static String dividendsJavaPath;
    private static BigInteger EXA = BigInteger.TEN.pow(18);

    @BeforeAll    
    static void setup() throws Exception {
        dexJavaPath = System.getProperty("Dex");
        loansJavaPath = System.getProperty("Loans");
        rewardsJavaPath = System.getProperty("Rewards");
        dividendsJavaPath = System.getProperty("Dividends");

        System.setProperty("Rewards", System.getProperty("rewardsPython"));
        System.setProperty("Dex", System.getProperty("dexPython"));
        System.setProperty("Loans", System.getProperty("loansPython"));
        System.setProperty("Dividends", System.getProperty("dividendsPython"));
        
        balanced = new Balanced();
        balanced.deployBalanced();
        owner = balanced.ownerClient;
        
        balanced.increaseDay(1);
        owner.baln.toggleEnableSnapshot();

        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });
        owner.governance.setFeeProcessingInterval(BigInteger.ZERO);
        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);
    }

    @Test
    @Order(1)
    void dividendsMigration() throws Exception {
        BalancedClient stakingClient = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient lpClient2 = balanced.newClient();
        BalancedClient feeGenerator = balanced.newClient();
        stakingClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);        
        lpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        lpClient2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);

        nextDay();
        
        verifyRewards(stakingClient);
        stakeBaln(stakingClient);

        BigInteger lpClientRewards = verifyRewards(lpClient);
        BigInteger lpClient2Rewards = verifyRewards(lpClient2);
        joinsICXBalnLP(lpClient, lpClientRewards, lpClientRewards);
        joinsICXBalnLP(lpClient2, lpClient2Rewards, lpClient2Rewards);
        
        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        nextDay();

        verifyBnusdFees(stakingClient);
        verifyBnusdFees(lpClient);
        verifyDaofundBnusdDividends();

        balanced.dividends._update(dividendsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setDividendsOnlyToStakedBalnDay(owner.dividends.getSnapshotId().add(BigInteger.ONE));
        
        verifyBnusdFees(lpClient2);
        
        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        nextDay();
        
        verifyBnusdFees(stakingClient);
        verifyBnusdFees(lpClient);
        verifyDaofundBnusdDividends();

        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        nextDay();

        verifyBnusdFees(stakingClient);
        verifyBnusdFees(lpClient2);
        verifyNoBnusdFees(lpClient2);
        verifyNoBnusdFees(lpClient);
        verifyDaofundBnusdDividends();

        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        nextDay();

        verifyBnusdFees(stakingClient);
        verifyNoBnusdFees(lpClient2);
        verifyNoBnusdFees(lpClient);
        verifyDaofundBnusdDividends();
    }
    

    @Test
    @Order(2)
    void stabilityFundEffect_beforeContinuous() throws Exception {
        //open diffrent kinds of postions
        BalancedClient client = balanced.newClient();
        BalancedClient stabilityClient = balanced.newClient();
        client.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        
        nextDay();

        BigInteger rewardsAmount = verifyRewards(client);
        depositToStabilityContract(stabilityClient, BigInteger.TEN.pow(22));
        
        nextDay();

        assertEquals(rewardsAmount, verifyRewards(client));
    }

    @Test
    @Order(3)
    void rewardsUpdate() throws Exception {
        BalancedClient loansClient = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient loansAndlpClient = balanced.newClient();
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        
        loansClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansAndlpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient.bnUSD.transfer(lpClient.getAddress(), loanAmount, null);

        joinsICXBnusdLP(lpClient, loanAmount, loanAmount);
        joinsICXBnusdLP(loansAndlpClient, loanAmount, loanAmount);

        nextDay();

        // verify rewards can be claimed before or after update
        verifyRewards(loansClient);
        verifyRewards(lpClient);

        updateRewards();

        verifyNoRewards(loansClient);
        verifyNoRewards(lpClient);
        verifyRewards(loansAndlpClient);
        BigInteger bwtBalancePreDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePreDist = owner.baln.balanceOf(balanced.reserve._address());

        // verify rewards still work
        nextDay();

        verifyRewards(loansClient);
        verifyRewards(lpClient);
        verifyRewards(loansAndlpClient);

        BigInteger bwtBalancePostDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePostDist = owner.baln.balanceOf(balanced.reserve._address());

        assertTrue(bwtBalancePostDist.compareTo(bwtBalancePreDist) > 0);
        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
        assertTrue(reserveBalancePostDist.compareTo(reserveBalancePreDist) > 0);
    }

    @Test
    @Order(4)
    void updateLoansAndDex() throws Exception {
        BalancedClient loansClient = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient loansAndlpClient = balanced.newClient();
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        
        loansClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansAndlpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient.bnUSD.transfer(lpClient.getAddress(), loanAmount, null);

        joinsICXBnusdLP(lpClient, loanAmount, loanAmount);
        joinsICXBnusdLP(loansAndlpClient, loanAmount, loanAmount);

        nextDay();

        verifyRewards(loansClient);
        verifyRewards(lpClient);
        verifyRewards(loansAndlpClient);

        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setAddressesOnContract("dex");
        assertEquals(balanced.stakedLp._address().toString(), owner.dex.getStakedLp().toString());

        nextDay();

        verifyRewards(loansClient);
        verifyRewards(lpClient);
        verifyRewards(loansAndlpClient);
    }

    @Test
    @Order(5)
    void stakingLp() throws Exception {
        BalancedClient loansClient = balanced.newClient();
        BalancedClient stakedLPClient = balanced.newClient();
        BalancedClient unstakedLPClient = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);
        
        loansClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", lpAmount.multiply(BigInteger.TWO), null, null);
        loansClient.bnUSD.transfer(stakedLPClient.getAddress(), lpAmount, null);
        loansClient.bnUSD.transfer(unstakedLPClient.getAddress(), lpAmount, null);

        joinsICXBnusdLP(stakedLPClient, lpAmount, lpAmount);
        stakeICXBnusdLP(stakedLPClient);
        joinsICXBnusdLP(unstakedLPClient, lpAmount, lpAmount);
        Assertions.assertThrows(UserRevertedException.class, () -> leaveICXBnusdLP(unstakedLPClient));

        nextDay();

        verifyRewards(loansClient);
        verifyRewards(unstakedLPClient);
        verifyRewards(stakedLPClient);
    }

    @Test
    @Order(6)
    void migrateToContinuousRewards() throws Exception {
        BigInteger daysToMigration = BigInteger.TWO;
        owner.governance.setContinuousRewardsDay(owner.governance.getDay().add(daysToMigration));

        BalancedClient loansClient = balanced.newClient();
        BalancedClient stakedLPClient = balanced.newClient();
        BalancedClient unstakedLPClient = balanced.newClient();
        BalancedClient testerWithTooSmallLoan = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);
        BigInteger toSmallLoan = BigInteger.TEN.pow(19);
        
        loansClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", lpAmount.multiply(BigInteger.TWO), null, null);
        testerWithTooSmallLoan.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", toSmallLoan, null, null);

        loansClient.bnUSD.transfer(stakedLPClient.getAddress(), lpAmount, null);
        loansClient.bnUSD.transfer(unstakedLPClient.getAddress(), lpAmount, null);

        joinsICXBnusdLP(stakedLPClient, lpAmount, lpAmount);
        stakeICXBnusdLP(stakedLPClient);
        joinsICXBnusdLP(unstakedLPClient, lpAmount, lpAmount);

        //migrate
        nextDay();
        
        verifyNoRewards(testerWithTooSmallLoan);

        nextDay();

        verifyRewards(loansClient);
        verifyRewards(stakedLPClient);
        verifyRewards(unstakedLPClient);

        Thread.sleep(100);

        verifyRewards(loansClient);
        verifyRewards(testerWithTooSmallLoan);
        verifyRewards(stakedLPClient);
        verifyNoRewards(unstakedLPClient);

        stakeICXBnusdLP(unstakedLPClient);
        Thread.sleep(100);
        verifyRewards(unstakedLPClient);

        closeLoansPostionAndVerifyNoRewards(loansClient);
        verifyContractRewards();
    }
    
    @Test
    @Order(7)
    void openNewPostionsAndVerifyRewards() throws Exception {
        BalancedClient loanTaker = balanced.newClient();
        BigInteger loan = BigInteger.TEN.pow(21).multiply(BigInteger.valueOf(2));
        loanTaker.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loan, null, null);        

        BalancedClient liquidityProvider = balanced.newClient();
        BigInteger bnusdDeposit = BigInteger.TEN.pow(21);
        BigInteger icxDeposit = BigInteger.TEN.pow(22);
        loanTaker.bnUSD.transfer(liquidityProvider.getAddress(), bnusdDeposit, null); 
        joinsICXBnusdLP(liquidityProvider, icxDeposit, bnusdDeposit);

        Thread.sleep(100);
        verifyRewards(loanTaker);
        verifyNoRewards(liquidityProvider);

        stakeICXBnusdLP(liquidityProvider);
        Thread.sleep(100);
        verifyRewards(liquidityProvider);
    }

    @Test
    @Order(8)
    void verifyStabilityContractHasNoEffectOnLoansRewards() throws Exception {
        BalancedClient loanTaker = balanced.newClient();
        BigInteger loan = BigInteger.TEN.pow(21).multiply(BigInteger.valueOf(2));
        loanTaker.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loan, null, null);

        BigInteger totalLoansSupplyPre = owner.loans.getBalanceAndSupply("Loans", owner.getAddress()).get("_totalSupply");

        loanTaker.rewards.claimRewards();
        Thread.sleep(100);
        BigInteger rewards = verifyRewards(loanTaker);
  
        BigInteger totalBnusdSupply = owner.bnUSD.totalSupply();
        depositToStabilityContract(owner, totalBnusdSupply);
        loanTaker.rewards.claimRewards();
        Thread.sleep(150);

        //would be false if we used total supply
        assertTrue(rewards.compareTo(verifyRewards(loanTaker)) < 0);

        BigInteger totalLoansSupplyPost = owner.loans.getBalanceAndSupply("Loans", owner.getAddress()).get("_totalSupply");
        assertEquals(totalLoansSupplyPre, totalLoansSupplyPost);
    }


    @Test
    @Order(9)
    void stakingAndUnstakingLp() throws Exception {
        BalancedClient lpClient = balanced.newClient();
        BalancedClient lpBuyer = balanced.newClient();
        
        BigInteger lpAmount = BigInteger.TEN.pow(22);
        depositToStabilityContract(lpClient, lpAmount.multiply(BigInteger.TWO));
        
        joinsICXBnusdLP(lpClient, lpAmount, lpAmount);
        stakeICXBnusdLP(lpClient);
        Thread.sleep(100);
        
        unstakeICXBnusdLP(lpClient);
        verifyRewards(lpClient);
        Thread.sleep(100);

        verifyNoRewards(lpClient);
        verifyNoRewards(lpBuyer);

        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = lpClient.dex.balanceOf(lpClient.getAddress(), icxBnusdPoolId);
        lpClient.dex.transfer(lpBuyer.getAddress(), poolBalance, icxBnusdPoolId, null);
        stakeICXBnusdLP(lpBuyer);
        Thread.sleep(100);

        verifyNoRewards(lpClient);
        verifyRewards(lpBuyer);

        unstakeICXBnusdLP(lpBuyer);
        leaveICXBnusdLP(lpBuyer);
    }

    @Test
    @Order(10)
    void rebalancingRaisePrice() throws Exception {
        BalancedClient loanTaker = balanced.newClient();
        BigInteger loan = BigInteger.TEN.pow(22);
        loanTaker.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loan, null, null);
        Map<String, BigInteger> originalBalanceAndSupply = loanTaker.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());

        reducePriceBelowThreshold();
        rebalance();

        Map<String, BigInteger> balanceAndSupply = loanTaker.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertTrue(originalBalanceAndSupply.get("_totalSupply").compareTo(balanceAndSupply.get("_totalSupply")) > 0);
        assertTrue(originalBalanceAndSupply.get("_balance").compareTo(balanceAndSupply.get("_balance")) > 0);
    }

    @Test
    @Order(11)
    void rebalancingLowerPrice() throws Exception {
        BalancedClient loanTaker = balanced.newClient();
        BigInteger loan = BigInteger.TEN.pow(22);
        loanTaker.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loan, null, null);
        Map<String, BigInteger> originalBalanceAndSupply = loanTaker.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());

        raisePriceAboveThreshold();
        rebalance();

        Map<String, BigInteger> balanceAndSupply = loanTaker.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertTrue(originalBalanceAndSupply.get("_totalSupply").compareTo(balanceAndSupply.get("_totalSupply")) < 0);
        assertTrue(originalBalanceAndSupply.get("_balance").compareTo(balanceAndSupply.get("_balance")) < 0);
    }

    @Test
    @Order(12)
    void LiquidateAndRetirebadDebt() throws Exception {
         BalancedClient voter = balanced.newClient();
        BigInteger POINTS = BigInteger.valueOf(10_000);

        depositToStabilityContract(voter, BigInteger.TEN.pow(18));
        
        BigInteger lockingRatio = BigInteger.valueOf(8500);
        JsonObject setLockingRatioParameters = new JsonObject()
        .add("_value", lockingRatio.intValue());
        
        JsonArray setLockingRatioCall = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParameters);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCall);
        executeVoteActions(balanced, voter, "setLockingRatio", actions);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = Balanced.hexObjectToInt(owner.loans.getParameters().get("origination fee"));
        BigInteger loan = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan, null, null);
        
        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress());
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        BigInteger balancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger balancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());

        assertTrue(balancePreRetire.compareTo(balancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);

        Map<String, Object> position = owner.loans.getAccountPositions(loanTaker.getAddress());

        assertTrue(((Map<String,Object>)position.get("assets")).isEmpty());
    }

    @Test
    @Order(13)
    void dividendsAfterUpdate() throws Exception {
        BalancedClient stakingClient = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient feeGenerator = balanced.newClient();
        stakingClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);        
        lpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);

        balanced.increaseDay(1);
        owner.dividends.distribute((tx) -> {});
        
        verifyRewards(stakingClient);
        stakeBaln(stakingClient);

        BigInteger lpClientRewards = verifyRewards(lpClient);
        joinsICXBalnLP(lpClient, lpClientRewards, lpClientRewards);
        
        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);

        balanced.increaseDay(1);
        owner.dividends.distribute((tx) -> {});

        verifyBnusdFees(stakingClient);
        verifyNoBnusdFees(lpClient);
        verifyDaofundBnusdDividends();

        feeGenerator.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        feeGenerator.staking.stakeICX(BigInteger.TEN.pow(22), null, null);
        
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.baln._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);
        feeGenerator.sicx.transfer(balanced.dex._address(), feeGenerator.sicx.balanceOf(feeGenerator.getAddress()), swapData.toString().getBytes());

        swapData = Json.object();
        swapParams = Json.object();
        swapParams.add("toToken", balanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);
        feeGenerator.baln.transfer(balanced.dex._address(), feeGenerator.baln.balanceOf(feeGenerator.getAddress()), swapData.toString().getBytes());

        balanced.increaseDay(1);
        owner.dividends.distribute((tx) -> {});

        verifyDaofundAllDivTypes();
        verifyAllFees(stakingClient);
        verifyNoBnusdFees(lpClient);
    }

    private void updateRewards() {
        balanced.rewards._update(rewardsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.rewards.addDataProvider(balanced.stakedLp._address());
        owner.rewards.addDataProvider(balanced.dex._address());
        owner.rewards.addDataProvider(balanced.loans._address());
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

    private void verifyContractRewards() throws Exception {
        BigInteger bwtBalancePreDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePreDist = owner.baln.balanceOf(balanced.reserve._address());
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        BigInteger bwtBalancePostDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePostDist = owner.baln.balanceOf(balanced.reserve._address());

        assertTrue(bwtBalancePostDist.compareTo(bwtBalancePreDist) > 0);
        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
        assertTrue(reserveBalancePostDist.compareTo(reserveBalancePreDist) > 0);
    }

    private void verifyDaofundBnusdDividends() throws Exception {
        BigInteger daoFundBalancePreDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        owner.dividends.transferDaofundDividends(0, 0);
        BigInteger daoFundBalancePostDist = owner.bnUSD.balanceOf(balanced.daofund._address());

        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
    }

    private void verifyDaofundAllDivTypes() throws Exception {
        BigInteger daoFundBnusdBalancePreDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundBalnBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger daoFundSicxBalancePreDist = owner.sicx.balanceOf(balanced.daofund._address());
        owner.dividends.transferDaofundDividends(0, 0);
        BigInteger daoFundBnusdBalancePostDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundBalnBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger daoFundSicxBalancePostDist = owner.sicx.balanceOf(balanced.daofund._address());

        assertTrue(daoFundBnusdBalancePostDist.compareTo(daoFundBnusdBalancePreDist) > 0);
        assertTrue(daoFundBalnBalancePostDist.compareTo(daoFundBalnBalancePreDist) > 0);
        assertTrue(daoFundSicxBalancePostDist.compareTo(daoFundSicxBalancePreDist) > 0);
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

    private void leaveICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.remove(icxBnusdPoolId, poolBalance, true);
    }

    private void joinsICXBalnLP(BalancedClient client, BigInteger icxAmount, BigInteger balnAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.baln.transfer(balanced.dex._address(), balnAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(balanced.baln._address(), balanced.sicx._address(), balnAmount, sicxDeposit, false);
    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }

    private void unstakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.stakedLp.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.stakedLp.unstake(icxBnusdPoolId, poolBalance);
    }

    private void stakeICXBalnLP(BalancedClient client) {
        BigInteger icxBalnPoolId = owner.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBalnPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBalnPoolId, null);
    }

    private void stakeBaln(BalancedClient client) {
        BigInteger balance = client.baln.balanceOf(client.getAddress());
        client.baln.stake(balance);
    }

    private void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }
    
    private BigInteger verifyRewards(BalancedClient client) {
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

    private void verifyBnusdFees(BalancedClient client) {
        BigInteger balancePreClaim = client.bnUSD.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger balancePostClaim = client.bnUSD.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);
    }

    private void verifyNoBnusdFees(BalancedClient client) {
        BigInteger balancePreClaim = client.bnUSD.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger balancePostClaim = client.bnUSD.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }

    private void verifyAllFees(BalancedClient client) {
        BigInteger bnusdBalancePreDist = owner.bnUSD.balanceOf(client.getAddress());
        BigInteger balnBalancePreDist = owner.baln.balanceOf(client.getAddress());
        BigInteger sicxBalancePreDist = owner.sicx.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger bnusdBalancePostDist = owner.bnUSD.balanceOf(client.getAddress());
        BigInteger balnBalancePostDist = owner.baln.balanceOf(client.getAddress());
        BigInteger sicxBalancePostDist = owner.sicx.balanceOf(client.getAddress());
    
        assertTrue(bnusdBalancePostDist.compareTo(bnusdBalancePreDist) > 0);
        assertTrue(balnBalancePostDist.compareTo(balnBalancePreDist) > 0);
        assertTrue(sicxBalancePostDist.compareTo(sicxBalancePreDist) > 0);
    }
   
    private void nextDay() {
        balanced.increaseDay(1);
        balanced.syncDistributions();
    }

    private void rebalance() throws Exception {
        BalancedClient rebalancer = balanced.newClient();
        while (true) {
            BigInteger threshold = calculateThreshold();
            owner.rebalancing.rebalance();
            if (threshold.equals(calculateThreshold())) {
                return;
            }

            System.out.println("rebalanced");
        }
   
    }

    private void reducePriceBelowThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.multiply(BigInteger.valueOf(105))) < 0) {
            reduceBnusdPrice();
        }
    }

    private void raisePriceAboveThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.negate().multiply(BigInteger.valueOf(105))) > 0) {
            raiseBnusdPrice();
        }
    }

    private BigInteger calculateThreshold() {
        BigInteger bnusdPriceInIcx = owner.bnUSD.lastPriceInLoop();
        BigInteger sicxPriceInIcx = owner.sicx.lastPriceInLoop();

        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(icxBnusdPoolId);
        BigInteger sicxLiquidity = Balanced.hexObjectToInt(poolStats.get("base"));
        BigInteger bnusdLiquidity = Balanced.hexObjectToInt(poolStats.get("quote"));

        BigInteger actualBnusdPriceInSicx = bnusdPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
        BigInteger bnusdPriceInSicx = sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage = (actualBnusdPriceInSicx.subtract(bnusdPriceInSicx)).multiply(EXA).divide(actualBnusdPriceInSicx);

        return priceDifferencePercentage;
    }

    private void reduceBnusdPrice() throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        depositToStabilityContract(sellerClient, BigInteger.TEN.pow(22));
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.sicx._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.bnUSD.transfer(balanced.dex._address(), amountToSell, swapData.toString().getBytes());
    }

    private void raiseBnusdPrice() throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        sellerClient.staking.stakeICX(amountToSell.multiply(BigInteger.TWO), null, null);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.sicx.transfer(balanced.dex._address(), amountToSell, swapData.toString().getBytes());
    }
}