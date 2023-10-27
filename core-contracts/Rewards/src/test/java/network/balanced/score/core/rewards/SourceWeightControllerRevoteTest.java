
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

package network.balanced.score.core.rewards;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.core.rewards.weight.SourceWeightController;
import network.balanced.score.core.rewards.weight.SourceWeightControllerOld;
import network.balanced.score.lib.interfaces.BoostedBaln;
import network.balanced.score.lib.interfaces.BoostedBalnScoreInterface;
import network.balanced.score.lib.structs.Point;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;

import score.Address;
import score.RevertedException;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.weight.SourceWeightController.VOTE_POINTS;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class SourceWeightControllerRevoteTest extends UnitTest {
    static final Long DAY_BLOCKS = 43200L;
    static final Long WEEK_BLOCKS = 7 * 43200L;

    static final ServiceManager sm = getServiceManager();
    static final Account owner = sm.createAccount();

    static MockContract<BoostedBaln> bBaln;

    static Score weightController;
    static Score weightControllerRef;

    static String stakedLPType = "stakedLPRewards";

    static int stakedLPId;
    static int externalSourcesId;

    static BigInteger unlockTime;

    static Account user1;
    static Account user2;
    static Account user3;

    @BeforeAll
    static void setup() throws Exception {
        user1 = sm.createAccount();
        user2 = sm.createAccount();
        user3 = sm.createAccount();

        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, owner);
        sm.getBlock().increase(WEEK_BLOCKS * 10);

        weightController = sm.deploy(owner, SourceWeightControllerOld.class, bBaln.getAddress());
        weightControllerRef = sm.deploy(owner, SourceWeightController.class, bBaln.getAddress());
        SourceWeightControllerOld.rewards =
                (RewardsImpl) sm.deploy(owner, RewardsImpl.class, bBaln.getAddress()).getInstance();
        SourceWeightController.rewards =
                (RewardsImpl) sm.deploy(owner, RewardsImpl.class, bBaln.getAddress()).getInstance();

        weightController.invoke(owner, "addType", stakedLPType, BigInteger.ZERO);
        weightControllerRef.invoke(owner, "addType", stakedLPType, BigInteger.ZERO);

        stakedLPId = (int) weightController.call("getTypeId", stakedLPType);

        weightController.invoke(owner, "changeTypeWeight", stakedLPId, EXA);
        weightControllerRef.invoke(owner, "changeTypeWeight", stakedLPId, EXA);

        weightController.invoke(owner, "addSource", "sICX/ICX", stakedLPId, BigInteger.ZERO);
        weightController.invoke(owner, "addSource", "sICX/bnUSD", stakedLPId, BigInteger.ZERO);
        weightControllerRef.invoke(owner, "addSource", "sICX/ICX", stakedLPId, BigInteger.ZERO);
        weightControllerRef.invoke(owner, "addSource", "sICX/bnUSD", stakedLPId, BigInteger.ZERO);
    }

    @Test
    void testFix() throws Exception {
        // Arrange
        mockUserLock(user1, EXA, 365);
        mockUserLock(user2, EXA, 21);
        mockUserLock(user3, EXA, 35);

        // Act
        vote(user1, "sICX/ICX", BigInteger.valueOf(3000));
        vote(user1, "sICX/bnUSD", BigInteger.valueOf(7000));
        vote(user2, "sICX/ICX", BigInteger.valueOf(4000));
        vote(user2, "sICX/bnUSD", BigInteger.valueOf(5000));
        vote(user3, "sICX/ICX", BigInteger.valueOf(2000));
        vote(user3, "sICX/bnUSD", BigInteger.valueOf(5000));

        compareToRef();

        // Until a vote expires everything should be as expected
        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        compareToRef();

        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        compareToRef();

        // Vote has expired and they should start to differ
        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        assertThrows(AssertionFailedError.class, ()-> compareToRef());

        Score upgrade = sm.deploy(owner, SourceWeightController.class, bBaln.getAddress());
        weightController.setInstance(upgrade.getInstance());

        BigInteger relativeWeightPre1 = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX",
            BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger relativeWeightPre2 = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));

        weightController.invoke(owner, "reset", (Object)new String[]{"sICX/ICX", "sICX/bnUSD"});
        weightController.invoke(owner, "revote", user1.getAddress(), (Object)new String[]{"sICX/ICX", "sICX/bnUSD"});
        weightController.invoke(owner, "revote", user2.getAddress(), (Object)new String[]{"sICX/ICX", "sICX/bnUSD"});
        weightController.invoke(owner, "revote", user3.getAddress(), (Object)new String[]{"sICX/ICX", "sICX/bnUSD"});
        // cannot re vote twice
        assertThrows(AssertionError.class, () -> weightController.invoke(owner, "revote", user3.getAddress(), (Object)new String[]{"sICX/ICX", "sICX/bnUSD"}));

        // Asset that the reset does not affect the current weeks vote
        BigInteger relativeWeightPost1 = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX",
            BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger relativeWeightPost2 = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD",
            BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(relativeWeightPre1, relativeWeightPost1);
        assertEquals(relativeWeightPre2, relativeWeightPost2);

        // This weeks values should still be wrong
        assertThrows(AssertionFailedError.class, () -> compareToRef());

        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        compareToRef();

        // Another vote expires and should not give any issues
        sm.getBlock().increase(WEEK_BLOCKS);
        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        compareToRef();

        // change a vote
        vote(user1, "sICX/bnUSD", BigInteger.valueOf(1000));
        vote(user1, "sICX/ICX", BigInteger.valueOf(4000));

        // relock and revote
        mockUserLock(user2, EXA, 21);
        vote(user2, "sICX/bnUSD", BigInteger.valueOf(3000));
        vote(user2, "sICX/ICX", BigInteger.valueOf(2000));

        updateWeights();
        compareToRef();

        sm.getBlock().increase(WEEK_BLOCKS);
        updateWeights();
        compareToRef();
    }


    private void compareToRef() {
        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger WEEK = MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(7));
        currentTime = currentTime.divide(WEEK).multiply(WEEK);

        Point point1 = (Point)weightController.call("getSourcePointsWeightAt", "sICX/ICX", currentTime);
        Point point1Ref = (Point)weightControllerRef.call("getSourcePointsWeightAt", "sICX/ICX", currentTime);
        Point point2 = (Point)weightController.call("getSourcePointsWeightAt", "sICX/bnUSD", currentTime);
        Point point2Ref = (Point)weightControllerRef.call("getSourcePointsWeightAt", "sICX/bnUSD", currentTime);
        Point sum = (Point)weightControllerRef.call("getPointsSumPerType", stakedLPId);
        Point sumRef = (Point)weightControllerRef.call("getPointsSumPerType", stakedLPId);


        BigInteger total = (BigInteger)weightControllerRef.call("getTotalWeight");
        BigInteger totalRef = (BigInteger)weightControllerRef.call("getTotalWeight");

        assertEquals(point1.slope, point1Ref.slope);
        assertEquals(point1.bias, point1Ref.bias);
        assertEquals(point2.slope, point2Ref.slope);
        assertEquals(point2.bias, point2Ref.bias);

        assertEquals(sum.bias, sumRef.bias);
        assertEquals(sum.slope, sumRef.slope);
        assertEquals(total, totalRef);
    }

    private void vote(Account user, String name, BigInteger weight) {
        weightController.invoke(user, "voteForSourceWeights", name, weight);
        weightControllerRef.invoke(user, "voteForSourceWeights", name, weight);
    }

    private void updateWeights() {
        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());

        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", currentTime);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", currentTime);

        weightControllerRef.invoke(owner, "updateRelativeWeight", "sICX/ICX", currentTime);
        weightControllerRef.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", currentTime);
    }

    private void mockUserLock(Account user, BigInteger slope, int days) {
        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger WEEK = MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(7));
        currentTime = currentTime.divide(WEEK).multiply(WEEK);
        when(bBaln.mock.lockedEnd(user.getAddress())).thenReturn(currentTime.add(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(days))));
        when(bBaln.mock.getLastUserSlope(user.getAddress())).thenReturn(slope);
    }
}
