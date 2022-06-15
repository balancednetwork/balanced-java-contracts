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
            "loan_balance", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> collateral = Context.newBranchDB("collateral_balance"
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

    private void setLoansPosition(String collateral, String symbol, BigInteger value) {
        debt.at(dbKey).at(collateral).set(symbol, value);
    }

    public BigInteger getDebt(String collateral, String symbol) {
        return debt.at(dbKey).at(collateral).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setCollateral(String symbol, BigInteger value) {
        collateral.at(dbKey).set(symbol, value);
    }

    public BigInteger getCollateral(String symbol) {
        return collateral.at(dbKey).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setDebt(String symbol, BigInteger value) {
        BigInteger previousDebt = BigInteger.ZERO;
        previousDebt = getDebt(SICX_SYMBOL, symbol);
    
        setLoansPosition(SICX_SYMBOL, symbol, value);

        BigInteger previousTotalDebt = LoansVariables.totalDebts.getOrDefault(symbol, BigInteger.ZERO);
        BigInteger currentValue = BigInteger.ZERO;
        if (value != null) {
            currentValue = value;
        }

        BigInteger newTotalDebt = previousTotalDebt.add(currentValue).subtract(previousDebt);
        LoansVariables.totalDebts.set(symbol, newTotalDebt);
        AssetDB.getAsset(symbol).getBorrowers(SICX_SYMBOL).set(getId(), currentValue);
    }

    public boolean hasDebt() {
        int assetsCount = AssetDB.assetList.size();
        for (int i = 0; i < assetsCount; i++) {
            String symbol = AssetDB.assetList.get(i);
            if (AssetDB.getAsset(symbol).isActive() && !getDebt(SICX_SYMBOL, symbol).equals(BigInteger.ZERO)) {
                return true;
            }
        }

        return false;
    }

    public BigInteger totalCollateralInLoop(String collateralSymbol) {
        Collateral collateral = CollateralDB.getCollateral(collateralSymbol);

        Address collateralAddress = collateral.getAssetAddress();
        Token collateralContract = new Token(collateralAddress);

        BigInteger amount = getCollateral(collateralSymbol);
        BigInteger price = collateralContract.priceInLoop();

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
        standing.collateral = totalCollateralInLoop(collateralSymbol);

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

        if (standing.ratio.compareTo(liquidationRatio.get().multiply(EXA).divide(POINTS)) > 0) {
            standing.standing = Standings.MINING;
        } else {
            standing.standing = Standings.LIQUIDATE;
        }

        return standing;
    }

    public Map<String, Object> toMap() {
        //TODO: cleanup asset holdings
        // change to Map<String,Map<String, BigInteger>> ex:
        //{"SICX":
        //     "bnUSD" : 1000,
        //     "bnBTC" : 1000,
        //     "SICX" : 4000,
        // "BALN":
        //     "bnUSD" : 1000,
        //     "bnBTC" : 1000,
        //     "BALN" : 4000,
        // }
        Map<String, BigInteger> assetAmounts = new HashMap<>();
        int assetSymbolsCount = AssetDB.assetList.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetList.get(i);
           
            Asset asset = AssetDB.getAsset(symbol);
            if (!asset.isActive()) {
                continue;
            }

            BigInteger amount = getDebt(SICX_SYMBOL, symbol);

            if (amount.compareTo(BigInteger.ZERO) > 0) {
                assetAmounts.put(symbol, amount);
            }
        }

        int collateralSymbolsCount = CollateralDB.collateralList.size();
        for (int i = 0; i < collateralSymbolsCount; i++) {
            String symbol = CollateralDB.collateralList.get(i);
            Collateral collateral = CollateralDB.getCollateral(symbol);
            if (!collateral.isActive()) {
                continue;
            }


            BigInteger amount = getCollateral(symbol);

            if (amount.compareTo(BigInteger.ZERO) > 0) {
                assetAmounts.put(symbol, amount);
            }
        }

        Standing standing = getStanding(SICX_SYMBOL, true);
        Map<String, Object> positionDetails = new HashMap<>();

        positionDetails.put("pos_id", getId());
        positionDetails.put("created", getCreated());
        positionDetails.put("address", getAddress().toString());
        positionDetails.put("assets", assetAmounts);
        positionDetails.put("total_debt", standing.totalDebt);
        positionDetails.put("collateral", standing.collateral);
        positionDetails.put("ratio", standing.ratio);
        positionDetails.put("standing", StandingsMap.get(standing.standing));

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
