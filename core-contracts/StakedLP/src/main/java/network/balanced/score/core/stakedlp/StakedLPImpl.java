/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.StakedLP;
import network.balanced.score.lib.interfaces.StakedLPXCall;
import network.balanced.score.lib.utils.*;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.getXCall;
import static network.balanced.score.lib.utils.Check.*;

public class StakedLPImpl implements StakedLP {

    final static AddressBranchDictDB<BigInteger, BigInteger> poolStakedDetails = new AddressBranchDictDB<>("poolStakeDetails",
            BigInteger.class);

    private static final DictDB<BigInteger, BigInteger> totalStakedAmount = Context.newDictDB("totalStaked",
            BigInteger.class);
    private static final IterableDictDB<BigInteger, String> dataSourceNames = new IterableDictDB<>("dataSourceNames",
            String.class, BigInteger.class, false);
    private static final IterableDictDB<String, BigInteger> dataSourceIds = new IterableDictDB<>("dataSourceIds",
            BigInteger.class, String.class, false);

    static final VarDB<Address> governance = Context.newVarDB("governanceAddress", Address.class);
    static final VarDB<Address> dex = Context.newVarDB("dexAddress", Address.class);
    private static final VarDB<Address> rewards = Context.newVarDB("rewardsAddress", Address.class);
    private final VarDB<String> currentVersion = Context.newVarDB("version", String.class);
    public static String NATIVE_NID;
    public StakedLPImpl(Address governance) {
        if (StakedLPImpl.governance.get() == null) {
            Context.require(governance.isContract(), "StakedLP: Governance address should be a contract");
            StakedLPImpl.governance.set(governance);
            BalancedAddressManager.setGovernance(governance);
        }
        if (currentVersion.getOrDefault("").equals(Versions.STAKEDLP)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.STAKEDLP);
        NATIVE_NID = (String) Context.call(getXCall(), "getNetworkId");
    }

    /*
     * Events
     */

    @EventLog(indexed = 1)
    public void Stake(String _owner, BigInteger _id, BigInteger _value) {
    }

    @EventLog(indexed = 1)
    public void Unstake(String _owner, BigInteger _id, BigInteger _value) {
    }

