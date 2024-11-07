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

package network.balanced.score.core.rewards;

import network.balanced.score.core.rewards.utils.BalanceData;
import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.BranchedAddressDictDB;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.utils.RewardsConstants.BALANCE;
import static network.balanced.score.core.rewards.utils.RewardsConstants.TOTAL_SUPPLY;
import static network.balanced.score.lib.utils.Constants.*;

public class DataSourceImpl {
    private final BranchDB<String, VarDB<Address>> contractAddress = Context.newBranchDB("contract_address",
            Address.class);
    private final BranchDB<String, VarDB<String>> name = Context.newBranchDB("name", String.class);
    private final BranchDB<String, VarDB<BigInteger>> workingSupply = Context.newBranchDB("working_supply",
            BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalDist = Context.newBranchDB("total_dist",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> distPercent = Context.newBranchDB("dist_percent",
            BigInteger.class);
    private final BranchedAddressDictDB<String, BigInteger> userWeight = new BranchedAddressDictDB<>("user_weight",
            BigInteger.class);
    private final BranchedAddressDictDB<String, BigInteger> userWorkingBalance = new BranchedAddressDictDB<>(
            "user_working_balance", BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> lastUpdateTimeUs = Context.newBranchDB("last_update_us",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalWeight = Context.newBranchDB("running_total",
            BigInteger.class);

    // name -> token -> day -> amount
    private final BranchDB<String, BranchDB<Address, DictDB<BigInteger, BigInteger>>> externalDist = Context
            .newBranchDB("external_total_dist", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> userBalance = Context.newBranchDB("user_balance",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalSupply = Context.newBranchDB("total_supplyV2",
            BigInteger.class);
    // name -> token -> user -> amount
    private final BranchDB<String, BranchDB<Address, DictDB<String, BigInteger>>> externalUserWeight = Context
            .newBranchDB("external_user_weight", BigInteger.class);
    // name -> token -> weight
    private final BranchDB<String, DictDB<Address, BigInteger>> externalTotalWeights = Context
            .newBranchDB("external_running_total", BigInteger.class);
    private final BranchDB<String, ArrayDB<Address>> rewardTokens = Context.newBranchDB("reward_tokens", Address.class);

    private final String dbKey;

    public DataSourceImpl(String key) {
        dbKey = key;
    }

    public Address getContractAddress() {
        return contractAddress.at(dbKey).get();
    }

    public void setContractAddress(Address address) {
        this.contractAddress.at(dbKey).set(address);
    }

    public String getName() {
        return name.at(dbKey).get();
    }

    public void setName(String name) {
        this.name.at(dbKey).set(name);
    }

    // Used for migration if it happens on Claim
    public BigInteger getWorkingSupply(boolean readonly) {
        BigInteger workingSupply = this.workingSupply.at(dbKey).get();
        if (workingSupply != null) {
            return workingSupply;
        }

        workingSupply = loadCurrentSupply(EOA_ZERO.toString()).get(TOTAL_SUPPLY);
        if (!readonly) {
            setWorkingSupply(workingSupply);
        }

        return workingSupply;
    }

    // Used for migration if it happens on BalanceUpdate
    public BigInteger getWorkingSupply(BigInteger prevSupply, boolean readonly) {
        BigInteger workingSupply = this.workingSupply.at(dbKey).get();
        if (workingSupply != null) {
            return workingSupply;
        }

        if (!readonly) {
            setWorkingSupply(prevSupply);
        }

        return prevSupply;
    }

    public BigInteger getWorkingSupply() {
        return workingSupply.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    private void setWorkingSupply(BigInteger supply) {
        this.workingSupply.at(dbKey).set(supply);
    }

    public BigInteger getTotalSupply() {
        return totalSupply.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    private void setTotalSupply(BigInteger supply) {
        this.totalSupply.at(dbKey).set(supply);
    }

    // Used for migration if it happens on Claim
    public BigInteger getWorkingBalance(String user, boolean readonly) {
        BigInteger workingBalance = userWorkingBalance.at(dbKey).get(user);
        if (workingBalance != null) {
            return workingBalance;
        }

        workingBalance = loadCurrentSupply(user).get(BALANCE);
        if (!readonly) {
            setWorkingBalance(user, workingBalance);
        }

        return workingBalance;
    }

    // Used for migration if it happens on BalanceUpdate
    public BigInteger getWorkingBalance(String user, BigInteger prevBalance, boolean readonly) {
        BigInteger workingBalance = userWorkingBalance.at(dbKey).get(user);
        if (workingBalance != null) {
            return workingBalance;
        }

        if (!readonly) {
            setWorkingBalance(user, prevBalance);
        }

        return prevBalance;
    }

    public BigInteger getWorkingBalance(String user) {
        return userWorkingBalance.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    private void setWorkingBalance(String user, BigInteger balance) {
        this.userWorkingBalance.at(dbKey).set(user, balance);
    }

    public BigInteger getBalance(String user) {
        return userBalance.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    private void setBalance(String user, BigInteger balance) {
        this.userBalance.at(dbKey).set(user, balance);
    }

    public BigInteger getTotalDist(BigInteger day, boolean readonly) {
        DictDB<BigInteger, BigInteger> distAt = totalDist.at(dbKey);
        BigInteger dist = distAt.get(day);
        if (dist != null) {
            return dist;
        }

        dist = RewardsImpl.getTotalDist(getName(), day, readonly);
        if (!readonly) {
            distAt.set(day, dist);
        }

        return dist;
    }

    public BigInteger getTotalDist(BigInteger day) {
        return totalDist.at(dbKey).getOrDefault(day, BigInteger.ZERO);
    }

    public void setTotalDist(BigInteger day, BigInteger value) {
        totalDist.at(dbKey).set(day, value);
    }

    public BigInteger getTotalExternalDist(BigInteger day, Address token) {
        return externalDist.at(dbKey).at(token).getOrDefault(day, BigInteger.ZERO);
    }

    public void setTotalExternalDist(BigInteger day, Address token, BigInteger value) {
        externalDist.at(dbKey).at(token).set(day, value);
    }

    public BigInteger getDistPercent() {
        return distPercent.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setDistPercent(BigInteger distPercent) {
        this.distPercent.at(dbKey).set(distPercent);
    }

    public BigInteger getUserWeight(String user) {
        return userWeight.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    public BigInteger getExternalUserWeight(String user, Address token) {
        return externalUserWeight.at(dbKey).at(token).getOrDefault(user, BigInteger.ZERO);
    }

    public BigInteger getLastUpdateTimeUs() {
        return lastUpdateTimeUs.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public BigInteger getTotalWeight() {
        return totalWeight.at(dbKey).getOrDefault(BigInteger.ZERO);
    }


    public BigInteger getTotalExternalWeight(Address token) {
        return externalTotalWeights.at(dbKey).getOrDefault(token, BigInteger.ZERO);
    }

    public Address[] getRewardTokens() {
        ArrayDB<Address> tokens = rewardTokens.at(dbKey);
        int size = tokens.size();
        Address[] tokensArray = new Address[size];
        for (int i = 0; i < tokens.size(); i++) {
            tokensArray[i] = tokens.get(i);
        }

        return tokensArray;
    }

    public ArrayDB<Address> getRewardTokensDB() {
        return rewardTokens.at(dbKey);
    }

    public void addRewardToken(Address token) {
        rewardTokens.at(dbKey).add(token);
    }

    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> loadCurrentSupply(String owner) {
        // Bad handling that is only relevant during migration, otherwise it will always succeed on first scenario
        try {
            return (Map<String, BigInteger>) Context.call(getContractAddress(), "getBalanceAndSupply", getName(), owner);
        } catch (Exception e) {
            try {
                return (Map<String, BigInteger>) Context.call(getContractAddress(), "getBalanceAndSupply", getName(), Address.fromString(owner));
            } catch (Exception _e) {
                return Map.of("_totalSupply", BigInteger.ZERO,
                        "_balance", BigInteger.ZERO);
            }
        }
    }

    public Map<Address, BigInteger> updateSingleUserData(BigInteger currentTime, BalanceData balances, String user, boolean readOnlyContext) {
        BigInteger currentUserWeight = getUserWeight(user);
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();
        Address[] externalRewards = getRewardTokens();
        Address baln = BalancedAddressManager.getBaln();
        Map<Address, BigInteger> totalWeight = updateTotalWeight(lastUpdateTimestamp, currentTime, balances, externalRewards, readOnlyContext);
        Map<Address, BigInteger> accruedRewards = new HashMap<>(externalRewards.length + 1);
        accruedRewards.put(baln, BigInteger.ZERO);

        //  If the user's current weight is less than the total, update their weight and issue rewards
        if (balances.prevWorkingBalance.compareTo(BigInteger.ZERO) > 0) {
            accruedRewards.put(baln, computeUserRewards(balances.prevWorkingBalance, totalWeight.get(baln), currentUserWeight));
            for (Address token : externalRewards) {
                accruedRewards.put(token, computeUserRewards(balances.prevBalance, totalWeight.get(token), getExternalUserWeight(user, token)));
            }
        }

        if (!readOnlyContext) {
            userWeight.at(dbKey).set(user, totalWeight.get(baln));
            for (Address token : externalRewards) {
                externalUserWeight.at(dbKey).at(token).set(user, totalWeight.get(token));
            }
        }

        return accruedRewards;
    }

    public void updateWorkingBalanceAndSupply(String user, BalanceData balances) {
        BigInteger balance = balances.balance;
        BigInteger supply = balances.supply;
        Context.require(balance.compareTo(BigInteger.ZERO) >= 0);
        Context.require(supply.compareTo(BigInteger.ZERO) >= 0);

        setBalance(user, balance);
        setTotalSupply(supply);

        BigInteger weight = RewardsImpl.boostWeight.get();
        BigInteger max = balance.multiply(EXA).divide(weight);

        BigInteger boost = BigInteger.ZERO;
        if (balances.boostedSupply.compareTo(BigInteger.ZERO) > 0 && balance.compareTo(BigInteger.ZERO) > 0) {
            boost = supply.multiply(balances.boostedBalance).multiply(EXA.subtract(weight)).divide(balances.boostedSupply).divide(weight);
        }

        BigInteger newWorkingBalance = balance.add(boost);
        newWorkingBalance = newWorkingBalance.min(max);

        BigInteger previousWorkingBalance = getWorkingBalance(user);
        BigInteger previousWorkingSupply = getWorkingSupply();

        BigInteger newTotalWorkingSupply =
                previousWorkingSupply.subtract(previousWorkingBalance).add(newWorkingBalance);

        setWorkingBalance(user, newWorkingBalance);
        setWorkingSupply(newTotalWorkingSupply);
    }

    private BigInteger computeTotalWeight(BigInteger previousTotalWeight,
                                          BigInteger emission,
                                          BigInteger totalSupply,
                                          BigInteger lastUpdateTime,
                                          BigInteger currentTime) {
        if (emission.equals(BigInteger.ZERO) || totalSupply.equals(BigInteger.ZERO)) {
            return previousTotalWeight;
        }

        BigInteger timeDelta = currentTime.subtract(lastUpdateTime);
        if (timeDelta.equals(BigInteger.ZERO)) {
            return previousTotalWeight;
        }

        BigInteger weightDelta =
                emission.multiply(timeDelta).multiply(EXA).divide(MICRO_SECONDS_IN_A_DAY).divide(totalSupply);

        return previousTotalWeight.add(weightDelta);
    }

    private Map<Address, BigInteger> updateTotalWeight(BigInteger lastUpdateTimestamp, BigInteger currentTime,
                                                       BalanceData balances, Address[] externalRewards, boolean readOnlyContext) {
        Address baln = BalancedAddressManager.getBaln();
        Map<Address, BigInteger> externalTotals = new HashMap<>(externalRewards.length + 1);
        externalTotals.put(baln, getTotalWeight());
        for (Address token : externalRewards) {
            externalTotals.put(token, externalTotalWeights.at(dbKey).getOrDefault(token, BigInteger.ZERO));
        }

        if (lastUpdateTimestamp.equals(BigInteger.ZERO)) {
            lastUpdateTimestamp = currentTime;
            if (!readOnlyContext) {
                lastUpdateTimeUs.at(dbKey).set(currentTime);
            }
        }

        if (currentTime.equals(lastUpdateTimestamp)) {
            return externalTotals;
        }

        // Emit rewards based on the time delta * reward rate
        BigInteger previousRewardsDay;
        BigInteger previousDayEndUs;

        while (lastUpdateTimestamp.compareTo(currentTime) < 0) {
            previousRewardsDay = lastUpdateTimestamp.divide(MICRO_SECONDS_IN_A_DAY);
            previousDayEndUs = previousRewardsDay.add(BigInteger.ONE).multiply(MICRO_SECONDS_IN_A_DAY);
            BigInteger endComputeTimestampUs = previousDayEndUs.min(currentTime);

            BigInteger emission = getTotalDist(previousRewardsDay, readOnlyContext);
            externalTotals.put(baln, computeTotalWeight(externalTotals.get(baln), emission, balances.prevWorkingSupply, lastUpdateTimestamp,
                    endComputeTimestampUs));
            for (Address token : externalRewards) {
                BigInteger externalEmission = getTotalExternalDist(previousRewardsDay, token);
                if (externalEmission.equals(BigInteger.ZERO)) {
                    continue;
                }

                externalTotals.put(token, computeTotalWeight(externalTotals.get(token), externalEmission,
                        balances.prevSupply, lastUpdateTimestamp, endComputeTimestampUs));
            }

            lastUpdateTimestamp = endComputeTimestampUs;
        }

        if (!readOnlyContext) {
            totalWeight.at(dbKey).set(externalTotals.get(baln));
            for (Address token : externalRewards) {
                externalTotalWeights.at(dbKey).set(token, externalTotals.get(token));
            }

            lastUpdateTimeUs.at(dbKey).set(currentTime);
        }

        return externalTotals;
    }

    public BigInteger getValue() {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());
        return datasource.getBnusdValue(getName());
    }

    public Map<String, Object> getDataAt(BigInteger day) {
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("contract_address", getContractAddress());
        sourceData.put("workingSupply", getWorkingSupply());
        sourceData.put("total_dist", getTotalDist(day, true));
        Address[] externalRewards = getRewardTokens();
        Map<String, Object> externalData = new HashMap<>();
        for (Address addr : externalRewards) {
            externalData.put("external_dist", getTotalExternalDist(day, addr));
            externalData.put("total_weight", getTotalExternalWeight(addr));
            sourceData.put(addr.toString(), externalData);
        }

        return sourceData;
    }

    public Map<String, Object> getData() {
        return getDataAt(RewardsImpl.getDay());
    }

    public Map<String, Object> getUserData(String user) {
        Map<String, Object> data = new HashMap<>();
        Address[] externalRewards = getRewardTokens();
        data.put("user_weight", getUserWeight(user));
        data.put("balance", getBalance(user));
        data.put("working balance", getWorkingBalance(user, true));

        for (Address addr : externalRewards) {
            data.put(addr.toString() + "_weight", getExternalUserWeight(user, addr));
        }
        return data;
    }

    private BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}