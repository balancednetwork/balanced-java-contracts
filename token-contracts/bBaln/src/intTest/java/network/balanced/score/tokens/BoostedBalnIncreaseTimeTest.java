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

import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.RevertedException;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;
import static network.balanced.score.tokens.TestHelper.getExpectedBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BoostedBalnIncreaseTimeTest {

    private static Balanced balanced;
    private static BalancedClient owner;

    @BeforeEach
    void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        owner.governance.changeScoreOwner(balanced.bBaln._address(), owner.getAddress());
    }

    @Test
    void testIncreaseTime() {
        DefaultScoreClient ownerClient = getOwnerClient();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        balanced.syncDistributions();
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        waitDays(1);
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress.toString());
        System.out.println("baln holding from reward: " + updatedBalnHolding);
        owner.rewards.claimRewards(null);
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: " + availableBalnBalance);
        System.out.println("total balance of baln: " + owner.baln.balanceOf(userAddress));

        assertThrows(RevertedException.class, () -> {
            long unlockTime =
                    -(System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
            String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
            owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO),
                    data.getBytes());
        });

        assertThrows(RevertedException.class, () -> {
            long unlockTime =
                    (System.currentTimeMillis() * 1000) - (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
            String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
            owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO),
                    data.getBytes());
        });

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        System.out.println("unlock time is: " + unlockTime);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        System.out.println("transfer amount: " + availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: " + balance);
        assertEquals(balance.divide(EXA),
                getExpectedBalance(availableBalnBalance.divide(BigInteger.TWO), unlockTime).divide(EXA));

        BigInteger updateUnlockTime =
                BigInteger.valueOf(unlockTime).add(BigInteger.TWO.multiply(WEEK_IN_MICRO_SECONDS));
        System.out.println("unlock time is: " + updateUnlockTime);
        owner.boostedBaln.increaseUnlockTime(updateUnlockTime);
        balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        assertEquals(balance.divide(EXA), getExpectedBalance(availableBalnBalance.divide(BigInteger.TWO),
                updateUnlockTime.longValue()).divide(EXA));
    }

    @Test
    void withdrawTest() {
        owner.boostedBaln.setPenaltyAddress(EOA_ZERO);
        DefaultScoreClient ownerClient = getOwnerClient();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        waitDays(1);
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress.toString());
        System.out.println("baln holding from reward: " + updatedBalnHolding);
        owner.rewards.claimRewards(null);
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: " + availableBalnBalance);
        System.out.println("total balance of baln: " + owner.baln.balanceOf(userAddress));

        long unlockTIme =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(7).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        System.out.println("unlock time is: " + unlockTIme);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        System.out.println("transfer amount: " + availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: " + balance);
        System.out.println("expected balance is: " + availableBalnBalance.divide(BigInteger.TWO));

        owner.boostedBaln.withdrawEarly();

        BigInteger balanceAfterWithdraw = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        assertEquals(balanceAfterWithdraw, BigInteger.ZERO);

        BigInteger newBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("new baln balance is: " + newBalnBalance);
        System.out.println("available baln balance is: " + availableBalnBalance);
        assert newBalnBalance.compareTo(availableBalnBalance) < 0;
    }

    void waitDays(int days) {
        balanced.increaseDay(days);
    }

    DefaultScoreClient getOwnerClient() {
        return new DefaultScoreClient(
                owner.baln.endpoint(),
                owner.baln._nid(),
                balanced.owner,
                DefaultScoreClient.ZERO_ADDRESS
        );
    }
}
