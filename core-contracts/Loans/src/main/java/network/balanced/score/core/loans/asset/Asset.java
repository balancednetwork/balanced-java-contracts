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

package network.balanced.score.core.loans.asset;

import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.utils.Token;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.LoansImpl.call;
import static network.balanced.score.core.loans.utils.LoansConstants.SICX_SYMBOL;

public class Asset {
    private static final String BORROWER_DB_PREFIX = "borrowers";
    private final BranchDB<String, VarDB<BigInteger>> assetAddedTime = Context.newBranchDB("added", BigInteger.class);
    private final BranchDB<String, VarDB<Address>> assetAddress = Context.newBranchDB("address", Address.class);
    // deprecated
    private final BranchDB<String, VarDB<BigInteger>> badDebt = Context.newBranchDB("bad_debt", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> badDebts = Context.newBranchDB(
            "multi_collateral_bad_debts", BigInteger.class);
    // deprecated
    private final BranchDB<String, VarDB<BigInteger>> liquidationPool = Context.newBranchDB("liquidation_pool",
            BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> liquidationPools = Context.newBranchDB(
            "multi_collateral_liquidation_pools", BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalBurnedTokens = Context.newBranchDB("burned",
            BigInteger.class);
    private final BranchDB<String, VarDB<Boolean>> isCollateral = Context.newBranchDB("is_collateral", Boolean.class);
    private final BranchDB<String, VarDB<Boolean>> active = Context.newBranchDB("active", Boolean.class);

    private final String dbKey;

    Asset(String key) {
        dbKey = key;
    }

    public void migrateLiquidationPool() {
        BigInteger liquidationPoolBalance = liquidationPool.at(dbKey).get();
        if (liquidationPoolBalance != null && liquidationPoolBalance.compareTo(BigInteger.ZERO) > 0) {
            setLiquidationPool(SICX_SYMBOL, liquidationPoolBalance);
        }
    }

    public void migrateBadDebt() {
        BigInteger badDebtBalance = badDebt.at(dbKey).get();
        if (badDebtBalance != null && badDebtBalance.compareTo(BigInteger.ZERO) > 0) {
            setBadDebt(SICX_SYMBOL, badDebtBalance);
        }
    }

    public void burn(BigInteger amount) {
        call(assetAddress.at(dbKey).get(), "burn", amount);
        VarDB<BigInteger> totalBurnedTokens = this.totalBurnedTokens.at(dbKey);
        totalBurnedTokens.set(totalBurnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public void burnFrom(Address from, BigInteger amount) {
        call(assetAddress.at(dbKey).get(), "burnFrom", from, amount);
        VarDB<BigInteger> totalBurnedTokens = this.totalBurnedTokens.at(dbKey);
        totalBurnedTokens.set(totalBurnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public BigInteger getAssetAddedTime() {
        return assetAddedTime.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public Address getAssetAddress() {
        return assetAddress.at(dbKey).get();
    }

    public void setBadDebt(String symbol, BigInteger badDebt) {
        this.badDebts.at(dbKey).set(symbol, badDebt);
    }

    public BigInteger getBadDebt(String symbol) {
        return badDebts.at(dbKey).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setLiquidationPool(String collateralSymbol, BigInteger liquidationPool) {
        this.liquidationPools.at(dbKey).set(collateralSymbol, liquidationPool);
    }

    public BigInteger getLiquidationPool(String collateralSymbol) {
        return liquidationPools.at(dbKey).getOrDefault(collateralSymbol, BigInteger.ZERO);
    }

    private BigInteger getTotalBurnedTokens() {
        return totalBurnedTokens.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public boolean isCollateral() {
        return isCollateral.at(dbKey).getOrDefault(false);
    }

    public void setActive(Boolean active) {
        this.active.at(dbKey).set(active);
    }

    public boolean isActive() {
        return active.at(dbKey).getOrDefault(false);
    }

    public LinkedListDB getBorrowers(String collateralSymbol) {
        if (collateralSymbol.equals(SICX_SYMBOL)) {
            return new LinkedListDB(BORROWER_DB_PREFIX, dbKey);
        } else {
            return new LinkedListDB(collateralSymbol + "|" + BORROWER_DB_PREFIX, dbKey);
        }
    }

    void setAsset(Address assetAddress, Boolean active) {
        this.assetAddress.at(dbKey).set(assetAddress);
        this.active.at(dbKey).set(active);
        this.isCollateral.at(dbKey).set(false);
    }

    Map<String, Object> toMap() {
        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token tokenContract = new Token(assetAddress);

        Map<String, Object> assetDetails = new HashMap<>();
        Map<String, Map<String, Object>> loansDetails = new HashMap<>();

        int collateralListCount = CollateralDB.collateralList.size();
        for (int i = 0; i < collateralListCount; i++) {
            Map<String, Object> loansDetail = new HashMap<>();
            String symbol = CollateralDB.collateralList.get(i);
            loansDetail.put("borrowers", getBorrowers(symbol).size());
            loansDetail.put("bad_debt", getBadDebt(symbol));
            loansDetail.put("liquidation_pool", getLiquidationPool(symbol));
            loansDetails.put(symbol, loansDetail);
        }

        assetDetails.put("symbol", tokenContract.symbol());
        assetDetails.put("address", assetAddress);
        assetDetails.put("peg", tokenContract.getPeg());
        assetDetails.put("added", getAssetAddedTime());
        assetDetails.put("active", isActive());
        assetDetails.put("total_supply", tokenContract.totalSupply());
        assetDetails.put("total_burned", getTotalBurnedTokens());
        assetDetails.put("debt_details", loansDetails);
        assetDetails.put("bad_debt", getBadDebt(SICX_SYMBOL));
        assetDetails.put("liquidation_pool", getLiquidationPool(SICX_SYMBOL));
        assetDetails.put("borrowers", getBorrowers(SICX_SYMBOL).size());

        return assetDetails;
    }
}