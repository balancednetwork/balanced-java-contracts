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

package network.balanced.score.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.tokens.utils.DummyContract;
import network.balanced.score.tokens.utils.IRC2Token;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * Test voting power in the following scenario.
 * Alice:
 * ~~~~~~~
 * ^
 * | *       *
 * | | \     |  \
 * | |  \    |    \
 * +-+---+---+------+---> t
 * Bob:
 * ~~~~~~~
 * ^
 * |         *
 * |         | \
 * |         |  \
 * +-+---+---+---+--+---> t
 * Alice has 100% of voting power in the first period.
 * She has 2/3 power at the start of 2nd period, with Bob having 1/2 power
 * (due to smaller locktime).
 * Alice's power grows to 100% by Bob's unlock.
 * Checking that totalSupply is appropriate.
 * After the test is done, check all over again with balanceOfAt / totalSupplyAt
 */
public class VotingPowerTest extends AbstractBoostedBalnTest {
    private static final ServiceManager sm = getServiceManager();
    private Score bBALNScore;

    private static final BigInteger INITIAL_SUPPLY = BigInteger.TEN.multiply(ICX);
    private static final String BOOSTED_BALANCE = "Boosted Balance";
    private static final String B_BALANCED_SYMBOL = "bBALN";
    private static final Account alice = sm.createAccount();
    private static final Account bob = sm.createAccount();

    public static BigInteger SECOND = BigInteger.TEN.pow(6);
    public static BigInteger HOUR = SECOND.multiply(BigInteger.valueOf(3600L));
    public static BigInteger DAY = HOUR.multiply(BigInteger.valueOf(24L));
    public static BigInteger WEEK = DAY.multiply(BigInteger.valueOf(7L));
    public static BigInteger YEAR = DAY.multiply(BigInteger.valueOf(365L));
    public static BigInteger MAX_TIME = YEAR.multiply(BigInteger.valueOf(4L));

    private BoostedBalnImpl scoreSpy;

    @BeforeEach
    public void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC2Token.class, INITIAL_SUPPLY);
        Score rewardScore = sm.deploy(owner, DummyContract.class);
        bBALNScore = sm.deploy(owner, BoostedBalnImpl.class, tokenScore.getAddress(), rewardScore.getAddress(),
                dividendsScore.getAddress(), BOOSTED_BALANCE, B_BALANCED_SYMBOL);
        Score dividendsScore = sm.deploy(owner, DummyContract.class);
        bBALNScore = sm.deploy(owner, BoostedBalnImpl.class, tokenScore.getAddress(), rewardScore.getAddress(),
                dividendsScore.getAddress(), BOOSTED_BALANCE, B_BALANCED_SYMBOL);
        tokenScore.invoke(owner, "mintTo", alice.getAddress(), ICX.multiply(BigInteger.valueOf(100L)));
        tokenScore.invoke(owner, "mintTo", bob.getAddress(), ICX.multiply(BigInteger.valueOf(100L)));

        scoreSpy = (BoostedBalnImpl) spy(bBALNScore.getInstance());
        bBALNScore.setInstance(scoreSpy);

        bBALNScore.invoke(owner, "setMinimumLockingAmount", ICX);

        ServiceManager.Block currentBlock = sm.getBlock();
        currentBlock.increase(1_000_000);
