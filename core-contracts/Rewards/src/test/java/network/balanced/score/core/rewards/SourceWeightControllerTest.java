
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

package network.balanced.score.core.rewards;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import score.Address;


import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;


import network.balanced.score.core.rewards.weight.SourceWeightController;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

public class SourceWeightControllerTest extends UnitTest {
    static final Long DAY_BLOCKS = 43200L;
    static final Long WEEK_BLOCKS = 7*43200L;

    static final ServiceManager sm = getServiceManager();
    static final Account owner = sm.createAccount();

    MockContract<BoostedBaln> bBaln;

    Score weightController;

    String stakedLPType = "stakedLPRewards";
    String externalSources = "externalSources";

    int stakedLPId;
    int externalSourcesId;

    BigInteger maxVotePercentage = BigInteger.valueOf(10000);
    BigInteger unlockTime;
    @BeforeEach
    void setup() throws Exception {
        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, owner);
        sm.getBlock().increase(WEEK_BLOCKS *10);

        weightController = sm.deploy(owner, SourceWeightController.class, bBaln.getAddress());
        weightController.invoke(owner, "addType", stakedLPType, BigInteger.ZERO);
        weightController.invoke(owner, "addType", externalSources, BigInteger.ZERO);

        stakedLPId = (int) weightController.call("getTypeId", stakedLPType);
        externalSourcesId = (int) weightController.call("getTypeId", externalSources);

        weightController.invoke(owner, "changeTypeWeight", stakedLPId, EXA);

