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
import network.balanced.score.lib.interfaces.Dex;
import network.balanced.score.lib.interfaces.DexScoreInterface;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.RewardsScoreInterface;
import network.balanced.score.lib.test.UnitTest;
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

    public static final Account governanceScore = Account.newScoreAccount(1);
    private final Account alice = sm.createAccount();
    private final Account bob = sm.createAccount();

    public MockContract<Dex> dex;
    public MockContract<Rewards> rewards;

    public static final String poolOneName = "pool1";
    public static final String poolTwoName = "pool2";

    @BeforeEach
    public void setup() throws Exception {
        stakedLpScore = sm.deploy(owner, StakedLPImpl.class, governanceScore.getAddress());
        dex = new MockContract<>(DexScoreInterface.class, sm, owner);
        rewards = new MockContract<>(RewardsScoreInterface.class, sm, owner);

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
    void setAndGetDex() {
        Executable setDexNotFromGovernance = () -> stakedLpScore.invoke(owner, "setDex", dex.getAddress());
        String expectedErrorMessage = "Reverted(0): StakedLP: Sender not governance contract";
        expectErrorMessage(setDexNotFromGovernance, expectedErrorMessage);

        stakedLpScore.invoke(governanceScore, "setDex", dex.getAddress());
        Address actualDex = (Address) stakedLpScore.call("getDex");
        assertEquals(dex.getAddress(), actualDex);
    }

    @Test
    Address setAndGetAdmin() {
        Account admin = sm.createAccount();
        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "setAdmin", admin.getAddress());
        Address actualAdmin = (Address) stakedLpScore.call("getAdmin");
        assertEquals(admin.getAddress(), actualAdmin);
        return actualAdmin;
    }

    @Test
    void changeGovernanceAddress() {
        assertEquals(governanceScore.getAddress(), stakedLpScore.call("getGovernance"));
        Address admin = setAndGetAdmin();
        Address newGovernance = Account.newScoreAccount(2).getAddress();
        stakedLpScore.invoke(owner, "setGovernance", newGovernance);
        assertEquals(newGovernance, stakedLpScore.call("getGovernance"));
    }

    @Test
    void setAndGetRewards() {
        Address admin = setAndGetAdmin();
        stakedLpScore.invoke(governanceScore, "setRewards", rewards.getAddress());
        assertEquals(rewards.getAddress(), stakedLpScore.call("getRewards"));
    }

    private void stakeLpTokens(Account from, BigInteger poolId, BigInteger value) {
        stakedLpScore.invoke(dex.account, "onIRC31Received", from.getAddress(), from.getAddress(), poolId, value,
                new byte[0]);
    }

    @Test
    void testStake() {
        setAndGetDex();
        setAndGetRewards();

        // Mint LP tokens in Alice and Bob account
        BigInteger initialLpTokenBalance = BigInteger.TEN.pow(10);
        when(dex.mock.balanceOf(eq(alice.getAddress()), Mockito.any(BigInteger.class))).thenReturn(initialLpTokenBalance);
        when(dex.mock.balanceOf(eq(bob.getAddress()), Mockito.any(BigInteger.class))).thenReturn(initialLpTokenBalance);

        // Stake Zero tokens
        Executable zeroStakeValue = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.ZERO);
        String expectedErrorMessage = "Reverted(0): StakedLP: Token value should be a positive number";
        expectErrorMessage(zeroStakeValue, expectedErrorMessage);

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

        // Stake 20 LP tokens from alice and bob account
        stakeLpTokens(alice, BigInteger.TWO, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("totalStaked", BigInteger.TWO));

        stakeLpTokens(bob, BigInteger.TWO, BigInteger.valueOf(20L));
        verify(rewards.mock).updateRewardsData(poolTwoName, BigInteger.valueOf(20L), bob.getAddress(), BigInteger.ZERO);
        assertEquals(BigInteger.valueOf(40L), stakedLpScore.call("totalStaked", BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.TWO));
    }

    @Test
    void testUnstake() {
        setAndGetDex();
        setAndGetRewards();

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
        expectedErrorMessage = "Reverted(0): StakedLP: Cannot unstake, user don't have enough staked balance, Amount " +
                "to unstake: " +
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
        verify(rewards.mock).updateRewardsData(poolOneName, totalStakedBalanceBeforeUnstake, alice.getAddress(),
                aliceStakedBalance);

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
//        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "removePool", BigInteger.TWO);

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
