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
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    }

    @Test
    void depositBnUSD_empty() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger BSRSupply = BigInteger.ZERO;
        BigInteger deposit = BigInteger.valueOf(100).multiply(EXA);

        BigInteger expectedBsr = deposit;

        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(deposit);
        when(mockBalanced.bsr.mock.xTotalSupply()).thenReturn(BSRSupply);

        // Act
        byte[] data = tokenData("_deposit", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), deposit, data);

        // Assert
        verify(mockBalanced.bsr.mock).mintTo(user.getAddress().toString(), expectedBsr, new byte[0]);
    }

    @Test
    void depositBnUSD() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger BSRSupply = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger bnUSDBalance = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger deposit = BigInteger.valueOf(100).multiply(EXA);
        BigInteger rate = bnUSDBalance.multiply(EXA).divide(BSRSupply);

        BigInteger expectedBsr = deposit.multiply(EXA).divide(rate);

        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(deposit.add(bnUSDBalance));
        when(mockBalanced.bsr.mock.xTotalSupply()).thenReturn(BSRSupply);

        // Act
        byte[] data = tokenData("_deposit", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), deposit, data);

        // Assert
        verify(mockBalanced.bsr.mock).mintTo(user.getAddress().toString(), expectedBsr, new byte[0]);
    }

    @Test
    void xDepositBnUSD() {
        //Arrange
        String user = "nid/address";
        BigInteger BSRSupply = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger bnUSDBalance = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger deposit = BigInteger.valueOf(100).multiply(EXA);
        BigInteger rate = bnUSDBalance.multiply(EXA).divide(BSRSupply);

        BigInteger expectedBsr = deposit.multiply(EXA).divide(rate);

        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(deposit.add(bnUSDBalance));
        when(mockBalanced.bsr.mock.xTotalSupply()).thenReturn(BSRSupply);

        // Act
        byte[] data = tokenData("_deposit", Map.of());
        savings.invoke(mockBalanced.bnUSD.account, "xTokenFallback", user, deposit, data);

        // Assert
        verify(mockBalanced.bsr.mock).mintTo(user, expectedBsr, new byte[0]);
    }

    @Test
    void withdraw() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger BSRSupply = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger bnUSDBalance = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger withdrawAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger rate = bnUSDBalance.multiply(EXA).divide(BSRSupply);

        BigInteger expectedBnUSD = withdrawAmount.multiply(rate).divide(EXA);

        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(bnUSDBalance);
        when(mockBalanced.bsr.mock.xTotalSupply()).thenReturn(BSRSupply);

        // Act
        byte[] data = tokenData("_withdraw", Map.of());
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user.getAddress(), withdrawAmount, data);

        // Assert
        verify(mockBalanced.bnUSD.mock).hubTransfer(user.getAddress().toString(), expectedBnUSD, new byte[0]);
        verify(mockBalanced.bsr.mock).burn(withdrawAmount);
    }

    @Test
    void xWithdraw() {
        //Arrange
        String user = "nid/address";
        BigInteger BSRSupply = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger bnUSDBalance = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger withdrawAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger rate = bnUSDBalance.multiply(EXA).divide(BSRSupply);

        BigInteger expectedBnUSD = withdrawAmount.multiply(rate).divide(EXA);

        when(mockBalanced.bnUSD.mock.balanceOf(savings.getAddress())).thenReturn(bnUSDBalance);
        when(mockBalanced.bsr.mock.xTotalSupply()).thenReturn(BSRSupply);

        // Act
        byte[] data = tokenData("_withdraw", Map.of());
        savings.invoke(mockBalanced.bsr.account, "xTokenFallback", user, withdrawAmount, data);

        // Assert
        verify(mockBalanced.bnUSD.mock).hubTransfer(user, expectedBnUSD, new byte[0]);
        verify(mockBalanced.bsr.mock).burn(withdrawAmount);
    }

    @Test
    void withdraw_invalidToken() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act
        byte[] data = tokenData("_withdraw", Map.of());
        Executable lock = () -> savings.invoke(mockBalanced.sicx.account, "tokenFallback", user.getAddress(), lockAmount, data);
        expectErrorMessage(lock, "Only BSR can be withdrawn");
    }

    @Test
    void lockBSR() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user.getAddress(), lockAmount, data);

        // Assert
        BigInteger amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        assertEquals(lockAmount, amountLocked);
    }

    @Test
    void xLockBSR() {
        //Arrange
        String user = "nid/address";
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act & Assert
        byte[] data = tokenData("_lock", Map.of());
        Executable xLock = () -> savings.invoke(mockBalanced.bsr.account, "xTokenFallback", user, lockAmount, data);
        expectErrorMessage(xLock, "Only ICON addresses are allowed to lock into the saving account at this time");
    }

    @Test
    void lock_invalidToken() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);

        // Act
        byte[] data = tokenData("_lock", Map.of());
        Executable lock = () -> savings.invoke(mockBalanced.sicx.account, "tokenFallback", user.getAddress(), lockAmount, data);
        expectErrorMessage(lock, "Only BSR can be locked");
    }

    @Test
    void unlockBSR() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawAmount = lockAmount.divide(BigInteger.TWO);
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user.getAddress(), lockAmount, data);

        // Act
        savings.invoke(user, "unlock", withdrawAmount);

        // Assert
        BigInteger amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        assertEquals(lockAmount.subtract(withdrawAmount), amountLocked);

        // Act
        savings.invoke(user, "unlock", amountLocked);

        // Assert
        amountLocked = (BigInteger) savings.call("getLockedAmount", user.getAddress().toString());
        assertEquals(BigInteger.ZERO, amountLocked);
    }

    @Test
    void unlockBSR_moreThanBalance() {
        //Arrange
        Account user = sm.createAccount();
        BigInteger lockAmount = BigInteger.valueOf(100).multiply(EXA);
        byte[] data = tokenData("_lock", Map.of());
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user.getAddress(), lockAmount, data);

        // Act & Assert
        Executable withdrawOverBalance = () -> savings.invoke(user, "unlock", lockAmount.add(BigInteger.ONE));
        expectErrorMessage(withdrawOverBalance, "Cannot unlock more than locked balance");
    }

    @Test
    void unlockBSR_negativeAmount() {
        //Arrange
        Account user = sm.createAccount();

        // Act & Assert
        Executable withdrawOverBalance = () -> savings.invoke(user, "unlock", BigInteger.ONE.negate());
        expectErrorMessage(withdrawOverBalance, "Cannot unlock a negative or zero amount");
    }

    @Test
    void bnUSDRewards() {
        Executable depositBnUSDRewards = () -> savings.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.loans.getAddress(), BigInteger.valueOf(10), new byte[0]);
        assertDoesNotThrow(depositBnUSDRewards);
    }

    @Test
    void tokenRewards_BSR() {
        Executable depositBnUSDRewards = () -> savings.invoke(mockBalanced.bsr.account, "tokenFallback", mockBalanced.daofund.getAddress(), BigInteger.valueOf(10), new byte[0]);
        expectErrorMessage(depositBnUSDRewards, "BSR can't be a rewards tokens");
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
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user1.getAddress(), lockAmount1, data);
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user2.getAddress(), lockAmount2, data);

        // Act
        BigInteger balnRewards = BigInteger.valueOf(100).multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(100).multiply(EXA);
        savings.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.daofund.getAddress(), balnRewards, new byte[0]);
        savings.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), sICXRewards, new byte[0]);
        savings.invoke(mockBalanced.bsr.account, "tokenFallback", user3.getAddress(), lockAmount3, data);

        // Assert
        Map<String, BigInteger> rewards1 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user1.getAddress().toString());
        Map<String, BigInteger> rewards2 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user2.getAddress().toString());
        Map<String, BigInteger> rewards3 = (Map<String, BigInteger>) savings.call("getUnclaimedRewards", user3.getAddress().toString());

        BigInteger total = lockAmount1.add(lockAmount2);
        BigInteger sICXWeight = sICXRewards.multiply(EXA).divide(total);
        BigInteger balnWeight = balnRewards.multiply(EXA).divide(total);
        assertEquals(sICXWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(balnWeight.multiply(lockAmount1).divide(EXA), rewards1.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(sICXWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(balnWeight.multiply(lockAmount2).divide(EXA), rewards2.get(mockBalanced.baln.getAddress().toString()));
        assertEquals(BigInteger.ZERO, rewards3.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(BigInteger.ZERO, rewards3.get(mockBalanced.baln.getAddress().toString()));


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