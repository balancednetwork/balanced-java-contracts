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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.Address;

class RewardsUpdate implements ScoreIntegrationTest {
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
    }

    @Test
    void rewardsUpdate_simple() throws Exception {
        BalancedClient loansClientClaimsBefore = balanced.newClient();
        BalancedClient loansClientClaimsAfter = balanced.newClient();
        BalancedClient loansClientCollecting = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient loansAndlpClient = balanced.newClient();
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        
        loansClientClaimsBefore.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClientClaimsAfter.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClientCollecting.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansAndlpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClientClaimsBefore.bnUSD.transfer(lpClient.getAddress(), loanAmount, null);

        joinsICXBnusdLP(lpClient, loanAmount, loanAmount);
        joinsICXBnusdLP(loansAndlpClient, loanAmount, loanAmount);

        nextDay();

        // verify rewards can be claimed before or after update
        verifyRewards(loansClientClaimsBefore);
        verifyRewards(lpClient);

        verifyExternalAndUpdateRewards(lpClient);

        verifyNoRewards(loansClientClaimsBefore);
        verifyNoRewards(lpClient);
        verifyRewards(loansAndlpClient);
        verifyRewards(loansClientClaimsAfter);

        assertEquals(
            owner.baln.balanceOf(loansClientClaimsBefore.getAddress()).divide(BigInteger.TEN), 
            owner.baln.balanceOf(loansClientClaimsAfter.getAddress()).divide(BigInteger.TEN)
        );

        assertEquals(
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.TEN), 
            owner.baln.balanceOf(loansClientClaimsAfter.getAddress()).divide(BigInteger.TEN)
        );

        assertEquals(
            owner.rewards.getBalnHolding(loansClientClaimsBefore.getAddress()), 
            BigInteger.ZERO
        );

        assertEquals(
            owner.rewards.getBalnHolding(loansClientClaimsAfter.getAddress()), 
            BigInteger.ZERO
        );

        BigInteger bwtBalancePreDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePreDist = owner.baln.balanceOf(balanced.reserve._address());

        nextDay();

        verifyRewards(loansClientClaimsBefore);
        verifyRewards(loansClientClaimsAfter);
        verifyRewards(lpClient);
        verifyRewards(loansAndlpClient);


        assertEquals(
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.TEN), 
            owner.baln.balanceOf(loansClientClaimsAfter.getAddress()).divide(BigInteger.TEN)
        );


        BigInteger bwtBalancePostDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePostDist = owner.baln.balanceOf(balanced.reserve._address());

        assertTrue(bwtBalancePostDist.compareTo(bwtBalancePreDist) > 0);
        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
        assertTrue(reserveBalancePostDist.compareTo(reserveBalancePreDist) > 0);
    }

    @Test
    void verifyExternalAndUpdateRewards_usersNotClaiming_distChanging() throws Exception {
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

        verifyExternalAndUpdateRewards(loansClientCollecting);

        assertEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );

        nextDay();

        verifyRewards(loansClientClaiming);
        verifyRewards(lpClientClaiming);

        assertEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(lpClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.rewards.getBalnHolding(loansClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );

        verifyRewards(loansClientCollecting);
        verifyRewards(lpClientCollecting);

        assertEquals(
            owner.baln.balanceOf(lpClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.baln.balanceOf(lpClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
        assertEquals(
            owner.baln.balanceOf(loansClientClaiming.getAddress()).divide(BigInteger.valueOf(100)), 
            owner.baln.balanceOf(loansClientCollecting.getAddress()).divide(BigInteger.valueOf(100))
        );
    }
    

    private void verifyExternalAndUpdateRewards(BalancedClient clientInFocus) {
        BigInteger day = owner.governance.getDay();
        BigInteger emissionPre = owner.rewards.getEmission(day);
        BigInteger emission1Pre = owner.rewards.getEmission(BigInteger.valueOf(59));
        BigInteger emission2Pre = owner.rewards.getEmission(BigInteger.valueOf(60));
        BigInteger emission3Pre = owner.rewards.getEmission(BigInteger.valueOf(61));
        BigInteger emission4Pre = owner.rewards.getEmission(BigInteger.valueOf(423));
        BigInteger emission5Pre = owner.rewards.getEmission(BigInteger.valueOf(853));
        BigInteger emission6Pre = owner.rewards.getEmission(BigInteger.valueOf(923));
        BigInteger emission7Pre = owner.rewards.getEmission(BigInteger.valueOf(1192));
        BigInteger emission8Pre = owner.rewards.getEmission(BigInteger.valueOf(1251));
        Map<String, BigInteger> balnHoldingsPre = owner.rewards.getBalnHoldings(new Address[] {clientInFocus.getAddress()});
        BigInteger balnHoldingPre = owner.rewards.getBalnHolding(clientInFocus.getAddress());
        Map<String, Object> distStatusPre = owner.rewards.distStatus();
        List<String> dataSourceNamesPre = owner.rewards.getDataSourceNames();
        List<String> recipientsPre = owner.rewards.getRecipients();
        Map<String, BigInteger> recipientsSplitPre = owner.rewards.getRecipientsSplit();
        Map<String, Map<String, Object>> dataSourcesPre = owner.rewards.getDataSources();
        Map<String, Object> sourceDataPre = owner.rewards.getSourceData("Loans");
        Map<String, BigInteger> recipientAtPre = owner.rewards.recipientAt(day);
        Map<String, BigInteger> recipientAtLastDayPre = owner.rewards.recipientAt(day.subtract(BigInteger.ONE));
       
        balanced.rewards._update(rewardsJavaPath, Map.of("_governance", balanced.governance._address()));
       
        BigInteger emissionPost = owner.rewards.getEmission(day);
        BigInteger emission1Post = owner.rewards.getEmission(BigInteger.valueOf(59));
        BigInteger emission2Post = owner.rewards.getEmission(BigInteger.valueOf(60));
        BigInteger emission3Post = owner.rewards.getEmission(BigInteger.valueOf(61));
        BigInteger emission4Post = owner.rewards.getEmission(BigInteger.valueOf(423));
        BigInteger emission5Post = owner.rewards.getEmission(BigInteger.valueOf(853));
        BigInteger emission6Post = owner.rewards.getEmission(BigInteger.valueOf(923));
        BigInteger emission7Post = owner.rewards.getEmission(BigInteger.valueOf(1192));
        BigInteger emission8Post = owner.rewards.getEmission(BigInteger.valueOf(1251));

        Map<String, BigInteger> balnHoldingsPost = owner.rewards.getBalnHoldings(new Address[] {clientInFocus.getAddress()});
        BigInteger balnHoldingPost = owner.rewards.getBalnHolding(clientInFocus.getAddress());
        Map<String, Object> distStatusPost = owner.rewards.distStatus();
        List<String> dataSourceNamesPost = owner.rewards.getDataSourceNames();
        List<String> recipientsPost = owner.rewards.getRecipients();
        Map<String, BigInteger> recipientsSplitPost = owner.rewards.getRecipientsSplit();
        Map<String, Map<String, Object>> dataSourcesPost = owner.rewards.getDataSources();
        Map<String, Object> sourceDataPost = owner.rewards.getSourceData("Loans");
        Map<String, BigInteger> recipientAtPost = owner.rewards.recipientAt(day);
        Map<String, BigInteger> recipientAtLastDayPost = owner.rewards.recipientAt(day.subtract(BigInteger.ONE));

        assertEquals(emissionPre, emissionPost);
        assertEquals(emission1Pre, emission1Post);
        assertEquals(emission2Pre, emission2Post);
        assertEquals(emission3Pre, emission3Post);
        assertEquals(emission4Pre, emission4Post);
        assertEquals(emission5Pre, emission5Post);
        assertEquals(emission6Pre, emission6Post);
        assertEquals(emission7Pre, emission7Post);
        assertEquals(emission8Pre, emission8Post);
        assertEquals(balnHoldingsPre.toString(), balnHoldingsPost.toString());
        assertEquals(balnHoldingPre, balnHoldingPost);
        assertEquals(distStatusPre.toString(), distStatusPost.toString());
        assertEquals(dataSourceNamesPre.toString(), dataSourceNamesPost.toString());
        assertEquals(recipientsPre.toString(), recipientsPost.toString());
        assertEquals(recipientsSplitPre.toString(), recipientsSplitPost.toString());
        assertEquals(dataSourcesPre.toString(), dataSourcesPost.toString());
        assertEquals(sourceDataPre.toString(), sourceDataPost.toString());
        assertEquals(recipientAtPre.toString(), recipientAtPost.toString());
        assertEquals(recipientAtLastDayPre.toString(), recipientAtLastDayPost.toString());

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