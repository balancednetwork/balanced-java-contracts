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

import network.balanced.score.lib.interfaces.StakedLP;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.List;

import static network.balanced.score.lib.utils.Check.*;

public class StakedLPImpl implements StakedLP {


    private static final BranchDB<Address, DictDB<BigInteger, BigInteger>> poolStakedDetails =
            Context.newBranchDB("poolStakeDetails", BigInteger.class);
    private static final DictDB<BigInteger, BigInteger> totalStakedAmount = Context.newDictDB("totalStaked",
            BigInteger.class);
    private static final DictDB<BigInteger, String> dataSourceNames = Context.newDictDB("dataSourceNames",
            String.class);
    private static final DictDB<String, BigInteger> dataSourceIds = Context.newDictDB("dataSourceIds",
            BigInteger.class);

    static final VarDB<Address> governance = Context.newVarDB("governanceAddress", Address.class);
    static final VarDB<Address> dex = Context.newVarDB("dexAddress", Address.class);
    private static final VarDB<Address> rewards = Context.newVarDB("rewardsAddress", Address.class);

    public StakedLPImpl(Address governance) {
        if (StakedLPImpl.governance.get() == null) {
            Context.require(governance.isContract(), "StakedLP: Governance address should be a contract");
            StakedLPImpl.governance.set(governance);
        } else if (dataSourceIds.get("sICX/bnUSD") == null) {
            List<String> dataSources = (List<String>) Context.call(rewards.get(), "getDataSourceNames");
            for (String source : dataSources) {
                if (source.equals("Loans") || source.equals("sICX/ICX")) {
                    continue;
                }

                migratePool(source);
            }
        }
    }

    /*
     * Events
     */

    @EventLog(indexed = 1)
    public void Stake(Address _owner, BigInteger _id, BigInteger _value) {}

    @EventLog(indexed = 1)
    public void Unstake(Address _owner, BigInteger _id, BigInteger _value) {}

    @External(readonly = true)
    public String name() {
        return "Balanced StakedLP";
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
        return poolStakedDetails.at(_owner).getOrDefault(_id, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalStaked(BigInteger _id) {
        return totalStakedAmount.getOrDefault(_id, BigInteger.ZERO);
    }

    @External
    public void unstake(BigInteger id, BigInteger value) {
        Address caller = Context.getCaller();
        Context.require(value.compareTo(BigInteger.ZERO) > 0, "StakedLP: Cannot unstake less than zero value");

        BigInteger previousBalance = poolStakedDetails.at(caller).getOrDefault(id, BigInteger.ZERO);
        BigInteger previousTotal = totalStaked(id);
        String poolName = getSourceName(id);

        Context.require(previousBalance.compareTo(value) >= 0, "StakedLP: Cannot unstake, user don't have enough " +
                "staked balance, Amount to unstake: " + value + " Staked balance of user: " + caller + " is: " + previousBalance);

        BigInteger newBalance = previousBalance.subtract(value);
        BigInteger newTotal = previousTotal.subtract(value);
        Context.require(newBalance.signum() >= 0 && newTotal.signum() >= 0, "StakedLP: New staked balance of user and" +
                " total amount can't be negative");
        poolStakedDetails.at(caller).set(id, newBalance);
        totalStakedAmount.set(id, newTotal);

        Unstake(caller, id, value);

        Context.call(rewards.get(), "updateRewardsData", poolName, previousTotal, caller, previousBalance);

        try {
            Context.call(dex.get(), "transfer", caller, value, id, new byte[0]);
        } catch (Exception e) {
            Context.revert("StakedLP: Failed to transfer LP tokens back to user. Reason: " + e.getMessage());
        }
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        only(dex);
        Context.require(_value.signum() > 0, "StakedLP: Token value should be a positive number");
        this.stake(_from, _id, _value);
    }

    @External
    public void addDataSource(BigInteger id, String name) {
        only(governance);
        Context.require(dataSourceNames.get(id) == null, "Datasource with id " + id  + " already exist");
        dataSourceIds.set(name, id);
        dataSourceNames.set(id, name);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        BigInteger poolId = dataSourceIds.get(_name);
        BigInteger totalSupply = totalStaked(poolId);
        BigInteger balance = balanceOf(_owner, poolId);

        Map<String, BigInteger> rewardsData = new HashMap<>();
        rewardsData.put("_balance", balance);
        rewardsData.put("_totalSupply", totalSupply);

        return rewardsData;
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        return Context.call(BigInteger.class, dex.get(), "getLPBnusdValue", dataSourceIds.get(_name));
    }

    private void stake(Address user, BigInteger id, BigInteger value) {

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

        Stake(user, id, value);

        Context.call(rewards.get(), "updateRewardsData", poolName, previousTotal, user, previousBalance);
    }

    private String getSourceName(BigInteger id) {
        String name = dataSourceNames.get(id);
        Context.require(name != null, "Pool id: " + id + " is not a valid pool");
        return name;
    }

    private void migratePool(String name) {
        BigInteger id = Context.call(BigInteger.class, dex.get(), "lookupPid", name);
        dataSourceIds.set(name, id);
        dataSourceNames.set(id, name);
    }
}
