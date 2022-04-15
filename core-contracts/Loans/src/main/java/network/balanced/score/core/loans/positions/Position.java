package network.balanced.score.core.loans.positions;

import score.Context;
import score.VarDB;
import score.ArrayDB;
import score.DictDB;
import score.BranchDB;
import score.Address;

import java.util.Map;
import static java.util.Map.entry;
import scorex.util.HashMap;
import java.math.BigInteger;

import network.balanced.score.core.loans.asset.*;
import network.balanced.score.core.loans.LoansImpl;
import network.balanced.score.core.loans.snapshot.*;
import network.balanced.score.core.loans.utils.Standing;

import static network.balanced.score.core.loans.utils.DBHelpers.*;
import static network.balanced.score.core.loans.utils.Constants.*;

public class Position {
    public VarDB<Integer> id; 
    public VarDB<Long> created;
    public VarDB<Address> address;
    public ArrayDB<Integer> snaps;
    public BranchDB<Integer, DictDB<String, BigInteger>> assets;

    public Position(String dbKey) {
        id = (VarDB<Integer>)Context.newBranchDB("id", Integer.class).at(dbKey);
        created = (VarDB<Long>)Context.newBranchDB("created", Long.class).at(dbKey);
        address = (VarDB<Address>)Context.newBranchDB("address", Address.class).at(dbKey);
        snaps = (ArrayDB<Integer>)Context.newBranchDB("snaps", Integer.class).at(dbKey);
        assets = (BranchDB<Integer, DictDB<String, BigInteger>>)Context.newBranchDB("assets", BigInteger.class).at(dbKey);
    }

    public BigInteger get(String symbol) {
        Context.require(contains(AssetDB.assetSymbols, symbol), symbol + "is not a supported asset on Balanced.");
        return assets.at(lastSnap()).getOrDefault(symbol, BigInteger.ZERO);
    }
    
    public void set(String symbol, BigInteger value) {
        if (LoansImpl._getDay() < LoansImpl.continuousRewardDay.get()) {
            int day = checkSnap();
            assets.at(day).set(symbol, value);
        } else {
            assets.at(lastSnap()).set(symbol, value);
        }

        if (contains(AssetDB.activeAssets, symbol)) {
            AssetDB.get(symbol).getBorrowers().set(id.get(), value);
        }
    }

    private int lastSnap() {
        return snaps.get(snaps.size() - 1);
    }

    public int checkSnap() {
        int day = LoansImpl._getDay();
        int lastDay = lastSnap();
        if (day <= lastDay) {
            return day;
        }

        snaps.add(day);
        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol =  AssetDB.assetSymbols.get(i);
            if (!assets.at(lastDay).getOrDefault(symbol, BigInteger.ZERO).equals(BigInteger.ZERO)) {
                BigInteger value = assets.at(lastDay).get(symbol);
                assets.at(day).set(symbol, value);
            }
        }
    
