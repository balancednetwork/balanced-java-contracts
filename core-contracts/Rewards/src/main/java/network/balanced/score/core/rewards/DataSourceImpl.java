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

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;

public class DataSourceImpl {
        public VarDB<Address> contractAddress;
        public VarDB<String> name;
        public VarDB<BigInteger> day;
        public VarDB<Boolean> precomp; 
        public VarDB<Integer> offset; 
        public DictDB<BigInteger, BigInteger> totalValue;
        public DictDB<BigInteger, BigInteger> totalDist;
        public VarDB<BigInteger> distPercent;

        public DictDB<String, BigInteger> userWeight;
        public VarDB<BigInteger> lastUpdateTimeUs;
        public VarDB<BigInteger> totalWeight;
        public VarDB<BigInteger> totalSupply;

    public DataSourceImpl(String key) {
        contractAddress = (VarDB<Address>) Context.newBranchDB("contract_address", Address.class).at(key);
        name = (VarDB<String>) Context.newBranchDB("name", String.class).at(key);
        day = (VarDB<BigInteger>) Context.newBranchDB("day", BigInteger.class).at(key);
        precomp = (VarDB<Boolean>) Context.newBranchDB("precomp", Boolean.class).at(key); 
        offset = (VarDB<Integer>) Context.newBranchDB("offset", Integer.class).at(key); 
        totalValue = (DictDB<BigInteger, BigInteger>) Context.newBranchDB("total_value", BigInteger.class).at(key);
        totalDist = (DictDB<BigInteger, BigInteger>) Context.newBranchDB("total_dist", BigInteger.class).at(key);
        distPercent = (VarDB<BigInteger>) Context.newBranchDB("dist_percent", BigInteger.class).at(key);
        userWeight = (DictDB<String, BigInteger>) Context.newBranchDB("user_weight", BigInteger.class).at(key);
        lastUpdateTimeUs = (VarDB<BigInteger>) Context.newBranchDB("last_update_us", BigInteger.class).at(key);
        totalWeight = (VarDB<BigInteger>) Context.newBranchDB("running_total", BigInteger.class).at(key);
        totalSupply = (VarDB<BigInteger>) Context.newBranchDB("total_supply", BigInteger.class).at(key);
    }

    public Map<String, BigInteger> loadCurrentSupply(Address owner) {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(contractAddress.get());
        return datasource.getBalanceAndSupply(name.get(), owner);
    }

    public BigInteger computeSingelUserData(BigInteger currentTime, BigInteger prevTotalSupply, Address user, BigInteger prevBalance) {
        BigInteger currentUserWeight = userWeight.getOrDefault(user.toString(), BigInteger.ZERO);
        BigInteger totalWeight = computeTotalWeight(this.totalWeight.getOrDefault(BigInteger.ZERO),
                                                    totalDist.getOrDefault(RewardsImpl.getDay(), BigInteger.ZERO),
                                                    prevTotalSupply,
                                                    lastUpdateTimeUs.getOrDefault(BigInteger.ZERO), 
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
        BigInteger currentUserWeight = userWeight.getOrDefault(user.toString(), BigInteger.ZERO);
        BigInteger totalWeight = updateTotalWeight(currentTime, prevTotalSupply);
        BigInteger accruedRewards = BigInteger.ZERO;
        //  If the user" +s current weight is less than the total, update their weight and issue rewards
        if (currentUserWeight.compareTo(totalWeight) == -1) {
            if (prevBalance.compareTo(BigInteger.ZERO) == 1) {
                accruedRewards = computeUserRewards(prevBalance, totalWeight, currentUserWeight);
                accruedRewards = accruedRewards.divide(EXA);
            }
            userWeight.set(user.toString(), totalWeight);
        }

        return accruedRewards;
    }

    public void setDay(BigInteger day){
        this.day.set(day);
    }

    public void setDistPercent(BigInteger distPercent) {
        this.distPercent.set(distPercent);
    }

    public BigInteger getValue() {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(contractAddress.get());
        return datasource.getBnusdValue(name.get());
    }

    public Map<String, Object> getDataAt(BigInteger day) {
        return Map.of(
            "day", day,
            "contract_address", contractAddress.get(),
            "dist_percent", distPercent.getOrDefault(BigInteger.ZERO),
            "precomp", precomp.getOrDefault(false),
            "offset", offset.getOrDefault(0),
            "total_value", totalValue.getOrDefault(day, BigInteger.ZERO),
            "total_dist", totalDist.getOrDefault(day, BigInteger.ZERO)
        );
    }

    public Map<String, Object> getData() {
        BigInteger day = this.day.get();
        return getDataAt(day);
    }

    public void distribute(int batchSize) {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(contractAddress.get());

        BigInteger day = this.day.get();
        String name = this.name.get();

        boolean precomputeDone = datasource.precompute(day.intValue(), batchSize);

        if (!precomp.getOrDefault(false) && precomputeDone) {
            precomp.set(true);
            BigInteger sourceTotalValue = datasource.getTotalValue(name, day.intValue());

            totalValue.set(day, sourceTotalValue);
        } 

        if (!precomp.get()) {
            return;
        }

        int offset = this.offset.getOrDefault(0);
        Map<Address, BigInteger> dataBatch = datasource.getDataBatch(name, day.intValue(), batchSize, offset);
        this.offset.set(offset + batchSize);
        if (dataBatch.isEmpty()) {
            this.day.set(day.add(BigInteger.ONE));
            this.offset.set(0);
            this.precomp.set(false);
        }

        BigInteger remaining = totalDist.get(day);
        BigInteger shares = totalValue.get(day);
        BigInteger originalShares = shares;

        BigInteger batchSum = BigInteger.ZERO;
        for(BigInteger value : dataBatch.values()) {
            batchSum = batchSum.add(value);
        }

        BigInteger tokenShare = BigInteger.ZERO;
        for (Address address : dataBatch.keySet()) {
            tokenShare = remaining.multiply(dataBatch.get(address)).divide(shares);
            Context.require(shares.compareTo(BigInteger.ZERO) == 1,  
                        RewardsImpl.TAG + ": zero or negative divisor for " + name + ", " +
                        "sum: " + batchSum + ", " +
                        "total: " + shares + ", " +
                        "remaining: " + remaining + ", " +
                        "token_share: " + tokenShare + ", " +
                        "starting: " + originalShares );
            
            remaining = remaining.subtract(tokenShare);
            shares = shares.subtract(dataBatch.get(address));
            BigInteger prevHoldings = RewardsImpl.balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO);
            RewardsImpl.balnHoldings.set(address.toString(), prevHoldings.add(tokenShare));
        }
    
        totalDist.set(day, remaining);
        totalValue.set(day, shares);
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

        BigInteger dayInMicroseconds = BigInteger.valueOf(U_SECONDS_DAY);
        BigInteger weightDelta = emission.multiply(timeDelta).multiply(EXA).divide(dayInMicroseconds.multiply(totalSupply));

        BigInteger newTotalWeight = previousTotalWeight.add(weightDelta);
        return newTotalWeight;
    }

