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
import foundation.icon.xcall.NetworkAddress;
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
import score.ByteArrayObjectWriter;
import score.Context;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class StakedLPTest extends UnitTest {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    private Score stakedLpScore;

    private final Account alice = sm.createAccount();
    private final Account bob = sm.createAccount();

    protected MockBalanced mockBalanced;
    public MockContract<Governance> governance;

    public MockContract<Dex> dex;
    public MockContract<Rewards> rewards;
    public static StakedLPImpl stakedLpScoreSpy;

    public static final String poolOneName = "pool1";
    public static final String poolTwoName = "pool2";
    private final String NATIVE_NID = "0x1.ICON";

    @BeforeEach
    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);

        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);

        governance = mockBalanced.governance;
        stakedLpScore = sm.deploy(owner, StakedLPImpl.class, governance.getAddress());
        stakedLpScoreSpy = (StakedLPImpl) spy(stakedLpScore.getInstance());
        stakedLpScore.setInstance(stakedLpScoreSpy);
        dex = mockBalanced.dex;
        rewards = mockBalanced.rewards;

        stakedLpScore.invoke(governance.account, "addDataSource", BigInteger.ONE, poolOneName);
        stakedLpScore.invoke(governance.account, "addDataSource", BigInteger.TWO, poolTwoName);
    }

    @Test
    void getSourceName() {
        assertEquals(poolOneName, stakedLpScore.call("getSourceName", BigInteger.ONE));
    }

    @Test
    void getSourceId() {
        assertEquals(BigInteger.ONE, stakedLpScore.call("getSourceId", poolOneName));
    }

    @Test
    void getAllowedPoolIds() {
        List<BigInteger> ids = (List<BigInteger>) stakedLpScore.call("getAllowedPoolIds");
        assertTrue(ids.contains(BigInteger.ONE));
        assertTrue(ids.contains(BigInteger.TWO));
    }

    @Test
    void getDataSources() {
        List<String> ids = (List<String>) stakedLpScore.call("getDataSources");
        assertTrue(ids.contains(poolOneName));
        assertTrue(ids.contains(poolTwoName));
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
        testGovernanceControlMethods(stakedLpScore, governance.account, governance.account, "setDex", dex.getAddress(), "getDex");
    }

    @Test
    void changeGovernanceAddress() {
        assertEquals(governance.getAddress(), stakedLpScore.call("getGovernance"));
        Address newGovernance = Account.newScoreAccount(2).getAddress();
        stakedLpScore.invoke(owner, "setGovernance", newGovernance);
        assertEquals(newGovernance, stakedLpScore.call("getGovernance"));
    }

    @Test
    void setAndGetRewards() {
        stakedLpScore.invoke(governance.account, "setRewards", rewards.getAddress());
        assertEquals(rewards.getAddress(), stakedLpScore.call("getRewards"));
    }

    private void stakeLpTokens(Account from, BigInteger poolId, BigInteger value) {
        String fromNetworkAddress = NetworkAddress.valueOf(from.getAddress().toString(), NATIVE_NID).toString();
        stakedLpScore.invoke(dex.account, "onXIRC31Received", fromNetworkAddress, fromNetworkAddress, poolId, value,
                new byte[0]);
    }

    private void stakeLpTokens(String from, BigInteger poolId, BigInteger value) {
        stakedLpScore.invoke(dex.account, "onXIRC31Received", from, from, poolId, value,
                new byte[0]);
    }

    @Test
    void crossChainStake() {
        setAndGetDex();
        setAndGetRewards();
        String fromNetworkAddress1 = "0x1.ETH/0x123";
        String fromNetworkAddress2 = "0x1.ETH/0x124";

        // Mint LP tokens in Alice and Bob account
        BigInteger initialLpTokenBalance = BigInteger.TEN.pow(10);
        when(dex.mock.xBalanceOf(eq(fromNetworkAddress1), Mockito.any(BigInteger.class))).thenReturn(initialLpTokenBalance);
        when(dex.mock.xBalanceOf(eq(fromNetworkAddress2), Mockito.any(BigInteger.class))).thenReturn(initialLpTokenBalance);

        // Stake Zero tokens
        Executable zeroStakeValue = () -> stakeLpTokens(fromNetworkAddress1, BigInteger.ONE, BigInteger.ZERO);
        String expectedErrorMessage = "Reverted(0): StakedLP: Token value should be a positive number";
        expectErrorMessage(zeroStakeValue, expectedErrorMessage);

        // Pool 1 related tests
        // Stake 10 LP tokens from alice account
        stakeLpTokens(fromNetworkAddress1, BigInteger.ONE, BigInteger.TEN);
        assertEquals(BigInteger.TEN, stakedLpScore.call("xBalanceOf", fromNetworkAddress1, BigInteger.ONE));
        assertEquals(BigInteger.TEN, stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 100 more LP tokens from alice account
        stakeLpTokens(fromNetworkAddress1, BigInteger.ONE, BigInteger.valueOf(100L));
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("xBalanceOf", fromNetworkAddress1, BigInteger.ONE));
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 20 LP tokens from bob account
        stakeLpTokens(fromNetworkAddress2, BigInteger.ONE, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("xBalanceOf", fromNetworkAddress2, BigInteger.ONE));
        assertEquals(BigInteger.valueOf(130L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Pool 2 related tests
        assertEquals(BigInteger.ZERO, stakedLpScore.call("xBalanceOf", fromNetworkAddress1, BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("xBalanceOf", fromNetworkAddress2, BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("totalStaked", BigInteger.TWO));

        // Stake 20 LP tokens from alice and bob account
        stakeLpTokens(fromNetworkAddress1, BigInteger.TWO, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("totalStaked", BigInteger.TWO));

        stakeLpTokens(fromNetworkAddress2, BigInteger.TWO, BigInteger.valueOf(20L));
        verify(rewards.mock).updateBalanceAndSupply(poolTwoName, BigInteger.valueOf(20L), fromNetworkAddress1, BigInteger.valueOf(20L));
        assertEquals(BigInteger.valueOf(40L), stakedLpScore.call("totalStaked", BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("xBalanceOf", fromNetworkAddress1, BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("xBalanceOf", fromNetworkAddress2, BigInteger.TWO));
    }

    @Test
    void xUnstake() {
        setAndGetDex();
        setAndGetRewards();
        String fromNetworkAddress = "0x1.ETH/0x123";
        // Stake 20 LP tokens from alice and bob account
        stakeLpTokens(fromNetworkAddress, BigInteger.TWO, BigInteger.valueOf(20L));

        String[] protocols = new String[0];
        byte[] data = getAddLPData(BigInteger.TWO, BigInteger.valueOf(10L));
        handleCallMessageWithOutProtocols(fromNetworkAddress, data, protocols);
        verify(rewards.mock).updateBalanceAndSupply(poolTwoName, BigInteger.valueOf(10L), fromNetworkAddress, BigInteger.valueOf(10L));
    }

    static byte[] getAddLPData(BigInteger id, BigInteger value) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(3);
        writer.write("xunstake");
        writer.write(id);
        writer.write(value);
        writer.end();
        return writer.toByteArray();
    }


    protected void handleCallMessageWithOutProtocols(String from, byte[] data, String[] protocols) {
        stakedLpScore.invoke(mockBalanced.xCall.account, "handleCallMessage", from, data, protocols);
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
        verify(rewards.mock).updateBalanceAndSupply(poolTwoName, BigInteger.valueOf(40L), bob.getAddress().toString(), BigInteger.valueOf(20L));
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
                aliceStakedBalance.add(BigInteger.ONE) + " Staked balance of user: " + NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString() + " is: " +
                aliceStakedBalance;
        expectErrorMessage(unstakeMoreThanStakedAmount, expectedErrorMessage);

        // Unstake half of alice staked Balance
        BigInteger aliceUnstakeAmount = aliceStakedBalance.divide(BigInteger.TWO);
        stakedLpScore.invoke(alice, "unstake", BigInteger.ONE, aliceUnstakeAmount);
        aliceStakedBalance = aliceStakedBalance.subtract(aliceUnstakeAmount);
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(aliceUnstakeAmount);

        assertEquals(aliceStakedBalance, stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake, stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        verify(dex.mock).hubTransfer(NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString(), aliceUnstakeAmount, BigInteger.ONE, new byte[0]);
        verify(rewards.mock).updateBalanceAndSupply(poolOneName, totalStakedBalanceBeforeUnstake, NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString(),
                aliceStakedBalance);

        // Adjust the values after first unstake

        // Unstake Bob's full staked balance
        stakedLpScore.invoke(bob, "unstake", BigInteger.ONE, bobStakedBalance);
        assertEquals(bobStakedBalance.subtract(bobStakedBalance), stakedLpScore.call("balanceOf", bob.getAddress(),
                BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        verify(dex.mock).hubTransfer(NetworkAddress.valueOf(bob.getAddress().toString(), NATIVE_NID).toString(), bobStakedBalance, BigInteger.ONE, new byte[0]);

        // Unstake alice remaining amount
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance);

        stakedLpScore.invoke(alice, "unstake", BigInteger.ONE, aliceStakedBalance);
        assertEquals(aliceStakedBalance.subtract(aliceStakedBalance), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(aliceStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        verify(dex.mock, times(2)).hubTransfer(NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString(), aliceStakedBalance, BigInteger.ONE, new byte[0]);

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
        verify(dex.mock).hubTransfer(NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString(), aliceStakedBalancePool2, BigInteger.TWO, new byte[0]);
    }


    @Test
    @SuppressWarnings("unchecked")
    void testStakeUnstake_unnamedPool() {
        // Arrange
        setAndGetDex();
        setAndGetRewards();
        BigInteger poolId = BigInteger.valueOf(3);
        String name = "newPoolName";
        BigInteger initialLpTokenBalance = BigInteger.TEN.pow(10);
        when(dex.mock.balanceOf(alice.getAddress(), poolId)).thenReturn(initialLpTokenBalance);

        // Act & Assert
        Executable zeroStakeValue = () -> stakeLpTokens(alice, poolId, BigInteger.TEN);
        String expectedErrorMessage = "Pool id: " + poolId + " is not a valid pool";
        expectErrorMessage(zeroStakeValue, expectedErrorMessage);

        // Act
        stakedLpScore.invoke(governance.account, "addDataSource", poolId, name);
        stakeLpTokens(alice, poolId, BigInteger.TEN);

        // Assert
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) stakedLpScore.call("getBalanceAndSupply", name, alice.getAddress().toString());
        assertEquals(BigInteger.TEN, balanceAndSupply.get("_balance"));
        assertEquals(BigInteger.TEN, balanceAndSupply.get("_totalSupply"));
        verify(rewards.mock).updateBalanceAndSupply(name, balanceAndSupply.get("_totalSupply"), alice.getAddress().toString(), balanceAndSupply.get("_balance"));

        // Act
        stakedLpScore.invoke(alice, "unstake", poolId, BigInteger.TWO);

        // Assert
        balanceAndSupply = (Map<String, BigInteger>) stakedLpScore.call("getBalanceAndSupply", name, alice.getAddress().toString());
        assertEquals(BigInteger.valueOf(8), balanceAndSupply.get("_balance"));
        assertEquals(BigInteger.valueOf(8), balanceAndSupply.get("_totalSupply"));
        verify(rewards.mock).updateBalanceAndSupply(name, balanceAndSupply.get("_totalSupply"), NetworkAddress.valueOf(alice.getAddress().toString(), NATIVE_NID).toString(), balanceAndSupply.get("_balance"));
    }
}
