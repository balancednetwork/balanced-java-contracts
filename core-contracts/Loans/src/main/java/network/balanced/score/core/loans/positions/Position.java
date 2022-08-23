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

import network.balanced.score.core.loans.LoansVariables;
import network.balanced.score.core.loans.asset.Asset;
import network.balanced.score.core.loans.asset.AssetDB;
import network.balanced.score.core.loans.collateral.Collateral;
import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.utils.Standing;
import network.balanced.score.core.loans.utils.Token;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.LoansVariables.*;
import static network.balanced.score.core.loans.utils.LoansConstants.*;

public class Position {
    static final String TAG = "BalancedLoansPositions";

    private final BranchDB<String, VarDB<Integer>> id = Context.newBranchDB("id", Integer.class);
    private final BranchDB<String, VarDB<BigInteger>> created = Context.newBranchDB("created", BigInteger.class);
    private final BranchDB<String, VarDB<Address>> address = Context.newBranchDB("address", Address.class);
    private final BranchDB<String, BranchDB<String, DictDB<String, BigInteger>>> debt = Context.newBranchDB(
            "loan_balance", BigInteger.class); // Address:CollateralSymbol:AssetSymbol:debt
    private final BranchDB<String, DictDB<String, BigInteger>> collateral = Context.newBranchDB("collateral_balance"
            , BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> totalDebt = Context.newBranchDB(
        "total_debt_in_asset", BigInteger.class);

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

    private void setLoansPosition(String collateral, String symbol, BigInteger value) {
        debt.at(dbKey).at(collateral).set(symbol, value);
    }

    public BigInteger getDebt(String collateral, String symbol) {
        return debt.at(dbKey).at(collateral).getOrDefault(symbol, BigInteger.ZERO);
    }
    
    private void setPositionTotalDebt(String symbol, BigInteger value) {
        totalDebt.at(dbKey).set(symbol, value);
    }

    public BigInteger getTotalDebt(String symbol) {
        BigInteger totalDebt = this.totalDebt.at(dbKey).get(symbol);
        if (totalDebt == null) {
            totalDebt = getDebt(SICX_SYMBOL, symbol);
        }

        return totalDebt;
    }

    public void setCollateral(String symbol, BigInteger value) {
        collateral.at(dbKey).set(symbol, value);
    }

    public BigInteger getCollateral(String symbol) {
        return collateral.at(dbKey).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setDebt(String collateralSymbol, String assetSymbol, BigInteger value) {
        BigInteger previousDebt = getDebt(collateralSymbol, assetSymbol);
        BigInteger previousUserDebt = getTotalDebt(assetSymbol);
        DictDB<String, BigInteger> totalPerCollateralDebts = LoansVariables.totalPerCollateralDebts.at(collateralSymbol);

        BigInteger previousTotalDebt = LoansVariables.totalDebts.getOrDefault(assetSymbol, BigInteger.ZERO);
        BigInteger previousTotalPerCollateralDebt = totalPerCollateralDebts.getOrDefault(assetSymbol, BigInteger.ZERO);
        BigInteger currentValue = BigInteger.ZERO;
        if (value != null) {
            currentValue = value;
        }

        BigInteger debtChange = currentValue.subtract(previousDebt);
        BigInteger newTotalDebt = previousTotalDebt.add(debtChange);
        BigInteger newTotalPerCollateralDebt = previousTotalPerCollateralDebt.add(debtChange);

        Asset asset = AssetDB.getAsset(assetSymbol);
        BigInteger debtAndBadDebtPerCollateral = newTotalPerCollateralDebt.add(asset.getBadDebt(collateralSymbol));
        BigInteger debtCeiling = LoansVariables.debtCeiling.get(collateralSymbol);
        Context.require(debtCeiling == null 
                        || debtChange.signum() != 1 
                        || debtAndBadDebtPerCollateral.compareTo(debtCeiling) <= 0, 
                        TAG + ": Cannot mint more " + assetSymbol + " on collateral " + collateralSymbol);
    
        LoansVariables.totalDebts.set(assetSymbol, newTotalDebt);
        totalPerCollateralDebts.set(assetSymbol, newTotalPerCollateralDebt);
        setLoansPosition(collateralSymbol, assetSymbol, value);
        setPositionTotalDebt(assetSymbol, previousUserDebt.add(debtChange));

        if ( value == null) {
            AssetDB.getAsset(assetSymbol).getBorrowers(collateralSymbol).remove(getId());
        } else {
            AssetDB.getAsset(assetSymbol).getBorrowers(collateralSymbol).set(getId(), currentValue);
        }
    }

    public boolean hasDebt() {
        int assetsCount = AssetDB.assetList.size();
        for (int i = 0; i < assetsCount; i++) {
            String symbol = AssetDB.assetList.get(i);
            if (AssetDB.getAsset(symbol).isActive() && !getTotalDebt(symbol).equals(BigInteger.ZERO)) {
                return true;
            }
        }

        return false;
    }

    public BigInteger totalCollateralInLoop(String collateralSymbol,  boolean readOnly) {
        Collateral collateral = CollateralDB.getCollateral(collateralSymbol);

        Address collateralAddress = collateral.getAssetAddress();
        Token collateralContract = new Token(collateralAddress);

        BigInteger amount = getCollateral(collateralSymbol);
        BigInteger price;
        if (readOnly) {
            price  = collateralContract.lastPriceInLoop();
        } else {
            price  = collateralContract.priceInLoop();
        }
        

        return amount.multiply(price).divide(EXA);
    }

    public BigInteger totalDebtInLoop(String collateralSymbol, boolean readOnly) {
        BigInteger totalDebt = BigInteger.ZERO;
        int assetsCount = AssetDB.assetList.size();
        for (int i = 0; i < assetsCount; i++) {
            String assetSymbol = AssetDB.assetList.get(i);
            if (!AssetDB.getAsset(assetSymbol).isActive()) {
                continue;
            }

            BigInteger amount = getDebt(collateralSymbol, assetSymbol);

            BigInteger price = BigInteger.ZERO;
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                price = getAssetPrice(assetSymbol, readOnly);
            }

            totalDebt = totalDebt.add(amount.multiply(price).divide(EXA));
        }

        return totalDebt;
    }

    public Standing getStanding(String collateralSymbol, Boolean readOnly) {
        Standing standing = new Standing();
        standing.totalDebt = totalDebtInLoop(collateralSymbol, readOnly);
        standing.collateral = totalCollateralInLoop(collateralSymbol, readOnly);

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
        BigInteger liquidationRatio  = LoansVariables.liquidationRatio.get(collateralSymbol);
        Context.require(liquidationRatio != null && liquidationRatio.compareTo(BigInteger.ZERO) > 0, "Liquidation ratio for " + collateralSymbol + " is not set");
        if (standing.ratio.compareTo(liquidationRatio.multiply(EXA).divide(POINTS)) > 0) {
            standing.standing = Standings.MINING;
        } else {
            standing.standing = Standings.LIQUIDATE;
        }

        return standing;
    }

    public Map<String, Object> toMap() {
        //{"SICX":
        //     "bnUSD" : 1000,
        //     "bnBTC" : 1000,
        //     "SICX" : 4000,
        // "BALN":
        //     "bnUSD" : 1000,
        //     "bnBTC" : 1000,
        //     "BALN" : 4000,
        // }

        Map<String, Map<String, BigInteger>> holdings = new HashMap<>();
        Map<String, Map<String, Object>> standings = new HashMap<>();
        int assetSymbolsCount = AssetDB.assetList.size();
        int collateralSymbolsCount = CollateralDB.collateralList.size();
        for (int i = 0; i < collateralSymbolsCount; i++) {
            Map<String, BigInteger> collateralAmounts = new HashMap<>();
            String collateralSymbol = CollateralDB.collateralList.get(i);
            Collateral collateral = CollateralDB.getCollateral(collateralSymbol);
            if (!collateral.isActive()) {
                continue;
            }

            for (int j = 0; j < assetSymbolsCount; j++) {
                String assetSymbol = AssetDB.assetList.get(j);
               
                Asset asset = AssetDB.getAsset(assetSymbol);
                if (!asset.isActive()) {
                    continue;
                }
    
                BigInteger amount = getDebt(collateralSymbol, assetSymbol);
                collateralAmounts.put(assetSymbol, amount);
            }

            BigInteger amount = getCollateral(collateralSymbol);

            collateralAmounts.put(collateralSymbol, amount);
            holdings.put(collateralSymbol, collateralAmounts);

            Standing standing = getStanding(collateralSymbol, true);
            Map<String, Object> standingMap = new HashMap<>();
            standingMap.put("total_debt", standing.totalDebt);
            standingMap.put("collateral", standing.collateral);
            standingMap.put("ratio", standing.ratio);

            standingMap.put("standing", StandingsMap.get(standing.standing));
            standings.put(collateralSymbol, standingMap);
        }

        Map<String, Object> positionDetails = new HashMap<>();

        Standing sICXstanding = getStanding(SICX_SYMBOL, true);
        positionDetails.put("pos_id", getId());
        positionDetails.put("created", getCreated());
        positionDetails.put("address", getAddress().toString());
        positionDetails.put("assets", holdings.get("sICX"));
        positionDetails.put("holdings", holdings);
        positionDetails.put("standings", standings);
        positionDetails.put("total_debt", sICXstanding.totalDebt);
        positionDetails.put("collateral", sICXstanding.collateral);
        positionDetails.put("ratio", sICXstanding.ratio);
        positionDetails.put("standing", StandingsMap.get(sICXstanding.standing));

        return positionDetails;
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
