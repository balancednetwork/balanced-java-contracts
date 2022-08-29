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

package network.balanced.score.core.staking;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.PrepDelegations;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.*;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.*;

class StakingTest extends TestBase {

    private static int scoreAccountCount = 1;
    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    public static final Account alice = sm.createAccount();
    public static final Account governanceScore = Account.newScoreAccount(scoreAccountCount++);
    public static final Account sicx = Account.newScoreAccount(scoreAccountCount++);
    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    private Score staking;
    private StakingImpl stakingSpy;

    Map<String, Object> prepsResponse = new HashMap<>();
    Map<String, Object> iissInfo = new HashMap<>();
    Map<String, Object> stake = new HashMap<>();
    Map<String, Object> iScore = new HashMap<>();
    BigInteger nextPrepTerm;
    BigInteger unlockPeriod;

    Verification getIISSInfo = () -> Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo");
    Verification getPreps = () -> Context.call(SYSTEM_SCORE_ADDRESS, "getPReps", BigInteger.ONE,
            BigInteger.valueOf(100L));
    Verification queryIscore = () -> Context.call(eq(SYSTEM_SCORE_ADDRESS), eq("queryIScore"), any(Address.class));
    Verification getStake = () -> Context.call(eq(SYSTEM_SCORE_ADDRESS), eq("getStake"), any(Address.class));
    Verification getUnstakeLockPeriod = () -> Context.call(SYSTEM_SCORE_ADDRESS, "estimateUnstakeLockPeriod");
    Verification claimIScore = () -> Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore");

    BigInteger sicxBalance;
    BigInteger sicxTotalSupply;
    Verification sicxBalanceOf = () -> Context.call(eq(sicx.getAddress()), eq("balanceOf"), any(Address.class));
    Verification getSicxTotalSupply = () -> Context.call(sicx.getAddress(), "totalSupply");

    @BeforeEach
    void setUp() throws Exception {

        setupSystemScore();
        setupSicxScore();

        setupStakingScore();
    }

    private void setupStakingScore() throws Exception {
        staking = sm.deploy(owner, StakingImpl.class);
        stakingSpy = (StakingImpl) spy(staking.getInstance());
        staking.setInstance(stakingSpy);

        // Configure Staking contract
        staking.invoke(owner, "setSicxAddress", sicx.getAddress());
        staking.invoke(owner, "toggleStakingOn");
    }

    void setupSystemScore() {
        // Write methods will have no effect
        contextMock.when(() -> Context.call(eq(SYSTEM_SCORE_ADDRESS), eq("setStake"),
                any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(SYSTEM_SCORE_ADDRESS), eq("setDelegation"),
                any(List.class))).thenReturn(null);
        contextMock.when(claimIScore).thenReturn(null);

        stake.put("unstakes", List.of());
        contextMock.when(getStake).thenReturn(stake);

        iScore.put("estimatedICX", BigInteger.ZERO);
        contextMock.when(queryIscore).thenReturn(iScore);

        setupGetPrepsResponse();
        contextMock.when(getPreps).thenReturn(prepsResponse);

        nextPrepTerm = BigInteger.valueOf(1000);
        iissInfo.put("nextPRepTerm", nextPrepTerm);
        contextMock.when(getIISSInfo).thenReturn(iissInfo);

        unlockPeriod = BigInteger.valueOf(8 * 43200L);
        contextMock.when(getUnstakeLockPeriod).thenReturn(Map.of("unstakeLockPeriod", unlockPeriod));
    }

    void setupSicxScore() {
        contextMock.when(() -> Context.call(eq(sicx.getAddress()), eq("mintTo"), any(Address.class),
                any(BigInteger.class), any(byte[].class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(sicx.getAddress()), eq("burn"), any(BigInteger.class))).thenReturn(null);

        sicxBalance = BigInteger.ZERO;
        contextMock.when(sicxBalanceOf).thenReturn(sicxBalance);

        sicxTotalSupply = BigInteger.ZERO;
        contextMock.when(getSicxTotalSupply).thenReturn(sicxTotalSupply);
    }

    void setupGetPrepsResponse() {
        prepsResponse.put("blockHeight", BigInteger.valueOf(123456L));
        List<Map<String, Object>> prepsList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            prepsList.add(Map.of("address", sm.createAccount().getAddress()));
        }
        prepsResponse.put("preps", prepsList);
    }

