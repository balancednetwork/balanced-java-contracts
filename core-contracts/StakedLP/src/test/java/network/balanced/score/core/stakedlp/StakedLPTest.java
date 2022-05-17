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
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.stakedlp.utils.Dex;
import network.balanced.score.core.stakedlp.utils.Rewards;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class StakedLPTest extends TestBase {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    private Score stakedLpScore;
    private Score dexScore;
    public static final Account governanceScore = Account.newScoreAccount(1);
    private Score rewardsScore;
    private final Account alice = sm.createAccount();
    private final Account bob = sm.createAccount();

    private Rewards rewardsSpy;

    private void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(expectedErrorMessage, e.getMessage());
    }

    private byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }

    @BeforeEach
    public void setup() throws Exception {
        stakedLpScore = sm.deploy(owner, StakedLP.class, governanceScore.getAddress());
        assert (stakedLpScore.getAddress().isContract());

        dexScore = sm.deploy(owner, Dex.class);
        assert (dexScore.getAddress().isContract());

        rewardsScore = sm.deploy(owner, Rewards.class);
        assert (rewardsScore.getAddress().isContract());

        // Setup spy
        rewardsSpy = (Rewards) spy(rewardsScore.getInstance());
        rewardsScore.setInstance(rewardsSpy);
    }

    @Test
    @DisplayName("Deployment with non contract address")
    void testDeploy() {
        Account notContract = sm.createAccount();
        Executable deploymentWithNonContract = () -> sm.deploy(owner, StakedLP.class, notContract.getAddress());

        String expectedErrorMessage = "StakedLP: Governance address should be a contract";
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
        Executable setDexNotFromGovernance = () -> stakedLpScore.invoke(owner, "setDex", dexScore.getAddress());
        String expectedErrorMessage = "StakedLP: Sender not governance contract";
        expectErrorMessage(setDexNotFromGovernance, expectedErrorMessage);

        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "setDex", dexScore.getAddress());
        Address actualDex = (Address) stakedLpScore.call("getDex");
        assertEquals(dexScore.getAddress(), actualDex);
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
        stakedLpScore.invoke(Account.getAccount(admin), "setGovernance", newGovernance);
        assertEquals(newGovernance, stakedLpScore.call("getGovernance"));
    }

    @Test
    void setAndGetRewards() {
        Address admin = setAndGetAdmin();
        stakedLpScore.invoke(Account.getAccount(admin), "setRewards", rewardsScore.getAddress());
        assertEquals(rewardsScore.getAddress(), stakedLpScore.call("getRewards"));
    }

    @Test
    void addAndRemovePools() {
        BigInteger poolId = BigInteger.ONE;
        assertEquals(Boolean.FALSE, stakedLpScore.call("isSupportedPool", poolId));

        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "addPool", poolId);
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", poolId));

        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "removePool", poolId);
        assertEquals(Boolean.FALSE, stakedLpScore.call("isSupportedPool", poolId));
    }

    private void stakeLpTokens(Account from, BigInteger poolId, BigInteger value, byte[] data) {
        dexScore.invoke(from, "transferFrom", from.getAddress(), stakedLpScore.getAddress(), poolId, value, data);
    }

    @Test
    void testStake() {
        setAndGetDex();
        setAndGetRewards();

        // Mint LP tokens in Alice and Bob account
        dexScore.invoke(alice, "mint", BigInteger.ONE, BigInteger.TEN.pow(20), "https://example.com");
        dexScore.invoke(alice, "mint", BigInteger.TWO, BigInteger.TEN.pow(20), "https://example.com");
        dexScore.invoke(alice, "transferFrom", alice.getAddress(), bob.getAddress(), BigInteger.ONE,
                BigInteger.TEN.pow(10), new byte[0]);
        dexScore.invoke(alice, "transferFrom", alice.getAddress(), bob.getAddress(), BigInteger.TWO,
                BigInteger.TEN.pow(10), new byte[0]);

        byte[] stakeLpData = tokenData("stake", Map.of());

        // Stake Zero tokens
        Executable zeroStakeValue = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.ZERO, stakeLpData);
        String expectedErrorMessage = "StakedLP: Token value should be a positive number";
        expectErrorMessage(zeroStakeValue, expectedErrorMessage);

        // Stake without any data
        Executable emptyDataStake = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.TEN, new byte[0]);
        expectedErrorMessage = "StakedLP: Data can't be empty";
        expectErrorMessage(emptyDataStake, expectedErrorMessage);

        // Stake with wrong method name in data
        byte[] unsupportedMethod = tokenData("hello", Map.of());
        Executable unsupportedMethodName = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.TEN,
                unsupportedMethod);
        expectedErrorMessage = "Reverted(0): StakedLP: No valid method called";
        expectErrorMessage(unsupportedMethodName, expectedErrorMessage);

        // Stake for unsupported pool
        Executable unsupportedPoolStake = () -> stakeLpTokens(alice, BigInteger.ONE, BigInteger.TEN, stakeLpData);
        expectedErrorMessage = "StakedLP: Pool with " + BigInteger.ONE + " is not supported";
        expectErrorMessage(unsupportedPoolStake, expectedErrorMessage);

        //Add 1 and 2 as supported Pools
        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "addPool", BigInteger.ONE);
        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "addPool", BigInteger.TWO);
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", BigInteger.ONE));
        assertEquals(Boolean.TRUE, stakedLpScore.call("isSupportedPool", BigInteger.TWO));

        // Pool 1 related tests
        // Stake 10 LP tokens from alice account
        stakeLpTokens(alice, BigInteger.ONE, BigInteger.TEN, stakeLpData);
        assertEquals(BigInteger.TEN, stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.TEN, stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 100 more LP tokens from alice account
        stakeLpTokens(alice, BigInteger.ONE, BigInteger.valueOf(100L), stakeLpData);
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.valueOf(110L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Stake 20 LP tokens from bob account
        stakeLpTokens(bob, BigInteger.ONE, BigInteger.valueOf(20L), stakeLpData);
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.ONE));
        assertEquals(BigInteger.valueOf(130L), stakedLpScore.call("totalStaked", BigInteger.ONE));

        // Pool 2 related tests
        assertEquals(BigInteger.ZERO, stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.ZERO, stakedLpScore.call("totalStaked", BigInteger.TWO));

        // Stake 20 LP tokens from alice and bob account
        stakeLpTokens(alice, BigInteger.TWO, BigInteger.valueOf(20L), stakeLpData);
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("totalStaked", BigInteger.TWO));

        stakeLpTokens(bob, BigInteger.TWO, BigInteger.valueOf(20L), stakeLpData);
        verify(rewardsSpy).updateRewardsData((String) dexScore.call("getPoolName", BigInteger.TWO),
                BigInteger.valueOf(20L), bob.getAddress(), BigInteger.ZERO);
        assertEquals(BigInteger.valueOf(40L), stakedLpScore.call("totalStaked", BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", alice.getAddress(), BigInteger.TWO));
        assertEquals(BigInteger.valueOf(20L), stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.TWO));
    }

    @Test
    void testUnstake() {
        testStake();

        // Unstake from Pool 1
        BigInteger aliceStakedBalance = (BigInteger) stakedLpScore.call("balanceOf", alice.getAddress(),
                BigInteger.ONE);
        BigInteger bobStakedBalance = (BigInteger) stakedLpScore.call("balanceOf", bob.getAddress(), BigInteger.ONE);
        BigInteger aliceLPBalance = (BigInteger) dexScore.call("balanceOf", alice.getAddress(), BigInteger.ONE);
        BigInteger bobLPBalance = (BigInteger) dexScore.call("balanceOf", bob.getAddress(), BigInteger.ONE);
        BigInteger totalStakedBalanceBeforeUnstake = (BigInteger) stakedLpScore.call("totalStaked", BigInteger.ONE);

        //Unstake value less than zero
        Executable unstakeLessThanZero = () -> stakedLpScore.invoke(alice, "unstake", BigInteger.ONE,
                BigInteger.valueOf(-100L));
        String expectedErrorMessage = "StakedLP: Cannot unstake less than zero value";
        expectErrorMessage(unstakeLessThanZero, expectedErrorMessage);

        // Unstake more than the staked amount
        BigInteger finalAliceStakedBalance = aliceStakedBalance;
        Executable unstakeMoreThanStakedAmount = () -> stakedLpScore.invoke(alice, "unstake", BigInteger.ONE,
                finalAliceStakedBalance.add(BigInteger.ONE));
        expectedErrorMessage = "StakedLP: Cannot unstake, user don't have enough staked balance, Amount to unstake: " +
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
        assertEquals(aliceLPBalance.add(aliceUnstakeAmount), dexScore.call("balanceOf", alice.getAddress(),
                BigInteger.ONE));

        verify(rewardsSpy).updateRewardsData((String) dexScore.call("getPoolName", BigInteger.ONE),
                totalStakedBalanceBeforeUnstake, alice.getAddress(), aliceStakedBalance);

        // Adjust the values after first unstake
        aliceLPBalance = aliceLPBalance.add(aliceUnstakeAmount);
        aliceStakedBalance = aliceStakedBalance.subtract(aliceUnstakeAmount);
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(aliceUnstakeAmount);

        // Unstake Bob's full staked balance
        stakedLpScore.invoke(bob, "unstake", BigInteger.ONE, bobStakedBalance);
        assertEquals(bobStakedBalance.subtract(bobStakedBalance), stakedLpScore.call("balanceOf", bob.getAddress(),
                BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        assertEquals(bobLPBalance.add(bobStakedBalance), dexScore.call("balanceOf", bob.getAddress(), BigInteger.ONE));

        // Unstake alice remaining amount
        totalStakedBalanceBeforeUnstake = totalStakedBalanceBeforeUnstake.subtract(bobStakedBalance);

        stakedLpScore.invoke(alice, "unstake", BigInteger.ONE, aliceStakedBalance);
        assertEquals(aliceStakedBalance.subtract(aliceStakedBalance), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.ONE));
        assertEquals(totalStakedBalanceBeforeUnstake.subtract(aliceStakedBalance), stakedLpScore.call("totalStaked",
                BigInteger.ONE));
        assertEquals(aliceLPBalance.add(aliceStakedBalance), dexScore.call("balanceOf", alice.getAddress(),
                BigInteger.ONE));

        // Unstake from pool 2
        // Unstake from unsupported pool
        stakedLpScore.invoke(Account.getAccount(governanceScore.getAddress()), "removePool", BigInteger.TWO);

        BigInteger aliceStakedBalancePool2 = (BigInteger) stakedLpScore.call("balanceOf", alice.getAddress(),
                BigInteger.TWO);
        BigInteger aliceLPBalancePool2 = (BigInteger) dexScore.call("balanceOf", alice.getAddress(), BigInteger.TWO);
        BigInteger totalStakedBalancePool2 = (BigInteger) stakedLpScore.call("totalStaked", BigInteger.TWO);

        // Unstake alice all staked amount of pool2
        stakedLpScore.invoke(alice, "unstake", BigInteger.TWO, aliceStakedBalancePool2);
        assertEquals(aliceStakedBalancePool2.subtract(aliceStakedBalancePool2), stakedLpScore.call("balanceOf",
                alice.getAddress(), BigInteger.TWO));
        assertEquals(totalStakedBalancePool2.subtract(aliceStakedBalancePool2), stakedLpScore.call("totalStaked",
                BigInteger.TWO));
        assertEquals(aliceLPBalancePool2.add(aliceStakedBalancePool2), dexScore.call("balanceOf", alice.getAddress(),
                BigInteger.TWO));
    }

}