        return day;
    }

    public int getSnapshotId(int day) {
        if (day < 0) {
            int index = day + snaps.size();
            Context.require(index >= 0, "Snapshot index " + day + " out of range.");
            return snaps.get(index);
        }

        int low = 0;
        int high = snaps.size();
        int middle;
        while (low < high) {
            middle = (low + high) / 2;
            if (snaps.get(middle) > day) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        if (snaps.get(0) == day) {
            return day;
        } else if (low == 0) {
            return -1;
        }

        return snaps.get(low - 1);    
    }
    

    public boolean hasDebt(int day) {
        int id = getSnapshotId(day);
        if (id == -1) {
            return false;
        }

        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol =  AssetDB.activeAssets.get(i);
            BigInteger debt = assets.at(id).getOrDefault(symbol, BigInteger.ZERO);
            if (!debt.equals(BigInteger.ZERO)) {
               return true;
            }
        }

        return false;
    }

    public BigInteger totalCollateral(int day) {
        int id = getSnapshotId(day);
        BigInteger value = BigInteger.ZERO;
        if (id == -1) {
            return value;
        }
        
        for (int i = 0; i < AssetDB.activeCollateral.size(); i++) {
            String symbol =  AssetDB.activeCollateral.get(i);
            Asset asset = AssetDB.get(symbol);
            BigInteger amount = assets.at(id).get(symbol);
            BigInteger price;
            if (day == -1 || day == LoansImpl._getDay()) {
                price = asset.priceInLoop();
            } else {
                price = SnapshotDB.get(day).prices.get(symbol);
            }

            value = value.add(amount.multiply(price).divide(EXA));
        }

        return value;
    }

    public BigInteger totalDebt(int day, boolean readOnly) {
        int id = getSnapshotId(day);
        BigInteger value = BigInteger.ZERO;
        if (id == -1) {
            return value;
        }
        
        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol =  AssetDB.activeAssets.get(i);
            BigInteger amount = assets.at(id).getOrDefault(symbol, BigInteger.ZERO);
            BigInteger price = BigInteger.ZERO;
            if (amount.compareTo(BigInteger.ZERO) == 1) {
                if (day == -1 || day == LoansImpl._getDay()) {
                    price = getAssetPrice(symbol, readOnly);
                } else {
                    price = SnapshotDB.get(day).prices.get(symbol);
                }
            }

            value = value.add(amount.multiply(price).divide(EXA));
        }

        return value;
    }


    public Standing getStanding(int day, Boolean readOnly) {
        Standing standing = new Standing();
        standing.totalDebt = totalDebt(day, readOnly);
        standing.collateral = totalCollateral(day);
        
        if (standing.totalDebt.equals(BigInteger.valueOf(0))) {
            standing.ratio = BigInteger.ZERO;
            if (standing.collateral.equals(BigInteger.valueOf(0))) {
                standing.standing = Standings.ZERO;
                return standing;
            }

            standing.standing = Standings.NO_DEBT;
            return standing;
        }

        standing.ratio = standing.collateral.multiply(EXA).divide(standing.totalDebt);
        if (LoansImpl._getDay() < LoansImpl.continuousRewardDay.get()) {
            if (standing.ratio.compareTo(LoansImpl.miningRatio.get().multiply(EXA).divide(POINTS)) == 1) {
                BigInteger assetPrice = getAssetPrice("bnUSD", readOnly);
                BigInteger bnusdDebt = standing.totalDebt.multiply(EXA).divide(assetPrice);
                if (bnusdDebt.compareTo(LoansImpl.minMiningDebt.get()) == -1) {
                    standing.standing = Standings.NOT_MINING;
                } else {
                    standing.standing = Standings.MINING;
                }
            } else if (standing.ratio.compareTo(LoansImpl.lockingRatio.get().multiply(EXA).divide(POINTS)) == 1) {
                standing.standing = Standings.NOT_MINING;
            } else if (standing.ratio.compareTo(LoansImpl.liquidationRatio.get().multiply(EXA).divide(POINTS)) == 1) {
                standing.standing = Standings.LOCKED;                
            } else {
                standing.standing = Standings.LIQUIDATE;
            }
        } else {
            if (standing.ratio.compareTo(LoansImpl.liquidationRatio.get().multiply(EXA).divide(POINTS)) == 1) {
                    standing.standing = Standings.MINING;
            } else {
                    standing.standing = Standings.LIQUIDATE;
            }
        }

        return standing;
    }

    public Standings updateStanding(int day) {
        Standing standing = getStanding(day, false);
        DictDB<String, BigInteger> state = SnapshotDB.get(day).positionStates.at(id.get());
        state.set("total_debt", standing.totalDebt);
        state.set("ratio", standing.ratio);
        state.set("standing", BigInteger.valueOf(standing.standing.ordinal()));
        return standing.standing;
    }

    public Map<String, Object> toMap(int day) {
        int index = SnapshotDB.getSnapshotId(day);
        if (index == -1 || day > LoansImpl._getDay()) {
            return Map.of();
        }

        HashMap<String, BigInteger> assetAmounts = new HashMap<String, BigInteger>(AssetDB.assetSymbols.size());
        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            
            BigInteger amount = assets.at(index).getOrDefault(symbol, null);
            if (amount != null) {
                assetAmounts.put(symbol, amount);
            }
        }

        Standing standing = getStanding(index, true);
        return Map.ofEntries(
            entry("pos_id", id.get()),
            entry("created",  created.get()),
            entry("address", address.get()),
            entry("snap_id", index),
            entry("snaps_length", snaps.size()),
            entry("last_snap", snaps.get(snaps.size() - 1)),
            entry("first day", snaps.get(0)),
            entry("assets", assetAmounts),
            entry("total_debt", standing.totalDebt),
            entry("collateral", standing.collateral),
            entry("ratio", standing.ratio),
            entry("standing", StandingsMap.get(standing.standing))
        );
    }

    private BigInteger getAssetPrice(String symbol, Boolean readOnly) {
        Asset asset = AssetDB.get(symbol);
        if (readOnly) {
            return asset.lastPriceInLoop();
        } else {
            return asset.priceInLoop();
        }
    }
}
