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
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.base.BalancedToken;
import network.balanced.score.lib.interfaces.base.BalancedTokenScoreClient;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

public class DividendsIntegrationTest {
    static Wallet tester;
    static KeyWallet owner;
    static Balanced balanced;

    @ScoreClient
    static Dividends dividends;

    @ScoreClient
    static Loans loans;

    @ScoreClient
    static Dex dex;

    @ScoreClient
    static BalancedDollar bnusd;


    @ScoreClient
    static Staking staking;


    @ScoreClient
    static Sicx sicx;

    @ScoreClient
    static BalancedToken baln;

    @ScoreClient
    static Governance governance;

    @ScoreClient
    static BalancedToken testerScore;

    @ScoreClient
    static DividendsCopy testerScoreDividends;

    @ScoreClient
    static RewardsCopy rewardsCopy;

    @ScoreClient
    static DividendsCopy dividendsCopy;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));
        System.setProperty("DAOfund", System.getProperty("daofund"));

        // uncomment below line to run migration test
//        System.setProperty("Dividends", System.getProperty("python"));

        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.setupBalanced();

        dividends = new DividendsScoreClient(balanced.dividends);
        dividendsCopy = new DividendsCopyScoreClient(balanced.dividends);
        loans = new LoansScoreClient(balanced.loans);
        bnusd = new BalancedDollarScoreClient(balanced.bnusd);
        staking = new StakingScoreClient(balanced.staking);
        sicx = new SicxScoreClient(balanced.sicx);
        dex = new DexScoreClient(balanced.dex);
        rewardsCopy = new RewardsCopyScoreClient(balanced.rewards);
        baln = new BalancedTokenScoreClient(balanced.baln);
        governance = new GovernanceScoreClient(balanced.governance);
        DefaultScoreClient clientWithTester = new DefaultScoreClient("http://localhost:9082/api/v3", BigInteger.valueOf(3), tester, balanced.baln._address());
        DefaultScoreClient clientWithTester2 = new DefaultScoreClient("http://localhost:9082/api/v3", BigInteger.valueOf(3), tester, balanced.baln._address());
        testerScore = new BalancedTokenScoreClient(clientWithTester);
        testerScoreDividends = new DividendsCopyScoreClient(clientWithTester2);

    }


    @Test
    void testName(){
        assertEquals("Balanced Dividends", dividends.name());
    }

    @Test
    void testUserDividends() {
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        BigInteger originationFees = BigInteger.valueOf(100);
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> balances = Map.of("bnUSD", dividendsBalance, "ICX", BigInteger.ZERO);

        // verify the getBalances of dividends contract
        assertEquals(balances, dividends.getBalances());


        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }

        // claims rewards for that user
        rewardsCopy.claimRewards();
        BigInteger balance = baln.balanceOf(Address.fromString(owner.getAddress().toString()));

        // stakes balance token
        baln.stake(balance);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        // take loans to create a dividends
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        for (int i = 0; i<3; i++) {
            dividendsCopy.distribute();
        }
        // user dividends ready to  claim
        Map<String, BigInteger> userOldDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        // bnusd ready to claim
        BigInteger bnusdToClaim = userOldDividends.get(balanced.bnusd._address().toString());

        // previous bnusd balance of a user
        BigInteger userOldBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // dividends result should not be empty
        assertFalse(userOldDividends.isEmpty());

        Map<String, BigInteger> oldbalance = dividends.getBalances();
        BigInteger bnusdAtDividends = oldbalance.get("bnUSD");

        // dividends claimed by user
        dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);

        Map<String, BigInteger> userNewDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        // once claimed previous userDividends should be empty
        assertTrue(userNewDividends.isEmpty());

        BigInteger userNewBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // verify new balance of user as dividends are added on the balance too
        assertEquals(userNewBalance, userOldBalance.add(bnusdToClaim));

        Map<String, BigInteger> newbalance = dividends.getBalances();
        BigInteger newbnusdAtDividends = newbalance.get("bnUSD");

        // contract balances should be decreased after claim
        assertEquals(newbnusdAtDividends, bnusdAtDividends.subtract(bnusdToClaim));


    }

    @Test
    void testClaimDividends() {
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();


        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        BigInteger originationFees = BigInteger.valueOf(100);
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));

        // test if the dividends is arrived at dividends contract
        assertEquals(dividendsBalance, bnusd.balanceOf(balanced.dividends._address()));

        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }

        rewardsCopy.claimRewards();
        BigInteger balance = baln.balanceOf(Address.fromString(owner.getAddress().toString()));

        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger amount = collateral;
        ScoreIntegrationTest.transfer(balanced.dex._address(), amount);

        ((StakingScoreClient) staking).stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), null
                , null);

        JSONObject data = new JSONObject();
        data.put("method", "_swap_icx");
        BigInteger swappedAmount = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(18));
        sicx.transfer(balanced.dex._address(), swappedAmount, data.toString().getBytes());
        Map<String, BigInteger> dexFees = dex.getFees();
        BigInteger icxBalnFee = dexFees.get("icx_baln_fee");
        BigInteger sicxBalance = icxBalnFee.multiply(swappedAmount).divide(BigInteger.valueOf(10000));
        assertEquals(sicxBalance, sicx.balanceOf(balanced.dividends._address()));
        baln.stake(balance);
        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.TWO))) {
            rewardsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }

        Map<String, BigInteger> firstDividendsInfo = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        assertFalse(firstDividendsInfo.isEmpty());
        // calculate the amount to be received by the user
        BigInteger dividendsBnusdFee = firstDividendsInfo.get(balanced.bnusd._address().toString());
        BigInteger dividendsSicxFee = firstDividendsInfo.get(balanced.sicx._address().toString());

        // take old balance of user
        BigInteger oldBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger oldSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        Map<String, BigInteger> oldbalance = dividends.getBalances();

        // total balance of dividends
        BigInteger sicxAtDividends = oldbalance.get("sICX");
        BigInteger bnusdAtDividends = oldbalance.get("bnUSD");

        dividends.claim(currentDay.intValue() - 1, currentDay.intValue() + 1);

        BigInteger newBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger newSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        // test balance of user
        assertEquals(newBnusdBalance, oldBnusdBalance.add(dividendsBnusdFee));
        assertEquals(newSicxBalance, oldSicxBalance.add(dividendsSicxFee));

        Map<String, BigInteger> newDividendsbalance = dividends.getBalances();

        // total balance of dividends
        BigInteger newsicxAtDividends = newDividendsbalance.get("sICX");
        BigInteger newbnusdAtDividends = newDividendsbalance.get("bnUSD");

        // test the balance of dividends
        assertEquals(newsicxAtDividends, sicxAtDividends.subtract(dividendsSicxFee));
        assertEquals(newbnusdAtDividends, bnusdAtDividends.subtract(dividendsBnusdFee));


        // test user dividends at that day again
        Map<String, BigInteger> dividendsAfterClaim = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        assertTrue(dividendsAfterClaim.isEmpty());

    }

    @Test
    void testDaofundTransfer() {
        // test the transfer of dividends to daofund
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            dividendsCopy.distribute();
            rewardsCopy.distribute();
        }

        for (int i = 0; i < 3; i++) {
            dividendsCopy.distribute();
        }
        // get dividends for daofund
        BigInteger bnusdAtDividends = dividends.getDaoFundDividends(1, 2).get(balanced.bnusd._address().toString());

        Map<String, BigInteger> dividendsBalances = new HashMap<>();
        dividendsBalances = dividends.getBalances();
        BigInteger bnusdBalance = dividendsBalances.get("bnUSD");

        dividends.transferDaofundDividends(1, 2);

        // assert daofund balance after transfer
        assertEquals(bnusdAtDividends, bnusd.balanceOf(balanced.daofund._address()));

        dividendsBalances = dividends.getBalances();
        BigInteger newBnusdBalance = dividendsBalances.get("bnUSD");

        // verify new balance of dividends contract
        assertEquals(newBnusdBalance, bnusdBalance.subtract(bnusdAtDividends));

    }

    @Test
    void testChangeInPercentage() {
        // verify the change in percentage
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();
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

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.TWO))) {
            dividendsCopy.distribute();
        }
        BigInteger bnusdBalance = dividends.getBalances().get("bnUSD");

        BigInteger bnusdAtDividends = dividends.getDaoFundDividends(currentDay.intValue(), currentDay.intValue() + 1).get(balanced.bnusd._address().toString());

        dividends.transferDaofundDividends(currentDay.intValue(), currentDay.intValue() + 1);

        // verify if the daofund contract receives the dividends or not
        assertEquals(bnusdAtDividends, bnusd.balanceOf(balanced.daofund._address()));

        // verify the total bnUSD transferred as per the new percentage or not
        assertEquals(bnusdAtDividends, bnusdBalance.divide(BigInteger.TEN));
    }

    @Test
    void testRemoveCategories() {
        // test the removal of categories from dividends
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();
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

        List<String> categories = new ArrayList<>();
        categories = dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    void testAddCategories() {
        // add new categories in dividends
        dividends.addDividendsCategory("test");
        List<String> categories = new java.util.ArrayList<>();
        categories = dividends.getDividendsCategories();
        assertEquals("test", categories.get(categories.size() - 1));
    }

    @Test
    void testBnusdBalnDividends() {
        // verify the user of a baln/bnUSD LP dividends
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();


        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }
        // create bnUSD market
        ((GovernanceScoreClient) governance).createBnusdMarket(new BigInteger("500").multiply(BigInteger.TEN.pow(18)));

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        BigInteger currentDay = dividends.getDay();
        // checks if the day is passed or not
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }
        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        // claim rewards of loans
        rewardsCopy.claimRewards();
        // create baln market
        governance.createBalnMarket(new BigInteger("50").multiply(BigInteger.TEN.pow(18)), new BigInteger("50").multiply(BigInteger.TEN.pow(18)));

        // provides LP to baln/bnUSD market
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        bnusd.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }
        dex.add(balanced.baln._address(), balanced.bnusd._address(), lpAmount, lpAmount, true);

        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        // take loans to provide the dividends
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        currentDay = dividends.getDay();

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }


        BigInteger previousBalances = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        // user Dividends should not be empty
        assertFalse(userDividends.isEmpty());

        dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);

        // after claim dividends should be empty
        Map<String, BigInteger> newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        assertTrue(newUserDividends.isEmpty());

        // verify if the dividends is claimed or not
        assertEquals(userDividends.get(balanced.bnusd._address().toString()), bnusd.balanceOf(Address.fromString(owner.getAddress().toString())).subtract(previousBalances));

    }

    @Test
    void testSicxBalnDividends() {
        // verify the user of a sicx/baln LP dividends
        baln.toggleEnableSnapshot();
        dividendsCopy.distribute();


        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));

        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }
        // create bnusd market
        ((GovernanceScoreClient) governance).createBnusdMarket(new BigInteger("500").multiply(BigInteger.TEN.pow(18)));

        // take sicx from staking contract
        ((StakingScoreClient) staking).stakeICX(new BigInteger("500").multiply(BigInteger.TEN.pow(18)), null, null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));

        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }
        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        // claim baln rewards
        rewardsCopy.claimRewards();
        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }

        // provides lp to the baln and sicx
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);

        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        currentDay = dividends.getDay();

        // take loans for dividends
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }


        BigInteger previousBalances = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);


        // verify the user Dividends is not empty
        assertFalse(userDividends.isEmpty());

        // claim dividends
        dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);


        // verify if the dividends is claimed or not.
        assertEquals(userDividends.get(balanced.bnusd._address().toString()), bnusd.balanceOf(Address.fromString(owner.getAddress().toString())).subtract(previousBalances));

        // After claiming userDividends should be empty
        Map<String, BigInteger> newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);
        assertTrue(newUserDividends.isEmpty());
    }

    @Test
    void testBalnStakeAndLpUsers() {
        // test the dividends received by two user i.e baln stake and lp provider on baln/sicx pool
        BigInteger currentDay = stakeAndLp();
        BigInteger dailyFees = BigInteger.ZERO;
        if (dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).get(balanced.bnusd._address().toString()) == null) {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);
        } else {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);

        }

        // getUserDividends for both of user
        Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);
        Map<String, BigInteger> userDividendsTester = dividends.getUserDividends(Address.fromString(tester.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        // bnusd to be received by each user
        BigInteger userDividendsBnusd = userDividends.get(balanced.bnusd._address().toString());
        BigInteger userDividendsTesterBnusd = userDividendsTester.get(balanced.bnusd._address().toString());

        // Dividends of owner should not be empty
        assertFalse(userDividends.isEmpty());

        // Dividends of another account also should not be empty
        assertFalse(userDividendsTester.isEmpty());

        // sum of both dividends add up to 60 percent of the daily fees of that day
        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd), dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100)));
