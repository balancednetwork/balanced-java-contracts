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

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.U_SECONDS_DAY;
import static network.balanced.score.core.rewards.utils.Check.continuousRewardsActive;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import scorex.util.HashMap;

public class DataSourceImpl {
        public final BranchDB<String, VarDB<Address>> contractAddress = Context.newBranchDB("contract_address", Address.class);
        public final BranchDB<String, VarDB<String>> name = Context.newBranchDB("name", String.class);
        public final BranchDB<String, VarDB<BigInteger>> day = Context.newBranchDB("day", BigInteger.class);
        public final BranchDB<String, VarDB<Boolean>> precomp = Context.newBranchDB("precomp", Boolean.class); 
        public final BranchDB<String, VarDB<Integer>> offset = Context.newBranchDB("offset", Integer.class); 
        public final BranchDB<String, DictDB<BigInteger, BigInteger>> totalValue = Context.newBranchDB("total_value", BigInteger.class);
        public final BranchDB<String, DictDB<BigInteger, BigInteger>> totalDist = Context.newBranchDB("total_dist", BigInteger.class);
        public final BranchDB<String, VarDB<BigInteger>> distPercent = Context.newBranchDB("dist_percent", BigInteger.class);
        public final BranchDB<String, DictDB<String, BigInteger>> userWeight = Context.newBranchDB("user_weight", BigInteger.class);
        public final BranchDB<String, VarDB<BigInteger>> lastUpdateTimeUs = Context.newBranchDB("last_update_us", BigInteger.class);
        public final BranchDB<String, VarDB<BigInteger>> totalWeight = Context.newBranchDB("running_total", BigInteger.class);
        public final BranchDB<String, VarDB<BigInteger>> totalSupply = Context.newBranchDB("total_supply", BigInteger.class);

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

    public BigInteger getUserWeight(String user) {
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

    public BigInteger computeSingelUserData(BigInteger currentTime, BigInteger prevTotalSupply, Address user, BigInteger prevBalance) {
        BigInteger currentUserWeight = getUserWeight(user.toString());
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();
    
        if (!continuousRewardsActive() && RewardsImpl.continuousRewardsDay.get() != null) {
            lastUpdateTimestamp = RewardsImpl.continuousRewardsDay.get().multiply(U_SECONDS_DAY);
        }
        
        BigInteger totalWeight = computeTotalWeight(getTotalWeight(),
                                                    getTotalDist(RewardsImpl.getDay()),
                                                    prevTotalSupply,
                                                    lastUpdateTimestamp, 
                                                    currentTime);

        BigInteger accruedRewards = BigInteger.ZERO;

        //  If the user" +s current weight is less than the total, update their weight and issue rewards
        if (currentUserWeight.compareTo(totalWeight) == -1) {
            if (prevBalance.compareTo(BigInteger.ZERO) == 1) {
                accruedRewards = computeUserRewards(prevBalance, totalWeight, currentUserWeight);
                accruedRewards = accruedRewards.divide(EXA);
            }
        }

        return accruedRewards;
    }

    public BigInteger updateSingleUserData(BigInteger currentTime, BigInteger prevTotalSupply, Address user, BigInteger prevBalance) {
        BigInteger currentUserWeight = getUserWeight(user.toString());
        BigInteger totalWeight = updateTotalWeight(currentTime, prevTotalSupply);
        BigInteger accruedRewards = BigInteger.ZERO;
        //  If the user" +s current weight is less than the total, update their weight and issue rewards
        if (currentUserWeight.compareTo(totalWeight) < 0) {
            if (prevBalance.compareTo(BigInteger.ZERO) > 0) {
                accruedRewards = computeUserRewards(prevBalance, totalWeight, currentUserWeight);
                accruedRewards = accruedRewards.divide(EXA);
            }

            if (!continuousRewardsActive()) {
                return BigInteger.ZERO;
            }
        
            userWeight.at(dbKey).set(user.toString(), totalWeight);
        }

        return accruedRewards;
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

    public void distribute(int batchSize) {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());

        BigInteger day = getDay();
        String name = getName();

        Object precomputeDoneObj = RewardsImpl.call(getContractAddress(), "precompute", day.intValue(), batchSize);

        boolean precomputeDone;
        try {
            precomputeDone = (boolean)precomputeDoneObj;
        } catch (Exception e) {
            precomputeDone = !((BigInteger)precomputeDoneObj).equals(BigInteger.ZERO);
        }


        if (!getPrecomp() && precomputeDone) {
            precomp.at(dbKey).set(true);
            BigInteger sourceTotalValue = datasource.getTotalValue(name, day.intValue());

            totalValue.at(dbKey).set(day, sourceTotalValue);
        } 

        if (!getPrecomp()) {
            return;
        }

        int offset = this.getOffset();
        Map<String, BigInteger> dataBatch = (Map<String, BigInteger>) RewardsImpl.call(getContractAddress(), "getDataBatch", name, day.intValue(), batchSize, offset);
        this.offset.at(dbKey).set(offset + batchSize);
        if (dataBatch.isEmpty()) {
            this.day.at(dbKey).set(day.add(BigInteger.ONE));
            this.offset.at(dbKey).set(0);
            this.precomp.at(dbKey).set(false);
        }

        BigInteger remaining = getTotalDist(day);
        BigInteger shares = getTotalValue(day);
        BigInteger originalShares = shares;

        BigInteger batchSum = BigInteger.ZERO;
        for (Map.Entry<String, BigInteger> entry : dataBatch.entrySet()) {
            batchSum = batchSum.add(entry.getValue());
        }

        BigInteger tokenShare = BigInteger.ZERO;
        for (Map.Entry<String,BigInteger> entry : dataBatch.entrySet()) {
            BigInteger value = entry.getValue();
            Address address = Address.fromString(entry.getKey());
            tokenShare = remaining.multiply(value).divide(shares);
            Context.require(shares.compareTo(BigInteger.ZERO) == 1,  
                        RewardsImpl.TAG + ": zero or negative divisor for " + name + ", " +
                        "sum: " + batchSum + ", " +
                        "total: " + shares + ", " +
                        "remaining: " + remaining + ", " +
                        "token_share: " + tokenShare + ", " +
                        "starting: " + originalShares );
            
            remaining = remaining.subtract(tokenShare);
            shares = shares.subtract(value);
            BigInteger prevHoldings = RewardsImpl.balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO);
            RewardsImpl.balnHoldings.set(address.toString(), prevHoldings.add(tokenShare));
        }
    
        totalDist.at(dbKey).set(day, remaining);
        totalValue.at(dbKey).set(day, shares);
        Report(day, name, remaining, shares);
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

        BigInteger weightDelta = emission.multiply(timeDelta).multiply(EXA).divide(U_SECONDS_DAY.multiply(totalSupply));

        BigInteger newTotalWeight = previousTotalWeight.add(weightDelta);
        return newTotalWeight;
    }