        weightController.invoke(owner, "addSource", "sICX/ICX", stakedLPId, BigInteger.ZERO);
        weightController.invoke(owner, "addSource", "sICX/bnUSD", stakedLPId, BigInteger.ZERO);

        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger unlockTime = currentTime.add(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(365)));
        when(bBaln.mock.lockedEnd(any(Address.class))).thenReturn(unlockTime);
    }

    @Test
    void voteTest() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);
        BigInteger expectedShare = EXA.divide(BigInteger.TWO);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO));
        sm.getBlock().increase(WEEK_BLOCKS);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        BigInteger ICXLPShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger bnUSDLpShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(expectedShare, ICXLPShare);
        assertEquals(expectedShare, bnUSDLpShare);
    }

    @Test
    void voteTest_disableVoting() {
        // Arrange
        Account user = sm.createAccount();
        Account user2 = sm.createAccount();
        mockUserWeight(user, EXA);
        BigInteger expectedShare = EXA.divide(BigInteger.TWO);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO));
        sm.getBlock().increase(WEEK_BLOCKS);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        weightController.invoke(owner, "setVotable", "sICX/bnUSD", false);
        sm.getBlock().increase(DAY_BLOCKS * 10);

        // Assert
        String expectedErrorMessage = "Reverted(0): sICX/bnUSD is not a votable source, you can only remove weight";
        Executable voteOnDisabled_noPower =  () -> vote(user2, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO));
        expectErrorMessage(voteOnDisabled_noPower, expectedErrorMessage);

        Executable voteOnDisabled_changePower =  () -> vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TEN));
        expectErrorMessage(voteOnDisabled_changePower, expectedErrorMessage);

        vote(user, "sICX/bnUSD", BigInteger.ZERO);

        // Assert
        sm.getBlock().increase(WEEK_BLOCKS);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        BigInteger ICXLPShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger bnUSDLpShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(EXA, ICXLPShare);
        assertEquals(BigInteger.ZERO, bnUSDLpShare);
    }

    @Test
    void voteTest_aboveMaxPower() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.TWO));

        // Assert
        String expectedErrorMessage = "Reverted(0): Used too much power";
        Executable voteWithToMuchPower =  () -> vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO).add(BigInteger.ONE));
        expectErrorMessage(voteWithToMuchPower, expectedErrorMessage);
    }

    @Test
    void voteTest_negativePower() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);

        // Act & Assert
        String expectedErrorMessage = "Reverted(0): Weight has to be between 0 and 10000";
        Executable voteWithToMuchPower =  () -> vote(user, "sICX/ICX", BigInteger.ONE.negate());
        expectErrorMessage(voteWithToMuchPower, expectedErrorMessage);
    }

    @Test
    void voteTest_changeVote() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO));

        // Assert
        String expectedErrorMessage = "Reverted(0): Cannot vote so often";
        Executable voteWithToMuchPower =  () -> vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.valueOf(4)));
        expectErrorMessage(voteWithToMuchPower, expectedErrorMessage);

        // Act
        sm.getBlock().increase(DAY_BLOCKS * 10);
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.valueOf(4)));
        sm.getBlock().increase(WEEK_BLOCKS);

        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        BigInteger ICXLPShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger bnUSDLpShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(EXA.divide(BigInteger.valueOf(3)), ICXLPShare);
        assertEquals(EXA.divide(BigInteger.valueOf(3)).multiply(BigInteger.TWO), bnUSDLpShare);
    }

    @Test
    void getUserData() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.TWO));
        sm.getBlock().increase(WEEK_BLOCKS);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        Map<String, Map<String, BigInteger>> data = (Map<String, Map<String, BigInteger>>) weightController.call("getUserVoteData", user.getAddress());
        assertTrue(data.containsKey("sICX/ICX"));
        assertTrue(data.containsKey("sICX/bnUSD"));
        assertEquals(maxVotePercentage.divide(BigInteger.TWO), data.get("sICX/ICX").get("power"));
        assertEquals(maxVotePercentage.divide(BigInteger.TWO), data.get("sICX/bnUSD").get("power"));

        // Act
        sm.getBlock().increase(DAY_BLOCKS * 10);
        vote(user, "sICX/ICX", BigInteger.ZERO);
        vote(user, "sICX/bnUSD", maxVotePercentage);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        data = (Map<String, Map<String, BigInteger>>) weightController.call("getUserVoteData", user.getAddress());
        assertTrue(!data.containsKey("sICX/ICX"));
        assertTrue(data.containsKey("sICX/bnUSD"));
        assertEquals(maxVotePercentage, data.get("sICX/bnUSD").get("power"));
    }

    @Test
    void voteTest_multipleTypes() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);
        BigInteger expectedShare = EXA.divide(BigInteger.valueOf(3));
        weightController.invoke(owner, "changeTypeWeight", externalSourcesId, EXA.divide(BigInteger.TWO));
        weightController.invoke(owner, "addSource", "OMMbnUSD", externalSourcesId, BigInteger.ZERO);

        // Act
        vote(user, "sICX/ICX", maxVotePercentage.divide(BigInteger.valueOf(4)));
        vote(user, "sICX/bnUSD", maxVotePercentage.divide(BigInteger.valueOf(4)));
        vote(user, "OMMbnUSD", maxVotePercentage.divide(BigInteger.TWO));
        sm.getBlock().increase(WEEK_BLOCKS);
        weightController.invoke(owner, "updateRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        weightController.invoke(owner, "updateRelativeWeight", "OMMbnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        BigInteger ICXLPShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger bnUSDLpShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        BigInteger OMMbnUSDShare = (BigInteger) weightController.call("getRelativeWeight", "OMMbnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(expectedShare, ICXLPShare);
        assertEquals(expectedShare, bnUSDLpShare);
        assertEquals(expectedShare, OMMbnUSDShare);

        // Act
        weightController.invoke(owner, "changeTypeWeight", externalSourcesId, EXA);
        sm.getBlock().increase(WEEK_BLOCKS);

        // Assert
        ICXLPShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        bnUSDLpShare = (BigInteger) weightController.call("getRelativeWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        OMMbnUSDShare = (BigInteger) weightController.call("getRelativeWeight", "OMMbnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        assertEquals(EXA.divide(BigInteger.valueOf(4)), ICXLPShare);
        assertEquals(EXA.divide(BigInteger.valueOf(4)), bnUSDLpShare);
        assertEquals(EXA.divide(BigInteger.TWO), OMMbnUSDShare);
    }

    // Todo Votable

    private void vote(Account user, String name, BigInteger weight) {
        weightController.invoke(user, "voteForSourceWeights", name, weight);
    }

    private void mockUserWeight(Account user, BigInteger weight) {
        when(bBaln.mock.getLastUserSlope(user.getAddress())).thenReturn(weight);
    }
}
