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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static network.balanced.score.core.staking.utils.Constant.ONE_EXA;
import static network.balanced.score.core.staking.utils.Constant.SYSTEM_SCORE_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class StakingTest extends TestBase {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    public static final Account governanceScore = Account.newScoreAccount(1);
    private static Staking tokenVestingFactory;
    private final MockedStatic<Context> utilities = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    private Score staking;
    private Score sicx;
    private Staking scoreSpy;

    Map<String, Object> prepsResponse = new HashMap<>();
    Map<String, Object> iissInfo = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        setupGetPrepsResponse();
        iissInfo.put("nextPRepTerm", BigInteger.valueOf(1000L));

        sicx = sm.deploy(owner, MockSicx.class);
        assert (sicx.getAddress().isContract());

        utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo")).thenReturn(iissInfo);
        utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getPReps", BigInteger.ONE, BigInteger.valueOf(100L))).thenReturn(prepsResponse);
        staking = sm.deploy(owner, Staking.class);

        scoreSpy = (Staking) spy(staking.getInstance());
        staking.setInstance(scoreSpy);

        staking.invoke(owner, "setSicxAddress", sicx.getAddress());
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
        staking.invoke(owner, "setBlockHeightWeek", BigInteger.valueOf(55555));
        assertEquals(BigInteger.valueOf(55555), staking.call("getBlockHeightWeek"));
    }

    @Test
    void getTodayRate() {
        assertEquals(BigInteger.TEN.pow(18), staking.call("getTodayRate"));
    }

    @Test
    void toggleStakingOn() {
        assertEquals(false, staking.call("getStakingOn"));
        staking.invoke(owner, "toggleStakingOn");
        assertEquals(true, staking.call("getStakingOn"));
    }

    @Test
    void setAndGetSicxAddress() {
        assertEquals(sicx.getAddress(), staking.call("getSicxAddress"));
    }

    @Test
    void unstakeBatchLimit() {
        assertEquals(BigInteger.valueOf(200L), staking.call("getUnstakeBatchLimit"));
        staking.invoke(owner, "setUnstakeBatchLimit", BigInteger.valueOf(300L));
        assertEquals(BigInteger.valueOf(300L), staking.call("getUnstakeBatchLimit"));
    }

    @Test
    void getPrepList() {
        ArrayList<Address> expectedList = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prepsList = (List<Map<String,Object>>) prepsResponse.get("preps");
        for (Map<String, Object> prepMap : prepsList) {
            expectedList.add((Address) prepMap.get("address"));
        }
        assertEquals(expectedList, staking.call("getPrepList"));


    }

    @Test
    void getUnstakingAmount() {
        assertEquals(BigInteger.ZERO, staking.call("getUnstakingAmount"));
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
    void setPrepDelegations() {
        Map<String, BigInteger> prepDict;
        staking.invoke(owner, "setPrepDelegations", Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"),
                BigInteger.valueOf(1000000000000000000L));
        prepDict = (Map<String, BigInteger>) staking.call("getPrepDelegations");
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"),
                BigInteger.valueOf(1000000000000000000L));
    }

    @Test
    void setAddressDelegations() {
        Map<String, BigInteger> prepDict;
        staking.invoke(owner, "setAddressDelegations", owner.getAddress(), Address.fromString(
                        "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"), BigInteger.valueOf(1000000000000000000L),
                BigInteger.valueOf(1000000000000000000L));
        prepDict = (Map<String, BigInteger>) staking.call("getPrepDelegations");
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"),
                BigInteger.valueOf(10000000000000000L));
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"), BigInteger.valueOf(0));
        // If total ICX hold is 0
        staking.invoke(owner, "setAddressDelegations", owner.getAddress(), Address.fromString(
                        "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"), BigInteger.valueOf(1000000000000000000L),
                BigInteger.valueOf(0));
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"),
                BigInteger.valueOf(10000000000000000L));
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"), BigInteger.valueOf(0));
        staking.invoke(owner, "setAddressDelegations", owner.getAddress(), Address.fromString(
                        "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"), BigInteger.valueOf(1000000000000000000L),
                BigInteger.valueOf(1000000000000000000L));
        prepDict = (Map<String, BigInteger>) staking.call("getPrepDelegations");
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357c"),
                BigInteger.valueOf(20000000000000000L));
        assertEquals(prepDict.get("hx0b047c751658f7ce1b2595da34d57a0e7dad357d"), BigInteger.valueOf(0));
    }

    @Test
    void percentToIcx() {
        BigInteger val = (BigInteger) staking.call("percentToIcx", BigInteger.valueOf(1000000000000000000L),
                BigInteger.valueOf(1000000000000000000L));
        assertEquals(val, BigInteger.valueOf(10000000000000000L));
    }

    @Test
    void claimIscore() {
        Map<String, Object> iscore = new HashMap<>();
        iscore.put("blockHeight", "0x0");
        iscore.put("estimatedICX", BigInteger.valueOf(0L));
        iscore.put("iscore", "0x0");

        try {
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "queryIScore",
                    staking.getAddress())).thenReturn(iscore);
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore")).thenReturn(0);
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
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "queryIScore",
                    staking.getAddress())).thenReturn(iscore2);
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore")).thenReturn(0);
            staking.invoke(owner, "checkForIscore");
            assertEquals(true, staking.call("getDistributing"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void stake() {
        try {
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "setStake",
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
            utilities.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            utilities.when(() -> Context.newDictDB("icx_payable", BigInteger.class))
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
            utilities.when(() -> Context.newDictDB("icx_payable", BigInteger.class))
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
            utilities.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.valueOf(5L));
            Score staking = sm.deploy(owner, Staking.class);
            assertEquals(BigInteger.valueOf(5L), staking.call("totalClaimableIcx"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void getRate() {
        VarDB<Address> _sICX_address = mock(VarDB.class);
        VarDB<BigInteger> _total_stake = mock(VarDB.class);
        VarDB<BigInteger> _daily_reward = mock(VarDB.class);

        try {
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            utilities.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            utilities.when(() -> Context.newVarDB("_daily_reward", BigInteger.class))
                    .thenReturn(_daily_reward);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.valueOf(5L));
            when(_daily_reward.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.valueOf(10L));
            utilities.when(() -> Context.call(sicx.getAddress(), "totalSupply")).thenReturn(BigInteger.valueOf(5L));

            Score staking = sm.deploy(owner, Staking.class);
            assertEquals(ONE_EXA.multiply(BigInteger.valueOf(3L)), staking.call("getRate"));


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
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            utilities.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
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
            utilities.when(() -> Context.newDictDB("_address_delegations", String.class))
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
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            utilities.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);
            utilities.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            when(_sICX_address.get()).thenReturn(sicx.getAddress());
            when(_rate.getOrDefault(BigInteger.ZERO)).thenReturn(ONE_EXA);
            when(_prep_delegations.getOrDefault("hx55814f724bbffe49bfa4555535cd9d7e0e1dff32", BigInteger.ZERO)).thenReturn(new BigInteger("50000000000000000000"));
            when(_prep_delegations.getOrDefault("hx55814f724bbffe49bfa4555535cd9d7e0e1dff31", BigInteger.ZERO)).thenReturn(new BigInteger("50000000000000000000"));
            utilities.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
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
            utilities.when(() -> Context.newDictDB("_address_delegations", String.class))
                    .thenReturn(_address_delegations);
            utilities.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("100000000000000000000"));

            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "setDelegation",
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
            utilities.when(() -> Context.newVarDB("_distributing", Boolean.class))
                    .thenReturn(_distributing);
            utilities.when(() -> Context.newVarDB("_total_unstake_amount", BigInteger.class))
                    .thenReturn(_total_unstake_amount);
            utilities.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            utilities.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            utilities.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            when(_distributing.get()).thenReturn(true);
            when(_total_unstake_amount.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("1000000000000000000"));
            when(_icx_to_claim.getOrDefault(BigInteger.ZERO)).thenReturn(BigInteger.ZERO);
            when(_total_stake.getOrDefault(BigInteger.ZERO)).thenReturn(new BigInteger("4000000000000000000"));
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getStake",
                    Address.fromString("cx0000000000000000000000000000000000000004"))).thenReturn(stakedInfo);
            utilities.when(Context::getValue).thenReturn(BigInteger.ZERO);
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357d", BigInteger.ZERO)).thenReturn(new BigInteger("2000000000000000000"));
            when(_prep_delegations.getOrDefault("hx0b047c751658f7ce1b2595da34d57a0e7dad357c", BigInteger.ZERO)).thenReturn(new BigInteger("2000000000000000000"));

            Score staking = sm.deploy(owner, Staking.class);
            Staking scoreSpy = (Staking) spy(staking.getInstance());
            staking.setInstance(scoreSpy);
//            doReturn(ONE_EXA).when(scoreSpy).getRate();
//            doNothing().when(scoreSpy).checkForUnstakedBalance();
//            doNothing().when(scoreSpy).checkForIscore();
            doReturn(new BigInteger("4000000000000000000")).when(scoreSpy).getTotalStake();
            utilities.when(() -> Context.getBalance(staking.getAddress())).thenReturn(new BigInteger(
                    "5000000000000000000"));
            staking.invoke(owner, "performChecks");
            assertEquals(new BigInteger("5000000000000000000"), staking.call("getLifetimeReward"));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void distributeEvenly() {
        try {
            Map<String, BigInteger> delegationIcx;
            staking.invoke(owner, "setSicxAddress", sicx.getAddress());
            utilities.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            staking.invoke(owner, "distributeEvenly", new BigInteger("100000000000000000000"), BigInteger.ONE,
                    owner.getAddress());
            delegationIcx = (Map<String, BigInteger>) staking.call("getPrepDelegations");
            assertEquals(new BigInteger("1000000000000000000"), delegationIcx.get(
                    "hx0b047c751658f7ce1b2595da34d57a0e7dad357d"));
            assertEquals(new BigInteger("1000000000000000000"), delegationIcx.get(
                    "hx0b047c751658f7ce1b2595da34d57a0e7dad357c"));

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
    void delegate() throws Exception {
        PrepDelegations p = new PrepDelegations();
        p._address = Address.fromString("hx0b047c751658f7ce1b2595da34d57a0e7dad357a");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p
        };
        Map<String, BigInteger> previousDelegations = new HashMap<>();
        previousDelegations.put("hx0b047c751658f7ce1b2595da34d57a0e7dad357a", new BigInteger("100000000000000000000"));
        try {
            utilities.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
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
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo")).thenReturn(termDetails);
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
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            utilities.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);

            utilities.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
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
            utilities.when(() -> Context.newVarDB("_rate", BigInteger.class))
                    .thenReturn(_rate);
            utilities.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            utilities.when(() -> Context.call(sicx.getAddress(), "balanceOf", owner.getAddress())).thenReturn(new BigInteger("100000000000000000000"));
            utilities.when(() -> Context.call(sicx.getAddress(), "mintTo", owner.getAddress(), new BigInteger(
                    "100000000000000000000"), new byte[12])).thenReturn(new BigInteger("0"));
            utilities.when(Context::getValue).thenReturn(new BigInteger("100000000000000000000"));
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
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
                    .thenReturn(_sICX_address);
            utilities.when(() -> Context.newVarDB("sICX_supply", BigInteger.class))
                    .thenReturn(sicxSupply);

            utilities.when(() -> Context.newDictDB("_prep_delegations", BigInteger.class))
                    .thenReturn(_prep_delegations);
            utilities.when(() -> Context.newVarDB("_total_unstake_amount", BigInteger.class))
                    .thenReturn(_total_unstake_amount);
            utilities.when(() -> Context.newVarDB("icx_to_claim", BigInteger.class))
                    .thenReturn(_icx_to_claim);
            utilities.when(() -> Context.newVarDB("_daily_reward", BigInteger.class))
                    .thenReturn(_daily_reward);
            utilities.when(() -> Context.newVarDB("_total_stake", BigInteger.class))
                    .thenReturn(_total_stake);
            utilities.when(() -> Context.call(sicx.getAddress(), "burn", new BigInteger("100000000000000000000"))).thenReturn(new BigInteger("0"));
            utilities.when(() -> Context.call(SYSTEM_SCORE_ADDRESS, "getStake",
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
            utilities.when(() -> Context.getBalance(staking.getAddress())).thenReturn(new BigInteger(
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
            utilities.when(() -> Context.newVarDB("sICX_address", Address.class))
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