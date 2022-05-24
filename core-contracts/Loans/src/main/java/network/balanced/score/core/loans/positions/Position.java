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

package network.balanced.score.core.loans.positions;

import network.balanced.score.core.loans.LoansImpl;
import network.balanced.score.core.loans.asset.Asset;
import network.balanced.score.core.loans.asset.AssetDB;
import network.balanced.score.core.loans.snapshot.SnapshotDB;
import network.balanced.score.core.loans.utils.Standing;
import network.balanced.score.core.loans.utils.Token;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static java.util.Map.entry;
import static network.balanced.score.core.loans.LoansVariables.*;
import static network.balanced.score.core.loans.utils.Checks.isContinuousRewardsActivated;
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;

public class Position {

    static final String TAG = "BalancedLoansPositions";
    private final BranchDB<String, VarDB<Integer>> id = Context.newBranchDB("id", Integer.class);
    private final BranchDB<String, VarDB<BigInteger>> created = Context.newBranchDB("created", BigInteger.class);
    private final BranchDB<String, VarDB<Address>> address = Context.newBranchDB("address", Address.class);
    private final BranchDB<String, ArrayDB<Integer>> snaps = Context.newBranchDB("snaps", Integer.class);
    private final BranchDB<String, BranchDB<Integer, DictDB<String, BigInteger>>> assets = Context.newBranchDB("assets",
            BigInteger.class);
    private final BranchDB<String, DictDB<String, Boolean>> dataMigrationStatus = Context.newBranchDB("data_migration " +
            "_status", Boolean.class);
    private final BranchDB<String, BranchDB<String, DictDB<String, BigInteger>>> loansPosition = Context.newBranchDB(
            "loan_balance", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> collateralPosition = Context.newBranchDB("collateral_balance"
            , BigInteger.class);

    private final String dbKey;

    Position(String dbKey) {
        this.dbKey = dbKey;
    }

    void setId(Integer id) {
        this.id.at(dbKey).set(id);
    }

    public Integer getId() {
        return id.at(dbKey).get();
    }

    void setCreated(BigInteger time) {
        created.at(dbKey).set(time);
    }

    private BigInteger getCreated() {
        return created.at(dbKey).get();
    }

    public void setAddress(Address address) {
        this.address.at(dbKey).set(address);
    }

    public Address getAddress() {
        return address.at(dbKey).get();
    }

    void addSnaps(Integer value) {
        snaps.at(dbKey).add(value);
    }

    Integer getSnaps(Integer index) {
        return snaps.at(dbKey).get(index);
    }

    private Integer getSnapsSize() {
        return snaps.at(dbKey).size();
    }

    public BigInteger getAssets(Integer snapID, String symbol) {
        return assets.at(dbKey).at(snapID).getOrDefault(symbol, BigInteger.ZERO);
    }

    void setAssets(Integer snapID, String symbol, BigInteger value) {
        assets.at(dbKey).at(snapID).set(symbol, value);
    }

    public void setDataMigrationStatus(String symbol, Boolean value) {
        dataMigrationStatus.at(dbKey).set(symbol, value);
    }

    public Boolean getDataMigrationStatus(String symbol) {
        return dataMigrationStatus.at(dbKey).getOrDefault(symbol, false);
    }

    public void setLoansPosition(String collateral, String symbol, BigInteger value) {
        loansPosition.at(dbKey).at(collateral).set(symbol, value);
    }

    public BigInteger getLoansPosition(String collateral, String symbol) {
        return loansPosition.at(dbKey).at(collateral).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setCollateralPosition(String symbol, BigInteger value) {
        collateralPosition.at(dbKey).set(symbol, value);
    }

    public BigInteger getCollateralPosition(String symbol) {
        return collateralPosition.at(dbKey).getOrDefault(symbol, BigInteger.ZERO);
    }

    public BigInteger getAssetPosition(String symbol) {
        Context.require(arrayDbContains(AssetDB.assetSymbols, symbol), TAG + ": " + symbol + " is not a supported " +
                "asset on Balanced.");

        if (!isContinuousRewardsActivated() || !getDataMigrationStatus(symbol)) {
            return getAssets(lastSnap(), symbol);
        }

        Asset asset = AssetDB.getAsset(symbol);
        Context.require(asset.isActive(), TAG + ": " + symbol + " is not an active asset on Balanced.");

        if (asset.isCollateral()) {
            return getCollateralPosition(symbol);
        } else {
            return getLoansPosition(SICX_SYMBOL, symbol);
        }
    }

    public void setAssetPosition(String symbol, BigInteger value) {
        if (!isContinuousRewardsActivated()) {
            BigInteger day = checkSnap();
            setAssets(day.intValue(), symbol, value);
        } else {
            if (symbol.equals(SICX_SYMBOL)) {
                setCollateralPosition(SICX_SYMBOL, value);
            } else {
                setLoansPosition(SICX_SYMBOL, symbol, value);
            }
            setDataMigrationStatus(symbol, true);
        }

        if (arrayDbContains(AssetDB.activeAssets, symbol)) {
            AssetDB.getAsset(symbol).getBorrowers().set(getId(), value);
        }
    }

    private Integer lastSnap() {
        return getSnaps(getSnapsSize() - 1);
    }

    private BigInteger checkSnap() {
        Context.require(!isContinuousRewardsActivated(), continuousRewardsErrorMessage);
        BigInteger day = LoansImpl._getDay();
        int lastDay = lastSnap();
        if (day.intValue() <= lastDay) {
            return day;
        }

        addSnaps(day.intValue());
        int previous = getSnaps(getSnapsSize() - 2);
        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (!getAssets(lastDay, symbol).equals(BigInteger.ZERO)) {
                BigInteger value = getAssets(previous, symbol);
                setAssets(day.intValue(), symbol, value);
            }
        }
        return day;
    }

    public Integer getSnapshotId(Integer day) {
        if (day < 0) {
            int index = day + getSnapsSize();
            if (index < 0) {
                return -1;
            }
            return getSnaps(index);
        }

        int low = 0;
        int high = getSnapsSize();
        int middle;
        while (low < high) {
            middle = (low + high) / 2;
            if (getSnaps(middle) > day) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        if (getSnaps(0) == day.intValue()) {
            return day;
        } else if (low == 0) {
            return -1;
        }

        return getSnaps(low - 1);
    }


    public boolean hasDebt(Integer day) {
        if (!isContinuousRewardsActivated()) {
            int id = getSnapshotId(day);
            if (id == -1) {
                return false;
            }

            int activeAssetsCount = AssetDB.activeAssets.size();
            for (int i = 0; i < activeAssetsCount; i++) {
                String symbol = AssetDB.activeAssets.get(i);
                BigInteger debt = getAssets(id, symbol);
                if (!debt.equals(BigInteger.ZERO)) {
                    return true;
                }
            }

            return false;
        }

        int activeAssetsCount = AssetDB.activeAssets.size();
        for (int i = 0; i < activeAssetsCount; i++) {
            String symbol = AssetDB.activeAssets.get(i);
            if (getDataMigrationStatus(symbol)) {
                if (!getLoansPosition(SICX_SYMBOL, symbol).equals(BigInteger.ZERO)) {
                    return true;
                }
            } else {
                if (!getAssets(lastSnap(), symbol).equals(BigInteger.ZERO)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the total value of the total collateral in loop
     *
     * @param day Day for which the total collateral sum has to be read
     * @return Total collateral value
     */
    public BigInteger totalCollateral(Integer day) {
        BigInteger totalCollateral = BigInteger.ZERO;

        if (!isContinuousRewardsActivated()) {
            int id = getSnapshotId(day);
            if (id == -1) {
                return totalCollateral;
            }

            int activeCollateralCount = AssetDB.activeCollateral.size();
            for (int i = 0; i < activeCollateralCount; i++) {
                String symbol = AssetDB.activeCollateral.get(i);
                Asset asset = AssetDB.getAsset(symbol);

                Address assetAddress = asset.getAssetAddress();
                Token assetContract = new Token(assetAddress);

                BigInteger amount = getAssets(id, symbol);
                BigInteger price;
                if (day == -1 || day == LoansImpl._getDay().intValue()) {
                    price = assetContract.priceInLoop();
                } else {
                    price = SnapshotDB.get(day).getPrices(symbol);
                }

                totalCollateral = totalCollateral.add(amount.multiply(price).divide(EXA));
            }

            return totalCollateral;
        }

        int activeCollateralCount = AssetDB.activeCollateral.size();
        for (int i = 0; i < activeCollateralCount; i++) {
            String symbol = AssetDB.activeCollateral.get(i);
            Asset asset = AssetDB.getAsset(symbol);

            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);

            BigInteger amount;
            if (getDataMigrationStatus(symbol) && day == -1) {
                amount = getCollateralPosition(symbol);
            } else {
                amount = getAssets(lastSnap(), symbol);
            }
            BigInteger price;
            if (day == -1 || day == LoansImpl._getDay().intValue()) {
                price = assetContract.priceInLoop();
            } else {
                price = SnapshotDB.get(day).getPrices(symbol);
            }

            totalCollateral = totalCollateral.add(amount.multiply(price).divide(EXA));
        }

        return totalCollateral;

    }

    /**
     * Returns the total value of all outstanding debt in loop. Only valid for updated positions.
     *
     * @param day      Day for which total debt required
     * @param readOnly True if the price has to be updated in token contract
     * @return Total debt in loop
     */
    public BigInteger totalDebt(Integer day, boolean readOnly) {
        BigInteger totalDebt = BigInteger.ZERO;

        if (!isContinuousRewardsActivated()) {
            int id = getSnapshotId(day);
            if (id == -1) {
                return totalDebt;
            }

            int activeAssetsCount = AssetDB.activeAssets.size();
            for (int i = 0; i < activeAssetsCount; i++) {
                String symbol = AssetDB.activeAssets.get(i);
                BigInteger amount = getAssets(id, symbol);
                BigInteger price = BigInteger.ZERO;
                if (amount.compareTo(BigInteger.ZERO) > 0) {
                    if (day == -1 || day == LoansImpl._getDay().intValue()) {
                        price = getAssetPrice(symbol, readOnly);
                    } else {
                        price = SnapshotDB.get(day).getPrices(symbol);
                    }
                }

                totalDebt = totalDebt.add(amount.multiply(price).divide(EXA));
            }

            return totalDebt;
        }

        int activeAssetsCount = AssetDB.activeAssets.size();
        for (int i = 0; i < activeAssetsCount; i++) {
            String symbol = AssetDB.activeAssets.get(i);

            BigInteger amount;
            if (getDataMigrationStatus(symbol) && day == -1) {
                amount = getLoansPosition(SICX_SYMBOL, symbol);
            } else {
                amount = getAssets(lastSnap(), symbol);
            }

            BigInteger price = BigInteger.ZERO;
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                if (day == -1 || day == LoansImpl._getDay().intValue()) {
                    price = getAssetPrice(symbol, readOnly);
                } else {
                    price = SnapshotDB.get(day).getPrices(symbol);
                }
            }
            totalDebt = totalDebt.add(amount.multiply(price).divide(EXA));
        }

        return totalDebt;
    }


    /**
     * Calculates the standing for a position. Uses the readonly method for asset prices if the _readonly flag is True.
     *
     * @param day      Day for which the standing has to be calculated
     * @param readOnly True if the price is not to be updated
     * @return Total standing for a day
     */
    public Standing getStanding(Integer day, Boolean readOnly) {
        Standing standing = new Standing();
        standing.totalDebt = totalDebt(day, readOnly);
        standing.collateral = totalCollateral(day);

        if (standing.totalDebt.equals(BigInteger.ZERO)) {
            standing.ratio = BigInteger.ZERO;
            if (standing.collateral.equals(BigInteger.ZERO)) {
                standing.standing = Standings.ZERO;
                return standing;
            }

            standing.standing = Standings.NO_DEBT;
            return standing;
        }

        standing.ratio = standing.collateral.multiply(EXA).divide(standing.totalDebt);

        if (!isContinuousRewardsActivated()) {
            if (standing.ratio.compareTo(miningRatio.get().multiply(EXA).divide(POINTS)) > 0) {
                BigInteger assetPrice;
                if (day == -1 || day == LoansImpl._getDay().intValue()) {
                    assetPrice = getAssetPrice(BNUSD_SYMBOL, readOnly);
                } else {
                    assetPrice = SnapshotDB.get(day).getPrices(BNUSD_SYMBOL);
                }

                BigInteger bnusdDebt = standing.totalDebt.multiply(EXA).divide(assetPrice);
                if (bnusdDebt.compareTo(minMiningDebt.get()) < 0) {
                    standing.standing = Standings.NOT_MINING;
                } else {
                    standing.standing = Standings.MINING;
                }
            } else if (standing.ratio.compareTo(lockingRatio.get().multiply(EXA).divide(POINTS)) > 0) {
                standing.standing = Standings.NOT_MINING;
            } else if (standing.ratio.compareTo(liquidationRatio.get().multiply(EXA).divide(POINTS)) > 0) {
                standing.standing = Standings.LOCKED;
            } else {
                standing.standing = Standings.LIQUIDATE;
            }
        } else {
            if (standing.ratio.compareTo(liquidationRatio.get().multiply(EXA).divide(POINTS)) > 0) {
                standing.standing = Standings.MINING;
            } else {
                standing.standing = Standings.LIQUIDATE;
            }
        }

        return standing;
    }

    public Standings updateStanding(Integer day) {
        Context.require(!isContinuousRewardsActivated(), continuousRewardsErrorMessage);

        DictDB<String, BigInteger> state = SnapshotDB.get(day).getAllPositionStates(getId());
        Standing standing = getStanding(day, false);
        state.set("total_debt", standing.totalDebt);
        state.set("ratio", standing.ratio);
        state.set("standing", BigInteger.valueOf(standing.standing.ordinal()));
        return standing.standing;
    }

    public Map<String, Object> toMap(Integer day) {
        int index = SnapshotDB.getSnapshotId(day);
        if (index == -1 || day > LoansImpl._getDay().intValue()) {
            return Map.of();
        }

        Map<String, BigInteger> assetAmounts = new HashMap<>();
        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.getAsset(symbol);
            if (!asset.isActive()) {
                continue;
            }

            BigInteger amount;
            if (getDataMigrationStatus(symbol) && day == -1) {
                if (symbol.equals(SICX_SYMBOL)) {
                    amount = getCollateralPosition(symbol);
                } else {
                    amount = getLoansPosition(SICX_SYMBOL, symbol);
                }
            } else {
                amount = getAssets(index, symbol);
            }

            if (amount.compareTo(BigInteger.ZERO) > 0) {
                assetAmounts.put(symbol, amount);
            }
        }

        Standing standing = getStanding(index, true);
        return Map.ofEntries(
                entry("pos_id", getId()),
                entry("created", getCreated()),
                entry("address", getAddress().toString()),
                entry("snap_id", index),
                entry("snaps_length", getSnapsSize()),
                entry("last_snap", lastSnap()),
                entry("first day", getSnaps(0)),
                entry("assets", assetAmounts),
                entry("total_debt", standing.totalDebt),
                entry("collateral", standing.collateral),
                entry("ratio", standing.ratio),
                entry("standing", StandingsMap.get(standing.standing))
        );
    }

    private BigInteger getAssetPrice(String symbol, Boolean readOnly) {
        Asset asset = AssetDB.getAsset(symbol);
        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);
        if (readOnly) {
            return assetContract.lastPriceInLoop();
        } else {
            return assetContract.priceInLoop();
        }
    } 
}
