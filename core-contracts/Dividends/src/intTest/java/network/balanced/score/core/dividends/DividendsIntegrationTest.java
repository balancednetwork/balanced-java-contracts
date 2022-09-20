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

package network.balanced.score.core.dividends;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;

import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.dummyConsumer;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DividendsIntegrationTest {
    static Wallet tester;
    static Wallet tester2;
    static Wallet tester_bbaln;
    static Wallet tester_bbaln2;
    static Wallet tester_bbaln3;
    static KeyWallet owner;
    static Balanced balanced;

    static DividendsScoreClient dividends;
    static LoansScoreClient loans;
    static DexScoreClient dex;
    static BalancedDollarScoreClient bnusd;
    static StakingScoreClient staking;
    static SicxScoreClient sicx;
    static BalancedTokenScoreClient baln;
    static GovernanceScoreClient governance;
    static BoostedBalnScoreClient boostedBaln;
    static BoostedBalnScoreClient boostedBaln2;


    static BalancedTokenScoreClient testerScore;
    static DexScoreClient testerScoreDex;
    static SicxScoreClient testerScoreSicx;
    static BalancedTokenScoreClient testerScoreBaln;
    static BalancedTokenScoreClient balnTesterScore;
    static BalancedTokenScoreClient balnTesterScore2;
    static BalancedTokenScoreClient balnTesterScore3;
    static DividendsScoreClient bbalntesterScoreDividends2;
    static DividendsScoreClient bbalntesterScoreDividends3;
    static DividendsScoreClient bbalntesterScoreDividends;

    static DividendsScoreClient testerScoreDividends;
    static RewardsScoreClient rewards;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));

        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester2 = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester_bbaln = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester_bbaln2 = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester_bbaln3 = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.owner;

        dividends = new DividendsScoreClient(balanced.dividends);
        loans = new LoansScoreClient(balanced.loans);
        bnusd = new BalancedDollarScoreClient(balanced.bnusd);
        staking = new StakingScoreClient(balanced.staking);
        sicx = new SicxScoreClient(balanced.sicx);
        dex = new DexScoreClient(balanced.dex);
        rewards = new RewardsScoreClient(balanced.rewards);
        baln = new BalancedTokenScoreClient(balanced.baln);
        governance = new GovernanceScoreClient(balanced.governance);
        DefaultScoreClient clientWithTester = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester, balanced.baln._address());
        DefaultScoreClient clientWithTester2 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester, balanced.dividends._address());
        DefaultScoreClient clientWithTester3 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester2, balanced.dex._address());
        DefaultScoreClient clientWithTester4 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester2, balanced.sicx._address());
        DefaultScoreClient clientWithTester5 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester2, balanced.baln._address());
        DefaultScoreClient bbalnTester = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln, balanced.baln._address());
        DefaultScoreClient bbalnTester2 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln2, balanced.baln._address());
        DefaultScoreClient bbalnTester3 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln3, balanced.baln._address());
        DefaultScoreClient dividendsTester = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln, balanced.dividends._address());
        DefaultScoreClient dividendsTester2 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln2, balanced.dividends._address());
        DefaultScoreClient dividendsTester3 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), tester_bbaln3, balanced.dividends._address());
        boostedBaln = new BoostedBalnScoreClient(chain.getEndpointURL(), chain.networkId, tester_bbaln, balanced.bBaln._address());
        boostedBaln2 = new BoostedBalnScoreClient(chain.getEndpointURL(), chain.networkId, tester_bbaln2, balanced.bBaln._address());
        testerScore = new BalancedTokenScoreClient(clientWithTester);
        testerScoreDividends = new DividendsScoreClient(clientWithTester2);
        testerScoreDex = new DexScoreClient(clientWithTester3);
        testerScoreSicx = new SicxScoreClient(clientWithTester4);
        testerScoreBaln = new BalancedTokenScoreClient(clientWithTester5);
        balnTesterScore = new BalancedTokenScoreClient(bbalnTester);
        balnTesterScore2 = new BalancedTokenScoreClient(bbalnTester2);
        balnTesterScore3 = new BalancedTokenScoreClient(bbalnTester3);
        bbalntesterScoreDividends = new DividendsScoreClient(dividendsTester);
        bbalntesterScoreDividends2 = new DividendsScoreClient(dividendsTester2);
        bbalntesterScoreDividends3 = new DividendsScoreClient(dividendsTester3);

        activateDividends();
    }

    public static void activateDividends() {
        governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        dividends.setDistributionActivationStatus(true);
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
        dividends.setBBalnDay(governance.getDay().add(BigInteger.valueOf(100L)));


    }

    @Test
    @Order(1)
    void testName() {
        activateDividends();
        assertEquals("Balanced Dividends", dividends.name());
    }

    public BigInteger calculateDividends(BigInteger currentDay, BigInteger fee) {
        List<BigInteger> poolList = new ArrayList<>();
        poolList.add(BigInteger.valueOf(3));
        poolList.add(BigInteger.valueOf(4));
        BigInteger dividendsSwitchingDay = dividends.getDividendsOnlyToStakedBalnDay();

        BigInteger myBalnFromPools = BigInteger.ZERO;
        BigInteger totalBalnFromPools = BigInteger.ZERO;
        if (dividendsSwitchingDay.equals(BigInteger.ZERO) || (currentDay.compareTo(dividendsSwitchingDay) < 0)) {
            for (BigInteger poolId : poolList) {
                BigInteger myLp = dex.balanceOfAt(Address.fromString(balanced.owner.getAddress().toString()), poolId, currentDay, true);
                BigInteger totalLp = dex.totalSupplyAt(poolId, currentDay, true);
                BigInteger totalBaln = dex.totalBalnAt(poolId, currentDay, true);
                BigInteger equivalentBaln = BigInteger.ZERO;

                if (myLp.compareTo(BigInteger.ZERO) > 0 && totalLp.compareTo(BigInteger.ZERO) > 0 && totalBaln.compareTo(BigInteger.ZERO) > 0) {
                    equivalentBaln = (myLp.multiply(totalBaln)).divide(totalLp);
                }

                myBalnFromPools = myBalnFromPools.add(equivalentBaln);
                totalBalnFromPools = totalBalnFromPools.add(totalBaln);

            }
        }

        BigInteger stakedBaln = baln.stakedBalanceOfAt(Address.fromString(balanced.owner.getAddress().toString()),
                currentDay);
        BigInteger totalStakedBaln = baln.totalStakedBalanceOfAt(currentDay);

        BigInteger myTotalBalnToken = stakedBaln.add(myBalnFromPools);
        BigInteger totalBalnToken = totalStakedBaln.add(totalBalnFromPools);

        BigInteger dividends = BigInteger.ZERO;
        if (myTotalBalnToken.compareTo(BigInteger.ZERO) > 0 && totalBalnToken.compareTo(BigInteger.ZERO) > 0) {
            BigInteger numerator = myTotalBalnToken.multiply(BigInteger.valueOf(600000000000000000L)).
                    multiply(fee);
            BigInteger denominator = totalBalnToken.multiply(EXA);

            dividends = numerator.divide(denominator);
        }
        return dividends;
    }

    @Test
    @Order(2)
    void testChangeInPercentage() {

        balanced.increaseDay(1);
        governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

        // verify the change in percentage
        DistributionPercentage map = new DistributionPercentage();
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("900000000000000000");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("100000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };

        // set new percentage of categories of dividends
        dividends.setDividendsCategoryPercentage(percentMap);
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        BigInteger daoBalanceBefore = bnusd.balanceOf(balanced.daofund._address());

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        BigInteger originationFees = BigInteger.valueOf(100);
        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = dividends.getDividendsPercentage();
        BigInteger dividendsToDao = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);

        BigInteger daoBalanceAfter = bnusd.balanceOf(balanced.daofund._address());
        System.out.println(daoBalanceBefore);
        System.out.println(dividendsToDao);
        assertEquals(daoBalanceAfter, daoBalanceBefore.add(dividendsToDao));
    }


    @Test
    @Order(3)
    void testContinuousRewards() {
        // test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
        // the dividends
        balanced.increaseDay(1);
        balanced.syncDistributions();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        staking.stakeICX(amount, Address.fromString(tester.getAddress().toString()), null);
        staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);
        balanced.syncDistributions();
        // claim rewards for the user
        rewards.claimRewards();

        // provides liquidity to baln/Sicx pool
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by tester wallet
        testerScore.stake(BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18)));

        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);

        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        BigInteger originationFees = BigInteger.valueOf(100);
        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = dividends.getDividendsPercentage();
        BigInteger daoDividends = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);
        dividendsBalance = dividendsBalance.subtract(daoDividends);

        Map<String, BigInteger> userDividends =
                dividends.getUnclaimedDividends(Address.fromString(owner.getAddress().toString()));

        Map<String, BigInteger> userDividendsTester =
                dividends.getUnclaimedDividends(Address.fromString(tester.getAddress().toString()));

        BigInteger userDividendsBnusd = userDividends.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        BigInteger userDividendsTesterBnusd = userDividendsTester.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        // LP provider should have zero dividends to claim after continuous rewards is activated
        assertEquals(userDividendsBnusd, BigInteger.ZERO);
        // only baln staker should receive dividends
        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd), dividendsBalance);
    }

    void stakeAndProvideLiquidity() {

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        balanced.syncDistributions();
        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        staking.stakeICX(amount, Address.fromString(tester.getAddress().toString()), null);
        staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");


        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);

        balanced.syncDistributions();
        // claim rewards for the user
        rewards.claimRewards();

        // provides liquidity to baln/Sicx pool
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by tester wallet
        testerScore.stake(BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18)));

        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);

        balanced.increaseDay(1);
    }

    @Test
    @Order(4)
    void testContinuousDividends_daofund() {
        stakeAndProvideLiquidity();
        // Arrange
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        dividends.setBBalnDay(governance.getDay().add(BigInteger.valueOf(100L)));
        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        balanced.increaseDay(1);
        dividends.distribute((txr) -> {});

        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
         loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = bnusd.balanceOf(balanced.daofund._address());

        // Assert
        BigInteger daoPercentage = dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFee = fee.multiply(daoPercentage).divide(EXA);
        assertEquals(daoFundBalancePre.add(daoFee), daoFundBalancePost);
    }

    @Test
    @Order(5)
    void testContinuousDividends_staker() throws Exception {
        // Arrange
        BalancedClient staker = balanced.newClient();
        balanced.ownerClient.rewards.claimRewards();
        BigInteger balnBalance = baln.availableBalanceOf(balanced.ownerClient.getAddress());
        balanced.ownerClient.baln.transfer(staker.getAddress(), balnBalance, new byte[0]);
        staker.baln.stake(balnBalance);
        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));
        BigInteger daoDistPercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        // Assert
        // newly staked user
        BigInteger bnusdBalancePre = staker.bnUSD.balanceOf(staker.getAddress());
        staker.dividends.claimDividends();
        BigInteger bnusdBalancePost = staker.bnUSD.balanceOf(staker.getAddress());
