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

import static network.balanced.score.core.rewards.utils.RewardsConstants.HUNDRED_PERCENTAGE;
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
    private final BranchDB<String, VarDB<Address>> dataProvider = Context.newBranchDB("data_provider",
            Address.class);
    private final BranchDB<String, VarDB<String>> name = Context.newBranchDB("name", String.class);
    private final BranchDB<String,  VarDB<BigInteger>> cumulativeTotalDist = Context.newBranchDB("cumulative_total_dist", BigInteger.class);
    private final BranchDB<String,  VarDB<BigInteger>> workingSupply = Context.newBranchDB("working_supply",
        BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalDist = Context.newBranchDB("total_dist",
            BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> distPercent = Context.newBranchDB("dist_percent_at_day",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWorkingBalance = Context.newBranchDB("user_working_balance",
        BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> lastUpdateTimeUs = Context.newBranchDB("last_update_us",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalWeight = Context.newBranchDB("running_total",
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

    public Address getDataProvider() {
        return dataProvider.at(dbKey).get();
    }

    public void setDataProvider(Address address){
        this.dataProvider.at(dbKey).set(address);
    }

    public String getName() {
        return name.at(dbKey).get();
    }

    public void setName(String name){
        this.name.at(dbKey).set(name);
    }

    public BigInteger getCumulativeTotalDist() {
        return cumulativeTotalDist.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setCumulativeTotalDist(BigInteger dist){
        this.cumulativeTotalDist.at(dbKey).set(dist);
    }

    public void addCumulativeTotalDist(BigInteger dist){
        VarDB<BigInteger> cumulativeTotalDist = this.cumulativeTotalDist.at(dbKey);
        BigInteger remaningDist = cumulativeTotalDist.getOrDefault(BigInteger.ZERO);
        cumulativeTotalDist.set(remaningDist.add(dist));
        System.out.println(remaningDist.add(dist));
        
    }

    public void removeCumulativeTotalDist(BigInteger dist){
        VarDB<BigInteger> cumulativeTotalDist = this.cumulativeTotalDist.at(dbKey);
        BigInteger remaningDist = cumulativeTotalDist.getOrDefault(BigInteger.ZERO).subtract(dist);
        Context.require(remaningDist.signum() >= 0, RewardsImpl.TAG + ": There are no rewards left to claim for " + getName());
        System.out.println(remaningDist);
        cumulativeTotalDist.set(remaningDist);
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

    public BigInteger getTotalDist(BigInteger day, boolean readOnly) {
        DictDB<BigInteger, BigInteger> totalDist = this.totalDist.at(dbKey);
        BigInteger dist = totalDist.get(day);
        if (dist == null) {
            dist = calculateTotalDist(day, readOnly);
            if (!readOnly) {
                addCumulativeTotalDist(dist);
                totalDist.set(day, dist);
            }
        }

        return dist;
    }

    public BigInteger getDistPercent(BigInteger day, boolean readOnly) {
        DictDB<BigInteger, BigInteger> distPercent = this.distPercent.at(dbKey);
        BigInteger percentage = distPercent.get(day);
        if (percentage == null) {
            percentage = distPercent.getOrDefault(day.subtract(BigInteger.ONE), BigInteger.ZERO);
            if (!readOnly) {
                distPercent.set(day, percentage);
            }
        }

        return percentage;
    }

    public void setDistPercent(BigInteger day, BigInteger distPercent) {
        this.distPercent.at(dbKey).set(day, distPercent);
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

        BigInteger currentUserWeight = getUserWeight(user);
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();


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
            removeCumulativeTotalDist(accruedRewards);
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

        if (lastUpdateTimestamp.equals(BigInteger.ZERO)) {
            lastUpdateTimestamp = currentTime;
            if (!readOnlyContext) {
                lastUpdateTimeUs.at(dbKey).set(currentTime);
            }
        }

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
            BigInteger emission = getTotalDist(previousRewardsDay, readOnlyContext);
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

    public Map<String, Object> getData() {
        Map<String, Object> sourceData = new HashMap<>();
        BigInteger day = RewardsImpl.getDay();
        sourceData.put("contract_address", getContractAddress());
        sourceData.put("data_provider", getDataProvider());
        sourceData.put("dist_percent", getDistPercent(day, true));
        sourceData.put("total_weight", getTotalWeight());
        sourceData.put("working_total", getWorkingSupply());

        return sourceData;
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

    private BigInteger calculateTotalDist(BigInteger day, boolean readOnly) {
        BigInteger totalDist = RewardsImpl.dailyDistribution(day);
        BigInteger percentage = getDistPercent(day, readOnly);

        return percentage.multiply(totalDist).divide(HUNDRED_PERCENTAGE);
    }
}