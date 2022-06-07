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
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import foundation.icon.score.client.RevertedException;
import javafx.scene.effect.Light.Point;

import org.junit.jupiter.api.Order;

import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

class ContinousRewardsMigration implements ScoreIntegrationTest {
    private Balanced balanced;
    private BalancedClient owner;
    private String dexJavaPath;
    private String loansJavaPath;
    private String rewardsJavaPath;
    private String dividendsJavaPath;
    private BigInteger EXA = BigInteger.TEN.pow(18);

    @BeforeEach    
    void setup() throws Exception {
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

        System.setProperty("Rewards", rewardsJavaPath);
        System.setProperty("Dex", dexJavaPath);
        System.setProperty("Loans", loansJavaPath);
        System.setProperty("Dividends", dividendsJavaPath);

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

        nextDay();
        balanced.dividends._update(dividendsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setDividendsOnlyToStakedBalnDay(owner.dividends.getSnapshotId().add(BigInteger.ONE));
        updateRewards();

        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setContractAddresses();
    }

    @Test
    void migrateToContinuousRewards_base() throws Exception {
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
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(loansClient.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(stakedLPClient.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(unstakedLPClient.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(testerWithTooSmallLoan.getAddress()));
        //migrate
        nextDay();
        
        BigInteger rewardsDayOne = owner.rewards.getBalnHolding(unstakedLPClient.getAddress());
        verifyNoRewards(testerWithTooSmallLoan);

        nextDay();

        assertEquals(rewardsDayOne.multiply(BigInteger.TWO), owner.rewards.getBalnHolding(unstakedLPClient.getAddress()));

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
    }

    @Test
    void migrateToContinuousRewards_contractRewards() throws Exception {
        BigInteger daysToMigration = BigInteger.TWO;
        owner.governance.setContinuousRewardsDay(owner.governance.getDay().add(daysToMigration));

        BigInteger bwtInitalBalance = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daofundInitalBalance = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveInitalBalance= owner.baln.balanceOf(balanced.reserve._address());

        //migrate
        nextDay();
        
        BigInteger bwtDayOneRewards = owner.baln.balanceOf(balanced.bwt._address()).subtract(bwtInitalBalance);
        BigInteger daofundDayOneRewards = owner.baln.balanceOf(balanced.daofund._address()).subtract(daofundInitalBalance);
        BigInteger reserveDayOneRewards = owner.baln.balanceOf(balanced.reserve._address()).subtract(reserveInitalBalance);

        nextDay();
        owner.rewards.distribute((txr) -> {});
        assertEquals(owner.governance.getDay().add(BigInteger.ONE), balanced.hexObjectToInt(owner.rewards.distStatus().get("platform_day")));
        assertEquals(bwtDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.bwt._address()).subtract(bwtInitalBalance));
        assertEquals(daofundDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.daofund._address()).subtract(daofundInitalBalance));
        assertEquals(reserveDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.reserve._address()).subtract(reserveInitalBalance));

        Thread.sleep(100);

        owner.rewards.distribute((txr) -> {});
        assertEquals(owner.governance.getDay().add(BigInteger.ONE), balanced.hexObjectToInt(owner.rewards.distStatus().get("platform_day")));
        assertEquals(bwtDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.bwt._address()).subtract(bwtInitalBalance));
        assertEquals(daofundDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.daofund._address()).subtract(daofundInitalBalance));
        assertEquals(reserveDayOneRewards.multiply(BigInteger.valueOf(3)), owner.baln.balanceOf(balanced.reserve._address()).subtract(reserveInitalBalance));

        BigInteger prevDay = owner.governance.getDay();
        BigInteger rewardsBalance = owner.baln.balanceOf(balanced.rewards._address());
        BigInteger emission = owner.rewards.getEmission(prevDay.add(BigInteger.TWO));
        Map<String,BigInteger> recipientSplit = owner.rewards.recipientAt(prevDay.add(BigInteger.TWO));
        BigInteger contractRewardsPercentage = recipientSplit.get("DAOfund").add(recipientSplit.get("Reserve Fund")).add(recipientSplit.get("Worker Tokens"));
        BigInteger userRewards = emission.multiply(EXA.subtract(contractRewardsPercentage)).divide(EXA);

        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        BigInteger rewardsMintedToUsers = owner.baln.balanceOf(balanced.rewards._address()).subtract(rewardsBalance);
        assertEquals(rewardsMintedToUsers, userRewards);

        assertEquals(prevDay.add(BigInteger.TWO), balanced.hexObjectToInt(owner.rewards.distStatus().get("platform_day")));
        assertEquals(bwtDayOneRewards.multiply(BigInteger.valueOf(4)), owner.baln.balanceOf(balanced.bwt._address()).subtract(bwtInitalBalance));
        assertEquals(daofundDayOneRewards.multiply(BigInteger.valueOf(4)), owner.baln.balanceOf(balanced.daofund._address()).subtract(daofundInitalBalance));
        assertEquals(reserveDayOneRewards.multiply(BigInteger.valueOf(4)), owner.baln.balanceOf(balanced.reserve._address()).subtract(reserveInitalBalance));
    }

    @Test
    void migrateToContinuousRewards_UsersNotClaimingDuringMigration() throws Exception {

        BalancedClient loansClientCollecting = balanced.newClient();
        BalancedClient loansClientClaiming = balanced.newClient();
        BalancedClient lpClientCollecting = balanced.newClient();
        BalancedClient lpClientClaiming = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);
        
        loansClientCollecting.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", lpAmount, null, null);
        loansClientClaiming.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", lpAmount, null, null);

        loansClientCollecting.bnUSD.transfer(lpClientCollecting.getAddress(), lpAmount, null);
        loansClientClaiming.bnUSD.transfer(lpClientClaiming.getAddress(), lpAmount, null);

        joinsICXBnusdLP(lpClientCollecting, lpAmount, lpAmount);
        joinsICXBnusdLP(lpClientClaiming, lpAmount, lpAmount);
        // stakeICXBnusdLP(stakedLPClient);

        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(loansClientCollecting.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(loansClientClaiming.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(lpClientCollecting.getAddress()));
        assertEquals(BigInteger.ZERO, owner.rewards.getBalnHolding(lpClientClaiming.getAddress()));

        // 5 days
        for (int i = 0; i < 5; i++) {
            nextDay();
            verifyRewards(loansClientClaiming);
            verifyRewards(lpClientClaiming);
        }
        assertEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );

        Map<String,BigInteger> recipientSplit = owner.rewards.getRecipientsSplit();
        BigInteger contractRewardsPercentage = recipientSplit.get("DAOfund").add(recipientSplit.get("Reserve Fund")).add(recipientSplit.get("Worker Tokens"));
        DistributionPercentage[] distPercentages = new DistributionPercentage[recipientSplit.size()];

        int index = 0;
        BigInteger change = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(16));
        for (Map.Entry<String, BigInteger> entry : recipientSplit.entrySet()) {
            DistributionPercentage dist = new DistributionPercentage();
            dist.recipient_name = entry.getKey();
            dist.dist_percent = entry.getValue();

            if (entry.getKey().equals("sICX/bnUSD")) {
                dist.dist_percent = entry.getValue().add(change);
            } else if (entry.getKey().equals("Loans")) {
                dist.dist_percent = entry.getValue().subtract(change);
            }
            distPercentages[index] = dist;
            index = index + 1;
        }

        nextDay();
        BigInteger loansRewardsPreChange = verifyRewards(loansClientClaiming);
        BigInteger lpRewardsPreChange = verifyRewards(lpClientClaiming);

        owner.governance.updateBalTokenDistPercentage(distPercentages);

        nextDay();
        BigInteger loansRewardsPostChange = verifyRewards(loansClientClaiming);
        BigInteger lpRewardsPostChange = verifyRewards(lpClientClaiming);

        assertTrue(loansRewardsPostChange.compareTo(loansRewardsPreChange) < 0);
        assertTrue(lpRewardsPostChange.compareTo(lpRewardsPreChange) > 0);

        // 5 days
        for (int i = 0; i < 5; i++) {
            nextDay();
            verifyRewards(loansClientClaiming);
            verifyRewards(lpClientClaiming);
        }

        assertEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );

        BigInteger daysToMigration = BigInteger.TWO;
        owner.governance.setContinuousRewardsDay(owner.governance.getDay().add(daysToMigration));

        //migrate
        nextDay();
        verifyRewards(loansClientClaiming);
        verifyRewards(lpClientClaiming);

        nextDay();
        verifyRewards(loansClientClaiming);
        verifyRewards(lpClientClaiming);

        BigInteger rewardsPreStake = owner.baln.balanceOf(lpClientClaiming.getAddress());
        stakeICXBnusdLP(lpClientClaiming);
        Thread.sleep(100);

        verifyRewards(loansClientClaiming);
        verifyRewards(lpClientClaiming);
        balanced.increaseDay(1);

        verifyRewards(lpClientClaiming);

        assertNotEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(EXA), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(EXA)
        );

        verifyRewards(loansClientClaiming);
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(EXA), 
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(EXA)
        );

        assertEquals(
            rewardsPreStake.divide(EXA), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(EXA)
        );
        
        verifyRewards(loansClientClaiming);
        verifyRewards(loansClientCollecting);
        verifyRewards(lpClientClaiming);
        verifyRewards(lpClientCollecting);

        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(EXA), 
            owner.baln.balanceOf(loansClientCollecting.getAddress()).divide(EXA)
        );

        assertEquals(
            rewardsPreStake.divide(EXA), 
            owner.baln.balanceOf(lpClientCollecting.getAddress()).divide(EXA)
        );

        verifyNoRewards(lpClientCollecting);
        stakeICXBnusdLP(lpClientCollecting);
        verifyRewards(lpClientCollecting);
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

   
    private void nextDay() {
        balanced.increaseDay(1);
        balanced.syncDistributions();
    }
}