//        Move to timing which is good for testing - beginning of a UTC week
        BigInteger timestamp = getBlockTimestamp();
        setBlockTimestamp(timestamp.divide(WEEK).add(BigInteger.ONE).multiply(WEEK).longValue());
    }

    @Test
    @DisplayName("Test initial balance")
    public void testInitialBalance() {
        BigInteger aliceBalance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        BigInteger bobBalance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        BigInteger totalSupply = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        Assertions.assertEquals(BigInteger.ZERO, bobBalance);
        Assertions.assertEquals(BigInteger.ZERO, aliceBalance);
        Assertions.assertEquals(BigInteger.ZERO, totalSupply);
    }

    @Test
    @DisplayName("Test voting power")
    public void testVotingPower() {

        Map<String, State> states = new HashMap<>();
        Map<String, List<State>> statesMapping = new HashMap<>();

        BigInteger timestamp = getBlockTimestamp();
        states.put("before_deposits", getState());
        BigInteger lockUntil = timestamp.divide(WEEK).add(BigInteger.ONE).multiply(WEEK).add(HOUR);
        BigInteger amount = ICX.multiply(BigInteger.TEN);

        addBlockHeight(HOUR);

        createLock(alice, lockUntil, amount);

        states.put("alice_deposit", getState());

        addBlockHeight(HOUR);

        BigInteger alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        BigInteger bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        BigInteger total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);
        //1 block minted
        BigInteger time = WEEK.subtract(HOUR.multiply(BigInteger.TWO).add(SECOND.multiply(BigInteger.TWO)));
        assertEquals(amount.divide(MAX_TIME).multiply(time), alice_balance);
        assertEquals(amount.divide(MAX_TIME).multiply(time), total_balance);
        assertEquals(BigInteger.ZERO, bob_balance);


        BigInteger t0 = getBlockTimestamp();

        List<State> alice_in_0 = new ArrayList<>();
        alice_in_0.add(getState());

        for (int i = 1; i <= 7; i++) {
            addBlockHeight(DAY);
            BigInteger dt = getBlockTimestamp().subtract(t0);
            alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
            bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
            total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

            BigInteger timeInterval = time.subtract(dt).max(BigInteger.ZERO);
            assertEquals(amount.divide(MAX_TIME).multiply(timeInterval), alice_balance);
            assertEquals(amount.divide(MAX_TIME).multiply(timeInterval), total_balance);
            assertEquals(BigInteger.ZERO, bob_balance);
            alice_in_0.add(getState());
        }
        statesMapping.put("alice_in_0", alice_in_0);
        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        assertEquals(BigInteger.ZERO, alice_balance);

        bBALNScore.invoke(alice, "withdraw");
        states.put("alice_withdraw", getState());
        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        assertEquals(BigInteger.ZERO, alice_balance);
        assertEquals(BigInteger.ZERO, total_balance);
        assertEquals(BigInteger.ZERO, bob_balance);

        addBlockHeight(HOUR);

//         Next week (for round counting)

        timestamp = getBlockTimestamp();
        BigInteger newWeek = timestamp.divide(WEEK).add(BigInteger.ONE).multiply(WEEK);
        setBlockTimestamp(newWeek.longValue());
        //1 block minted
        BigInteger TWO_WEEKS = WEEK.multiply(BigInteger.TWO).subtract(SECOND.multiply(BigInteger.TWO));
        timestamp = getBlockTimestamp();
        lockUntil = timestamp.divide(WEEK).add(BigInteger.TWO).multiply(WEEK);
        amount = ICX.multiply(BigInteger.TEN);

        createLock(alice, lockUntil, amount);
        states.put("alice_deposit_2", getState());

        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        assertEquals(amount.divide(MAX_TIME).multiply(TWO_WEEKS), alice_balance);
        assertEquals(amount.divide(MAX_TIME).multiply(TWO_WEEKS), total_balance);
        assertEquals(BigInteger.ZERO, bob_balance);

        lockUntil = getBlockTimestamp().divide(WEEK).add(BigInteger.ONE).multiply(WEEK);
        createLock(bob, lockUntil, amount);

        states.put("bob_deposit_2", getState());

        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        //2 transaction or 2 block minted
        BigInteger ONE_WEEK = WEEK.subtract(SECOND.multiply(BigInteger.valueOf(4L)));
        //2 transaction or 2 block minted
        TWO_WEEKS = WEEK.multiply(BigInteger.valueOf(2L)).subtract(SECOND.multiply(BigInteger.valueOf(4L)));
        assertEquals(amount.divide(MAX_TIME).multiply(TWO_WEEKS), alice_balance);
        assertEquals(amount.divide(MAX_TIME).multiply(ONE_WEEK), bob_balance);
        //2 transaction or 2 block minted
        assertEquals(amount.divide(MAX_TIME).multiply(WEEK.multiply(BigInteger.valueOf(3L)).subtract(SECOND.multiply(BigInteger.valueOf(8L)))), total_balance);

        t0 = getBlockTimestamp();
        addBlockHeight(HOUR);
//2 transaction or 2 block minted
        BigInteger aliceTime = WEEK.multiply(BigInteger.TWO).subtract(SECOND.multiply(BigInteger.valueOf(4L)));
        BigInteger bobTime = WEEK.subtract(SECOND.multiply(BigInteger.valueOf(4L)));

        List<State> alice_bob_in_2 = new LinkedList<>();
//    # Beginning of week: weight 3
//    # End of week: weight 1
        for (int i = 1; i <= 7; i++) {
            addBlockHeight(DAY);
            BigInteger dt = getBlockTimestamp().subtract(t0);
            alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
            bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
            total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

            assertEquals(amount.divide(MAX_TIME).multiply(aliceTime.subtract(dt).max(BigInteger.ZERO)), alice_balance);
            assertEquals(amount.divide(MAX_TIME).multiply(bobTime.subtract(dt).max(BigInteger.ZERO)), bob_balance);
            assertEquals(alice_balance.add(bob_balance), total_balance);
            alice_bob_in_2.add(getState());
        }
        statesMapping.put("alice_bob_in_2", alice_bob_in_2);
        addBlockHeight(HOUR);
        bBALNScore.invoke(bob, "withdraw");
        states.put("bob_withdraw_1", getState());

