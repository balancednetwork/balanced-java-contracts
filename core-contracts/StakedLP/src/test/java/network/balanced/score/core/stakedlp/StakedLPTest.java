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

package network.balanced.score.core.stakedlp;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class StakedLPTest extends UnitTest {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    private Score stakedLpScore;

    private final Account alice = sm.createAccount();
    private final Account bob = sm.createAccount();

    public MockBalanced mockBalanced;
    public MockContract<Governance> governance;
    public MockContract<Dex> dex;
    public MockContract<Rewards> rewards;

    public static final String poolOneName = "pool1";
    public static final String poolTwoName = "pool2";

    @BeforeEach
    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        dex = mockBalanced.dex;
        governance = mockBalanced.governance;
        rewards = mockBalanced.rewards;
        stakedLpScore = sm.deploy(owner, StakedLPImpl.class, governance.getAddress());
       
        when(dex.mock.getPoolName(BigInteger.ONE)).thenReturn(poolOneName);
        when(dex.mock.getPoolName(BigInteger.TWO)).thenReturn(poolTwoName);
        when(rewards.mock.getSourceData(poolOneName)).thenReturn(Map.of("contract_address", dex.getAddress()));
        when(rewards.mock.getSourceData(poolTwoName)).thenReturn(Map.of("contract_address", dex.getAddress()));
    }

    @Test
    @DisplayName("Deployment with non contract address")
    void testDeploy() {
        Account notContract = sm.createAccount();
        Executable deploymentWithNonContract = () -> sm.deploy(owner, StakedLPImpl.class, notContract.getAddress());

        String expectedErrorMessage = "Reverted(0): StakedLP: Governance address should be a contract";
        InvocationTargetException e = Assertions.assertThrows(InvocationTargetException.class,
                deploymentWithNonContract);
        assertEquals(expectedErrorMessage, e.getCause().getMessage());
    }

    @Test
    void name() {
        String contractName = "Balanced StakedLP";
        assertEquals(contractName, stakedLpScore.call("name"));
    }

    @Test
    Address setAndGetAdmin() {
        Account admin = sm.createAccount();
        stakedLpScore.invoke(Account.getAccount(governance.getAddress()), "setAdmin", admin.getAddress());
        Address actualAdmin = (Address) stakedLpScore.call("getAdmin");
        assertEquals(admin.getAddress(), actualAdmin);
        return actualAdmin;
    }

    @Test
    void addAndRemovePools() {
        BigInteger poolId = BigInteger.ONE;
        assertEquals(Boolean.FALSE, stakedLpScore.call("isSupportedPool", poolId));

        stakedLpScore.invoke(Account.getAccount(governance.getAddress()), "addPool", poolId);
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", poolId));

        stakedLpScore.invoke(Account.getAccount(governance.getAddress()), "removePool", poolId);
        assertEquals(Boolean.FALSE, stakedLpScore.call("isSupportedPool", poolId));
    }

    private void stakeLpTokens(Account from, BigInteger poolId, BigInteger value) {
        stakedLpScore.invoke(dex.account, "onIRC31Received",  from.getAddress(), from.getAddress(), poolId, value, new byte[0]);
    }

    @Test
    void testStake() {
        // Mint LP tokens in Alice and Bob account
        BigInteger initalLpTokenBalance = BigInteger.TEN.pow(10);
        when(dex.mock.balanceOf(eq(alice.getAddress()), Mockito.any(BigInteger.class))).thenReturn(initalLpTokenBalance);
        when(dex.mock.balanceOf(eq(bob.getAddress()), Mockito.any(BigInteger.class))).thenReturn(initalLpTokenBalance);

        // Stake Zero tokens
        Executable zeroStakeValue = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.ZERO);
        String expectedErrorMessage = "Reverted(0): StakedLP: Token value should be a positive number";
        expectErrorMessage(zeroStakeValue, expectedErrorMessage);

        // Stake for unsupported pool
        Executable unsupportedPoolStake = () -> stakeLpTokens(alice, BigInteger.valueOf(3), BigInteger.TEN);
        expectedErrorMessage = "Reverted(0): StakedLP: Pool with " + BigInteger.valueOf(3) + " is not supported";
        expectErrorMessage(unsupportedPoolStake, expectedErrorMessage);

        //Add  pool 1 and let pool 2 be added on first stake
        stakedLpScore.invoke(Account.getAccount(governance.getAddress()), "addPool", BigInteger.ONE);
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", BigInteger.ONE));

        // Pool 1 related tests
        // Stake 10 LP tokens from alice account
        stakeLpTokens(alice, BigInteger.ONE, BigInteger.TEN);
        assertEquals(BigInteger.TEN, stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.TEN, stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 100 more LP tokens from alice account
        stakeLpTokens(alice, BigInteger.ONE, BigInteger.valueOf(100L));
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 20 LP tokens from bob account
        stakeLpTokens(bob, BigInteger.ONE, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.valueOf(130L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Pool 2 related tests
        assertEquals(BigInteger.ZERO, stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("totalStaked", BigInteger.TWO));
        assertEquals(Boolean.FALSE, stakedLpScore.call("isSupportedPool", BigInteger.TWO));

        // Stake 20 LP tokens from alice and bob account
        stakeLpTokens(alice, BigInteger.TWO, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("totalStaked", BigInteger.TWO));

        stakeLpTokens(bob, BigInteger.TWO, BigInteger.valueOf(20L));
        verify(rewards.mock).updateRewardsData(poolTwoName, BigInteger.valueOf(20L), bob.getAddress(), BigInteger.ZERO);
        assertEquals(BigInteger.valueOf(40L), stakedLpScore.call("totalStaked", BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.TWO));
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", BigInteger.TWO));
    }

    @Test
    void testUnstake() {
        testStake();

        // Unstake from Pool 1
        BigInteger aliceStakedBalance = (BigInteger) stakedLpScore.call("balanceOf", alice.getAddress(),
                BigInteger.ONE);
        BigInteger bobStakedBalance = (BigInteger) stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.ONE);
        BigInteger totalStakedBalanceBeforeUnstake = (BigInteger) stakedLpScore.call("totalStaked", BigInteger.ONE);

        //Unstake value less than zero
        Executable unstakeLessThanZero = () -> stakedLpScore.invoke(alice, "unstake", BigInteger.ONE,
                BigInteger.valueOf(-100L));
        String expectedErrorMessage = "Reverted(0): StakedLP: Cannot unstake less than zero value";
        expectErrorMessage(unstakeLessThanZero, expectedErrorMessage);

        // Unstake more than the staked amount
        BigInteger finalAliceStakedBalance = aliceStakedBalance;
        Executable unstakeMoreThanStakedAmount = () -> stakedLpScore.invoke(alice, "unstake", BigInteger.ONE,
                finalAliceStakedBalance.add(BigInteger.ONE));
        expectedErrorMessage = "Reverted(0): StakedLP: Cannot unstake, user don't have enough staked balance, Amount to unstake: " +
                aliceStakedBalance.add(BigInteger.ONE) + " Staked balance of user: " + alice.getAddress() + " is: " +
                aliceStakedBalance;
        expectErrorMessage(unstakeMoreThanStakedAmount, expectedErrorMessage);

        // Unstake half of alice staked Balance
        BigInteger aliceUnstakeAmount = aliceStakedBalance.divide(BigInteger.TWO);
        stakedLpScore.invoke(alice, "unstake", BigInteger.ONE, aliceUnstakeAmount);
        assertEquals(aliceStakedBalance.subtract(aliceUnstakeAmount), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(aliceUnstakeAmount), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        verify(dex.mock).transfer(alice.getAddress(), aliceUnstakeAmount, BigInteger.ONE, new byte[0]);
        verify(rewards.mock).updateRewardsData(poolOneName, totalStakedBalanceBeforeUnstake, alice.getAddress(), aliceStakedBalance);

        // Adjust the values after first unstake
        aliceStakedBalance = aliceStakedBalance.subtract(aliceUnstakeAmount);
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(aliceUnstakeAmount);

        // Unstake Bob's full staked balance
        stakedLpScore.invoke(bob, "unstake", BigInteger.ONE, bobStakedBalance);
        assertEquals(bobStakedBalance.subtract(bobStakedBalance), stakedLpScore.call("balanceOf", bob.getAddress(),
                BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
                verify(dex.mock).transfer(bob.getAddress(), bobStakedBalance, BigInteger.ONE, new byte[0]);

        // Unstake alice remaining amount
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance);

        stakedLpScore.invoke(alice, "unstake", BigInteger.ONE, aliceStakedBalance);
        assertEquals(aliceStakedBalance.subtract(aliceStakedBalance), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(aliceStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        verify(dex.mock, times(2)).transfer(alice.getAddress(), aliceStakedBalance, BigInteger.ONE, new byte[0]);

        // Unstake from pool 2
        // Unstake from unsupported pool
        stakedLpScore.invoke(Account.getAccount(governance.getAddress()), "removePool", BigInteger.TWO);

        BigInteger aliceStakedBalancePool2 = (BigInteger) stakedLpScore.call("balanceOf", alice.getAddress(),
                BigInteger.TWO);
        // BigInteger aliceLPBalancePool2 = (BigInteger) dex.call("balanceOf", alice.getAddress(), BigInteger.TWO);
        BigInteger totalStakedBalancePool2 = (BigInteger) stakedLpScore.call("totalStaked", BigInteger.TWO);

        // Unstake alice all staked amount of pool2
        stakedLpScore.invoke(alice, "unstake", BigInteger.TWO, aliceStakedBalancePool2);
        assertEquals(aliceStakedBalancePool2.subtract(aliceStakedBalancePool2), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.TWO));
        assertEquals(totalStakedBalancePool2.subtract(aliceStakedBalancePool2), stakedLpScore.call("totalStaked",
                BigInteger.TWO));
        verify(dex.mock).transfer(alice.getAddress(), aliceStakedBalancePool2, BigInteger.TWO, new byte[0]);
    }

}