    JSONObject getUnstakeJsonData() {
        JSONObject unstakeData = new JSONObject();
        unstakeData.put("method", "unstake");
        return unstakeData;
    }

    @Test
    void name() {
        assertEquals("Staked ICX Manager", staking.call("name"));
    }

    @Test
    void setAndGetBlockHeightWeek() {
        assertEquals(nextPrepTerm, staking.call("getBlockHeightWeek"));

        BigInteger newBlockHeight = BigInteger.valueOf(55555);
        staking.invoke(owner, "setBlockHeightWeek", newBlockHeight);
        assertEquals(newBlockHeight, staking.call("getBlockHeightWeek"));

        nextPrepTerm = newBlockHeight.add(BigInteger.valueOf(7 * 43200 + 19L));
        iissInfo.put("nextPRepTerm", nextPrepTerm);
        contextMock.when(getIISSInfo).thenReturn(iissInfo);
        sm.call(owner, ICX.multiply(BigInteger.valueOf(199L)), staking.getAddress(), "stakeICX",
                new Address(new byte[Address.LENGTH]), new byte[0]);
        assertEquals(nextPrepTerm, staking.call("getBlockHeightWeek"));
    }

    @Test
    void testNewIscore() {
        assertEquals(ICX, staking.call("getTodayRate"));

        BigInteger extraICXBalance = BigInteger.valueOf(397L);
        BigInteger stakeAmount = BigInteger.valueOf(199L);
        contextMock.when(() -> Context.getBalance(staking.getAddress())).thenReturn(extraICXBalance.add(stakeAmount));

        sicxTotalSupply = BigInteger.valueOf(719L);
        contextMock.when(getSicxTotalSupply).thenReturn(sicxTotalSupply);

        doReturn(sicxTotalSupply).when(stakingSpy).getTotalStake();
        //noinspection ResultOfMethodCallIgnored
        contextMock.verify(() -> Context.newVarDB(eq(TOTAL_STAKE), eq(BigInteger.class)));

        sm.call(owner, stakeAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);

        assertEquals(extraICXBalance, staking.call("getLifetimeReward"));
        BigInteger newRate = sicxTotalSupply.add(extraICXBalance).multiply(ICX).divide(sicxTotalSupply);
        assertEquals(newRate, staking.call("getTodayRate"));

        contextMock.when(() -> Context.getBalance(staking.getAddress())).thenReturn(stakeAmount);
        sm.call(owner, stakeAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        assertEquals(extraICXBalance, staking.call("getLifetimeReward"));
    }

    @Test
    void toggleStakingOn() {
        assertEquals(true, staking.call("getStakingOn"));
        staking.invoke(owner, "toggleStakingOn");
        assertEquals(false, staking.call("getStakingOn"));
    }

    @Test
    void setAndGetSicxAddress() {
        assertEquals(sicx.getAddress(), staking.call("getSicxAddress"));

        Account newSicx = Account.newScoreAccount(scoreAccountCount++);
        staking.invoke(owner, "setSicxAddress", newSicx.getAddress());
        assertEquals(newSicx.getAddress(), staking.call("getSicxAddress"));
    }

    @Test
    void unstakeBatchLimit() {
        assertEquals(BigInteger.valueOf(200L), staking.call("getUnstakeBatchLimit"));
        staking.invoke(owner, "setUnstakeBatchLimit", BigInteger.valueOf(300L));
        assertEquals(BigInteger.valueOf(300L), staking.call("getUnstakeBatchLimit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPrepList() {
        ArrayList<Address> expectedList = new ArrayList<>();
        List<Map<String, Object>> prepsList = (List<Map<String, Object>>) prepsResponse.get("preps");
        for (Map<String, Object> prepMap : prepsList) {
            expectedList.add((Address) prepMap.get("address"));
        }
        assertArrayEquals(expectedList.toArray(), ((List<Address>) staking.call("getPrepList")).toArray());
        assertEquals(100, ((List<Address>) staking.call("getPrepList")).size());

        Account newPrep = sm.createAccount();
        PrepDelegations delegation = new PrepDelegations();
        delegation._address = newPrep.getAddress();
        delegation._votes_in_per = HUNDRED_PERCENTAGE;

        // Providing user delegation by non sicx holder doesn't increase prep list
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation});
        assertArrayEquals(expectedList.toArray(), ((List<Address>) staking.call("getPrepList")).toArray());

        // Sicx holder's delegation increase prep list
        contextMock.when(sicxBalanceOf).thenReturn(BigInteger.TEN);
        expectedList.add(newPrep.getAddress());
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation});
        assertArrayEquals(expectedList.toArray(), ((List<Address>) staking.call("getPrepList")).toArray());

