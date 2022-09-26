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

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static network.balanced.score.lib.utils.Constants.EXA;


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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act
        addBribe(source, amount);

        when(rewards.mock.lastUserVote(user.getAddress(), source)).thenReturn(startPeriod.add(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user.getAddress(), source)).thenReturn(userWeight);

        assertEquals(BigInteger.ZERO, bribing.call("claimable", user.getAddress(), source, bribeToken.getAddress()));

        // Assert
        sm.getBlock().increase(WEEK);
        when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
        BigInteger expectedRewards = userWeight.multiply(amount.divide(totalWeight));
        assertEquals(expectedRewards, bribing.call("claimable", user.getAddress(), source, bribeToken.getAddress()));
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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act
        addBribe(source, amount);

        // Assert
        when(rewards.mock.lastUserVote(user.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user.getAddress(), source)).thenReturn(userWeight);

        sm.getBlock().increase(WEEK);
        when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act
        addBribe(source, amount);

        // Assert
        when(rewards.mock.lastUserVote(user.getAddress(), source)).thenReturn(startPeriod.add(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user.getAddress(), source)).thenReturn(userWeight);

        sm.getBlock().increase(WEEK);
        when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
        BigInteger expectedRewards = userWeight.multiply(amount).divide(totalWeight);
        bribing.invoke(user, "claimBribe", source, bribeToken.getAddress());

        verify(bribeToken.mock).transfer(user.getAddress(), expectedRewards, new byte[0]);

        // Act
        sm.getBlock().increase(WEEK);
        when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act
        addBribe(source, amount);

        when(rewards.mock.lastUserVote(user.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user.getAddress(), source)).thenReturn(userWeight);

        sm.getBlock().increase(WEEK);
        when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act
        scheduledBribes(source, total, amounts);

        // Assert
        when(rewards.mock.lastUserVote(user1.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user1.getAddress(), source)).thenReturn(userWeight);
        when(rewards.mock.lastUserVote(user2.getAddress(), source)).thenReturn(startPeriod.subtract(BigInteger.ONE));
        when(rewards.mock.voteUserSlopes(user2.getAddress(), source)).thenReturn(userWeight);

        for (int i = 0; i < 4; i++) {
            sm.getBlock().increase(WEEK);

            when(rewards.mock.pointsWeight(source, getPeriod())).thenReturn(totalWeight);
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
        when(rewards.mock.pointsWeight(source, startPeriod)).thenReturn(totalWeight);

        // Act & Assert
        Executable scheduleWithWrongTotal = () -> scheduledBribes(source, total, amounts);;
        String expectedErrorMessage = "Scheduled bribes and amount deposited are not equal";

        expectErrorMessage(scheduleWithWrongTotal, expectedErrorMessage);
    }
}