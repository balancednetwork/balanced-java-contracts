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
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DividendsIntegrationTest {
    static KeyWallet owner;
    static Balanced balanced;

    static BalancedClient balancedClient1;
    static BalancedClient balancedClient2;
    static BalancedClient balancedClient3;
    static BalancedClient balancedClient4;
    static BalancedClient ownerClient;


    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));

        balanced = new Balanced();
        balanced.setupBalanced();

        balancedClient1 = balanced.newClient();
        balancedClient2 = balanced.newClient();
        balancedClient3 = balanced.newClient();
        balancedClient4 = balanced.newClient();

        owner = balanced.owner;
        ownerClient = balanced.ownerClient;

        activateDividends();
    }

    public static void activateDividends() {
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        ownerClient.dividends.setDistributionActivationStatus(true);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

    }

    @Test
    @Order(1)
    void testName() throws Exception {
        BalancedClient balancedClient = balanced.newClient();
        activateDividends();
        assertEquals("Balanced Dividends", balancedClient.dividends.name());
    }

    void stakeAndProvideLiquidity() {

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        balanced.syncDistributions();
        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        ownerClient.staking.stakeICX(amount, balancedClient4.getAddress(), null);
        ownerClient.staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");


        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);

        balanced.syncDistributions();
        // claim rewards for the user
        ownerClient.rewards.claimRewards();

        // provides liquidity to baln/Sicx pool
        ownerClient.baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        ownerClient.baln.transfer(balancedClient4.getAddress(),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by tester wallet
        balancedClient4.baln.stake(lpAmount);

        String name = "BALN/sICX";
        BigInteger pid = ownerClient.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        ownerClient.governance.setMarketName(pid, name);

        balanced.increaseDay(1);
    }

    void createNewUserForBBaln() {

        Address bbalnTesterAddress = balancedClient1.getAddress();
        Address bbalnTesterAddress2 = balancedClient2.getAddress();
        Address bbalnTesterAddress3 = balancedClient3.getAddress();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.syncDistributions();
        balanced.increaseDay(10);
        for (int i = 0; i < 8; i++) {
            balanced.syncDistributions();
        }

        balanced.ownerClient.rewards.claimRewards();
        // sent baln token to two users
        balanced.ownerClient.baln.transfer(bbalnTesterAddress, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(bbalnTesterAddress2, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(bbalnTesterAddress3, collateral, new byte[0]);

        // staking baln token with two different users.
        BigInteger stakedAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        balancedClient1.baln.stake(stakedAmount);
        balancedClient2.baln.stake(stakedAmount);

        // loan taken to send some dividends to contract
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
    }

    @Test
    @Order(7)
    void testBBaln_daofund() {
        /*
        If there are no supply of boosted baln even after bbaln day is started
        but there are some dividends received by dividends contract then,
        1. Daofund will get all the dividends .
        2. None of the user dividends will be increased.
         */
        createNewUserForBBaln();
        Address bbalnTesterAddress = balancedClient1.getAddress();
        Address bbalnTesterAddress2 = balancedClient2.getAddress();
        Address bbalnTesterAddress3 = balancedClient3.getAddress();

        Map<String, BigInteger> unclaimedDividends = ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress);
        Map<String, BigInteger> unclaimedDividends2 = ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress2);
        Map<String, BigInteger> unclaimedDividends3 = ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress3);

        ownerClient.dividends.setBBalnAddress(balancedClient1.boostedBaln._address());
        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        // dividends are sent to daofund directly as there are no boosted baln yet
        assertEquals(daoFundBalancePre.add(fee), daoFundBalancePost);
        // dividends shouldn't increase once bbalnDay is set unless there is some transaction
        assertEquals(unclaimedDividends, ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress));
        assertEquals(unclaimedDividends2, ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress2));

        // new user will have nothing unless he adds bbaln
        assertEquals(unclaimedDividends3, ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress3));
    }

    @Test
    @Order(8)
    void testBBaln_lock() {
        /*
        1. Daofund doesn't get all the dividends once user starts locking baln token.
        2. User1 locks balance for few weeks and starts getting dividends.
        2. User2 doesn't lock balance and the unclaimed dividends remain same for few weeks.
         */
        Address bbalnTesterAddress = balancedClient1.getAddress();
        Address bbalnTesterAddress2 = balancedClient2.getAddress();

        BigInteger unclaimedDividendsBefore2 =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());

        // user unstakes all the baln token
        balancedClient1.baln.stake(BigInteger.ZERO);
        BigInteger availableBalnBalance = balancedClient1.baln.availableBalanceOf(bbalnTesterAddress);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // locks baln for 4 weeks
        balancedClient1.baln.transfer(balancedClient1.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));
        BigInteger daoFundBalancePre = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger unclaimedDividendsBefore =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        // did tx to create a dividends
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsAfter =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());
        BigInteger daoFundBalancePost = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoPercentage = ownerClient.dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFee = fee.multiply(daoPercentage).divide(EXA);

        // daofund doesn't get all the dividends value once there is a supply in bbaln
        assertEquals(daoFundBalancePre.add(daoFee), daoFundBalancePost);

        // unclaimed dividends increases for the user once the dividends is received by contract
        assertTrue(unclaimedDividendsAfter.compareTo(unclaimedDividendsBefore) > 0);

        // day changes and creation of dividends
        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsAfter2 =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());
        BigInteger user1DividendsAfter =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());

        // as the user is not migrated to bbaln , the dividends
        // to be received by user remains same even after days
        assertEquals(unclaimedDividendsAfter2, unclaimedDividendsBefore2);

        assertTrue(user1DividendsAfter.compareTo(unclaimedDividendsAfter) > 0);

        BigInteger bnusdBalanceUser2Before = ownerClient.bnUSD.balanceOf(bbalnTesterAddress2);
        balancedClient2.dividends.claimDividends();

        BigInteger bnusdBalanceUser2After = ownerClient.bnUSD.balanceOf(bbalnTesterAddress2);
        BigInteger newUnclaimedDividends2 =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress2).get(balanced.bnusd._address().toString());

        // unclaimedDividends goes to user wallet
        assertEquals(bnusdBalanceUser2After, bnusdBalanceUser2Before.add(unclaimedDividendsAfter2));

        // user claims the rewards of baln stake after many days of bbaln start
        // once user claims dividends the unclaimedDividends become 0
        assertEquals(newUnclaimedDividends2, BigInteger.ZERO);
    }

    @Test
    @Order(9)
    void testBBaln_claim() {
        /*
        1. User1 claims the dividends and the expected dividends is sent to user wallet.
        2. After the claim , there will be dividends for that user only if dividends is received by the contract.
        3. Multiple claim of dividends doesn't increase the balance.
         */
        Address bbalnTesterAddress = balancedClient1.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger unclaimedDividendsBefore =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        BigInteger bnusdBalanceUserBefore = ownerClient.bnUSD.balanceOf(bbalnTesterAddress);

        balancedClient1.dividends.claimDividends();

        BigInteger newUnclaimedDividends =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        BigInteger actualBnusdAfterClaim = ownerClient.bnUSD.balanceOf(bbalnTesterAddress);
        // claims multiple times
        balancedClient1.dividends.claimDividends();

        // bnusd in user wallet doesn't increase
        assertEquals(actualBnusdAfterClaim, ownerClient.bnUSD.balanceOf(bbalnTesterAddress));

        BigInteger expectedBnusdAfterClaim = bnusdBalanceUserBefore.add(unclaimedDividendsBefore);

        // unclaimedDividends goes to user wallet
        assertEquals(actualBnusdAfterClaim, expectedBnusdAfterClaim);

        // once user claims dividends the unclaimedDividends become null
        assertEquals(newUnclaimedDividends, BigInteger.ZERO);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        newUnclaimedDividends =
                ownerClient.dividends.getUnclaimedDividends(bbalnTesterAddress).get(balanced.bnusd._address().toString());

        // unclaimed dividends have some value once dividends is received by contract
        assertTrue(newUnclaimedDividends.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    @Order(10)
    void testBBaln_newUser() {
        /*
        A new user comes and locks the baln and that user will be eligible to earn dividends
        anytime after that.
         */
        Address bbalnTesterAddress = balancedClient3.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger availableBalnBalance = balancedClient1.baln.availableBalanceOf(bbalnTesterAddress);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // a new user will have 0 accumulated dividends
        assertEquals(balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString()), BigInteger.ZERO);

        // locks baln for 4 weeks
        balancedClient3.baln.transfer(balancedClient1.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        // for dividends
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsBefore =
                balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        BigInteger bnusdBalancePre = ownerClient.bnUSD.balanceOf(bbalnTesterAddress);
        // after user locks baln, he will start getting dividends
        assertTrue(unclaimedDividendsBefore.compareTo(BigInteger.ZERO) > 0);

        balancedClient3.dividends.claimDividends();

        BigInteger bnusdBalancePost = ownerClient.bnUSD.balanceOf(bbalnTesterAddress);

        assertEquals(bnusdBalancePost, bnusdBalancePre.add(unclaimedDividendsBefore));

        // after claiming dividends unclaimed dividends will be null unless dividends is received.
        assertEquals(balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString()), BigInteger.ZERO);

    }

    @Test
    @Order(11)
    void testBBaln_newUser_kicked() {
        /*
        A user starts getting less dividends once kicked.
         */
        Address bbalnTesterAddress = balancedClient3.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsBefore =
                balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsAfter =
                balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        // checking dividends before they are kicked
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
        balancedClient3.dividends.claimDividends();

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsBefore =
                balancedClient3.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        balancedClient1.boostedBaln.kick(bbalnTesterAddress);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfter =
                balancedClient1.dividends.getUnclaimedDividends(bbalnTesterAddress).get(ownerClient.bnUSD._address().toString());
        // checking dividends once they are kicked
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
    }

    @Test
    @Order(12)
    void testRemoveCategories() {
        // test the removal of categories from dividends
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

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
        ownerClient.dividends.setDividendsCategoryPercentage(percentMap);

        // removing the categories
        ownerClient.dividends.removeDividendsCategory("baln_holders");
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        List<String> categories;
        categories = ownerClient.dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    @Order(13)
    void testAddCategories() {
        // add new categories in dividends

        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        ownerClient.dividends.setDistributionActivationStatus(true);
        ownerClient.dividends.addDividendsCategory("baln_holders");
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
        List<String> categories;
        categories = ownerClient.dividends.getDividendsCategories();
        assertEquals("baln_holders", categories.get(categories.size() - 1));
    }

    @Test
    @Order(14)
    void testContinuousDividends_daofund() {
        stakeAndProvideLiquidity();
        // Arrange
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        balanced.increaseDay(1);
        ownerClient.dividends.distribute((txr) -> {
        });

        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        // Assert
        BigInteger daoPercentage = ownerClient.dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFee = fee.multiply(daoPercentage).divide(EXA);
        assertEquals(daoFundBalancePre.add(daoFee), daoFundBalancePost);
    }

    @Test
    @Order(15)
    void testChangeInPercentage() {

        balanced.increaseDay(1);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

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
        ownerClient.dividends.setDividendsCategoryPercentage(percentMap);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        BigInteger daoBalanceBefore = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        BigInteger originationFees = BigInteger.valueOf(100);
        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = ownerClient.dividends.getDividendsPercentage();
        BigInteger dividendsToDao = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);

        BigInteger daoBalanceAfter = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        assertEquals(daoBalanceAfter, daoBalanceBefore.add(dividendsToDao));
    }


    @Test
    @Order(16)
    void testContinuousRewards() {
        // test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
        // the dividends
        balanced.increaseDay(1);
        balanced.syncDistributions();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null,
                null);

        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        ownerClient.staking.stakeICX(amount, balancedClient4.getAddress(), null);
        ownerClient.staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);
        balanced.syncDistributions();
        // claim rewards for the user
        ownerClient.rewards.claimRewards();

        // provides liquidity to baln/Sicx pool
        ownerClient.baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        ownerClient.baln.transfer(balancedClient4.getAddress(),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by tester wallet
        balancedClient4.baln.stake(lpAmount);

        String name = "BALN/sICX";
        BigInteger pid = ownerClient.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        ownerClient.governance.setMarketName(pid, name);

        // for bbaln user
        BigInteger prevBbalnUserBalance =
                ownerClient.dividends.getUnclaimedDividends(balancedClient1.getAddress()).get(ownerClient.bnUSD._address().toString());

        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        Map<String, BigInteger> userDividends =
                ownerClient.dividends.getUnclaimedDividends(Address.fromString(owner.getAddress().toString()));

        Map<String, BigInteger> userDividendsTester =
                ownerClient.dividends.getUnclaimedDividends(balancedClient4.getAddress());

        BigInteger userDividendsBnusd = userDividends.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        BigInteger userDividendsTesterBnusd = userDividendsTester.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        // LP provider should have zero dividends to claim after continuous rewards is activated
        assertEquals(userDividendsBnusd, BigInteger.ZERO);
        // neither baln staker nor lp provider should receive dividends
        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd), BigInteger.ZERO);
        BigInteger newBbalnUserBalance =
                ownerClient.dividends.getUnclaimedDividends(balancedClient1.getAddress()).get(ownerClient.bnUSD._address().toString());
        // user with bbaln dividends is increased
        assertTrue(newBbalnUserBalance.compareTo(prevBbalnUserBalance) > 0);
    }
}