    @External(readonly = true)
    public String name() {
        return Names.STAKEDLP;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    @External
    public void setDex(Address dex) {
        only(governance);
        isContract(dex);
        StakedLPImpl.dex.set(dex);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setGovernance(Address governance) {
        onlyOwner();
        isContract(governance);
        StakedLPImpl.governance.set(governance);
    }

    @External(readonly = true)
    public Address getRewards() {
        return rewards.get();
    }

    @External
    public void setRewards(Address rewards) {
        only(governance);
        isContract(rewards);
        StakedLPImpl.rewards.set(rewards);
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, BigInteger _id) {
        NetworkAddress owner = NetworkAddress.valueOf(_owner.toString(), NATIVE_NID);
        return poolStakedDetails.at(owner).getOrDefault(_id, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger xBalanceOf(String _owner, BigInteger _id) {
        NetworkAddress owner = NetworkAddress.valueOf(_owner);
        return poolStakedDetails.at(owner).getOrDefault(_id, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalStaked(BigInteger _id) {
        return totalStakedAmount.getOrDefault(_id, BigInteger.ZERO);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        StakedLPXCall.process(this, _from, _data);
    }

    public void xUnstake(String from, BigInteger id, BigInteger value) {
        Context.println("from is: "+from);
        Context.println("id is: "+id);
        Context.println("value is: "+value);
        unstake(id, NetworkAddress.valueOf(from), value);
    }

    @External
    public void unstake(BigInteger id, BigInteger value) {
        Address caller = Context.getCaller();
        NetworkAddress user = NetworkAddress.valueOf(caller.toString(), NATIVE_NID);
        unstake(id, user, value);
    }

    private void unstake(BigInteger id, NetworkAddress user, BigInteger value){
        Context.println("value is: "+value);
        Context.require(value.compareTo(BigInteger.ZERO) > 0, "StakedLP: Cannot unstake less than zero value");
        BigInteger previousBalance = poolStakedDetails.at(user).getOrDefault(id, BigInteger.ZERO);
        BigInteger previousTotal = totalStaked(id);
        String poolName = getSourceName(id);

        Context.require(previousBalance.compareTo(value) >= 0, "StakedLP: Cannot unstake, user don't have enough " +
                "staked balance, Amount to unstake: " + value + " Staked balance of user: " + user + " is: " + previousBalance);

        BigInteger newBalance = previousBalance.subtract(value);
        BigInteger newTotal = previousTotal.subtract(value);
        Context.require(newBalance.signum() >= 0 && newTotal.signum() >= 0, "StakedLP: New staked balance of user and" +
                " total amount can't be negative");
        poolStakedDetails.at(user).set(id, newBalance);
        totalStakedAmount.set(id, newTotal);

        Unstake(user.toString(), id, value);

        Context.call(rewards.get(), "updateBalanceAndSupply", poolName, newTotal, user.toString(), newBalance);

        try {
            Context.call(dex.get(), "hubTransfer", user.toString(), value, id, new byte[0]);
        } catch (Exception e) {
            Context.revert("StakedLP: Failed to transfer LP tokens back to user. Reason: " + e.getMessage());
        }
    }

    @External
    public void onXIRC31Received(String _operator, String _from, BigInteger _id, BigInteger _value, byte[] _data) {
        only(dex);
        Context.require(_value.signum() > 0, "StakedLP: Token value should be a positive number");
        NetworkAddress from = NetworkAddress.valueOf(_from);
        this.stake(from, _id, _value);
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        only(dex);
        Context.require(_value.signum() > 0, "StakedLP: Token value should be a positive number");
        NetworkAddress from = NetworkAddress.valueOf(_from.toString(), NATIVE_NID);
        this.stake(from, _id, _value);
    }

    @External
    public void addDataSource(BigInteger id, String name) {
        only(governance);
        Context.require(dataSourceNames.get(id) == null, "Datasource with id " + id + " already exist");
        dataSourceIds.set(name, id);
        dataSourceNames.set(id, name);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, String _owner) {
        BigInteger poolId = dataSourceIds.get(_name);
        BigInteger totalSupply = totalStaked(poolId);
        BigInteger balance = balanceOf(Address.fromString(_owner), poolId);

        Map<String, BigInteger> rewardsData = new HashMap<>();
        rewardsData.put("_balance", balance);
        rewardsData.put("_totalSupply", totalSupply);

        return rewardsData;
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        return Context.call(BigInteger.class, dex.get(), "getLPBnusdValue", dataSourceIds.get(_name));
    }

    @External(readonly = true)
    public String getSourceName(BigInteger id) {
        String name = dataSourceNames.get(id);
        Context.require(name != null, "Pool id: " + id + " is not a valid pool");
        return name;
    }

    @External(readonly = true)
    public BigInteger getSourceId(String name) {
        BigInteger id = dataSourceIds.get(name);
        Context.require(id != null, "datasource " + name + " is not a valid source");
        return id;
    }

    @External(readonly = true)
    public List<BigInteger> getAllowedPoolIds() {
        return dataSourceNames.keys();
    }

    @External(readonly = true)
    public List<String> getDataSources() {
        return dataSourceIds.keys();
    }

    @External(readonly = true)
    public boolean isSupportedPool(BigInteger id) {
        String name = dataSourceNames.get(id);
        return name != null;
    }

    private void stake(NetworkAddress user, BigInteger id, BigInteger value) {
        // Validate inputs
        Context.require(value.compareTo(BigInteger.ZERO) > 0,
                "StakedLP: Cannot stake less than zero, value to stake " + value);
        String poolName = getSourceName(id);
        // Compute and store changes
        BigInteger previousBalance = poolStakedDetails.at(user).getOrDefault(id, BigInteger.ZERO);
        BigInteger previousTotal = totalStaked(id);
        BigInteger newBalance = previousBalance.add(value);
        BigInteger newTotal = previousTotal.add(value);
        poolStakedDetails.at(user).set(id, newBalance);
        totalStakedAmount.set(id, newTotal);

        Stake(user.toString(), id, value);

        if(user.net().equals(NATIVE_NID)) {
            Context.call(rewards.get(), "updateBalanceAndSupply", poolName, newTotal, user.account(), newBalance);
        }else{
            Context.call(rewards.get(), "updateBalanceAndSupply", poolName, newTotal, user.toString(), newBalance);
        }
    }

}
