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

package network.balanced.score.core.loans.snapshot;

import score.Context;
import score.VarDB;
import score.ArrayDB;
import score.DictDB;
import score.BranchDB;

import java.math.BigInteger;
import scorex.util.HashMap;
import java.util.Map;

import network.balanced.score.core.loans.asset.*;
import network.balanced.score.core.loans.linkedlist.*;

public class Snapshot {
    private final BranchDB<String, VarDB<Integer>> day = Context.newBranchDB("snap_day", Integer.class);
    private final BranchDB<String, VarDB<BigInteger>> snapshotTime = Context.newBranchDB("snap_time", BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalMiningDebt = Context.newBranchDB("total_mining_debt", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> prices = Context.newBranchDB("prices", BigInteger.class);
    private final BranchDB<String, VarDB<Integer>> preComputeIndex = Context.newBranchDB("precompute_index", Integer.class);
    private final BranchDB<String, BranchDB<Integer, DictDB<String, BigInteger>>> positionStates = Context.newBranchDB(
            "pos_state", BigInteger.class);
    private final BranchDB<String, ArrayDB<Integer>> mining = Context.newBranchDB("mining", Integer.class);

    private final String dbKey;

    Snapshot(String key) {
        dbKey = key;
    }

    public void setDay(Integer day) {
        this.day.at(dbKey).set(day);
    }

    public Integer getDay() {
        return day.at(dbKey).get();
    }

    public void setSnapshotTime(BigInteger time) {
        snapshotTime.at(dbKey).set(time);
    }

    private BigInteger getSnapshotTime() {
        return snapshotTime.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setTotalMiningDebt(BigInteger debt) {
        totalMiningDebt.at(dbKey).set(debt);
    }

    public BigInteger getTotalMiningDebt() {
        return totalMiningDebt.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setPrices(String symbol, BigInteger price) {
        prices.at(dbKey).set(symbol, price);
    }

    public BigInteger getPrices(String symbol) {
        return prices.at(dbKey).get(symbol);
    }

    public void setPreComputeIndex(Integer index) {
        preComputeIndex.at(dbKey).set(index);
    }

    public Integer getPreComputeIndex() {
        return preComputeIndex.at(dbKey).getOrDefault(0);
    }

    public BigInteger getPositionStates(Integer day, String state) {
        return positionStates.at(dbKey).at(day).getOrDefault(state, BigInteger.ZERO);
    }

    public DictDB<String, BigInteger> getAllPositionStates(Integer day) {
        return positionStates.at(dbKey).at(day);
    }

    public void addMining(Integer value) {
        mining.at(dbKey).add(value);
    }

    public Integer getMining(Integer index) {
        return mining.at(dbKey).get(index);
    }

    public Integer getMiningSize() {
        return mining.at(dbKey).size();
    }

    public LinkedListDB getAddNonzero() {
        return new LinkedListDB("add_to_nonzero", dbKey);
    }

    public LinkedListDB getRemoveNonzero() {
        return new LinkedListDB("remove_from_nonzero", dbKey);
    }

    public Map<String, Object> toMap() {
        Map<String, BigInteger> prices = new HashMap<>();

        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for(int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (AssetDB.getAsset(symbol).getAssetAddedTime().compareTo(getSnapshotTime()) < 0 && AssetDB.getAsset(symbol).isActive()) {
                prices.put(symbol, this.getPrices(symbol));
            }
        }

        Map<String, Object> snapData = new HashMap<>();
        snapData.put("snap_day", getDay());
        snapData.put("snap_time", getSnapshotTime());
        snapData.put("total_mining_debt", getTotalMiningDebt());
        snapData.put("prices", prices);
        snapData.put("mining_count", getMiningSize());
        snapData.put("precompute_index", getPreComputeIndex());
        snapData.put("add_to_nonzero_count", getAddNonzero().size());
        snapData.put("remove_from_nonzero_count", getRemoveNonzero().size());

        return snapData;
    }
}