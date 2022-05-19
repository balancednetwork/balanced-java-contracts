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

import static network.balanced.score.lib.utils.ArrayDBUtils.*;
import static network.balanced.score.core.loans.utils.LoansConstants.*;

public class Position {
    public VarDB<Integer> id; 
    public VarDB<BigInteger> created;
    public VarDB<Address> address;
    public ArrayDB<BigInteger> snaps;
    public BranchDB<BigInteger, DictDB<String, BigInteger>> assets;
    public DictDB<String, Boolean> dataMigrationStatus;
    public DictDB<String, BigInteger> collateral;
    public BranchDB<String,  DictDB<String, BigInteger>> loans;

    public Position(String dbKey) {
        id = (VarDB<Integer>)Context.newBranchDB("id", Integer.class).at(dbKey);
        created = (VarDB<BigInteger>)Context.newBranchDB("created", BigInteger.class).at(dbKey);
        address = (VarDB<Address>)Context.newBranchDB("address", Address.class).at(dbKey);
        snaps = (ArrayDB<BigInteger>)Context.newBranchDB("snaps", BigInteger.class).at(dbKey);
        assets = (BranchDB<BigInteger, DictDB<String, BigInteger>>)Context.newBranchDB("assets", BigInteger.class).at(dbKey);
        dataMigrationStatus = (DictDB<String, Boolean>)Context.newBranchDB("data_migration _status", Boolean.class).at(dbKey);
        collateral = (DictDB<String, BigInteger>)Context.newBranchDB("loan_balance", BigInteger.class).at(dbKey);
        loans = (BranchDB<String,  DictDB<String, BigInteger>>)Context.newBranchDB("collateral_balance", BigInteger.class).at(dbKey);
        
    }

    public BigInteger get(String symbol) {
        Context.require(arrayDbContains(AssetDB.assetSymbols, symbol), symbol + "is not a supported asset on Balanced.");
        
        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0 || !dataMigrationStatus.getOrDefault(symbol, false) ) {
            return assets.at(lastSnap()).getOrDefault(symbol, BigInteger.ZERO);
        }

        if (AssetDB.get(symbol).isCollateral()) {
            return collateral.get(symbol);
        };

