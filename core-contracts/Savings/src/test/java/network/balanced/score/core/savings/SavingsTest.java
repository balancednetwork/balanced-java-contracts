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

package network.balanced.score.core.savings;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SavingsTest extends UnitTest {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score savings;


    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        savings = sm.deploy(owner, SavingsImpl.class, mockBalanced.governance.getAddress());
        savings.invoke(mockBalanced.governance.account, "addAcceptedToken", mockBalanced.sicx.getAddress());
        savings.invoke(mockBalanced.governance.account, "addAcceptedToken", mockBalanced.baln.getAddress());
        savings.invoke(mockBalanced.governance.account, "addAcceptedToken", mockBalanced.bnUSD.getAddress());
    }

    @Test
    void lockBnUSD() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), lockAmount, data);

        // Assert
        BigInteger amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        assertEquals(lockAmount, amountLocked);
    }

    @Test
    void lock_invalidToken() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act
        byte[] data = tokenData("_lock", Map.of());
        Executable lock = () -> savings.invoke(mockBalanced.sicx.account, "tokenFallback", user.getAddress(), lockAmount, data);
        expectErrorMessage(lock, "Only BnUSD can be locked");
    }

    @Test
    void unlockBnUSD() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawAmount = lockAmount.divide(BigInteger.TWO);
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), lockAmount, data);
        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(lockAmount);

        // Act
        savings.invoke(user, "unlock", withdrawAmount);

        // Assert
        BigInteger amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        assertEquals(lockAmount.subtract(withdrawAmount), amountLocked);

        // Act
        savings.invoke(user, "unlock", amountLocked);

        // Assert
        amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        verify(mockBalanced.bnUSD.mock, times(2)).transfer(user.getAddress(), withdrawAmount, new byte[0]);
        assertEquals(BigInteger.ZERO, amountLocked);
    }

    @Test
    void unlock_moreThanBalance() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), lockAmount, data);

        // Act & Assert
        Executable withdrawOverBalance = () -> savings.invoke(user, "unlock", lockAmount.add(BigInteger.ONE));
        expectErrorMessage(withdrawOverBalance, "Cannot unlock more than locked balance");
    }

    @Test
    void unlock_negativeAmount() {
        //Arrange
        Account user = sm.createAccount();

        // Act & Assert
        Executable withdrawOverBalance = () -> savings.invoke(user, "unlock", BigInteger.ONE.negate());
        expectErrorMessage(withdrawOverBalance, "Cannot unlock a negative or zero amount");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenRewards() {
        //Arrange
        Account user1 = sm.createAccount();
        Account user2 = sm.createAccount();
        Account user3 = sm.createAccount();
        BigInteger lockAmount1 = BigInteger.valueOf(100).multiply(EXA);
        BigInteger lockAmount2 = BigInteger.valueOf(200).multiply(EXA);
        BigInteger lockAmount3 = BigInteger.valueOf(200).multiply(EXA);

        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user1.getAddress(), lockAmount1, data);
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user2.getAddress(), lockAmount2, data);

        // Act
        BigInteger balnRewards = BigInteger.valueOf(100).multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(100).multiply(EXA);
        BigInteger bnUSDRewards = BigInteger.valueOf(2000).multiply(EXA);
        savings.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.daofund.getAddress(), balnRewards, new byte[0]);
        savings.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), sICXRewards, new byte[0]);
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), bnUSDRewards, new byte[0]);
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user3.getAddress(), lockAmount3, data);

        // Assert
        Map<String, BigInteger> rewards1 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user1.getAddress().toString());
        Map<String, BigInteger> rewards2 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user2.getAddress().toString());
        Map<String, BigInteger> rewards3 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user3.getAddress().toString());

        BigInteger total = lockAmount1.add(lockAmount2);
        BigInteger sICXWeight = sICXRewards.multiply(EXA).divide(total);
        BigInteger balnWeight = balnRewards.multiply(EXA).divide(total);
        BigInteger bnUSDWeight = bnUSDRewards.multiply(EXA).divide(total);
        assertEquals(sICXWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(balnWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(bnUSDWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.bnUSD.getAddress().toString()));
        assertEquals(sICXWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(balnWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(bnUSDWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.bnUSD.getAddress().toString()));
        assertEquals(BigInteger.ZERO, rewards3.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(BigInteger.ZERO, rewards3.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(BigInteger.ZERO, rewards3.get(mockBalanced.bnUSD.getAddress().toString()));


        // Act
        savings.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.daofund.getAddress(), balnRewards, new byte[0]);
        savings.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), sICXRewards, new byte[0]);

        // Assert
        rewards1 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user1.getAddress().toString());
        rewards2 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user2.getAddress().toString());
        rewards3 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user3.getAddress().toString());

        BigInteger newTotal = lockAmount1.add(lockAmount2).add(lockAmount3);
        BigInteger newSICXWeight = sICXWeight.add(sICXRewards.multiply(EXA).divide(newTotal));
        BigInteger newBalnWeight = balnWeight.add(balnRewards.multiply(EXA).divide(newTotal));
        assertEquals(newSICXWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(newBalnWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(newSICXWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(newBalnWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(newSICXWeight.subtract(sICXWeight).multiply(lockAmount3).divide(EXA), rewards3.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(newBalnWeight.subtract(balnWeight).multiply(lockAmount3).divide(EXA), rewards3.get(mockBalanced.baln.getAddress().toString()));

        // Act
        savings.invoke(user1, "claimRewards");

        // Assert
        verify(mockBalanced.sicx.mock).transfer(user1.getAddress(), rewards1.get(mockBalanced.sicx.getAddress().toString()), new byte[0]);
        verify(mockBalanced.baln.mock).transfer(user1.getAddress(), rewards1.get(mockBalanced.baln.getAddress().toString()), new byte[0]);
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), savings, "addAcceptedToken", mockBalanced.sicx.getAddress());
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), savings, "removeAcceptedToken", mockBalanced.sicx.getAddress());
    }
}