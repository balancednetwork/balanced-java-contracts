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

import static network.balanced.score.core.loans.LoansImpl.call;
import static network.balanced.score.core.loans.utils.LoansConstants.SICX_SYMBOL;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.utils.PositionBatch;
import network.balanced.score.core.loans.utils.Token;
import network.balanced.score.lib.interfaces.Sicx;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.HashMap;

public class Asset {
    private static final String BORROWER_DB_PREFIX = "borrowers";
    private final BranchDB<String, VarDB<BigInteger>> assetAddedTime = Context.newBranchDB("added", BigInteger.class);
    private final BranchDB<String, VarDB<Address>> assetAddress = Context.newBranchDB("address", Address.class);
    private final BranchDB<String, VarDB<BigInteger>> badDebt = Context.newBranchDB("bad_debt", BigInteger.class);
    private final BranchDB<String, DictDB<String, BigInteger>> badDebts = Context.newBranchDB("multi_collateral_bad_debts",
    BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> liquidationPool = Context.newBranchDB("liquidation_pool",
            BigInteger.class);
            private final BranchDB<String, DictDB<String, BigInteger>> liquidationPools = Context.newBranchDB("multi_collateral_liquidation_pools",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalBurnedTokens = Context.newBranchDB("burned",
            BigInteger.class);
    private final BranchDB<String, VarDB<Boolean>> isCollateral = Context.newBranchDB("is_collateral", Boolean.class);
    private final BranchDB<String, VarDB<Boolean>> active = Context.newBranchDB("active", Boolean.class);
    private final BranchDB<String, VarDB<Boolean>> deadMarket = Context.newBranchDB("dead_market", Boolean.class);

    private final String dbKey;

    Asset(String key) {
        dbKey = key;
    }

    public void migrateLiquidationPool() {
        BigInteger liquidationPoolBalance = liquidationPool.at(dbKey).get();
        if (liquidationPoolBalance != null && liquidationPoolBalance.compareTo(BigInteger.ZERO) > 0 ) {
            setLiquidationPool("sICX", liquidationPool.at(dbKey).getOrDefault(BigInteger.ZERO));
            // liquidationPool.at(dbKey).set(null);
        }
    }

    public void migrateBadDebt() {
        BigInteger badDebtBalance = badDebt.at(dbKey).get();
        if (badDebtBalance != null && badDebtBalance.compareTo(BigInteger.ZERO) > 0 ) {
            setBadDebt("sICX", liquidationPool.at(dbKey).getOrDefault(BigInteger.ZERO));
            // badDebt.at(dbKey).set(null);
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
        return isCollateral.at(dbKey).getOrDefault(true);
    }

    public void setActive(Boolean active) {
        this.active.at(dbKey).set(active);
    }

    public boolean isActive() {
        return active.at(dbKey).getOrDefault(false);
    }

    boolean isDeadMarket() {
        return deadMarket.at(dbKey).getOrDefault(false);
    }

    /**
     * Calculates whether the market is dead and sets the dead market flag. A dead market is defined as being below
     * the point at which total debt equals the minimum value of collateral that could be backing it.
     */
    public boolean checkForDeadMarket() {
        if (isCollateral() || !isActive()) {
            return false;
        }

        BigInteger badDebt = getBadDebt(SICX_SYMBOL);

        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token assetContract = new Token(assetAddress);

        BigInteger outStanding = assetContract.totalSupply().subtract(badDebt);

        Address sicxAddress = CollateralDB.getCollateral(SICX_SYMBOL).getAssetAddress();
        Token sicxContract = new Token(sicxAddress);

        // [Multi-collateral] Here it assumes every token should be denominated in terms of sicx.
        BigInteger poolValue =
                getLiquidationPool(SICX_SYMBOL).multiply(assetContract.priceInLoop()).divide(sicxContract.priceInLoop());
        BigInteger netBadDebt = badDebt.subtract(poolValue);
        Boolean isDead = netBadDebt.compareTo(outStanding.divide(BigInteger.TWO)) > 0;

        VarDB<Boolean> deadMarket = this.deadMarket.at(dbKey);
        if (deadMarket.getOrDefault(false) != isDead) {
            deadMarket.set(isDead);
        }
        return isDead;
    }

    public LinkedListDB getBorrowers(String collateralSymbol) {
        if (collateralSymbol == SICX_SYMBOL) {
            return new LinkedListDB(BORROWER_DB_PREFIX, dbKey);
        } else {
            return new LinkedListDB(collateralSymbol + "|" + BORROWER_DB_PREFIX, dbKey);
        }
    }

    public PositionBatch getBorrowersBatch(String collateralSymbol, int batchSize) {
       LinkedListDB borrowers = getBorrowers(collateralSymbol);

       int nodeId = borrowers.getHeadId();
       PositionBatch batch = new PositionBatch();
       batch.totalDebt = BigInteger.ZERO;
       Map<Integer, BigInteger> positionsMap = new HashMap<>();

       int iterations = Math.min(batchSize, borrowers.size());
       for (int i = 0; i < iterations; i++) {
           BigInteger debt = borrowers.nodeValue(nodeId);
           positionsMap.put(nodeId, debt);
           batch.totalDebt = batch.totalDebt.add(debt);
           borrowers.headToTail();
           nodeId = borrowers.getHeadId();
       }

       borrowers.serialize();
       batch.positions = positionsMap;
       batch.size = iterations;
       
       return batch;
    }

    public void removeBorrowers(int positionId) {
        getBorrowers(SICX_SYMBOL).remove(positionId);
    }

    void setAsset(Address assetAddress, Boolean active) {
        this.assetAddress.at(dbKey).set(assetAddress);
        this.active.at(dbKey).set(active);
        this.isCollateral.at(dbKey).set(false);
    }

    Map<String, Object> toMap() {
        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token tokenContract = new Token(assetAddress);

        Map<String, Object> AssetDetails = new HashMap<>();

        AssetDetails.put("symbol", tokenContract.symbol());
        AssetDetails.put("address", assetAddress);
        AssetDetails.put("peg", tokenContract.getPeg());
        AssetDetails.put("added", getAssetAddedTime());
        AssetDetails.put("active", isActive());
        AssetDetails.put("borrowers", getBorrowers(SICX_SYMBOL).size());
        AssetDetails.put("total_supply", tokenContract.totalSupply());
        AssetDetails.put("total_burned", getTotalBurnedTokens());
        AssetDetails.put("bad_debt", getBadDebt(SICX_SYMBOL));
        AssetDetails.put("liquidation_pool", getLiquidationPool(SICX_SYMBOL));
        AssetDetails.put("dead_market", isDeadMarket());

        return AssetDetails;
    }
}