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
import network.balanced.score.lib.utils.Names;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;

public class StakedLPImpl implements StakedLP {

    // Business Logic
    private static final DictDB<BigInteger, Boolean> supportedPools = Context.newDictDB("supportedPools",
            Boolean.class);
    private static final BranchDB<Address, DictDB<BigInteger, BigInteger>> poolStakedDetails =
            Context.newBranchDB("poolStakeDetails", BigInteger.class);
    private static final DictDB<BigInteger, BigInteger> totalStakedAmount = Context.newDictDB("totalStaked",
            BigInteger.class);

    // Linked Contracts
    static final VarDB<Address> governance = Context.newVarDB("governanceAddress", Address.class);
    static final VarDB<Address> admin = Context.newVarDB("adminAddress", Address.class);

    public StakedLPImpl(Address _governance) {
        if (governance.get() == null) {
            Context.require(_governance.isContract(), "StakedLP: Governance address should be a contract");
            governance.set(_governance);
        }

        setGovernance(governance.get());
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
        return Names.STAKEDLP;
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setAdmin(Address admin) {
        only(getGovernance());
        StakedLPImpl.admin.set(admin);
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
    public void addPool(BigInteger id) {
        only(getGovernance());
        if (!supportedPools.getOrDefault(id, Boolean.FALSE)) {
            supportedPools.set(id, Boolean.TRUE);
        }
    }

    @External
    public void removePool(BigInteger id) {
        only(getGovernance());
        if (supportedPools.getOrDefault(id, Boolean.FALSE)) {
            supportedPools.set(id, Boolean.FALSE);
        }
    }

    @External(readonly = true)
    public boolean isSupportedPool(BigInteger id) {
        return supportedPools.getOrDefault(id, Boolean.FALSE);
    }

    @External
    public void unstake(BigInteger id, BigInteger value) {
        Address caller = Context.getCaller();
        Context.require(value.compareTo(BigInteger.ZERO) > 0, "StakedLP: Cannot unstake less than zero value");

        BigInteger previousBalance = poolStakedDetails.at(caller).getOrDefault(id, BigInteger.ZERO);
        BigInteger previousTotal = totalStaked(id);

        Context.require(previousBalance.compareTo(value) >= 0, "StakedLP: Cannot unstake, user don't have enough " +
                "staked balance, Amount to unstake: " + value + " Staked balance of user: " + caller + " is: " + previousBalance);

        BigInteger newBalance = previousBalance.subtract(value);
        BigInteger newTotal = previousTotal.subtract(value);
        Context.require(newBalance.signum() >= 0 && newTotal.signum() >= 0, "StakedLP: New staked balance of user and" +
                " total amount can't be negative");
        poolStakedDetails.at(caller).set(id, newBalance);
        totalStakedAmount.set(id, newTotal);

        Unstake(caller, id, value);
        Address dexAddress = getDex();
        String poolName = (String) Context.call(dexAddress, "getPoolName", id);
        Context.call(getRewards(), "updateRewardsData", poolName, previousTotal, caller, previousBalance);

        try {
            Context.call(dexAddress, "transfer", caller, value, id, new byte[0]);
        } catch (Exception e) {
            Context.revert("StakedLP: Failed to transfer LP tokens back to user. Reason: " + e.getMessage());
        }
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        only(getDex());
        Context.require(_value.signum() > 0, "StakedLP: Token value should be a positive number");
        this.stake(_from, _id, _value);
    }

    private void stake(Address user, BigInteger id, BigInteger value) {

        // Validate inputs
        Context.require(isSupportedPool(id) || isNamedPool(id), "StakedLP: Pool with " + id + " is not supported");
        Context.require(value.compareTo(BigInteger.ZERO) > 0,
                "StakedLP: Cannot stake less than zero, value to stake " + value);

        // Compute and store changes
        BigInteger previousBalance = poolStakedDetails.at(user).getOrDefault(id, BigInteger.ZERO);
        BigInteger previousTotal = totalStaked(id);
        BigInteger newBalance = previousBalance.add(value);
        BigInteger newTotal = previousTotal.add(value);
        poolStakedDetails.at(user).set(id, newBalance);
        totalStakedAmount.set(id, newTotal);

        Stake(user, id, value);

        String poolName = (String) Context.call(getDex(), "getPoolName", id);
        Context.call(getRewards(), "updateRewardsData", poolName, previousTotal, user, previousBalance);
    }

    @SuppressWarnings("unchecked")
    private boolean isNamedPool(BigInteger id) {
        
        if (!supportedPools.getOrDefault(id, Boolean.FALSE)) {
            Address dexAddress = getDex();
            String poolName = (String) Context.call(dexAddress, "getPoolName", id);
            if (poolName == null) {
                return false;
            }
            
            Map<String, Object> dataSource = (Map<String, Object>) Context.call(getRewards(), "getSourceData", poolName);
            if (dataSource.isEmpty() || !dexAddress.equals(dataSource.get("contract_address"))) {
                return false;
            }

            supportedPools.set(id, Boolean.TRUE);
        }
        
        return true;
    }
}