        //Removing the delegation should reduce the prep list
        expectedList.remove(newPrep.getAddress());
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{});
        assertArrayEquals(expectedList.toArray(), ((List<Address>) staking.call("getPrepList")).toArray());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUnstakingAmount() {
        assertEquals(BigInteger.ZERO, staking.call("getUnstakingAmount"));

        // Calling from contracts other than sicx fails
        Executable callNotFromSicx = () -> staking.invoke(owner, "tokenFallback", owner.getAddress(),
                ICX.multiply(BigInteger.ONE), new byte[0]);
        String expectedErrorMessage = "Reverted(0): Staked ICX Manager: The Staking contract only accepts sICX tokens" +
                ".: " +
                sicx.getAddress().toString();
        expectErrorMessage(callNotFromSicx, expectedErrorMessage);

        // Calling without data fails
        Executable invalidData = () -> staking.invoke(sicx, "tokenFallback", owner.getAddress(),
                ICX.multiply(BigInteger.valueOf(919L)), new byte[0]);
        expectedErrorMessage = "Unexpected end of input at 1:1";
        expectErrorMessage(invalidData, expectedErrorMessage);

        JSONObject data = getUnstakeJsonData();

        // Trying to unstake with no total stake fails
        BigInteger unstakedAmount = ICX.multiply(BigInteger.valueOf(919L));
        Executable negativeTotalStake = () -> staking.invoke(sicx, "tokenFallback", owner.getAddress(), unstakedAmount,
                data.toString().getBytes());
        expectedErrorMessage = "Reverted(0): Staked ICX Manager: Total staked amount can't be set negative";
        expectErrorMessage(negativeTotalStake, expectedErrorMessage);

        //Successful unstake
        doReturn(unstakedAmount).when(stakingSpy).getTotalStake();
        staking.invoke(sicx, "tokenFallback", owner.getAddress(), unstakedAmount, data.toString().getBytes());
        // Since unit test doesn't reset the context if invocation is reverted, the number is double than expected
        assertEquals(unstakedAmount.multiply(BigInteger.TWO), staking.call("getUnstakingAmount"));

        BigInteger blockHeight = BigInteger.valueOf(sm.getBlock().getHeight());
        List<List<Object>> unstakeDetails = new ArrayList<>();
        unstakeDetails.add(List.of(BigInteger.ONE, unstakedAmount, owner.getAddress(), blockHeight.add(unlockPeriod),
                owner.getAddress()));

        assertArrayEquals(unstakeDetails.toArray(), ((List<List<Object>>) staking.call("getUnstakeInfo")).toArray());

        staking.invoke(sicx, "tokenFallback", owner.getAddress(), unstakedAmount, data.toString().getBytes());
        assertEquals(unstakedAmount.multiply(BigInteger.valueOf(3L)), staking.call("getUnstakingAmount"));
        unstakeDetails.add(List.of(BigInteger.TWO, unstakedAmount, owner.getAddress(),
                blockHeight.add(unlockPeriod.add(BigInteger.ONE)), owner.getAddress()));
        List<List<Object>> actualUnstakeDetails = (List<List<Object>>) staking.call("getUnstakeInfo");
        assertArrayEquals(unstakeDetails.toArray(), actualUnstakeDetails.toArray());
    }

    @Test
    void getTotalStake() {
        assertEquals(BigInteger.ZERO, staking.call("getTotalStake"));

        BigInteger totalStaked;
        // changes with iscore rewards, stake and unstake
        BigInteger stakeAmount = BigInteger.valueOf(199L);
        sm.call(owner, stakeAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        totalStaked = stakeAmount;
        assertEquals(totalStaked, staking.call("getTotalStake"));

        sm.call(owner, stakeAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        totalStaked = totalStaked.add(stakeAmount);
        assertEquals(totalStaked, staking.call("getTotalStake"));
        contextMock.verify(() -> Context.call(eq(sicx.getAddress()), eq("mintTo"), any(Address.class), eq(stakeAmount),
                any(byte[].class)), times(2));

        // Unstake same amount
        JSONObject data = getUnstakeJsonData();
        staking.invoke(sicx, "tokenFallback", owner.getAddress(), stakeAmount, data.toString().getBytes());
        totalStaked = totalStaked.subtract(stakeAmount);
        assertEquals(totalStaked, staking.call("getTotalStake"));

        // I-Score generated is added in total staked amount
        BigInteger extraICXBalance = ICX.multiply(BigInteger.valueOf(100_000L));
        contextMock.when(() -> Context.getBalance(staking.getAddress())).thenReturn(extraICXBalance.add(stakeAmount));
        contextMock.when(getSicxTotalSupply).thenReturn(totalStaked);
        Map<String, Object> unstakeList = new HashMap<>();
        unstakeList.put("unstake", stakeAmount);
        List<Map<String, Object>> unstakes = new ArrayList<>();
        unstakes.add(unstakeList);
        contextMock.when(getStake).thenReturn(Map.of("unstakes", unstakes));
        sm.call(owner, stakeAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        totalStaked = totalStaked.add(extraICXBalance).add(stakeAmount);
        assertEquals(totalStaked, staking.call("getTotalStake"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTopPreps() {
        List<Address> expectedList = new ArrayList<>();
        List<Map<String, Object>> prepsList = (List<Map<String, Object>>) prepsResponse.get("preps");
        for (Map<String, Object> prepMap : prepsList) {
            expectedList.add((Address) prepMap.get("address"));
        }
        assertArrayEquals(expectedList.toArray(), ((List<Address>) staking.call("getTopPreps")).toArray());

        // Top preps changes whenever week has passed
        setupGetPrepsResponse();
        contextMock.when(getPreps).thenReturn(prepsResponse);

        BigInteger oneWeekBlocks = BigInteger.valueOf(7 * 43200L);
        nextPrepTerm = BigInteger.valueOf(1000).add(oneWeekBlocks).add(BigInteger.TEN);
        iissInfo.put("nextPRepTerm", nextPrepTerm);
        contextMock.when(getIISSInfo).thenReturn(iissInfo);

        List<Address> newTopPreps = new ArrayList<>();
        prepsList = (List<Map<String, Object>>) prepsResponse.get("preps");
        for (Map<String, Object> prepMap : prepsList) {
            newTopPreps.add((Address) prepMap.get("address"));
        }
        assertNotSame(expectedList, newTopPreps);
        sm.call(owner, BigInteger.TEN, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        assertArrayEquals(newTopPreps.toArray(), ((List<Address>) staking.call("getTopPreps")).toArray());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPrepDelegations() {
        Map<String, BigInteger> actualPrepDelegation = new HashMap<>();
        List<Address> topPreps = (List<Address>) staking.call("getTopPreps");
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        for (Address prep : topPreps) {
            expectedPrepDelegations.put(prep.toString(), BigInteger.ZERO);
        }
        assertEquals(expectedPrepDelegations, staking.call("getPrepDelegations"));
        assertEquals(Map.of(), staking.call("getActualPrepDelegations"));

        Account newPrep = sm.createAccount();
        PrepDelegations delegation = new PrepDelegations();
        delegation._address = newPrep.getAddress();
        delegation._votes_in_per = HUNDRED_PERCENTAGE;

        // All sicx is used to specify non-top prep
        BigInteger totalStaked = BigInteger.TEN;
        contextMock.when(sicxBalanceOf).thenReturn(BigInteger.TEN);
        doReturn(totalStaked).when(stakingSpy).getTotalStake();
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation});
        actualPrepDelegation.put(newPrep.getAddress().toString(), BigInteger.TEN);
        expectedPrepDelegations.put(newPrep.getAddress().toString(), BigInteger.TEN);
        assertEquals(expectedPrepDelegations, staking.call("getPrepDelegations"));
        assertEquals(actualPrepDelegation, staking.call("getActualPrepDelegations"));

        // Stake ICX from a new user without any preference.
        BigInteger stakedAmount = BigInteger.valueOf(199L);
        totalStaked = totalStaked.add(stakedAmount);
        sm.call(alice, stakedAmount, staking.getAddress(), "stakeICX", new Address(new byte[Address.LENGTH]),
                new byte[0]);
        doReturn(totalStaked).when(stakingSpy).getTotalStake();

        BigInteger topPrepsCount = BigInteger.valueOf(topPreps.size());
        BigInteger amountToBeDistributed = stakedAmount;
        for (Address prep : topPreps) {
            BigInteger prepAmount = amountToBeDistributed.divide(topPrepsCount);
            expectedPrepDelegations.put(prep.toString(), prepAmount);
            amountToBeDistributed = amountToBeDistributed.subtract(prepAmount);
            topPrepsCount = topPrepsCount.subtract(BigInteger.ONE);
        }
        assertEquals(expectedPrepDelegations, staking.call("getPrepDelegations"));
        assertEquals(actualPrepDelegation, staking.call("getActualPrepDelegations"));

        // All preference to first prep from alice
        delegation._address = topPreps.get(0);
        contextMock.when(sicxBalanceOf).thenReturn(stakedAmount);
        staking.invoke(alice, "delegate", (Object) new PrepDelegations[]{delegation});
        expectedPrepDelegations.put(topPreps.get(0).toString(), stakedAmount);
        for (int i = 1; i < topPreps.size(); i++) {
            expectedPrepDelegations.put(topPreps.get(i).toString(), BigInteger.ZERO);
        }
        actualPrepDelegation.put(topPreps.get(0).toString(), stakedAmount);
        assertEquals(expectedPrepDelegations, staking.call("getPrepDelegations"));
        assertEquals(actualPrepDelegation, staking.call("getActualPrepDelegations"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAddressDelegation() {

        // Return empty map if icx is 0 and delegation null
        assertEquals(Map.of(), staking.call("getAddressDelegations", owner.getAddress()));

        // Return map with 0 value if icx is 0 and delegation yes
        BigInteger totalPrepsChosen = BigInteger.valueOf(7);
        BigInteger totalPercentage = HUNDRED_PERCENTAGE;
        PrepDelegations[] delegations = new PrepDelegations[7];
        for (int i = 0; i < 7; i++) {
            Account newPrep = sm.createAccount();
            PrepDelegations delegation = new PrepDelegations();
            delegation._address = newPrep.getAddress();
            BigInteger percentage = totalPercentage.divide(totalPrepsChosen);
            delegation._votes_in_per = percentage;
            delegations[i] = delegation;
            totalPercentage = totalPercentage.subtract(percentage);
            totalPrepsChosen = totalPrepsChosen.subtract(BigInteger.ONE);
        }
        staking.invoke(owner, "delegate", (Object) delegations);
        Map<String, BigInteger> expectedDelegation = new HashMap<>();
        Map<String, BigInteger> actualUserDelegation = new HashMap<>();
        for (PrepDelegations delegation : delegations) {
            expectedDelegation.put(delegation._address.toString(), BigInteger.ZERO);
            actualUserDelegation.put(delegation._address.toString(), delegation._votes_in_per);
        }
        assertEquals(actualUserDelegation, staking.call("getActualUserDelegationPercentage", owner.getAddress()));
        assertEquals(expectedDelegation, staking.call("getAddressDelegations", owner.getAddress()));

        // Return as per delegation if icx is +ve and delegation yes
        BigInteger totalAmount = BigInteger.valueOf(199L);
        contextMock.when(sicxBalanceOf).thenReturn(totalAmount);
        doReturn(ONE_EXA).when(stakingSpy).getTodayRate();
        totalPercentage = HUNDRED_PERCENTAGE;
        BigInteger amountToDistribute = totalAmount.multiply(ONE_EXA).divide(ONE_EXA);

        Map<String, BigInteger> networkDelegationPercentage = (Map<String, BigInteger>) staking.call(
                "getActualUserDelegationPercentage",
                owner.getAddress());
        for (Map.Entry<String, BigInteger> delegation : networkDelegationPercentage.entrySet()) {
            BigInteger amount = delegation.getValue().multiply(amountToDistribute).divide(totalPercentage);
            expectedDelegation.put(delegation.getKey(), amount);
            amountToDistribute = amountToDistribute.subtract(amount);
            totalPercentage = totalPercentage.subtract(delegation.getValue());
        }
        assertEquals(expectedDelegation, staking.call("getAddressDelegations", owner.getAddress()));

        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{});
        assertEquals(Map.of(), staking.call("getActualUserDelegationPercentage", owner.getAddress()));

        // Return evenly distributed map if icx is +ve and delegation null
        List<Address> topPreps = (List<Address>) staking.call("getTopPreps");
        amountToDistribute = totalAmount.multiply(ONE_EXA).divide(ONE_EXA);
        BigInteger totalTopPreps = BigInteger.valueOf(topPreps.size());
        Map<String, BigInteger> defaultDelegationList = new HashMap<>();
        for (Address prep : topPreps) {
            BigInteger amount = amountToDistribute.divide(totalTopPreps);
            defaultDelegationList.put(prep.toString(), amount);
            amountToDistribute = amountToDistribute.subtract(amount);
            totalTopPreps = totalTopPreps.subtract(BigInteger.ONE);
        }
        assertEquals(defaultDelegationList, staking.call("getAddressDelegations", alice.getAddress()));
    }

    @Test
    void checkForIscore() {

        Account newPrep = sm.createAccount();
        PrepDelegations delegation = new PrepDelegations();
        delegation._address = newPrep.getAddress();
        delegation._votes_in_per = HUNDRED_PERCENTAGE;

        iScore.put("estimatedICX", BigInteger.ZERO);
        contextMock.when(queryIscore).thenReturn(iScore);
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation});
        contextMock.verify(claimIScore, times(0));

        // claim iscore is called when there is estimated ICX
        iScore.put("estimatedICX", BigInteger.TEN);
        contextMock.when(queryIscore).thenReturn(iScore);
        staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation});
        contextMock.verify(claimIScore, times(1));
    }

    @Test
    void claimUnstakedICX() {
        BigInteger icxToClaim = BigInteger.valueOf(599L);
        BigInteger icxPayable = BigInteger.valueOf(401L);

        doReturn(BigInteger.TEN).when(stakingSpy).claimableICX(any(Address.class));
        doReturn(BigInteger.TWO).when(stakingSpy).totalClaimableIcx();
        String expectedErrorMessage = "Reverted(0): Staked ICX Manager: No sufficient icx to claim. Requested: 10 " +
                "Available: 2";
        Executable claimMoreThanAvailable = () -> staking.invoke(owner, "claimUnstakedICX", owner.getAddress());
        expectErrorMessage(claimMoreThanAvailable, expectedErrorMessage);

        doReturn(icxPayable).when(stakingSpy).claimableICX(any(Address.class));
        doReturn(icxToClaim).when(stakingSpy).totalClaimableIcx();

        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);

        staking.invoke(owner, "claimUnstakedICX", owner.getAddress());
        verify(stakingSpy).FundTransfer(owner.getAddress(), icxPayable,
                icxPayable + " ICX sent to " + owner.getAddress() + ".");
        contextMock.verify(() -> Context.transfer(owner.getAddress(), icxPayable));
    }

    @Test
    void delegate() {
        Account newPrep = sm.createAccount();
        PrepDelegations delegation = new PrepDelegations();
        delegation._address = newPrep.getAddress();
        delegation._votes_in_per = HUNDRED_PERCENTAGE;

        Executable duplicatePrep = () -> staking.invoke(owner, "delegate", (Object) new PrepDelegations[]{delegation,
                delegation});
        String expectedErrorMessage = "Reverted(0): Staked ICX Manager: You can not delegate same P-Rep twice in a " +
                "transaction.";
        expectErrorMessage(duplicatePrep, expectedErrorMessage);

        delegation._votes_in_per = BigInteger.TEN;
        Executable voteLessThanMinimum = () -> staking.invoke(owner, "delegate",
                (Object) new PrepDelegations[]{delegation});
        expectedErrorMessage = "Reverted(0): Staked ICX Manager: You should provide delegation percentage more than 0" +
                ".001%.";
        expectErrorMessage(voteLessThanMinimum, expectedErrorMessage);

        delegation._votes_in_per = HUNDRED_PERCENTAGE.subtract(BigInteger.ONE);
        Executable totalLessThanHundred = () -> staking.invoke(owner, "delegate",
                (Object) new PrepDelegations[]{delegation});
        expectedErrorMessage = "Reverted(0): Staked ICX Manager: Total delegations should be 100%.";
        expectErrorMessage(totalLessThanHundred, expectedErrorMessage);
    }

    @Test
    void unstake() {
        // stake
        sm.call(owner, ICX.multiply(BigInteger.valueOf(199L)), staking.getAddress(), "stakeICX",
                new Address(new byte[Address.LENGTH]), new byte[0]);
        sm.call(alice, ICX.multiply(BigInteger.valueOf(599L)), staking.getAddress(), "stakeICX",
                new Address(new byte[Address.LENGTH]), new byte[0]);

        // Unstake
        JSONObject data = getUnstakeJsonData();
        staking.invoke(sicx, "tokenFallback", owner.getAddress(), BigInteger.valueOf(100L), data.toString().getBytes());
        BigInteger ownerBlockHeight = BigInteger.valueOf(sm.getBlock().getHeight());
        staking.invoke(sicx, "tokenFallback", alice.getAddress(), BigInteger.valueOf(50L), data.toString().getBytes());
        BigInteger aliceBlockHeight = BigInteger.valueOf(sm.getBlock().getHeight());

        List<Map<String, Object>> ownerUnstakedDetails = new ArrayList<>();
        ownerUnstakedDetails.add(Map.of("amount", BigInteger.valueOf(100L), "from", owner.getAddress(), "blockHeight"
                , ownerBlockHeight.add(unlockPeriod), "sender", owner.getAddress()));
        assertEquals(ownerUnstakedDetails, staking.call("getUserUnstakeInfo", owner.getAddress()));

        List<Map<String, Object>> aliceUnstakedDetails = new ArrayList<>();
        aliceUnstakedDetails.add(Map.of("amount", BigInteger.valueOf(50L), "from", alice.getAddress(), "blockHeight"
                , aliceBlockHeight.add(unlockPeriod), "sender", alice.getAddress()));
        assertEquals(aliceUnstakedDetails, staking.call("getUserUnstakeInfo", alice.getAddress()));

        BigInteger totalUnstaked = BigInteger.valueOf(150L);
        assertEquals(totalUnstaked, staking.call("getUnstakingAmount"));

        contextMock.when(() -> Context.getBalance(any(Address.class))).thenReturn(BigInteger.TEN);
        Map<String, Object> unstakeList = new HashMap<>();
        unstakeList.put("unstake", totalUnstaked);
        List<Map<String, Object>> unstakes = new ArrayList<>();
        unstakes.add(unstakeList);
        contextMock.when(getStake).thenReturn(Map.of("unstakes", unstakes));

        sm.call(sm.createAccount(), BigInteger.TEN, staking.getAddress(), "stakeICX",
                new Address(new byte[Address.LENGTH]), new byte[0]);
        totalUnstaked = totalUnstaked.subtract(BigInteger.TEN);
        assertEquals(totalUnstaked, staking.call("getUnstakingAmount"));
        assertEquals(BigInteger.TEN, staking.call("claimableICX", owner.getAddress()));
        assertEquals(BigInteger.ZERO, staking.call("claimableICX", alice.getAddress()));
        assertEquals(BigInteger.TEN, staking.call("totalClaimableIcx"));

        contextMock.when(() -> Context.getBalance(any(Address.class))).thenReturn(BigInteger.valueOf(200L));
        sm.call(sm.createAccount(), BigInteger.valueOf(200L), staking.getAddress(), "stakeICX",
                new Address(new byte[Address.LENGTH]), new byte[0]);
        totalUnstaked = totalUnstaked.subtract(BigInteger.valueOf(200L).min(totalUnstaked));
        assertEquals(totalUnstaked, staking.call("getUnstakingAmount"));
        assertEquals(BigInteger.valueOf(100L), staking.call("claimableICX", owner.getAddress()));
        assertEquals(BigInteger.valueOf(50L), staking.call("claimableICX", alice.getAddress()));
        assertEquals(BigInteger.valueOf(150L), staking.call("totalClaimableIcx"));
    }

    @Test
    void transferUpdateDelegations() {

    }

    @Test
    void stakeICX() {

    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }

}