//        assertTrue(bnusdBalancePre.compareTo(bnusdBalancePost) < 0);

        // staked pre continuous
        BigInteger testerBnusdBalancePre =
                balanced.ownerClient.bnUSD.balanceOf(score.Address.fromString(tester.getAddress().toString()));
        testerScoreDividends.claimDividends();
        BigInteger testerBnusdBalancePost =
                balanced.ownerClient.bnUSD.balanceOf(score.Address.fromString(tester.getAddress().toString()));
        assertTrue(testerBnusdBalancePre.compareTo(testerBnusdBalancePost) < 0);
    }

    @Test
    @Order(6)
    void testCreateNewUserForBBaln() throws Exception{
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln.getAddress().toString());
        Address bbalnTesterAddress2 =  Address.fromString(tester_bbaln2.getAddress().toString());
        Address bbalnTesterAddress3 =  Address.fromString(tester_bbaln3.getAddress().toString());

        balanced.syncDistributions();
        balanced.increaseDay(2);
        balanced.syncDistributions();

        balanced.ownerClient.rewards.claimRewards();

        // sent baln token to two users
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        balanced.ownerClient.baln.transfer(bbalnTesterAddress, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(bbalnTesterAddress2, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(bbalnTesterAddress3, collateral, new byte[0]);

        // staking baln token with two different users.
        BigInteger stakedAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        balnTesterScore.stake(stakedAmount);
        balnTesterScore2.stake(stakedAmount);

        // loan taken to sent some dividends to contract
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

    }

    @Test
    @Order(7)
    void testBBaln_daofund() throws Exception {
        /*
        If there are no supply of boosted baln even after bbaln day is started
        but there are some dividends received by dividends contract then,
        1. Daofund will get all the dividends .
        2.  No any user dividends will be increased.
         */
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln.getAddress().toString());
        Address bbalnTesterAddress2 =  Address.fromString(tester_bbaln2.getAddress().toString());
        Address bbalnTesterAddress3 =  Address.fromString(tester_bbaln3.getAddress().toString());

        Map<String, BigInteger> unclaimedDividends = dividends.getUnclaimedDividends(bbalnTesterAddress);
        Map<String, BigInteger> unclaimedDividends2 = dividends.getUnclaimedDividends(bbalnTesterAddress2);
        Map<String, BigInteger> unclaimedDividends3 = dividends.getUnclaimedDividends(bbalnTesterAddress3);

        dividends.setBBalnDay(governance.getDay());
        dividends.setBBalnAddress(boostedBaln._address());
        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = bnusd.balanceOf(balanced.daofund._address());

        // dividends is sent to daofund directly as there are no boosted baln yet
        assertEquals(daoFundBalancePre.add(fee), daoFundBalancePost);
        // dividends shouldn't increase once bbalnDay is set unless there is some transaction
        assertEquals(unclaimedDividends, dividends.getUnclaimedDividends(bbalnTesterAddress) );
        assertEquals(unclaimedDividends2, dividends.getUnclaimedDividends(bbalnTesterAddress2) );

        // new user will have nothing unless he adds bbaln
        assertEquals(unclaimedDividends3, dividends.getUnclaimedDividends(bbalnTesterAddress3) );
    }

    @Test
    @Order(8)
    void testBBaln_lock() throws Exception {
        /*
        1. Daofund doesn't get all the dividends once user starts locking baln token.
        2. User1 locks balance for few weeks and starts getting dividends.
        2. User2 doesn't lock balance and the unclaimed dividends remain same for few weeks.
         */
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln.getAddress().toString());
        Address bbalnTesterAddress2 =  Address.fromString(tester_bbaln2.getAddress().toString());

        BigInteger unclaimedDividendsBefore2 = dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());

        // user unstakes all the baln token
        balnTesterScore.stake(BigInteger.ZERO);
        BigInteger availableBalnBalance = balnTesterScore.availableBalanceOf(bbalnTesterAddress);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime = (System.currentTimeMillis()*1000)+(BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // locks baln for 4 weeks
        balnTesterScore.transfer(boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger unclaimedDividendsBefore = dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        // did tx to create a dividends
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsAfter = dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());
        BigInteger daoFundBalancePost = bnusd.balanceOf(balanced.daofund._address());
        BigInteger daoPercentage = dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFee = fee.multiply(daoPercentage).divide(EXA);

        // daofund doesn't get all the dividends value once there is a supply in bbaln
        assertEquals(daoFundBalancePre.add(daoFee), daoFundBalancePost);

        // unclaimed dividends increases for the user once the dividends is received by contract
        assertTrue(unclaimedDividendsAfter.compareTo(unclaimedDividendsBefore) > 0);

        // day changes and creation of dividends
        balanced.increaseDay(1);
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.increaseDay(1);
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.increaseDay(1);
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsAfter2 = dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());
        BigInteger user1DividendsAfter = dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());

        // as the user is not migrated to bbaln , the dividends
        // to be received by user remains same even after days
        assertEquals(unclaimedDividendsAfter2, unclaimedDividendsBefore2);

        assertTrue(user1DividendsAfter.compareTo(unclaimedDividendsAfter) > 0);

        BigInteger bnusdBalanceUser2Before = bnusd.balanceOf(bbalnTesterAddress2);
        bbalntesterScoreDividends2.claimDividends();

        BigInteger bnusdBalanceUser2After = bnusd.balanceOf(bbalnTesterAddress2);
        BigInteger newUnclaimedDividends2 = dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());

        // unclaimedDividends goes to user wallet
        assertEquals(bnusdBalanceUser2After, bnusdBalanceUser2Before.add(unclaimedDividendsAfter2));

        // user claims the rewards of baln stake after many days of bbaln start
        // once user claims dividends the unclaimedDividends become 0
        assertEquals(newUnclaimedDividends2, BigInteger.ZERO);
    }

    @Test
    @Order(9)
    void testBBaln_claim() throws Exception {
        /*
        1. User1 claims the dividends and the expected dividends is sent to user wallet.
        2. After the claim , there will be dividends for that user only if dividends is received by the contract.
        3. Multiple claim of dividends doesn't increase the balance.
         */
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln.getAddress().toString());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger unclaimedDividendsBefore = dividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        BigInteger bnusdBalanceUserBefore = bnusd.balanceOf(bbalnTesterAddress);

        bbalntesterScoreDividends.claimDividends();

        BigInteger newUnclaimedDividends = dividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        BigInteger actualBnusdAfterClaim = bnusd.balanceOf(bbalnTesterAddress);
        // claims multiple times
        bbalntesterScoreDividends.claimDividends();

        // bnusd in user wallet doesn't increase
        assertEquals(actualBnusdAfterClaim, bnusd.balanceOf(bbalnTesterAddress));

        BigInteger expectedBnusdAfterClaim = bnusdBalanceUserBefore.add(unclaimedDividendsBefore);

        // unclaimedDividends goes to user wallet
        assertEquals(actualBnusdAfterClaim, expectedBnusdAfterClaim);

        // once user claims dividends the unclaimedDividends become null
        assertEquals(newUnclaimedDividends, BigInteger.ZERO);

        balanced.increaseDay(1);
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        newUnclaimedDividends = dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());

        // unclaimed dividends have some value once dividends is received by contract
        assertTrue(newUnclaimedDividends.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    @Order(10)
    void testBBaln_newUser() throws Exception {
        /*
        A new user comes and locks the baln and that user will be eligible to earn dividends
        anytime after that.
         */
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln3.getAddress().toString());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger availableBalnBalance = balnTesterScore.availableBalanceOf(bbalnTesterAddress);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime = (System.currentTimeMillis()*1000)+(BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // a new user will have 0 accumulated dividends
        assertEquals(bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString()), BigInteger.ZERO);
//        System.out.println(boostedBaln.getTxLock());
        System.out.println("here");
        // locks baln for 4 weeks
        balnTesterScore3.transfer(boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());
//        System.out.println(boostedBaln.getTxLock());

        // for dividends
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsBefore = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        BigInteger bnusdBalancePre = bnusd.balanceOf(bbalnTesterAddress);
        // after user locks baln, he will start getting dividends
        assertTrue(unclaimedDividendsBefore.compareTo(BigInteger.ZERO) > 0);

        bbalntesterScoreDividends3.claimDividends();

        BigInteger bnusdBalancePost = bnusd.balanceOf(bbalnTesterAddress);

        assertEquals(bnusdBalancePost, bnusdBalancePre.add(unclaimedDividendsBefore));
        unclaimedDividendsBefore = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());

        // after claiming dividends unclaimed dividends will be null unless dividends is received.
        assertEquals(bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString()), BigInteger.ZERO);

    }

    @Test
    @Order(11)
    void testBBaln_newUser_kicked() throws Exception {
        /*
        A user starts getting less dividends once kicked.
         */
        Address bbalnTesterAddress =  Address.fromString(tester_bbaln3.getAddress().toString());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsBefore = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsAfter = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        // checking dividends before they are kicked
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
        bbalntesterScoreDividends3.claimDividends();

        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsBefore = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        boostedBaln.kick(bbalnTesterAddress);
        loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfter = bbalntesterScoreDividends.getUnclaimedDividends(bbalnTesterAddress).get(bnusd._address().toString());
        // checking dividends once they are kicked
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
    }

    @Test
    @Order(12)
    void testRemoveCategories() {
        // test the removal of categories from dividends
        governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

        DistributionPercentage map = new DistributionPercentage();

        // firstly setting the baln_holders as 0 percentage
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("0");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("1000000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };

        // setting dividends category to 0 for baln_holders at first
        dividends.setDividendsCategoryPercentage(percentMap);

        // removing the categories
        dividends.removeDividendsCategory("baln_holders");
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        List<String> categories;
        categories = dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    @Order(13)
    void testAddCategories() {
        // add new categories in dividends

        governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        dividends.setDistributionActivationStatus(true);
        dividends.addDividendsCategory("test");
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
        List<String> categories;
        categories = dividends.getDividendsCategories();
        assertEquals("test", categories.get(categories.size() - 1));
    }
}
