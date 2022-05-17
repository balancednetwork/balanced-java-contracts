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
    public VarDB<BigInteger> day;
    public VarDB<BigInteger> time;
    public VarDB<BigInteger> totalMiningDebt;
    public DictDB<String, BigInteger> prices;
    public VarDB<Integer> preComputeIndex;
    public BranchDB<Integer, DictDB<String, BigInteger>> positionStates;
    public ArrayDB<Integer> mining;
    private final String dbKey;

    Snapshot(String key) {
        dbKey = key;
        day = (VarDB<BigInteger>)Context.newBranchDB("snap_day", BigInteger.class).at(dbKey);
        time = (VarDB<BigInteger>)Context.newBranchDB("snap_time", BigInteger.class).at(dbKey);
        totalMiningDebt = (VarDB<BigInteger>)Context.newBranchDB("total_mining_debt", BigInteger.class).at(dbKey);
        prices = (DictDB<String, BigInteger>)Context.newBranchDB("prices", BigInteger.class).at(dbKey);
        preComputeIndex = (VarDB<Integer>)Context.newBranchDB("precompute_index", Integer.class).at(dbKey);
        positionStates = (BranchDB<Integer, DictDB<String, BigInteger>>)Context.newBranchDB("pos_state", BigInteger.class).at(dbKey);
        mining = (ArrayDB<Integer>) Context.newBranchDB("mining", Integer.class).at(dbKey);
    }

    public LinkedListDB getAddNonzero() {
        return new LinkedListDB("add_to_nonzero", dbKey);
    }

    public LinkedListDB getRemoveNonzero() {
        return new LinkedListDB("remove_from_nonzero", dbKey);
    }

    public Map<String, Object> toMap() {
        Map<String, BigInteger> prices = new HashMap<>();
        for(int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (AssetDB.getAsset(symbol).getAssetAddedTime().compareTo(time.getOrDefault(BigInteger.ZERO)) < 0 && AssetDB.getAsset(symbol).isActive()) {
                prices.put(symbol, this.prices.get(symbol));
            }
        }

        return Map.of(
            "snap_day", day.get(),
            "snap_time", time.getOrDefault(BigInteger.ZERO),
            "total_mining_debt", totalMiningDebt.getOrDefault(BigInteger.ZERO),
            "prices", prices,
            "mining_count", mining.size(),
            "precompute_index", preComputeIndex.getOrDefault(0),
            "add_to_nonzero_count", getAddNonzero().size(),
            "remove_from_nonzero_count", getRemoveNonzero().size()
        );
    }
}