        return loans.at("sICX").get(symbol);

    }
    
    public void set(String symbol, BigInteger value) {
        BigInteger previousDebt;
        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0) {
            BigInteger day = checkSnap();
            previousDebt = assets.at(day).getOrDefault(symbol, BigInteger.ZERO);
            assets.at(day).set(symbol, value);
        } else {
            previousDebt = loans.at("sICX").getOrDefault(symbol, BigInteger.ZERO);
        }

        if (symbol == "sICX") {
            collateral.set("sICX", value);
        } else {
            loans.at("sICX").set(symbol, value);
        }
     
        if (!dataMigrationStatus.getOrDefault(symbol, false)) {
            dataMigrationStatus.set(symbol, true);
        }

        if (arrayDbContains(AssetDB.activeAssets, symbol)) {
            BigInteger previousTotalDebt = LoansImpl.totalDebts.getOrDefault(symbol, BigInteger.ZERO);
            BigInteger newTotalDebt = previousTotalDebt.subtract(previousDebt).add(value);
            LoansImpl.totalDebts.set(symbol, newTotalDebt);
            
            AssetDB.get(symbol).getBorrowers().set(id.get(), value);
        }
    }

    private BigInteger lastSnap() {
        return snaps.get(snaps.size() - 1);
    }

    public BigInteger checkSnap() {
        Context.require(LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0, continuousRewardsErrorMessage);
        BigInteger day = LoansImpl._getDay();
        BigInteger lastDay = lastSnap();
        if (day.compareTo(lastDay) <= 0) {
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

    public BigInteger getSnapshotId(BigInteger day) {
        if (day.compareTo(BigInteger.ZERO) < 0) {
            int index = day.intValue() + snaps.size();
            Context.require(index >= 0, "Snapshot index " + day + " out of range.");
            return snaps.get(index);
        }

        BigInteger low = BigInteger.ZERO;
        BigInteger high = BigInteger.valueOf(snaps.size());
        BigInteger middle;
        while (low.compareTo(high) < 0) {
            middle = low.add(high).divide(BigInteger.TWO);
            if (snaps.get(middle.intValue()).compareTo(day) > 0) {
                high = middle;
            } else {
                low = middle.add(BigInteger.ONE);
            }
        }

        if (snaps.get(0).equals(day)) {
            return day;
        } else if (low.equals(BigInteger.ZERO)) {
            return BigInteger.valueOf(-1);
        }

        return snaps.get(low.subtract(BigInteger.ONE).intValue());    
    }
    

    public boolean hasDebt(BigInteger day) {
        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0 ) {
            BigInteger id = getSnapshotId(day);
            if (id.compareTo(BigInteger.ZERO) < 0) {
                return false;
            }
    
            for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
                String symbol = AssetDB.activeAssets.get(i);
                BigInteger debt = assets.at(id).getOrDefault(symbol, BigInteger.ZERO);
                if (!debt.equals(BigInteger.ZERO)) {
                   return true;
                }
            }
    
            return false;
        }

        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol = AssetDB.activeAssets.get(i);
            if (!dataMigrationStatus.get(symbol)) {
                if (!assets.at(lastSnap()).getOrDefault(symbol, BigInteger.ZERO).equals(BigInteger.ZERO)) {
                    return true;
                }
            } else {
                if(!loans.at("sICX").getOrDefault(symbol, BigInteger.ZERO).equals(BigInteger.ZERO)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public BigInteger totalCollateral(BigInteger day) {
        BigInteger value = BigInteger.ZERO;

        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0 ) {
            BigInteger id = getSnapshotId(day);
            if (id.compareTo(BigInteger.ZERO) < 0) {
                return value;
            }
            
            for (int i = 0; i < AssetDB.activeCollateral.size(); i++) {
                String symbol =  AssetDB.activeCollateral.get(i);
                Asset asset = AssetDB.get(symbol);
                BigInteger amount = assets.at(id).get(symbol);
                BigInteger price;
                if (day.equals(BigInteger.valueOf(-1)) || day.equals(LoansImpl._getDay())) {
                    price = asset.priceInLoop();
                } else {
                    price = SnapshotDB.get(day).prices.getOrDefault(symbol, BigInteger.ZERO);
                }

                value = value.add(amount.multiply(price).divide(EXA));
            }

            return value;
        }

        for (int i = 0; i < AssetDB.activeCollateral.size(); i++) {
            String symbol =  AssetDB.activeCollateral.get(i);
            Asset asset = AssetDB.get(symbol);
            
            BigInteger amount = get(symbol);
            BigInteger price;
            if (day.equals(BigInteger.valueOf(-1)) || day.equals(LoansImpl._getDay())) {
                price = asset.priceInLoop();
            } else {
                price = SnapshotDB.get(day).prices.getOrDefault(symbol, BigInteger.ZERO);
            }

            value = value.add(amount.multiply(price).divide(EXA));
        }

        return value;

    }

    public BigInteger totalDebt(BigInteger day, boolean readOnly) {
        BigInteger value = BigInteger.ZERO;

        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0 ) {
            BigInteger id = getSnapshotId(day);
            if (id.compareTo(BigInteger.ZERO) < 0) {
                return value;
            }   
            for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
                String symbol =  AssetDB.activeAssets.get(i);
                BigInteger amount = assets.at(id).getOrDefault(symbol, BigInteger.ZERO);
                BigInteger price = BigInteger.ZERO;
                if (amount.compareTo(BigInteger.ZERO) > 0) {
                    if (day.equals(BigInteger.valueOf(-1)) || day.equals(LoansImpl._getDay())) {
                        price = getAssetPrice(symbol, readOnly);
                    } else {
                        price = SnapshotDB.get(day).prices.getOrDefault(symbol, BigInteger.ZERO);
                    }
                }

                value = value.add(amount.multiply(price).divide(EXA));
            }

            return value;
        }
        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol =  AssetDB.activeAssets.get(i);
            BigInteger amount = get(symbol);
            BigInteger price = BigInteger.ZERO;
            if (amount.compareTo(BigInteger.ZERO) > 1) {
                if (day.equals(BigInteger.valueOf(-1)) || day.equals(LoansImpl._getDay())) {
                    price = getAssetPrice(symbol, readOnly);
                } else {
                    price = SnapshotDB.get(day).prices.getOrDefault(symbol, BigInteger.ZERO);
                }
            }

            value = value.add(amount.multiply(price).divide(EXA));
        }

        return value;
    }


    public Standing getStanding(BigInteger day, Boolean readOnly) {
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
        if (LoansImpl._getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0) {
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

    public Standings updateStanding(BigInteger day) {
        Context.require(day.compareTo(LoansImpl.continuousRewardDay.get()) < 0, continuousRewardsErrorMessage);

        Standing standing = getStanding(day, false);
        DictDB<String, BigInteger> state = SnapshotDB.get(day).positionStates.at(id.get());
        state.set("total_debt", standing.totalDebt);
        state.set("ratio", standing.ratio);
        state.set("standing", BigInteger.valueOf(standing.standing.ordinal()));
        return standing.standing;
    }

    public Map<String, Object> toMap(BigInteger day) {
        BigInteger index = SnapshotDB.getSnapshotId(day);
        if (index.equals(BigInteger.valueOf(-1)) || day.compareTo(LoansImpl._getDay()) > 0) {
            return Map.of();
        }

        HashMap<String, BigInteger> assetAmounts = new HashMap<String, BigInteger>(AssetDB.assetSymbols.size());
        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.get(symbol);
            if (!asset.isActive()) {
                continue;
            }

            BigInteger amount = BigInteger.ZERO;
            if (day.equals(BigInteger.valueOf(-1))) {
                amount = get(symbol);
            } else {
                amount = assets.at(index).getOrDefault(symbol, BigInteger.ZERO);
            }
        
             if (!amount.equals(BigInteger.ZERO)) {
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
