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
import network.balanced.score.core.staking.utils.PrepDelegations;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.*;
import static network.balanced.score.test.UnitTest.expectErrorMessage;
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
    private Staking stakingSpy;

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
        staking = sm.deploy(owner, Staking.class);
        stakingSpy = (Staking) spy(staking.getInstance());
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
        String expectedErrorMessage = TAG + ": The Staking contract only accepts sICX tokens.: " +
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
        expectedErrorMessage = TAG + ": Total staked amount can't be set negative";
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
        String expectedErrorMessage = TAG + ": No sufficient icx to claim. Requested: 10 Available: 2";
        Executable claimMoreThanAvailable = () -> staking.invoke(owner, "claimUnstakedICX", owner.getAddress());
        expectErrorMessage(claimMoreThanAvailable, expectedErrorMessage);

        doReturn(icxPayable).when(stakingSpy).claimableICX(any(Address.class));
        doReturn(icxToClaim).when(stakingSpy).totalClaimableIcx();

        contextMock.when(()->Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);

        staking.invoke(owner, "claimUnstakedICX", owner.getAddress());
        verify(stakingSpy).FundTransfer(owner.getAddress(), icxPayable, icxPayable + " ICX sent to " + owner.getAddress() + ".");
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
        String expectedErrorMessage = TAG + ": You can not delegate same P-Rep twice in a transaction.";
        expectErrorMessage(duplicatePrep, expectedErrorMessage);

        delegation._votes_in_per = BigInteger.TEN;
        Executable voteLessThanMinimum = () -> staking.invoke(owner, "delegate",
                (Object) new PrepDelegations[]{delegation});
        expectedErrorMessage = TAG + ": You should provide delegation percentage more than 0.001%.";
        expectErrorMessage(voteLessThanMinimum, expectedErrorMessage);

        delegation._votes_in_per = HUNDRED_PERCENTAGE.subtract(BigInteger.ONE);
        Executable totalLessThanHundred = () -> staking.invoke(owner, "delegate",
                (Object) new PrepDelegations[]{delegation});
        expectedErrorMessage = TAG + ": Total delegations should be 100%.";
        expectErrorMessage(totalLessThanHundred, expectedErrorMessage);
    }

    @SuppressWarnings({"unchecked", "ResultOfMethodCallIgnored"})
    @Test
    void _delegations() {
        DictDB<String, String> _address_delegations = mock(DictDB.class);
        List<Map<String, Object>> delegationList = new scorex.util.ArrayList<>();
        Map<String, Object> delegateDict = new HashMap<>();
        delegateDict.put("address", "hx0b047c751658f7ce1b2595da34d57a0e7dad357d");
        delegateDict.put("value", new BigInteger("50000000000000000000"));
        delegationList.add(delegateDict);
        Map<String, Object> delegateDict2 = new HashMap<>();
        delegateDict2.put("address", "hx0b047c751658f7ce1b2595da34d57a0e7dad357c");
        delegateDict2.put("value", new BigInteger("50000000000000000000"));
        delegationList.add(delegateDict2);
        VarDB<BigInteger> _total_stake = mock(VarDB.class);

        try {
            contextMock.when(() -> Context.newDictDB("_address_delegations", String.class))
                    .thenReturn(_address_delegations);
            contextMock.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("100000000000000000000"));

            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "setDelegation",
                    delegationList)).thenReturn(0);
            staking.invoke(owner, "delegations", new BigInteger("50000000000000000000"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void _perform_checks() {
        VarDB<BigInteger> _icx_to_claim = mock(VarDB.class);
        VarDB<BigInteger> _total_stake = mock(VarDB.class);
        VarDB<BigInteger> _total_unstake_amount = mock(VarDB.class);
        DictDB<String, BigInteger> _prep_delegations = mock(DictDB.class);

        Map<String, Object> stakedInfo = new HashMap<>();
        Map<String, Object> getStake = new HashMap<>();
        List<Map<String, Object>> unstakeList = new scorex.util.ArrayList<>();
        getStake.put("remainingBlocks", "0x5ae5");
        getStake.put("unstake", new BigInteger("1000000000000000000"));
        getStake.put("unstakeBlockHeight", "0x2c73490");
        unstakeList.add(getStake);
        stakedInfo.put("stake", "0x5150ae84a8cdf00000");
        stakedInfo.put("unstakes", unstakeList);

        try {
            contextMock.when(() -> Context.newVarDB("_total_unstake_amount", BigInteger.class))
                    .thenReturn(_total_unstake_amount);
            contextMock.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            contextMock.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            contextMock.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            when(_total_unstake_amount.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("1000000000000000000"));
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.ZERO);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("4000000000000000000"));
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getStake",
                    Address.fromString("cx0000000000000000000000000000000000000004"))).thenReturn(stakedInfo);
            contextMock.when(Context::getValue).thenReturn(BigInteger.ZERO);
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", BigInteger.ZERO)).thenReturn(new BigInteger("2000000000000000000"));
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", BigInteger.ZERO)).thenReturn(new BigInteger("2000000000000000000"));

            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(ONE_EXA).when(scoreSpy).getRate();
