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

package network.balanced.score.core.rewards;

import static network.balanced.score.core.rewards.utils.Check.continuousRewardsActive;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.HashMap;

public class DataSourceImpl {
    private final BranchDB<String, VarDB<Address>> contractAddress = Context.newBranchDB("contract_address",
            Address.class);
    private final BranchDB<String, VarDB<String>> name = Context.newBranchDB("name", String.class);
    private final BranchDB<String, VarDB<BigInteger>> day = Context.newBranchDB("day", BigInteger.class);
    private final BranchDB<String, VarDB<Boolean>> precomp = Context.newBranchDB("precomp", Boolean.class);
    private final BranchDB<String, VarDB<Integer>> offset = Context.newBranchDB("offset", Integer.class);
    private final BranchDB<String,  VarDB<BigInteger>> workingSupply = Context.newBranchDB("working_supply",
    BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalValue = Context.newBranchDB("total_value",
            BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalDist = Context.newBranchDB("total_dist",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> distPercent = Context.newBranchDB("dist_percent",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWorkingBalance = Context.newBranchDB("user_working_balance",
    BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> lastUpdateTimeUs = Context.newBranchDB("last_update_us",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalWeight = Context.newBranchDB("running_total",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalSupply = Context.newBranchDB("total_supply",
            BigInteger.class);

    private final String dbKey;

    public DataSourceImpl(String key) {
        dbKey = key;
    }

    public Address getContractAddress() {
        return contractAddress.at(dbKey).get();
    }

    public void setContractAddress(Address address){
        this.contractAddress.at(dbKey).set(address);
    }

    public String getName() {
        return name.at(dbKey).get();
    }

    public void setName(String name){
        this.name.at(dbKey).set(name);
    }

    public BigInteger getDay() {
        return day.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setDay(BigInteger day){
        this.day.at(dbKey).set(day);
    }

    public BigInteger getWorkingSupply() {
        return workingSupply.at(dbKey).getOrDefault(loadCurrentSupply(EOA_ZERO).get("_totalSupply"));
    }

    public void setWorkingSupply(BigInteger supply){
        this.workingSupply.at(dbKey).set(supply);
    }

    public BigInteger getWorkingBalance(Address user) {
        return userWorkingBalance.at(dbKey).getOrDefault(user, loadCurrentSupply(user).get("_balance"));
    }

    public void setWorkingBalance(Address user, BigInteger balance) {
        this.userWorkingBalance.at(dbKey).set(user, balance);
    }

    public Boolean getPrecomp() {
        return precomp.at(dbKey).getOrDefault(false);
    }

    public Integer getOffset() {
        return offset.at(dbKey).getOrDefault(0);
    }

    public BigInteger getTotalValue(BigInteger day) {
        return totalValue.at(dbKey).getOrDefault(day, BigInteger.ZERO);
    }

    public BigInteger getTotalDist(BigInteger day) {
        return totalDist.at(dbKey).getOrDefault(day, BigInteger.ZERO);
    }

    public void  setTotalDist(BigInteger day, BigInteger value) {
        totalDist.at(dbKey).set(day, value);
    }
    
    public BigInteger getDistPercent() {
        return distPercent.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setDistPercent(BigInteger distPercent) {
        this.distPercent.at(dbKey).set(distPercent);
    }

    public BigInteger getUserWeight(Address user) {
        return userWeight.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    public BigInteger getLastUpdateTimeUs() {
        return lastUpdateTimeUs.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public BigInteger getTotalWeight() {
        return totalWeight.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public BigInteger getTotalSupply() {
        return totalSupply.at(dbKey).getOrDefault(BigInteger.ZERO);
    }


    public Map<String, BigInteger> loadCurrentSupply(Address owner) {
        try {
            DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());
            return datasource.getBalanceAndSupply(getName(), owner);
        } catch (Exception e ) {
            return Map.of("_totalSupply", BigInteger.ZERO,
                          "_balance", BigInteger.ZERO
            );
        }
    }

    public BigInteger updateSingleUserData(BigInteger currentTime, BigInteger prevTotalSupply, Address user,
                                           BigInteger prevBalance, boolean readOnlyContext) {
        if (!continuousRewardsActive()) {
            return BigInteger.ZERO;
        }

        BigInteger currentUserWeight = getUserWeight(user);
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();

        if (lastUpdateTimestamp.equals(BigInteger.ZERO)) {
            lastUpdateTimestamp = RewardsImpl.continuousRewardsDay.get().multiply(MICRO_SECONDS_IN_A_DAY);
        }

        BigInteger totalWeight = updateTotalWeight(lastUpdateTimestamp, currentTime, prevTotalSupply, readOnlyContext);

        if (currentUserWeight.equals(totalWeight)) {
            return BigInteger.ZERO;
        }

        BigInteger accruedRewards = BigInteger.ZERO;
        //  If the user's current weight is less than the total, update their weight and issue rewards
        if (prevBalance.compareTo(BigInteger.ZERO) > 0) {
            accruedRewards = computeUserRewards(prevBalance, totalWeight, currentUserWeight);
        }

        if (!readOnlyContext) {
            userWeight.at(dbKey).set(user, totalWeight);
        }
        return accruedRewards;
    }

    public Map<String, BigInteger> updateWorkingBalanceAndSupply(Address user, BigInteger balance, BigInteger supply, BigInteger time, Boolean boosted) {
        Map<String, BigInteger> workingSupplyAndBalance = getWorkingBalanceAndSupply(user, balance, supply, time, boosted);
        setWorkingBalance(user, workingSupplyAndBalance.get("workingBalance"));
        setWorkingSupply(workingSupplyAndBalance.get("workingSupply"));

        return workingSupplyAndBalance;
    }

    public Map<String, BigInteger> getWorkingBalanceAndSupply(Address user, BigInteger balance, BigInteger supply, BigInteger time, Boolean boosted) {
        BigInteger bBalnSupply = getBoostedSupply(time);
        if (bBalnSupply.equals(BigInteger.ZERO)) {
            return Map.of(
                "workingSupply", supply,
                "workingBalance", balance
            );
        }

        BigInteger bBalnBalance = BigInteger.ZERO;
        if (boosted) {
            bBalnBalance = getBoostedBalance(user, time);
        }

        BigInteger weight = RewardsImpl.boostWeight.get();
        BigInteger max = balance.multiply(EXA).divide(weight);
        BigInteger boost = supply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(weight)).divide(weight);
        BigInteger newWorkingBalance = balance.add(boost);
        newWorkingBalance = newWorkingBalance.min(max);

        BigInteger previousWorkingBalance = getWorkingBalance(user);
        BigInteger newTotalBalance = getWorkingSupply().subtract(previousWorkingBalance).add(newWorkingBalance);

        return Map.of(
            "workingSupply", newTotalBalance,
            "workingBalance", newWorkingBalance
        );
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

        BigInteger weightDelta = emission.multiply(timeDelta).multiply(EXA).divide(MICRO_SECONDS_IN_A_DAY).divide(totalSupply);

        return previousTotalWeight.add(weightDelta);
    }

    private BigInteger updateTotalWeight(BigInteger lastUpdateTimestamp, BigInteger currentTime,
                                         BigInteger totalSupply, boolean readOnlyContext) {

        BigInteger runningTotal = getTotalWeight();

        if (currentTime.equals(lastUpdateTimestamp)) {
            return runningTotal;
        }

        // Emit rewards based on the time delta * reward rate
        BigInteger previousRewardsDay;
        BigInteger previousDayEndUs;

        while (lastUpdateTimestamp.compareTo(currentTime) < 0) {
            previousRewardsDay = lastUpdateTimestamp.divide(MICRO_SECONDS_IN_A_DAY);
            previousDayEndUs = previousRewardsDay.add(BigInteger.ONE).multiply(MICRO_SECONDS_IN_A_DAY);
            BigInteger endComputeTimestampUs = previousDayEndUs.min(currentTime);

            BigInteger emission = getTotalDist(previousRewardsDay);
            runningTotal = computeTotalWeight(runningTotal, emission, totalSupply, lastUpdateTimestamp,
                    endComputeTimestampUs);
            lastUpdateTimestamp = endComputeTimestampUs;
        }

        if (!readOnlyContext) {
            totalWeight.at(dbKey).set(runningTotal);
            lastUpdateTimeUs.at(dbKey).set(currentTime);
        }

        return runningTotal;
    }

    public BigInteger getValue() {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());
        return datasource.getBnusdValue(getName());
    }

    public Map<String, Object> getDataAt(BigInteger day) {
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("day", day);
        sourceData.put("contract_address", getContractAddress());
        sourceData.put("dist_percent", getDistPercent());
        sourceData.put("precomp", getPrecomp());
        sourceData.put("offset", getOffset());
        sourceData.put("total_value", getTotalValue(day));
        sourceData.put("total_dist", getTotalDist(day));

        return sourceData;
    }

    public Map<String, Object> getData() {
        BigInteger day = this.getDay();
        return getDataAt(day);
    }

    @SuppressWarnings("unchecked")
    public void distribute(int batchSize) {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());

        BigInteger day = getDay();
        String name = getName();

        Object precomputeDoneObj = RewardsImpl.call(getContractAddress(), "precompute", day,
                BigInteger.valueOf(batchSize));
        boolean precomputeDone;
        try {
            precomputeDone = (boolean)precomputeDoneObj;
        } catch (Exception e) {
            precomputeDone = !((BigInteger)precomputeDoneObj).equals(BigInteger.ZERO);
        }

        boolean localPreCompute = getPrecomp();
        if (!localPreCompute && precomputeDone) {
            precomp.at(dbKey).set(true);
            localPreCompute = true;
            BigInteger sourceTotalValue = datasource.getTotalValue(name, day);
            totalValue.at(dbKey).set(day, sourceTotalValue);
        }

        if (!localPreCompute) {
            return;
        }

        int offset = this.getOffset();
        Map<String, BigInteger> dataBatch = (Map<String, BigInteger>) RewardsImpl.call(getContractAddress(), "getDataBatch",
                name, day.intValue(), batchSize, offset);
        this.offset.at(dbKey).set(offset + batchSize);
        if (dataBatch.isEmpty()) {
            this.day.at(dbKey).set(day.add(BigInteger.ONE));
            this.offset.at(dbKey).set(0);
            this.precomp.at(dbKey).set(false);
            return;
        }

        BigInteger remaining = getTotalDist(day);
        BigInteger shares = getTotalValue(day);
        BigInteger originalShares = shares;

        BigInteger batchSum = BigInteger.ZERO;
        for (Map.Entry<String, BigInteger> entry : dataBatch.entrySet()) {
            batchSum = batchSum.add(entry.getValue());
        }

        BigInteger tokenShare;
        for (Map.Entry<String,BigInteger> entry : dataBatch.entrySet()) {
            BigInteger value = entry.getValue();
            String address = entry.getKey();
            tokenShare = remaining.multiply(value).divide(shares);
            Context.require(shares.compareTo(BigInteger.ZERO) > 0,
                    RewardsImpl.TAG + ": zero or negative divisor for " + name + ", " +
                            "sum: " + batchSum + ", " +
                            "total: " + shares + ", " +
                            "remaining: " + remaining + ", " +
                            "token_share: " + tokenShare + ", " +
                            "starting: " + originalShares);

            remaining = remaining.subtract(tokenShare);
            shares = shares.subtract(value);
            BigInteger prevHoldings = RewardsImpl.balnHoldings.getOrDefault(address, BigInteger.ZERO);
            RewardsImpl.balnHoldings.set(address, prevHoldings.add(tokenShare));
        }

        totalDist.at(dbKey).set(day, remaining);
        totalValue.at(dbKey).set(day, shares);
    }

    private BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }

    private BigInteger getBoostedBalance(Address user, BigInteger time)  {
        try {
            return (BigInteger) RewardsImpl.call(RewardsImpl.boostedBaln.get(), "balanceOf", user, time);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private BigInteger getBoostedSupply(BigInteger time)  {
        try {
            return (BigInteger) RewardsImpl.call(RewardsImpl.boostedBaln.get(), "totalSupply", time);  
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }
}