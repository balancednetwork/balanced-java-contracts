/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import com.eclipsesource.json.JsonArray;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DividendsIntegrationTest {
    static Balanced balanced;

    static BalancedClient alice;
    static BalancedClient bob;
    static BalancedClient charlie;
    static BalancedClient Dave;
    static BalancedClient Eve;
    static BalancedClient Ferry;
    static BalancedClient owner;
    static BalancedClient reader;

    static BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        alice = balanced.newClient();
        bob = balanced.newClient();
        charlie = balanced.newClient();
        Dave = balanced.newClient();
        Eve = balanced.newClient();
        Ferry = balanced.newClient();

        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);

    }

    @Test
    @Order(1)
    void testName() throws Exception {
        BalancedClient balancedClient = balanced.newClient();
        assertEquals("Balanced Dividends", balancedClient.dividends.name());
    }

    @Test
    @Order(2)
    void setupBalnEarnings() {
        /* test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
         the dividends */

        balanced.increaseDay(1);
        balanced.syncDistributions();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        owner.loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD",
                loanAmount, null, null);

        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        owner.staking.stakeICX(amount, Dave.getAddress(), null);
        owner.staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);
        balanced.syncDistributions();
        // claim rewards for the user
        owner.rewards.claimRewards(null);

        // provides liquidity to baln/Sicx pool by owner
        owner.baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        owner.sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        owner.dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        owner.baln.transfer(Dave.getAddress(),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);
    }

    @Test
    @Order(3)
    void testBBaln_daofund() {
        /*
        If there are no supply of boosted baln even after bbaln day is started
        but there are some dividends received by dividends contract then,
        1. Daofund will get all the dividends .
        2. None of the user dividends will be increased.
         */
        createNewUserForBBaln();

        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();
        Address addressCharlie = charlie.getAddress();

        Map<String, BigInteger> unclaimedDividendsAlice = owner.dividends.getUnclaimedDividends(addressAlice);
        Map<String, BigInteger> unclaimedDividendsBob = owner.dividends.getUnclaimedDividends(addressBob);
        Map<String, BigInteger> unclaimedDividendsCharlie = owner.dividends.getUnclaimedDividends(addressCharlie);

        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        // loan taken after the update of the contract
        owner.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = owner.bnUSD.balanceOf(balanced.daofund._address());

        // dividends are sent to daofund directly as there are no boosted baln yet
        assertEquals(daoFundBalancePre.add(fee), daoFundBalancePost);
        // dividends shouldn't increase once bbalnDay is set unless there is some transaction
        assertEquals(unclaimedDividendsAlice, owner.dividends.getUnclaimedDividends(addressAlice));
        assertEquals(unclaimedDividendsBob, owner.dividends.getUnclaimedDividends(addressBob));

        // new user will have nothing unless he adds bbaln
        assertEquals(unclaimedDividendsCharlie, owner.dividends.getUnclaimedDividends(addressCharlie));
    }

    @Test
    @Order(4)
    void testBBaln_lock() {
        /*
        1. Daofund doesn't get all the dividends once user starts locking baln token.
        2. User1 locks balance for few weeks and starts getting dividends.
        2. User2 doesn't lock balance and the unclaimed dividends remain same for few weeks.
         */
        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();

        Map<String, BigInteger> unclaimedDividendsBeforeBob = reader.dividends.getUnclaimedDividends(addressBob);

        // user unstakes all the baln token
        alice.baln.stake(BigInteger.ZERO);
        BigInteger availableBalnBalance = alice.baln.availableBalanceOf(addressAlice);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // alice locks baln for 4 weeks
        alice.baln.transfer(alice.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger sICXFee = EXA.multiply(BigInteger.valueOf(100));
        owner.staking.stakeICX(sICXFee, null, null);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        BigInteger daoFundBnUSDBalancePre = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundSICXBalancePre = owner.sicx.balanceOf(balanced.daofund._address());
        Map<String, BigInteger> unclaimedDividendsBeforeAlice = owner.dividends.getUnclaimedDividends(addressAlice);

        // did tx to create a dividends

        owner.stakeDepositAndBorrow(collateral, loanAmount);
        owner.staking.stakeICX(sICXFee, null, null);
        owner.sicx.transfer(balanced.feehandler._address(), sICXFee, null);

        Map<String, BigInteger> unclaimedDividendsAfterAlice = owner.dividends.getUnclaimedDividends(addressAlice);

        BigInteger daoFundBnUSDBalancePost = reader.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundSICXBalancePost = reader.sicx.balanceOf(balanced.daofund._address());
        BigInteger daoPercentage = reader.dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFeeBnUSD = fee.multiply(daoPercentage).divide(EXA);
        BigInteger daoFeeSICX = sICXFee.multiply(daoPercentage).divide(EXA);

        // daofund doesn't get all the dividends value once there is a supply in bbaln
        assertEquals(daoFundBnUSDBalancePre.add(daoFeeBnUSD), daoFundBnUSDBalancePost);
        assertEquals(daoFundSICXBalancePre.add(daoFeeSICX), daoFundSICXBalancePost);

        // new fees are generated
        owner.staking.stakeICX(sICXFee, null, null);
        owner.sicx.transfer(balanced.feehandler._address(), sICXFee, null);
        owner.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        Map<String, BigInteger> unclaimedDividendsAfterBob = owner.dividends.getUnclaimedDividends(addressBob);
        Map<String, BigInteger> newDividendsAlice = owner.dividends.getUnclaimedDividends(addressAlice);

        /* as Bob is not migrated to bbaln , the unclaimed dividends
         remains same even after days */
        assertEquals(unclaimedDividendsAfterBob, unclaimedDividendsBeforeBob);

        /* dividends keeps on increasing for alice after dividends
        is received by contract */
        assertTrue(newDividendsAlice.get(balanced.sicx._address().toString()).compareTo(unclaimedDividendsAfterAlice.get(balanced.sicx._address().toString())) > 0);
        assertTrue(newDividendsAlice.get(balanced.bnusd._address().toString()).compareTo(unclaimedDividendsAfterAlice.get(balanced.bnusd._address().toString())) > 0);

        verifyClaim(bob);
        verifyClaim(alice);
    }

    @Test
    @Order(5)
    void testBBaln_claimOnly() {
        verifyClaim(Eve);
        // unclaimed dividends should go to Eve's wallet after claiming

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsAfterEve =
                owner.dividends.getUnclaimedDividends(Eve.getAddress()).get(balanced.bnusd._address().toString());

        // unclaimed dividends remains 0 for that user
        assertEquals(unclaimedDividendsAfterEve, BigInteger.ZERO);
    }

    @Test
    @Order(6)
    void testBBaln_claimAfterUnstake() {
        /* Ferry claims the dividends after unstaking baln token. */
        Address addressFerry = Ferry.getAddress();
        BigInteger unclaimedDividendsBeforeFerry =
                owner.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());
        BigInteger bnusdBeforeFerry = Ferry.bnUSD.balanceOf(addressFerry);
        // Ferry unstakes baln token
        Ferry.baln.stake(BigInteger.ZERO);
        Ferry.dividends.claimDividends();
        BigInteger unclaimedDividendsAfterFerry =
                owner.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());
        BigInteger bnusdAfterFerry = Eve.bnUSD.balanceOf(addressFerry);
        // unclaimed dividends become 0 after claiming
        assertEquals(unclaimedDividendsAfterFerry, BigInteger.ZERO);
        // unclaimed dividends should go to Eve's wallet after claiming
        assertEquals(bnusdBeforeFerry.add(unclaimedDividendsBeforeFerry), bnusdAfterFerry);

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfterFerry =
                owner.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());

        // unclaimed dividends remains 0 for that user
        assertEquals(unclaimedDividendsAfterFerry, BigInteger.ZERO);

    }

    @Test
    @Order(7)
    void testBBaln_claim() {
        /*
        1. Alice claims the dividends and the expected dividends is sent to Alice wallet.
        2. After the claim , there will be dividends for Alice only if dividends is received by the contract.
        3. Multiple claim of dividends doesn't increase the balance.
         */
        Address addressAlice = alice.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger unclaimedDividendsBeforeAlice =
                owner.dividends.getUnclaimedDividends(addressAlice).get(owner.bnUSD._address().toString());
        BigInteger bnusdBalanceBeforeAlice = owner.bnUSD.balanceOf(addressAlice);

        // alice claim dividends
        alice.dividends.claimDividends();

        BigInteger newUnclaimedDividendsAlice =
                owner.dividends.getUnclaimedDividends(addressAlice).get(owner.bnUSD._address().toString());
        BigInteger bnusdAfterAlice = owner.bnUSD.balanceOf(addressAlice);

        // claims second time
        alice.dividends.claimDividends();

        // bnusd in Alice wallet doesn't increase
        assertEquals(bnusdAfterAlice, owner.bnUSD.balanceOf(addressAlice));

        BigInteger expectedBnusdAfterClaim = bnusdBalanceBeforeAlice.add(unclaimedDividendsBeforeAlice);

        // unclaimedDividends goes to user wallet
        assertEquals(bnusdAfterAlice, expectedBnusdAfterClaim);

        // once user claims dividends the unclaimedDividends become zero
        assertEquals(newUnclaimedDividendsAlice, BigInteger.ZERO);

        balanced.increaseDay(1);
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        newUnclaimedDividendsAlice =
                owner.dividends.getUnclaimedDividends(addressAlice).get(balanced.bnusd._address().toString());

        // unclaimed dividends have some value once dividends is received by contract
        assertTrue(newUnclaimedDividendsAlice.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    @Order(8)
    void testBBaln_newUser() {
        /*
        A new user comes and locks the baln and that user will be eligible to earn dividends
        anytime after that.
         */
        Address addressCharlie = charlie.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger availableBalnBalanceCharlie = alice.baln.availableBalanceOf(addressCharlie);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // a new user will have 0 accumulated dividends
        assertEquals(alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString()),
                BigInteger.ZERO);

        // locks baln for 4 weeks
        charlie.baln.transfer(alice.boostedBaln._address(), availableBalnBalanceCharlie.divide(BigInteger.TWO),
                data.getBytes());

        // for dividends
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsBeforeCharlie =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString());
        BigInteger bnusdBalancePreCharlie = owner.bnUSD.balanceOf(addressCharlie);

        // after CHarlie locks baln, he will start getting dividends
        assertTrue(unclaimedDividendsBeforeCharlie.compareTo(BigInteger.ZERO) > 0);

        charlie.dividends.claimDividends();

        BigInteger bnusdBalancePostCharlie = owner.bnUSD.balanceOf(addressCharlie);

        assertEquals(bnusdBalancePostCharlie, bnusdBalancePreCharlie.add(unclaimedDividendsBeforeCharlie));

        // after claiming dividends unclaimed dividends will be zero unless dividends is received.
        assertEquals(alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString()),
                BigInteger.ZERO);

    }

    @Test
    @Order(9)
    void testBBaln_newUser_kicked() {
        /*
        A user starts getting less dividends once kicked.
         */
        Address addressCharlie = charlie.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsBefore =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString());
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsAfter =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString());
        // checking if the user are getting same dividends everytime
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
        charlie.dividends.claimDividends();


        // charlie unclaimed dividends after claim is 0
        assertEquals(charlie.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString()),
                BigInteger.ZERO);

        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsBefore =
                charlie.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString());
        charlie.dividends.claimDividends();

        alice.boostedBaln.kick(addressCharlie);
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfter =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(owner.bnUSD._address().toString());
        // user dividends is decreased once they are kicked
        assertTrue(unclaimedDividendsAfter.compareTo(unclaimedDividendsBefore) <= 0);
    }

    @Test
    @Order(10)
    void testRemoveCategories() {
        // test the removal of categories from dividends
        JsonArray categoryParameter = new JsonArray()
                .add(createJsonDistribution("baln_holders", BigInteger.ZERO))
                .add(createJsonDistribution("daofund", new BigInteger("1000000000000000000")));
        JsonArray categoryParameters = new JsonArray()
                .add(createParameter("Struct[]", categoryParameter));

        owner.governance.execute(createSingleTransaction(balanced.dividends._address(),
                "setDividendsCategoryPercentage", categoryParameters).toString());

        // removing the categories
        owner.governance.execute(createSingleTransaction(balanced.dividends._address(), "removeDividendsCategory",
                new JsonArray().add(createParameter("baln_holders"))).toString());

        List<String> categories;
        categories = owner.dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    @Order(11)
    void testAddCategories() {
        // add new categories in dividends
        owner.governance.execute(createSingleTransaction(balanced.dividends._address(), "addDividendsCategory",
                new JsonArray().add(createParameter("baln_holders"))).toString());

        List<String> categories;
        categories = owner.dividends.getDividendsCategories();
        assertEquals("baln_holders", categories.get(categories.size() - 1));
    }

    @Test
    @Order(12)
    void testChangeInPercentage() {

        balanced.increaseDay(1);

        JsonArray categoryParameter = new JsonArray()
                .add(createJsonDistribution("baln_holders", new BigInteger("900000000000000000")))
                .add(createJsonDistribution("daofund", new BigInteger("100000000000000000")));
        JsonArray categoryParameters = new JsonArray()
                .add(createParameter("Struct[]", categoryParameter));

        owner.governance.execute(createSingleTransaction(balanced.dividends._address(),
                "setDividendsCategoryPercentage", categoryParameters).toString());

        BigInteger daoBalanceBefore = owner.bnUSD.balanceOf(balanced.daofund._address());

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        owner.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD",
                loanAmount, null, null);

        BigInteger originationFees = BigInteger.valueOf(100);
        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = owner.dividends.getDividendsPercentage();
        BigInteger dividendsToDao = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);

        BigInteger daoBalanceAfter = owner.bnUSD.balanceOf(balanced.daofund._address());

        assertEquals(daoBalanceAfter, daoBalanceBefore.add(dividendsToDao));
    }


    void createNewUserForBBaln() {
        // alice and bob stakes baln token

        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();
        Address addressCharlie = charlie.getAddress();
        Address addressEve = Eve.getAddress();
        Address addressFerry = Ferry.getAddress();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.syncDistributions();
        balanced.increaseDay(10);
        for (int i = 0; i < 8; i++) {
            balanced.syncDistributions();
        }

        owner.rewards.claimRewards(null);
        // sent baln token to two users
        owner.baln.transfer(addressAlice, collateral, new byte[0]);
        owner.baln.transfer(addressBob, collateral, new byte[0]);
        owner.baln.transfer(addressCharlie, collateral, new byte[0]);
        owner.baln.transfer(addressEve, collateral, new byte[0]);
        owner.baln.transfer(addressFerry, collateral, new byte[0]);

        // staking baln token with multiple different users.
        BigInteger stakedAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        alice.baln.stake(stakedAmount);
        bob.baln.stake(stakedAmount);
        Eve.baln.stake(stakedAmount);
        Ferry.baln.stake(stakedAmount);

        // loan taken to send some dividends to contract
        owner.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
    }

    private void verifyClaim(BalancedClient client) {
        BigInteger bnUSDBefore = reader.bnUSD.balanceOf(client.getAddress());
        BigInteger sICXBefore = reader.sicx.balanceOf(client.getAddress());
        BigInteger balnBefore = reader.baln.balanceOf(client.getAddress());

        Map<String, BigInteger> divs = reader.dividends.getUnclaimedDividends(client.getAddress());
        client.dividends.claimDividends();

        BigInteger bnUSDAfter = reader.bnUSD.balanceOf(client.getAddress());
        BigInteger sICXAfter = reader.sicx.balanceOf(client.getAddress());
        BigInteger balnAfter = reader.baln.balanceOf(client.getAddress());

        assertEquals(bnUSDBefore.add(divs.get(balanced.bnusd._address().toString())), bnUSDAfter);
        assertEquals(sICXBefore.add(divs.get(balanced.sicx._address().toString())), sICXAfter);
        assertEquals(balnBefore.add(divs.get(balanced.baln._address().toString())), balnAfter);

        divs = reader.dividends.getUnclaimedDividends(client.getAddress());

        assertEquals(BigInteger.ZERO, divs.getOrDefault(balanced.bnusd._address().toString(), BigInteger.ZERO));
        assertEquals(BigInteger.ZERO, divs.getOrDefault(balanced.sicx._address().toString(), BigInteger.ZERO));
        assertEquals(BigInteger.ZERO, divs.getOrDefault(balanced.baln._address().toString(), BigInteger.ZERO));
    }

    void setMarketName(BigInteger poolID, String name) {
        JsonArray setMarketNameParameters = new JsonArray()
                .add(createParameter(poolID))
                .add(createParameter(name));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "setMarketName", setMarketNameParameters));

        owner.governance.execute(actions.toString());
    }

}