//            doNothing().when(scoreSpy).checkForUnstakedBalance();
//            doNothing().when(scoreSpy).checkForIscore();
            doReturn(new BigInteger("4000000000000000000")).when(scoreSpy).getTotalStake();
            contextMock.when(() -> Context.getBalance(staking.getAddress())).thenReturn(new BigInteger(
                    "5000000000000000000"));
            staking.invoke(owner, "performChecks");
            assertEquals(new BigInteger("5000000000000000000"), staking.call("getLifetimeReward"));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void delegateVotes() {
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a");
        p._votes_in_per = new BigInteger("100000000000000000000");
//        p2._address=Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357b");
//        p2._votes_in_per=new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p
        };
        staking.invoke(owner, "delegateVotes", owner.getAddress(), userDelegation, new BigInteger(
                "100000000000000000000"));
        HashMap<String, BigInteger> prepDelegations = (HashMap<String, BigInteger>) staking.call("getPrepDelegations");
        assertEquals(new BigInteger("0"), prepDelegations.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));
        assertEquals(new BigInteger("0"), prepDelegations.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"));
        assertEquals(new BigInteger("100000000000000000000"), prepDelegations.get(
                "hx0b047c751658f7ce1b2595da34d57a0e7dad357a"));

    }

    @Test
    void transferUpdateDelegations() {
        VarDB<Address> _sICX_address = mock(VarDB.class);
        VarDB<BigInteger> _rate = mock(VarDB.class);
        DictDB<String, BigInteger> _prep_delegations = mock(DictDB.class);


        Map<String, BigInteger> delegationPer = new HashMap<String, BigInteger>();
        Map<String, BigInteger> receiveeDelegation = new HashMap<String, BigInteger>();
        delegationPer.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", new BigInteger("100000000000000000000"));
        receiveeDelegation.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", new BigInteger("100000000000000000000"));
        Map<String, BigInteger> prepDict = new HashMap<>();

        try {
            contextMock.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            contextMock.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);

            contextMock.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            when(_sICX_address.get()).thenReturn(owner.getAddress());
            when(_rate.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(delegationPer).when(scoreSpy).getDelegationInPercentage(Address.fromString(
//                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff31"));
//            doReturn(BigInteger.ONE).when(scoreSpy).updateTopPreps();
//            doNothing().when(scoreSpy).stakeAndDelegateInNetwork(BigInteger.ONE);
//            doReturn(receiveeDelegation).when(scoreSpy).getDelegationInPercentage(Address.fromString(
//                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", BigInteger.ZERO)).thenReturn(new BigInteger("1000000000000000000"));
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", BigInteger.ZERO)).thenReturn(new BigInteger("1000000000000000000"));
            staking.invoke(owner, "toggleStakingOn");
            staking.invoke(owner, "transferUpdateDelegations", Address.fromString(
                            "hx55814f724bbffe49bfa4555535cd9d7e0e1dff31"),
                    Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"), ONE_EXA);
            prepDict = (Map<String, BigInteger>) staking.call("getPrepDelegations");
            assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"),
                    BigInteger.valueOf(1000000000000000000L));
            assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"),
                    BigInteger.valueOf(1000000000000000000L));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void stakeICX() {
        VarDB<Address> _sICX_address = mock(VarDB.class);
        VarDB<BigInteger> _rate = mock(VarDB.class);
        VarDB<BigInteger> _total_stake = mock(VarDB.class);
        DictDB<String, BigInteger> _prep_delegations = mock(DictDB.class);


        Map<String, BigInteger> delegationPer = new HashMap<>();
        Map<String, BigInteger> receiveDelegation = new HashMap<>();
        delegationPer.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", new BigInteger("100000000000000000000"));
        receiveDelegation.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", new BigInteger("100000000000000000000"));
        Map<String, BigInteger> prepDict;

        try {
            contextMock.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);
            contextMock.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            contextMock.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            contextMock.when(() -> Context.call(sicx.getAddress(), "mintTo", owner.getAddress(), new BigInteger(
                    "100000000000000000000"), new byte[12])).thenReturn(new BigInteger("0"));
            contextMock.when(Context::getValue).thenReturn(new BigInteger("100000000000000000000"));
            when(_rate.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doNothing().when(scoreSpy).performChecksForIscoreAndUnstakedBalance();
//            doReturn(BigInteger.ONE).when(scoreSpy).updateTopPreps();
//            doNothing().when(scoreSpy).stakeAndDelegateInNetwork(BigInteger.ONE);
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", BigInteger.ZERO)).thenReturn(new BigInteger("1000000000000000000"));
            staking.invoke(owner, "toggleStakingOn");
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            staking.invoke(owner, "stakeICX", owner.getAddress(), new byte[12]);

            prepDict = (Map<String, BigInteger>) staking.call("getPrepDelegations");
            assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"),
                    BigInteger.valueOf(1000000000000000000L));
            assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"),
                    BigInteger.valueOf(1000000000000000000L));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void _unstake() {
        VarDB<Address> _sICX_address = mock(VarDB.class);
        VarDB<BigInteger> _rate = mock(VarDB.class);
        VarDB<BigInteger> _total_stake = mock(VarDB.class);
        VarDB<BigInteger> sicxSupply = mock(VarDB.class);
        VarDB<BigInteger> _total_unstake_amount = mock(VarDB.class);
        DictDB<String, BigInteger> _prep_delegations = mock(DictDB.class);

        VarDB<BigInteger> _icx_to_claim = mock(VarDB.class);
        VarDB<BigInteger> _daily_reward = mock(VarDB.class);


        Map<String, BigInteger> delegationPer = new HashMap<>();
        Map<String, BigInteger> receiveDelegation = new HashMap<>();
        delegationPer.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", new BigInteger("100000000000000000000"));
        receiveDelegation.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", new BigInteger("100000000000000000000"));
        Map<String, Object> unstakeDict = new java.util.HashMap<>();
        List<Map<String, Object>> unstakeResponseList = new ArrayList<>();
        List<List<Object>> unstakeInfoList = new scorex.util.ArrayList<>();
        List<Object> unstakeInfo = new scorex.util.ArrayList<>();
        unstakeInfo.add(BigInteger.ONE);
        unstakeInfo.add(new BigInteger("100000000000000000000"));
        unstakeInfo.add(Address.fromString("hx0000000000000000000000000000000000000001"));
        unstakeInfo.add(new BigInteger("1000000000000000000"));
        unstakeInfo.add(Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
        unstakeInfoList.add(unstakeInfo);

        Map<String, Object> stakedInfo = new HashMap<>();
        Map<String, Object> getStake = new HashMap<>();
        List<Map<String, Object>> unstakeList = new scorex.util.ArrayList<>();
        getStake.put("remainingBlocks", "0x5ae5");
        getStake.put("unstake", new BigInteger("1000000000000000000"));
        getStake.put("unstakeBlockHeight", new BigInteger("1000000000000000000"));
        unstakeList.add(getStake);
        stakedInfo.put("stake", "0x5150ae84a8cdf00000");
        stakedInfo.put("unstakes", unstakeList);
        unstakeDict.put("sender", Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
        unstakeDict.put("blockHeight", new BigInteger("1000000000000000000"));
        unstakeDict.put("amount", new BigInteger("100000000000000000000"));
        unstakeDict.put("from", Address.fromString("hx0000000000000000000000000000000000000001"));
        unstakeResponseList.add(unstakeDict);
        try {
            contextMock.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            contextMock.when(() -> Context.newVarDB("sICX_supply", BigInteger.class))
                    .thenReturn(sicxSupply);

            contextMock.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            contextMock.when(() -> Context.newVarDB("_total_unstake_amount", BigInteger.class))
                    .thenReturn(_total_unstake_amount);
            contextMock.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            contextMock.when(() -> Context.newVarDB("_daily_reward", BigInteger.class))
                    .thenReturn(_daily_reward);
            contextMock.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            contextMock.when(() -> Context.call(sicx.getAddress(), "burn", new BigInteger(
                    "100000000000000000000"))).thenReturn(new BigInteger("0"));
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getStake",
                    Address.fromString("cx0000000000000000000000000000000000000004"))).thenReturn(stakedInfo);
            when(_rate.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            when(sicxSupply.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("500000000000000000000"));
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", BigInteger.ZERO)).thenReturn(new BigInteger("100000000000000000000"));
            when(_total_unstake_amount.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger(
                    "100000000000000000000"));
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(BigInteger.ONE).when(scoreSpy).calculateDelegatedICXOutOfTopPreps();
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.ZERO);
            when(_daily_reward.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("0"));
//            doNothing().when(scoreSpy).updateDelegationInNetwork(BigInteger.ONE);
//            doNothing().when(scoreSpy).stakeInNetwork(new BigInteger("500000000000000000000"));
//            doReturn(delegationPer).when(scoreSpy).getDelegationInPercentage(owner.getAddress());
            staking.invoke(owner, "unstake", owner.getAddress(), new BigInteger("100000000000000000000"),
                    Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
            assertEquals(unstakeResponseList, staking.call("getUserUnstakeInfo", Address.fromString(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32")));
            assertEquals(unstakeInfoList, staking.call("getUnstakeInfo"));
            staking.invoke(owner, "unstake", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"),
                    new BigInteger("100000000000000000000"), Address.fromString(
                            "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));
            unstakeDict.clear();
            unstakeDict.put("sender", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));
            unstakeDict.put("blockHeight", new BigInteger("1000000000000000000"));
            unstakeDict.put("amount", new BigInteger("100000000000000000000"));
            unstakeDict.put("from", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"));
            unstakeResponseList.clear();
            unstakeResponseList.add(unstakeDict);
            List<Map<String, Object>> userUnstakeInfo;
            userUnstakeInfo = (List<Map<String, Object>>) staking.call("getUserUnstakeInfo", Address.fromString(
                    "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));
            assertEquals(unstakeDict.get("amount"), userUnstakeInfo.get(0).get("amount"));
            assertEquals(unstakeDict.get("sender"), userUnstakeInfo.get(0).get("sender"));
            assertEquals(unstakeDict.get("blockHeight"), userUnstakeInfo.get(0).get("blockHeight"));
            assertEquals(unstakeDict.get("from"), userUnstakeInfo.get(0).get("from"));
            unstakeInfoList.add(List.of(BigInteger.TWO, new BigInteger("100000000000000000000"), Address.fromString(
                            "hx0b047c751658f7ce1b2595da34d57a0e7dad357d"), new BigInteger("1000000000000000000"),
                    Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357c")));
            staking.invoke(owner, "unstake", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a"),
                    new BigInteger("100000000000000000000"), Address.fromString(
                            "hx0b047c751658f7ce1b2595da34d57a0e7dad357e"));
            unstakeInfoList.add(List.of(BigInteger.valueOf(3L), new BigInteger("100000000000000000000"),
                    Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a"), new BigInteger(
                            "1000000000000000000"), Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357e")));
            assertEquals(unstakeInfoList, staking.call("getUnstakeInfo"));
            unstakeDict.clear();
            unstakeDict.put("sender", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357e"));
            unstakeDict.put("blockHeight", new BigInteger("1000000000000000000"));
            unstakeDict.put("amount", new BigInteger("100000000000000000000"));
            unstakeDict.put("from", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a"));
            unstakeResponseList.clear();
            unstakeResponseList.add(unstakeDict);
            userUnstakeInfo.clear();
            userUnstakeInfo = (List<Map<String, Object>>) staking.call("getUserUnstakeInfo", Address.fromString(
                    "hx0b047c751658f7ce1b2595da34d57a0e7dad357e"));
            assertEquals(unstakeDict.get("amount"), userUnstakeInfo.get(0).get("amount"));
            assertEquals(unstakeDict.get("sender"), userUnstakeInfo.get(0).get("sender"));
            assertEquals(unstakeDict.get("blockHeight"), userUnstakeInfo.get(0).get("blockHeight"));
            assertEquals(unstakeDict.get("from"), userUnstakeInfo.get(0).get("from"));
            contextMock.when(() -> Context.getBalance(staking.getAddress())).thenReturn(new BigInteger(
                    "104000000000000000000"));
            staking.invoke(owner, "checkForBalance");
            assertEquals(new BigInteger("100000000000000000000"), staking.call("claimableICX", Address.fromString(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32")));
            assertEquals(new BigInteger("4000000000000000000"), staking.call("claimableICX", Address.fromString(
                    "hx0b047c751658f7ce1b2595da34d57a0e7dad357c")));
            assertEquals(2, ((List<List<Object>>) staking.call("getUnstakeInfo")).size());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}