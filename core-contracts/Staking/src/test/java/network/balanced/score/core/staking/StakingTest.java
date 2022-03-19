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
import network.balanced.score.core.staking.utils.UnstakeDetails;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.*;

class StakingTest extends TestBase {

    private static int scoreAccountCount = 1;
    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
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

        Map<String, Object> unstakeData = new HashMap<>();
        unstakeData.put("method", "unstake");
        JSONObject data = new JSONObject(unstakeData);

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
        List<UnstakeDetails> unstakeDetails = new ArrayList<>();
        UnstakeDetails firstUnstake = new UnstakeDetails(BigInteger.ONE, unstakedAmount, owner.getAddress(),
                blockHeight.add(unlockPeriod), owner.getAddress());
        unstakeDetails.add(firstUnstake);

        assertUnstakeDetails(unstakeDetails.get(0), ((List<UnstakeDetails>)staking.call("getUnstakeInfo")).get(0));

        staking.invoke(sicx, "tokenFallback", owner.getAddress(), unstakedAmount, data.toString().getBytes());
        assertEquals(unstakedAmount.multiply(BigInteger.valueOf(3L)), staking.call("getUnstakingAmount"));
        unstakeDetails.add(new UnstakeDetails(BigInteger.TWO, unstakedAmount, owner.getAddress(),
                blockHeight.add(unlockPeriod.add(BigInteger.ONE)), owner.getAddress()));
        List<UnstakeDetails> actualUnstakeDetails = (List<UnstakeDetails>) staking.call("getUnstakeInfo");
        for (int i = 0; i < unstakeDetails.size(); i++) {
            assertUnstakeDetails(unstakeDetails.get(i), actualUnstakeDetails.get(i));
        }
    }

    public void assertUnstakeDetails(UnstakeDetails a, UnstakeDetails b) {
        assertEquals(a.nodeId, b.nodeId);
        assertEquals(a.key, b.key);
        assertEquals(a.unstakeAmount, b.unstakeAmount);
        assertEquals(a.unstakeBlockHeight, b.unstakeBlockHeight);
        assertEquals(a.receiverAddress, b.receiverAddress);
    }

    @Test
    void getTotalStake() {
        assertEquals(BigInteger.ZERO, staking.call("getTotalStake"));
    }

    @Test
    void getTopPreps() {
        ArrayList<Address> expectedList = new ArrayList<>();
        expectedList.add(Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"));
        expectedList.add(Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));
        assertEquals(expectedList, staking.call("getTopPreps"));
    }

    @Test
    void claimIscore() {
        Map<String, Object> iscore = new HashMap<>();
        iscore.put("blockHeight", "0x0");
        iscore.put("estimatedICX", BigInteger.valueOf(0L));
        iscore.put("iscore", "0x0");

        try {
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "queryIScore",
                    staking.getAddress())).thenReturn(iscore);
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore")).thenReturn(0);
            staking.invoke(owner, "checkForIscore");
            assertEquals(false, staking.call("getDistributing"));


        } catch (Exception e) {
            e.printStackTrace();
        }
        // If the iscore generated is more than 0
        Map<String, Object> iscore2 = new HashMap<>();
        iscore2.put("blockHeight", "0x0");
        iscore2.put("estimatedICX", BigInteger.valueOf(5L));
        iscore2.put("iscore", "0x0");
        try {
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "queryIScore",
                    staking.getAddress())).thenReturn(iscore2);
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore")).thenReturn(0);
            staking.invoke(owner, "checkForIscore");
            assertEquals(true, staking.call("getDistributing"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void stake() {
        try {
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "setStake",
                    BigInteger.valueOf(5L))).thenReturn(0);
            staking.invoke(owner, "stake", BigInteger.valueOf(5L));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void claimUnstakedICX() {
        BigInteger icxToClaim = BigInteger.valueOf(5L);
        BigInteger icxPayable = BigInteger.valueOf(2L);
        VarDB<BigInteger> _icx_to_claim = mock(VarDB.class);
        DictDB<Address, BigInteger> _icx_payable = mock(DictDB.class);

        try {
            contextMock.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            contextMock.when(() -> Context.newDictDB("icx_payable", BigInteger.class))
                    .thenReturn(_icx_payable);
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(icxToClaim);
            when(_icx_payable.getOrDefault(owner.getAddress(), BigInteger.ZERO)).thenReturn(icxPayable);
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doNothing().when(scoreSpy).sendIcx(any(Address.class), any(BigInteger.class), any(String.class));
            staking.invoke(owner, "claimUnstakedICX", owner.getAddress());

        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals(BigInteger.ZERO, staking.call("claimableICX", owner.getAddress()));
        assertEquals(BigInteger.ZERO, staking.call("totalClaimableIcx"));

    }

    @Test
    void claimableICX() {
        DictDB<Address, BigInteger> _icx_payable = mock(DictDB.class);
        try {
            contextMock.when(() -> Context.newDictDB("icx_payable", BigInteger.class))
                    .thenReturn(_icx_payable);
            when(_icx_payable.getOrDefault(owner.getAddress(), BigInteger.ZERO)).thenReturn(BigInteger.valueOf(5L));
            Score staking = sm.deploy(owner, Staking.class);
            assertEquals(BigInteger.valueOf(5L), staking.call("claimableICX", owner.getAddress()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void totalClaimableIcx() {
        VarDB<BigInteger> _icx_to_claim = mock(VarDB.class);
        try {
            contextMock.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.valueOf(5L));
            Score staking = sm.deploy(owner, Staking.class);
            assertEquals(BigInteger.valueOf(5L), staking.call("totalClaimableIcx"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void getAddressDelegations() {
        VarDB<Address> _sICX_address = mock(VarDB.class);

        Map<String, BigInteger> delegationPer = new HashMap<>();
        Map<String, BigInteger> delegationIcx = new HashMap<>();
        delegationPer.put("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32", new BigInteger("50000000000000000000"));
        delegationPer.put("hx55814f724bbffe49bfa4555535cd9d7e0e1dff31", new BigInteger("50000000000000000000"));
        try {
            contextMock.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            contextMock.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(delegationPer).when(scoreSpy).getDelegationInPercentage(owner.getAddress());
            delegationIcx = (Map<String, BigInteger>) staking.call("getAddressDelegations", owner.getAddress());
            assertEquals(new BigInteger("50000000000000000000"), delegationPer.get(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
            assertEquals(new BigInteger("50000000000000000000"), delegationPer.get(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff31"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void delegationInPer() {
        DictDB<String, String> _address_delegations = mock(DictDB.class);
        Map<String, BigInteger> delegationIcx;
        String delegation = "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32:50000000000000000000" +
                ".hx55814f724bbffe49bfa4555535cd9d7e0e1dff31:50000000000000000000.";

        try {
            contextMock.when(() -> Context.newDictDB("_address_delegations", String.class))
                    .thenReturn(_address_delegations);
            when(_address_delegations.getOrDefault(String.valueOf(owner.getAddress()), "")).thenReturn(delegation);

            Score staking = sm.deploy(owner, Staking.class);
            delegationIcx = (Map<String, BigInteger>) staking.call("delegationInPer", owner.getAddress());
            assertEquals(new BigInteger("50000000000000000000"), delegationIcx.get(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"));
            assertEquals(new BigInteger("50000000000000000000"), delegationIcx.get(
                    "hx55814f724bbffe49bfa4555535cd9d7e0e1dff31"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void _remove_previous_delegations() {
        VarDB<Address> _sICX_address = mock(VarDB.class);
        VarDB<BigInteger> _rate = mock(VarDB.class);
        DictDB<String, BigInteger> _prep_delegations = mock(DictDB.class);

        Map<String, BigInteger> delegationPer = new HashMap<String, BigInteger>();
        Map<String, BigInteger> delegationIcx = new HashMap<String, BigInteger>();
        delegationPer.put("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32", new BigInteger("50000000000000000000"));
        delegationPer.put("hx55814f724bbffe49bfa4555535cd9d7e0e1dff31", new BigInteger("50000000000000000000"));
        try {
            contextMock.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            contextMock.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);
            contextMock.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            when(_rate.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            when(_prep_delegations.getOrDefault("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32", BigInteger.ZERO)).thenReturn(new BigInteger("50000000000000000000"));
            when(_prep_delegations.getOrDefault("hx55814f724bbffe49bfa4555535cd9d7e0e1dff31", BigInteger.ZERO)).thenReturn(new BigInteger("50000000000000000000"));
            contextMock.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(delegationPer).when(scoreSpy).getDelegationInPercentage(owner.getAddress());
            staking.invoke(owner, "removePreviousDelegations", owner.getAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
        VarDB<Boolean> _distributing = mock(VarDB.class);
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
            contextMock.when(() -> Context.newVarDB("_distributing", Boolean.class))
                    .thenReturn(_distributing);
            contextMock.when(() -> Context.newVarDB("_total_unstake_amount", BigInteger.class))
                    .thenReturn(_total_unstake_amount);
            contextMock.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            contextMock.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            contextMock.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            when(_distributing.get()).thenReturn(true);
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
    void delegate() {
        PrepDelegations p = new PrepDelegations();
        p._address = Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p
        };
        Map<String, BigInteger> previousDelegations = new HashMap<>();
        previousDelegations.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357a", new BigInteger("100000000000000000000"));
        try {
            contextMock.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(BigInteger.ONE).when(scoreSpy).updateTopPreps();
//            doReturn(previousDelegations).when(scoreSpy).removePreviousDelegations(owner.getAddress());
//            doNothing().when(scoreSpy).performChecksForIscoreAndUnstakedBalance();
//            doNothing().when(scoreSpy).stakeAndDelegateInNetwork(BigInteger.ONE);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            staking.invoke(owner, "toggleStakingOn");
            staking.invoke(owner, "delegate", (Object) userDelegation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void resetTopPreps() {
        delegateVotes();
        assertEquals(new BigInteger("1000000000000000000"), staking.call("resetTopPreps"));

    }

    @Test
    void checkForWeek() {
        Map<String, Object> termDetails = new HashMap<String, Object>();
        List<Address> topPreps;
        termDetails.put("nextPRepTerm", BigInteger.valueOf(304400));
        try {
            contextMock.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo")).thenReturn(termDetails);
            staking.invoke(owner, "checkForWeek");


        } catch (Exception e) {
            e.printStackTrace();
        }
        topPreps = (List<Address>) staking.call("getTopPreps");
        assertEquals(Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"), topPreps.get(0));
        assertEquals(Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"), topPreps.get(1));
        assertEquals(new BigInteger("304400"), staking.call("getBlockHeightWeek"));

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

    @Test
    void tokenFallback() {
        VarDB<Address> _sICX_address = mock(VarDB.class);


        try {
            contextMock.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            when(_sICX_address.get()).thenReturn(owner.getAddress());
            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doNothing().when(scoreSpy).unstake(Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"),
//                    new BigInteger("4000000000000000000"), null);
//            doNothing().when(scoreSpy).unstake(Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"),
//                    new BigInteger("4000000000000000000"), Address.fromString(
//                            "hx436106433144e736a67710505fc87ea9becb141d"));
            staking.invoke(owner, "toggleStakingOn");
            staking.invoke(owner, "tokenFallback", Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"),
                    new BigInteger("4000000000000000000"), "{\"method\": \"unstake\"}".getBytes());
            staking.invoke(owner, "tokenFallback", Address.fromString("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32"),
                    new BigInteger("4000000000000000000"), ("{\"method\": \"unstake\"," +
                            "\"user\":\"hx436106433144e736a67710505fc87ea9becb141d\"}").getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}