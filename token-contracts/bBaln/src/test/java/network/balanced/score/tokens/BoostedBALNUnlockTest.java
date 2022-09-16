/*
 * Copyright (c) 2021-2022 Balanced.network.
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

import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.tokens.utils.DummyContract;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;


public class BoostedBALNUnlockTest extends AbstractBoostedBalnTest {

    private static final ServiceManager sm = getServiceManager();
    private Score bBALNScore;

    private BoostedBalnImpl scoreSpy;

    public static BigInteger WEEK =
            BigInteger.TEN.pow(6).multiply(BigInteger.valueOf(86400L).multiply(BigInteger.valueOf(7L)));
    private static final BigInteger INITIAL_SUPPLY = BigInteger.TEN.multiply(ICX);
    private static final String BOOSTED_BALANCE = "Boosted Balance";
    private static final String B_BALANCED_SYMBOL = "bBALN";

    @BeforeEach
    public void setup() throws Exception {
        Score rewardScore = sm.deploy(owner, DummyContract.class);
        bBALNScore = sm.deploy(owner, BoostedBalnImpl.class, tokenScore.getAddress(), rewardScore.getAddress(), dividendsScore.getAddress(),
                BOOSTED_BALANCE, B_BALANCED_SYMBOL);

        scoreSpy = (BoostedBalnImpl) spy(bBALNScore.getInstance());
        bBALNScore.setInstance(scoreSpy);

        bBALNScore.invoke(owner, "setMinimumLockingAmount", ICX);
    }

    @ParameterizedTest
    @MethodSource("weekListLock")
    @SuppressWarnings("unchecked")
    public void testCreateLockZeroBalance(long unlockTime) {

        long timestamp = sm.getBlock().getTimestamp();
        long expectedUnlock = unlockTime + timestamp;

        Map<String, Object> map = new HashMap<>();
        map.put("method", "createLock");
        map.put("params", Map.of("unlockTime", expectedUnlock));
        JSONObject json = new JSONObject(map);
        byte[] lockBytes = json.toString().getBytes();
        doNothing().when(scoreSpy).onBalanceUpdate(any(), any());
        tokenScore.invoke(owner, "transfer", bBALNScore.getAddress(), ICX, lockBytes);

        Map<String, BigInteger> balance = (Map<String, BigInteger>) bBALNScore.call("getLocked", owner.getAddress());
        long actual_unlock = balance.get("end").longValue();
        long delta = BigInteger.valueOf(actual_unlock - timestamp).divide(BigInteger.TEN.pow(6)).divide(BigInteger.TWO).longValue();

        sm.getBlock().increase(delta - 5);
        BigInteger _balance = (BigInteger) bBALNScore.call("balanceOf", owner.getAddress(), BigInteger.ZERO);
        assertTrue(_balance.compareTo(BigInteger.ZERO) > 0);

        sm.getBlock().increase(6);

        _balance = (BigInteger) bBALNScore.call("balanceOf", owner.getAddress(), BigInteger.ZERO);
        assertEquals(_balance, BigInteger.ZERO);
    }

    @ParameterizedTest
    @MethodSource("extendedUnlockWeeks")
    @SuppressWarnings("unchecked")
    public void testIncreaseLockZeroBalance(long unlockTime, long extendedTime) {

        long timestamp = sm.getBlock().getTimestamp();
        long expectedUnlock = unlockTime + timestamp;

        Map<String, Object> map = new HashMap<>();
        map.put("method", "createLock");
        map.put("params", Map.of("unlockTime", expectedUnlock));
        JSONObject json = new JSONObject(map);
        byte[] lockBytes = json.toString().getBytes();
        doNothing().when(scoreSpy).onBalanceUpdate(any(), any());
        tokenScore.invoke(owner, "transfer", bBALNScore.getAddress(), ICX.multiply(BigInteger.ONE), lockBytes);

        Map<String, BigInteger> balance = (Map<String, BigInteger>) bBALNScore.call("getLocked", owner.getAddress());
        BigInteger initialUnlock = balance.get("end");
        BigInteger expectedExtendedUnlockTime = initialUnlock.add(BigInteger.valueOf(extendedTime));

        bBALNScore.invoke(owner, "increaseUnlockTime", expectedExtendedUnlockTime);

        Map<String, BigInteger> newBalance = (Map<String, BigInteger>) bBALNScore.call("getLocked", owner.getAddress());
        BigInteger extendedActualUnlock = newBalance.get("end");

        long delta = extendedActualUnlock.subtract(BigInteger.valueOf(timestamp)).divide(BigInteger.TEN.pow(6)).divide(BigInteger.TWO).longValue();

        sm.getBlock().increase(delta - 2);
        BigInteger _balance = (BigInteger) bBALNScore.call("balanceOf", owner.getAddress(), BigInteger.ZERO);
        assertTrue(_balance.compareTo(BigInteger.ZERO) > 0);

        sm.getBlock().increase(3);

        _balance = (BigInteger) bBALNScore.call("balanceOf", owner.getAddress(), BigInteger.ZERO);
        assertEquals(_balance, BigInteger.ZERO);
    }

    @Test
    public void testKick() {
        long unlockTime = WEEK.longValue() * 2;
        long timestamp = sm.getBlock().getTimestamp();
        long expectedUnlock = unlockTime + timestamp;

        Map<String, Object> map = new HashMap<>();
        map.put("method", "createLock");
        map.put("params", Map.of("unlockTime", expectedUnlock));
        JSONObject json = new JSONObject(map);
        byte[] lockBytes = json.toString().getBytes();
        doNothing().when(scoreSpy).onBalanceUpdate(any(), any());
        doNothing().when(scoreSpy).onKick(any());
        tokenScore.invoke(owner, "transfer", bBALNScore.getAddress(), ICX.multiply(BigInteger.ONE), lockBytes);

        Map<String, BigInteger> balance = (Map<String, BigInteger>) bBALNScore.call("getLocked", owner.getAddress());

        BigInteger halfTime = BigInteger.valueOf(unlockTime).divide(MICRO_SECONDS_IN_A_SECOND).divide(BigInteger.valueOf(4));

        sm.getBlock().increase(halfTime.longValue());
        bBALNScore.call("kick", owner.getAddress());

        verify(scoreSpy, times(2)).onBalanceUpdate(eq(owner.getAddress()), any(BigInteger.class));

        sm.getBlock().increase(halfTime.longValue());
        bBALNScore.call("kick", owner.getAddress());

        verify(scoreSpy).onKick(owner.getAddress());
    }

    private static Stream<Arguments> weekListLock() {

        long low = WEEK.longValue() * 2;
        long high = WEEK.longValue() * 52;
        return Stream.of(Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1)), Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1)), Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1)));
    }

    private static Stream<Arguments> extendedUnlockWeeks() {

        long week = WEEK.longValue();
        long low = week * 2;
        long high = week * 52;
        return Stream.of(Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1), ThreadLocalRandom.current().nextLong(week, low)), Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1), ThreadLocalRandom.current().nextLong(week, low)), Arguments.of(ThreadLocalRandom.current().nextLong(low, high + 1), ThreadLocalRandom.current().nextLong(week, low)));
    }
}