    private BigInteger updateTotalWeight(BigInteger currentTime, BigInteger totalSupply) {
        BigInteger previousRunningTotal = totalWeight.getOrDefault(BigInteger.ZERO);
        BigInteger originalTotalWeight = previousRunningTotal;
        BigInteger lastUpdateTimestamp = lastUpdateTimeUs.getOrDefault(BigInteger.ZERO);
        BigInteger originalLastUpdateTimestamp = lastUpdateTimestamp;

        if (lastUpdateTimestamp.equals(BigInteger.ZERO)) {
            lastUpdateTimestamp = currentTime;
            lastUpdateTimeUs.set(currentTime);
        }

        if (currentTime.equals(lastUpdateTimestamp)) {
            return previousRunningTotal;
        }

        BigInteger dayInMicroseconds = BigInteger.valueOf(U_SECONDS_DAY);
        
        // Emit rewards based on the time delta * reward rate
        BigInteger startTimestampUs = BigInteger.valueOf(RewardsImpl.startTimestamp.get());
        BigInteger previousRewardsDay = BigInteger.ZERO;
        BigInteger previousDayEndUs = BigInteger.ZERO;

        BigInteger newTotal = BigInteger.ZERO;
        while (lastUpdateTimestamp.compareTo(currentTime) == -1) {
            previousRewardsDay = lastUpdateTimestamp.subtract(startTimestampUs).divide(dayInMicroseconds);
            previousDayEndUs = startTimestampUs.add(dayInMicroseconds.multiply(previousRewardsDay.add(BigInteger.ONE)));
            BigInteger endComputeTimestampUs = previousDayEndUs.min(currentTime);

            BigInteger emission = totalDist.getOrDefault(previousRewardsDay, BigInteger.ZERO);
            newTotal = computeTotalWeight(previousRunningTotal,
                                                     emission,
                                                     totalSupply,
                                                     lastUpdateTimestamp,
                                                     endComputeTimestampUs);
        
            previousRunningTotal = newTotal;
            lastUpdateTimestamp = endComputeTimestampUs;
        }
        
        if (newTotal.compareTo(originalTotalWeight) != -1) {
            totalWeight.set(newTotal);
        }

        if (currentTime.compareTo(originalLastUpdateTimestamp) == 1) {
            lastUpdateTimeUs.set(currentTime);
        } 

        return newTotal;
    }

    private BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger detlaWeight = totalWeight.subtract(userWeight);
        return detlaWeight.multiply(prevUserBalance);
    }

    @EventLog(indexed=2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {}
}