//        System.out.println(userDividendsBnusd.add(userDividendsTesterBnusd));
//        System.out.println(dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100)));
    }

    @Test
    void testContinuousRewards() {
        // test continuous rewards for dividends i.e once continuous rewards is activated only staked baln will get the dividends
        baln.toggleEnableSnapshot();
        baln.setTimeOffset();
        dividendsCopy.distribute();
        // set continuous rewards day

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }
        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market
        ((GovernanceScoreClient) governance).createBnusdMarket(amount);

        ((StakingScoreClient) staking).stakeICX(amount, Address.fromString(tester.getAddress().toString()), null);
        ((StakingScoreClient) staking).stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)), Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");


        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }
        rewardsCopy.setTotalDist();
        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        // claim rewards for the user
        rewardsCopy.claimRewards();

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }
        // provides liquidity to baln/Sicx pool
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()), BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);
        // stake balance by tester wallet
        testerScore.stake(BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18)));
        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);
        currentDay = dividends.getDay();

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        currentDay = dividends.getDay();
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }
        BigInteger dailyFees = BigInteger.ZERO;
        if (dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).get(balanced.bnusd._address().toString()) == null) {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);
        } else {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);

        }

        Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);
        Map<String, BigInteger> userDividendsTester = dividends.getUserDividends(Address.fromString(tester.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);


        BigInteger userDividendsBnusd = userDividends.getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);
        BigInteger userDividendsTesterBnusd = userDividendsTester.getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);
        // LP provider should have zero dividends to claim after continuous rewards is activated
        assertEquals(userDividendsBnusd, BigInteger.ZERO);
        // only baln staker should receive dividends
        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd), dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100)));
    }

    @Test
    void testMigration() throws Exception {
        //  claims the dividends after contract is updated
        // we need to deploy python at first then java contract should be updated
        BigInteger currentDay = stakeAndLp();

        // update the contract
        balanced.dividends._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));

        BigInteger dailyFees = BigInteger.ZERO;
        if (dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).get(balanced.bnusd._address().toString()) == null) {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);
        } else {
            dailyFees = (BigInteger) dividends.getDailyFees(currentDay.subtract(BigInteger.ONE)).getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);

        }

        for (int i = 0; i < 15; i++) {
            dividendsCopy.distribute();
        }

        // check user dividends
        Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);
        Map<String, BigInteger> userDividendsTester = dividends.getUserDividends(Address.fromString(tester.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        // take bnUSD as dividends
        BigInteger userDividendsBnusd = userDividends.get(balanced.bnusd._address().toString());
        BigInteger userDividendsTesterBnusd = userDividendsTester.get(balanced.bnusd._address().toString());

        // test the userDividends should not be empty
        assertFalse(userDividends.isEmpty());

        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd), dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100)));

        // old balance of user
        BigInteger prevbnusdBalanceUser = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // claim the dividends
        dividendsCopy.claim(currentDay.intValue() - 1, currentDay.intValue() + 1);

        // new balance of user
        BigInteger bnusdBalanceUser = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // verify the balance of the user
        assertEquals(bnusdBalanceUser, prevbnusdBalanceUser.add(userDividendsBnusd));

        Map<String, BigInteger> newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        // after claim user dividends should be empty
        assertTrue(newUserDividends.isEmpty());

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        // take loans again to get dividends
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), null, null);

        currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }

        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }
        userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        // the map should not be empty for the user dividends
        assertFalse(userDividends.isEmpty());

        userDividendsBnusd = userDividends.getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO);

        // take previous balance of user
        BigInteger previousBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // claim the dividends
        dividendsCopy.claim(currentDay.intValue() - 1, currentDay.intValue() + 1);
        newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1);

        // after claim the userDividends for that day will be empty
        assertTrue(newUserDividends.isEmpty());

        // take new balance of user
        BigInteger newBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // test the new bnusd balance of user
        assertEquals(previousBalance.add(userDividendsBnusd), newBalance);

    }

    BigInteger stakeAndLp() {
        // stakes balance token and provides liquidity to baln/sICX pool by different user
        baln.toggleEnableSnapshot();
        baln.setTimeOffset();
        dividendsCopy.distribute();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));

        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }

        // create bnusd market
        ((GovernanceScoreClient) governance).createBnusdMarket(new BigInteger("500").multiply(BigInteger.TEN.pow(18)));

        // takes sICX from staking contract
        ((StakingScoreClient) staking).stakeICX(new BigInteger("500").multiply(BigInteger.TEN.pow(18)), Address.fromString(tester.getAddress().toString()), null);
        ((StakingScoreClient) staking).stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)), Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        BigInteger currentDay = dividends.getDay();
        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
        }
        rewardsCopy.setTotalDist();
        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }

        // claims rewards
        rewardsCopy.claimRewards();

        for (int i = 0; i < 10; i++) {
            rewardsCopy.distribute();
        }

        // add liquidity to baln/sICX pool
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()), BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance through another user
        testerScore.stake(BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18)));

        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);
        currentDay = dividends.getDay();

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }

        currentDay = dividends.getDay();
        // take loans to create dividends
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        while (!dividends.getDay().equals(currentDay.add(BigInteger.ONE))) {
            rewardsCopy.distribute();
            dividendsCopy.distribute();
        }
        for (int i = 0; i < 10; i++) {
            dividendsCopy.distribute();
        }

        return currentDay;
    }

    @Test
    void testMigrationSecondCase(){
        // claims the dividends before update
        BigInteger currentDay = stakeAndLp();

        for (int i = 0; i < 15; i++) {
            dividendsCopy.distribute();
        }

        // user dividends should not be empty
        assertFalse(dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1).isEmpty());

        // claim the dividends by the user
        dividendsCopy.claim(currentDay.intValue() - 1, currentDay.intValue() + 1);

        // update the contract
        balanced.dividends._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));

        for (int i = 0; i < 3; i++) {
            dividendsCopy.distribute();
        }

        // after claim the userDividends should be empty
        assertTrue(dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() - 1, currentDay.intValue() + 1).isEmpty());
    }

}
