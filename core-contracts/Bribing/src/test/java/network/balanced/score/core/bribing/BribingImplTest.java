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

package network.balanced.score.core.bribing;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.iconloop.score.test.Account;


class BribingImplTest extends BribingImplTestBase {
    @BeforeEach
    public void setup() throws Exception {
        setupBase();
    }

    @Test
    public void addReward() {
        // Arrange
        String source = "testSource";
        Account user = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger totalWeight = EXA;
        BigInteger userWeight = totalWeight.divide(BigInteger.valueOf(4));
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);
        // Act
        addBribe(source, amount);

        when(rewards.mock.getLastUserVote(user.getAddress(), source)).thenReturn(startPeriod.add(BigInteger.ONE));

        doReturn(userWeight).when(bribingSpy).calculateUserBias(user.getAddress(), source);

        assertEquals(BigInteger.ZERO, bribing.call("claimable", user.getAddress(), source, bribeToken.getAddress()));

        // Assert
        sm.getBlock().increase(WEEK);
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());

        BigInteger expectedRewards = userWeight.multiply(amount.divide(totalWeight));
        assertEquals(expectedRewards, bribing.call("claimable", user.getAddress(), source, bribeToken.getAddress()));
    }

    @Test
    public void addReward_nonValidSource() {
        // Arrange
        String source = "testSource";
        BigInteger amount = BigInteger.valueOf(1000).multiply(EXA);

        Map<String, Object> params = Map.of("source", source);
        byte[] data = tokenData("addBribe", params);
        when(rewards.mock.isVotable(source)).thenReturn(false);

        // Act & Assert
        Executable scheduleWithWrongTotal = () -> bribing.invoke(bribeToken.account, "tokenFallback", bribeToken.getAddress(), amount, data);
        String expectedErrorMessage = "testSource is not a valid datasource";

        expectErrorMessage(scheduleWithWrongTotal, expectedErrorMessage);
    }

    @Test
    public void claimBribe() {
        // Arrange
        String source = "testSource";
        Account user = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger totalWeight = EXA;
        BigInteger userWeight = totalWeight.divide(BigInteger.valueOf(3));
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);

        // Act
        addBribe(source, amount);

        // Assert
        when(rewards.mock.getLastUserVote(user.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        doReturn(userWeight).when(bribingSpy).calculateUserBias(user.getAddress(), source);

        sm.getBlock().increase(WEEK);
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());
        BigInteger expectedRewards = userWeight.multiply(amount.divide(totalWeight));
        bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());

        verify(bribeToken.mock).transfer(user.getAddress(), expectedRewards, new byte[0]);
    }

    @Test
    public void claimBribe_carryUnclaimed() {
        // Arrange
        String source = "testSource";
        Account user = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger totalWeight = EXA;
        BigInteger userWeight = totalWeight.divide(BigInteger.valueOf(3));
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);

        // Act
        addBribe(source, amount);

        // Assert
        when(rewards.mock.getLastUserVote(user.getAddress(), source)).thenReturn(startPeriod.add(BigInteger.ONE));
        doReturn(userWeight).when(bribingSpy).calculateUserBias(user.getAddress(), source);

        sm.getBlock().increase(WEEK);
         doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());
        BigInteger expectedRewards = userWeight.multiply(amount).divide(totalWeight);
        bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());

        verify(bribeToken.mock).transfer(user.getAddress(), expectedRewards, new byte[0]);

        // Act
        sm.getBlock().increase(WEEK);
         doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());
        bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());

        // Assert
        amount = amount.subtract(expectedRewards);
        expectedRewards = userWeight.multiply(amount).divide(totalWeight);
        verify(bribeToken.mock).transfer(user.getAddress(), expectedRewards, new byte[0]);
    }

    @Test
    public void claimBribe_twice() {
        // Arrange
        String source = "testSource";
        Account user = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger totalWeight = EXA;
        BigInteger userWeight = totalWeight.divide(BigInteger.valueOf(3));
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);

        // Act
        addBribe(source, amount);

        when(rewards.mock.getLastUserVote(user.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        doReturn(userWeight).when(bribingSpy).calculateUserBias(user.getAddress(), source);

        sm.getBlock().increase(WEEK);
         doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());;
        BigInteger expectedRewards = userWeight.multiply(amount.divide(totalWeight));
        bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());

        verify(bribeToken.mock).transfer(user.getAddress(), expectedRewards, new byte[0]);

        // Assert
        Executable claimTwice = () -> bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());
        String expectedErrorMessage =  user.getAddress().toString() + " has no bribe in " + bribeToken.getAddress().toString() + " to  claim for source: " + source;

        expectErrorMessage(claimTwice, expectedErrorMessage);
    }

    @Test
    public void scheduledBribes() {
        // Arrange
        String source = "testSource";
        Account user1 = sm.createAccount();
        Account user2 = sm.createAccount();
        BigInteger total = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger[] amounts = new BigInteger[] {
            BigInteger.valueOf(500).multiply(EXA),
            BigInteger.valueOf(300).multiply(EXA),
            BigInteger.valueOf(150).multiply(EXA),
            BigInteger.valueOf(50).multiply(EXA)
        };

        BigInteger totalWeight = EXA;
        BigInteger userWeight = totalWeight.divide(BigInteger.TWO);
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);

        // Act
        scheduledBribes(source, total, amounts);

        // Assert
        when(rewards.mock.getLastUserVote(user1.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        doReturn(userWeight).when(bribingSpy).calculateUserBias(user1.getAddress(), source);
        when(rewards.mock.getLastUserVote(user2.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        doReturn(userWeight).when(bribingSpy).calculateUserBias(user2.getAddress(), source);

        for (int i = 0; i < 4; i++) {
            sm.getBlock().increase(WEEK);

            doReturn(totalWeight).when(bribingSpy).getSourceBias(source, getPeriod());;
            BigInteger expectedRewards = userWeight.multiply(amounts[i].divide(totalWeight));
            bribing.invoke(user1, "claimBribe", source, bribeToken.getAddress());
            bribing.invoke(user2, "claimBribe", source, bribeToken.getAddress());

            verify(bribeToken.mock).transfer(user1.getAddress(), expectedRewards, new byte[0]);
            verify(bribeToken.mock).transfer(user2.getAddress(), expectedRewards, new byte[0]);
        }
    }

    @Test
    public void scheduledBribes_wrongTotal() {
        // Arrange
        String source = "testSource";
        BigInteger total = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger[] amounts = new BigInteger[] {
            BigInteger.valueOf(500).multiply(EXA),
            BigInteger.valueOf(300).multiply(EXA),
            BigInteger.valueOf(150).multiply(EXA),
            BigInteger.valueOf(51).multiply(EXA)
        };

        BigInteger totalWeight = EXA;
        sm.getBlock().increase(WEEK);

        BigInteger startPeriod = getPeriod();
        doReturn(totalWeight).when(bribingSpy).getSourceBias(source, startPeriod);

        // Act & Assert
        Executable scheduleWithWrongTotal = () -> scheduledBribes(source, total, amounts);
        String expectedErrorMessage = "Scheduled bribes and amount deposited are not equal";

        expectErrorMessage(scheduleWithWrongTotal, expectedErrorMessage);
    }

    @Test
    public void liveDataTest() {
        // 0x6ee54320d sICX/bnUSD slope
        // 0xe886f78826bf91f98000 sICX/bnUSD bias ~ 1098078 bBaln total votes
        // user1 : {"end":"0x60053036ca000","power":"0x2710","slope":"0x17a029af"} // 1120 bBaln voted for pool
        // user2 : {"end":"0x66b094febe000","power":"0xfa0","slope":"0x2c9bb4a"} // 5629 bBaln voted for pool
        // period 1686182400000000
        // bribe amount 4000 Baln
        // 1120 / 1098078 *4000 ~ 4.1 baln for user 1
        // 5608 / 1098078 ~ 20.4 baln for user 2

        // Arrange
        Account user1 = sm.createAccount();
        Account user2 = sm.createAccount();
        BigInteger period = new BigInteger("1686182400000001");
        BigInteger startPeriod = period.divide(WEEK_IN_MS).multiply(WEEK_IN_MS);
        String source = "sICX/bnUSD";
        BigInteger amount = BigInteger.valueOf(4000).multiply(EXA);
        BigInteger sourceBias = new BigInteger("e886f78826bf91f98000", 16);
        doReturn(sourceBias).when(bribingSpy).getSourceBias(eq(source), any(BigInteger.class));

        Map<String, BigInteger> user1Slope = Map.of(
            "end" , new BigInteger("60053036ca000", 16),
            "power", new BigInteger("2710", 16),
            "slope",new BigInteger("17a029af", 16)
        );

        Map<String, BigInteger> user2Slope = Map.of(
            "end" , new BigInteger("66b094febe000", 16),
            "power", new BigInteger("fa0", 16),
            "slope", new BigInteger("2c9bb4a", 16)
        );

        doReturn(user1Slope).when(bribingSpy).getUserSlope(user1.getAddress(), source);
        doReturn(user2Slope).when(bribingSpy).getUserSlope(user2.getAddress(), source);
        when(rewards.mock.getLastUserVote(user1.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        when(rewards.mock.getLastUserVote(user2.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));

        // Act
        doReturn(period.subtract(WEEK_IN_MS)).when(bribingSpy).getBlockTime();
        addBribe(source, amount);
        doReturn(period).when(bribingSpy).getBlockTime();
        BigInteger roundedAmountUser1 = ((BigInteger)bribing.call("claimable", user1.getAddress(), source, bribeToken.getAddress())).divide(EXA);
        BigInteger roundedAmountUser2 =((BigInteger)bribing.call("claimable", user2.getAddress(), source, bribeToken.getAddress())).divide(EXA);
        System.out.println(bribing.call("claimable", user1.getAddress(), source, bribeToken.getAddress()));
        System.out.println(bribing.call("claimable", user2.getAddress(), source, bribeToken.getAddress()));
        // Assert
        assertEquals(roundedAmountUser1, BigInteger.valueOf(4));
        assertEquals(roundedAmountUser2, BigInteger.valueOf(20));
    }
}