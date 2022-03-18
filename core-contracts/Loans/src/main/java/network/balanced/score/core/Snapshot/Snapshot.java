package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.ArrayDB;
import score.DictDB;
import score.BranchDB;
import score.Address;

import java.math.BigInteger;
import scorex.util.HashMap;
import java.util.Map;

import network.balanced.score.core.AssetDB;
import network.balanced.score.core.SnapshotDB;
import network.balanced.score.core.PositionsDB;

public class Snapshot {
    public VarDB<Integer> day;
    public VarDB<Long> time;
    public VarDB<BigInteger> totalMiningDebt;
    public DictDB<String, BigInteger> prices;
    public VarDB<Integer> preComputeIndex;
    public BranchDB<Integer, DictDB<String, BigInteger>> positionStates;
    public ArrayDB<Integer> mining;
    private String dbKey;

    public Snapshot(String key) {
        dbKey = key;
        day = (VarDB<Integer>)Context.newBranchDB("snap_day", Integer.class).at(dbKey);
        time = (VarDB<Long>)Context.newBranchDB("snap_time", Long.class).at(dbKey);
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
        HashMap<String, BigInteger> prices = new HashMap<String, BigInteger>(AssetDB.assetSymbols.size());
        for(int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (AssetDB.get(symbol).added.get() < time.getOrDefault(0l) && AssetDB.get(symbol).isActive()) {
                prices.put(symbol, this.prices.get(symbol));
            }
        }

        return Map.of(
            "snap_day", day.get(),
            "snap_time", time.getOrDefault(0l),
            "total_mining_debt", totalMiningDebt.getOrDefault(BigInteger.ZERO),
            "prices", prices,
            "mining_count", mining.size(),
            "precompute_index", preComputeIndex.getOrDefault(0),
            "add_to_nonzero_count", getAddNonzero().size(),
            "remove_from_nonzero_count", getRemoveNonzero().size()
        );
    }
}