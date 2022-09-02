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
import static network.balanced.score.lib.utils.Constants.POINTS;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.dummyConsumer;
import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DividendsIntegrationTest {
    static Wallet tester;
    static Wallet tester2;
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

    static BalancedTokenScoreClient testerScore;
    static DexScoreClient testerScoreDex;
    static SicxScoreClient testerScoreSicx;
    static BalancedTokenScoreClient testerScoreBaln;
    static DividendsScoreClient testerScoreDividends;
    static RewardsScoreClient rewards;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));

        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester2 = createWalletWithBalance(BigInteger.TEN.pow(24));
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

        loans.depositAndBorrow(BigInteger.valueOf(5000).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), null, null);

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
        testerScore = new BalancedTokenScoreClient(clientWithTester);
        testerScoreDividends = new DividendsScoreClient(clientWithTester2);
        testerScoreDex = new DexScoreClient(clientWithTester3);
        testerScoreSicx = new SicxScoreClient(clientWithTester4);
        testerScoreBaln = new BalancedTokenScoreClient(clientWithTester5);
        activateDividends();
    }

    public static void activateDividends(){
        governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        dividends.setDistributionActivationStatus(true);
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
    }

    @Test
    @Order(1)
    void testName(){
        activateDividends();
        assertEquals("Balanced Dividends", dividends.name());
    }

    public BigInteger calculateDividends(BigInteger currentDay, BigInteger fee){
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

        BigInteger stakedBaln = baln.stakedBalanceOfAt(Address.fromString(balanced.owner.getAddress().toString()), currentDay);
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
    @Order(3)
    void testClaimDividends() {
        balanced.increaseDay(1);

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        BigInteger originationFees = BigInteger.valueOf(100);
//         take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger dividendsOldBalance = bnusd.balanceOf(balanced.dividends._address());
        loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = dividends.getDividendsPercentage();
        BigInteger daoDividends = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);
        dividendsBalance = dividendsBalance.subtract(daoDividends);

//         test if the dividends is arrived at dividends contract
        assertEquals(dividendsBalance, bnusd.balanceOf(balanced.dividends._address()).subtract(dividendsOldBalance));

        balanced.increaseDay(1);

//        balanced.syncDistributions();

        rewards.claimRewards();
        BigInteger balance = baln.balanceOf(Address.fromString(owner.getAddress().toString()));

        loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        ScoreIntegrationTest.transfer(balanced.dex._address(), collateral);

        staking.stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), null, null);

        JSONObject data = new JSONObject();
        data.put("method", "_swap_icx");
        BigInteger swappedAmount = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(18));
        sicx.transfer(balanced.dex._address(), swappedAmount, data.toString().getBytes());
        Map<String, BigInteger> dexFees = dex.getFees();
        BigInteger icxBalnFee = dexFees.get("icx_baln_fee");
        BigInteger sicxBalance = icxBalnFee.multiply(swappedAmount).divide(BigInteger.valueOf(10000));

        BigInteger daoDividendsSicx = daoFundDividendsPercent.get("daofund").multiply(sicxBalance).divide(EXA);
        sicxBalance = sicxBalance.subtract(daoDividendsSicx);

        assertEquals(sicxBalance, sicx.balanceOf(balanced.dividends._address()));
        baln.stake(balance);

        balanced.increaseDay(1);

        loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);


        BigInteger currentDay = dividends.getDay();

        balanced.increaseDay(1);

        for (int i = 0; i < 5; i++) {
            dividends.distribute(dummyConsumer());
        }

        Map<String, BigInteger> firstDividendsInfo = dividends.getUnclaimedDividends(Address.fromString(owner.getAddress().toString()));
        assertTrue(firstDividendsInfo.get(balanced.bnusd._address().toString()).compareTo(BigInteger.ZERO) > 0);
        assertFalse(firstDividendsInfo.isEmpty());
        // calculate the amount to be received by the user
        BigInteger dividendsBnusdFee = firstDividendsInfo.get(balanced.bnusd._address().toString());
        BigInteger dividendsSicxFee = firstDividendsInfo.get(balanced.sicx._address().toString());

        // take old balance of user
        BigInteger oldBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger oldSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        Map<String, BigInteger> oldBalance = dividends.getBalances();

        // total balance of dividends
        BigInteger sicxAtDividends = oldBalance.get("sICX");
        BigInteger bnusdAtDividends = oldBalance.get("bnUSD");

        dividends.claimDividends();

        BigInteger newBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger newSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        // test balance of user
        assertEquals(newBnusdBalance, oldBnusdBalance.add(dividendsBnusdFee));
        assertEquals(newSicxBalance, oldSicxBalance.add(dividendsSicxFee));

        Map<String, BigInteger> newDividendsBalance = dividends.getBalances();

        // total balance of dividends
        BigInteger newSicxAtDividends = newDividendsBalance.get("sICX");
        BigInteger newBnusdAtDividends = newDividendsBalance.get("bnUSD");

        // test the balance of dividends
        assertEquals(newSicxAtDividends, sicxAtDividends.subtract(dividendsSicxFee));
        assertEquals(newBnusdAtDividends, bnusdAtDividends.subtract(dividendsBnusdFee));

        // test user dividends at that day again
        Map<String, BigInteger> dividendsAfterClaim =
                dividends.getUnclaimedDividends(Address.fromString(owner.getAddress().toString()));
        assertEquals(dividendsAfterClaim.get(balanced.bnusd._address().toString()), BigInteger.ZERO);
    }
    
    @Test
    @Order(9)
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

        assertEquals(daoBalanceAfter, daoBalanceBefore.add(dividendsToDao));
    }


    @Test
    @Order(2)
    void testContinuousRewards() {
        // test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
        // the dividends
        balanced.increaseDay(1);

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

    @Test
    @Order(20)
    void testContinuousDividends_daofund() {
        // Arrange
//        dividends.setContinuousDividendsDay(governance.getDay().add(BigInteger.ONE));
        balanced.increaseDay(1);
        dividends.distribute((txr) -> {});

        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));
        BigInteger daoDistPercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
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
    @Order(21)
    void testContinuousDividends_staker() throws Exception {
        // Arrange
        BalancedClient staker = balanced.newClient();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        balanced.ownerClient.rewards.claimRewards();

        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(18)), null);
        // stake balance by tester wallet
        testerScore.stake(BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(18)));

        BigInteger balnBalance = baln.availableBalanceOf(balanced.ownerClient.getAddress());
        balanced.ownerClient.baln.transfer(staker.getAddress(), balnBalance, new byte[0]);
        staker.baln.stake(balnBalance);
        BigInteger feePercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));
        BigInteger daoDistPercent = hexObjectToBigInteger(loans.getParameters().get("origination fee"));
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        staker.dividends.claimDividends();
        BigInteger bnusdBalancePre1 = staker.bnUSD.balanceOf(staker.getAddress());

        // Act
        BigInteger daoFundBalancePre = bnusd.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
        , loanAmount, null, null);

        // Assert
        // newly staked user
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BigInteger bnusdBalancePre = staker.bnUSD.balanceOf(staker.getAddress());
        staker.dividends.claimDividends();
        BigInteger bnusdBalancePost = staker.bnUSD.balanceOf(staker.getAddress());
        assertTrue(bnusdBalancePre.compareTo(bnusdBalancePost) < 0);

        // staked pre continouos
        BigInteger testerBnusdBalancePre = balanced.ownerClient.bnUSD.balanceOf(score.Address.fromString(tester.getAddress().toString()));
        testerScoreDividends.claimDividends();
        BigInteger testerBnusdBalancePost = balanced.ownerClient.bnUSD.balanceOf(score.Address.fromString(tester.getAddress().toString()));
        assertTrue(testerBnusdBalancePre.compareTo(testerBnusdBalancePost) < 0);
    }

    @Test
    @Order(30)
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
    @Order(31)
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