//3 transaction or 3 block minted
        BigInteger timePeriod =
                WEEK.subtract(HOUR.multiply(BigInteger.TWO).add(SECOND.multiply(BigInteger.valueOf(6L))));
        t0 = getBlockTimestamp();
        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        assertEquals(alice_balance, total_balance);
        assertEquals(amount.divide(MAX_TIME).multiply(timePeriod), total_balance);
        assertEquals(BigInteger.ZERO, bob_balance);

        addBlockHeight(HOUR);

//3 transaction or 3 block minted
        aliceTime = WEEK.subtract(HOUR.multiply(BigInteger.TWO).add(SECOND.multiply(BigInteger.valueOf(6L))));

        List<State> alice_in_2 = new LinkedList<>();

        for (int i = 1; i <= 7; i++) {
            addBlockHeight(DAY);
            BigInteger dt = getBlockTimestamp().subtract(t0);
            alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
            bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
            total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);
            assertEquals(amount.divide(MAX_TIME).multiply(aliceTime.subtract(dt).max(BigInteger.ZERO)), alice_balance);
            assertEquals(BigInteger.ZERO, bob_balance);
            assertEquals(alice_balance, total_balance);
            alice_in_2.add(getState());
        }

        statesMapping.put("alice_in_2", alice_in_2);

        bBALNScore.invoke(alice, "withdraw");
        states.put("alice_withdraw_2", getState());
        addBlockHeight(HOUR);
        bBALNScore.invoke(bob, "withdraw");
        states.put("bob_withdraw_2", getState());
        alice_balance = (BigInteger) bBALNScore.call("balanceOf", alice.getAddress(), BigInteger.ZERO);
        bob_balance = (BigInteger) bBALNScore.call("balanceOf", bob.getAddress(), BigInteger.ZERO);
        total_balance = (BigInteger) bBALNScore.call("totalSupply", BigInteger.ZERO);

        assertEquals(BigInteger.ZERO, alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(BigInteger.ZERO, total_balance);


//         Now test historical balanceOfAt and others
        State before_deposits = states.get("before_deposits");
        BigInteger before_deposits_block = BigInteger.valueOf(before_deposits.block);
        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), before_deposits_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), before_deposits_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", before_deposits_block);
        assertEquals(BigInteger.ZERO, alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(BigInteger.ZERO, total_balance);


        State alice_deposit = states.get("alice_deposit");
        BigInteger alice_deposit_block = BigInteger.valueOf(alice_deposit.block);
        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), alice_deposit_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), alice_deposit_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", alice_deposit_block);

        time = WEEK.subtract(HOUR.add(SECOND.multiply(BigInteger.TWO)));

        assertEquals(amount.divide(MAX_TIME).multiply(time), alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(alice_balance, total_balance);


        int i = 0;
        for (State stage : statesMapping.get("alice_in_0")) {
            BigInteger alice_block = BigInteger.valueOf(stage.block);
            alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), alice_block);
            bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), alice_block);
            total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", alice_block);

            BigInteger time_left =
                    WEEK.multiply(BigInteger.valueOf(7 - i)).divide(BigInteger.valueOf(7L)).subtract(HOUR.add(HOUR).add(SECOND.multiply(BigInteger.valueOf(2))));

            assertEquals(BigInteger.ZERO, bob_balance);
            assertEquals(amount.divide(MAX_TIME).multiply(time_left.max(BigInteger.ZERO)), alice_balance);
            assertEquals(alice_balance, total_balance);
            i++;
        }

        State alice_withdraw = states.get("alice_withdraw");
        BigInteger alice_withdraw_block = BigInteger.valueOf(alice_withdraw.block);

        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), alice_withdraw_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), alice_withdraw_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", alice_withdraw_block);

        assertEquals(BigInteger.ZERO, alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(BigInteger.ZERO, total_balance);


        State alice_deposit_2 = states.get("alice_deposit_2");
        BigInteger alice_deposit_2_block = BigInteger.valueOf(alice_deposit_2.block);

        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), alice_deposit_2_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), alice_deposit_2_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", alice_deposit_2_block);
        time = WEEK.multiply(BigInteger.TWO).subtract(SECOND.multiply(BigInteger.TWO));
        assertEquals(amount.divide(MAX_TIME).multiply(time), alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(alice_balance, total_balance);


        State bob_deposit_2 = states.get("bob_deposit_2");
        BigInteger bob_deposit_2_block = BigInteger.valueOf(bob_deposit_2.block);

        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), bob_deposit_2_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), bob_deposit_2_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", bob_deposit_2_block);

        assertEquals(amount.divide(MAX_TIME).multiply(WEEK.multiply(BigInteger.TWO).subtract(SECOND.multiply(BigInteger.valueOf(4)))), alice_balance);
        assertEquals(amount.divide(MAX_TIME).multiply(WEEK.multiply(BigInteger.valueOf(3)).subtract(SECOND.multiply(BigInteger.valueOf(8)))), total_balance);
        assertEquals(alice_balance.add(bob_balance), total_balance);

        BigInteger t = BigInteger.valueOf(bob_deposit_2.timestamp);

        i = 0;
        for (State stage : statesMapping.get("alice_bob_in_2")) {
            BigInteger w_block = BigInteger.valueOf(stage.block);
            BigInteger w_timestamp = BigInteger.valueOf(stage.timestamp);
            alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), w_block);
            bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), w_block);
            total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", w_block);
            BigInteger delta_time = w_timestamp.subtract(t);

            BigInteger alice_time =
                    WEEK.multiply(BigInteger.TWO).subtract(delta_time).subtract(SECOND.multiply(BigInteger.valueOf(4))).max(BigInteger.ZERO);

            BigInteger bob_time =
                    WEEK.subtract(delta_time).subtract(SECOND.multiply(BigInteger.valueOf(4))).max(BigInteger.ZERO);
            assertEquals(amount.divide(MAX_TIME).multiply(alice_time), alice_balance);
            assertEquals(amount.divide(MAX_TIME).multiply(bob_time), bob_balance);
            assertEquals(alice_balance.add(bob_balance), total_balance);
            i++;
        }


        State bob_withdraw_1 = states.get("bob_withdraw_1");
        BigInteger bob_withdraw_1_block = BigInteger.valueOf(bob_withdraw_1.block);

        alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), bob_withdraw_1_block);
        bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), bob_withdraw_1_block);
        total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", bob_withdraw_1_block);

        assertEquals(amount.divide(MAX_TIME).multiply(WEEK.subtract(HOUR.multiply(BigInteger.TWO).add(SECOND.multiply(BigInteger.valueOf(6))))), alice_balance);
        assertEquals(BigInteger.ZERO, bob_balance);
        assertEquals(alice_balance, total_balance);

        t = BigInteger.valueOf(bob_withdraw_1.timestamp);
        i = 0;
        for (State stage : statesMapping.get("alice_in_2")) {
            BigInteger w_block = BigInteger.valueOf(stage.block);
            BigInteger w_timestamp = BigInteger.valueOf(stage.timestamp);
            alice_balance = (BigInteger) bBALNScore.call("balanceOfAt", alice.getAddress(), w_block);
            bob_balance = (BigInteger) bBALNScore.call("balanceOfAt", bob.getAddress(), w_block);
            total_balance = (BigInteger) bBALNScore.call("totalSupplyAt", w_block);
            BigInteger delta_time = w_timestamp.subtract(t);

            BigInteger a_time =
                    WEEK.subtract(delta_time).subtract(HOUR.multiply(BigInteger.TWO).add(SECOND.multiply(BigInteger.valueOf(6L)))).max(BigInteger.ZERO);
            assertEquals(amount.divide(MAX_TIME).multiply(a_time), alice_balance);
            assertEquals(BigInteger.ZERO, bob_balance);
            assertEquals(alice_balance.add(bob_balance), total_balance);
            i++;
        }
    }

    private void createLock(Account account, BigInteger lockUntil, BigInteger amount) {
        doNothing().when(scoreSpy).onBalanceUpdate(any(), any());
        Map<String, Object> map = new HashMap<>();
        map.put("method", "createLock");
        map.put("params", Map.of("unlockTime", lockUntil));
        JSONObject json = new JSONObject(map);
        byte[] lockBytes = json.toString().getBytes();
        tokenScore.invoke(account, "transfer", bBALNScore.getAddress(), amount, lockBytes);
    }

    private BigInteger getBlockTimestamp() {
        return BigInteger.valueOf(sm.getBlock().getTimestamp());
    }

    private State getState() {
        ServiceManager.Block block = sm.getBlock();
        return new State(block.getHeight(), block.getTimestamp());
    }

    private void setBlockTimestamp(long timestamp) {
        ServiceManager.Block block = sm.getBlock();
        try {
            Field timestampField = block.getClass().getDeclaredField("timestamp");

            timestampField.setAccessible(true);

            timestampField.setLong(block, timestamp - 2_000_000L);
            block.increase();
            timestampField.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    private void addBlockHeight(BigInteger timestamp) {
        long block_delta = timestamp.divide(SECOND).divide(BigInteger.TWO).longValue();
        sm.getBlock().increase(block_delta);
    }


    static class State {
        long block;
        long timestamp;

        public State(long block, long timestamp) {
            this.block = block;
            this.timestamp = timestamp;
        }
    }


}