    private BigInteger updateTotalWeight(BigInteger currentTime, BigInteger totalSupply) {
        BigInteger previousRunningTotal = getTotalWeight();
        BigInteger originalTotalWeight = previousRunningTotal;
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();
        if (!continuousRewardsActive() && RewardsImpl.continuousRewardsDay.get() != null) {
            lastUpdateTimestamp = RewardsImpl.continuousRewardsDay.get().multiply(U_SECONDS_DAY);
            lastUpdateTimeUs.at(dbKey).set(lastUpdateTimestamp);
        }

        BigInteger originalLastUpdateTimestamp = lastUpdateTimestamp;

        if (currentTime.equals(lastUpdateTimestamp)) {
            return previousRunningTotal;
        }
        
        // Emit rewards based on the time delta * reward rate
        BigInteger previousRewardsDay = BigInteger.ZERO;
        BigInteger previousDayEndUs = BigInteger.ZERO;

        BigInteger newTotal = BigInteger.ZERO;
        while (lastUpdateTimestamp.compareTo(currentTime) < 0) {
            previousRewardsDay = lastUpdateTimestamp.divide(U_SECONDS_DAY);
            previousDayEndUs = U_SECONDS_DAY.multiply(previousRewardsDay.add(BigInteger.ONE));
            BigInteger endComputeTimestampUs = previousDayEndUs.min(currentTime);

            BigInteger emission = getTotalDist(previousRewardsDay);
            newTotal = computeTotalWeight(previousRunningTotal,
                                                     emission,
                                                     totalSupply,
                                                     lastUpdateTimestamp,
                                                     endComputeTimestampUs);
        
            previousRunningTotal = newTotal;
            lastUpdateTimestamp = endComputeTimestampUs;
        }
        
        if (!continuousRewardsActive()) {
            return originalTotalWeight;
        }
        
        if (newTotal.compareTo(originalTotalWeight) >= 0) {
            totalWeight.at(dbKey).set(newTotal);
        }

        if (currentTime.compareTo(originalLastUpdateTimestamp) > 0) {
            lastUpdateTimeUs.at(dbKey).set(currentTime);
        } 

        return newTotal;
    }

    private BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance);
    }

    @EventLog(indexed=2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {}
}