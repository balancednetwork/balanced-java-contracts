/*
 * Copyright (c) 2023-2024 Balanced.network.
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

package network.balanced.score.core.loans.debt;

import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.utils.TokenUtils;
import network.balanced.score.lib.utils.BalancedAddressManager;
import score.Address;
import score.ArrayDB;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.BNUSD_SYMBOL;
import static network.balanced.score.core.loans.utils.LoansConstants.SICX_SYMBOL;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;
import static network.balanced.score.lib.utils.Constants.YEAR_IN_MICRO_SECONDS;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Constants.EXA;

public class DebtDB {
    private static final String TOTAL_COLLATERAL_DEBTS = "totalCollateralDebts";
    private static final String TOTAL_COLLATERAL_DEBT_SHARES = "totalCollateralDebtShares";
    private static final String DEBT_CEILINGS = "debt_ceilings";
    private static final String INTEREST_RATES = "interestRates";
    private static final String LAST_INTEREST_COMPOUND = "lastInterestCompound";
    private static final String ACCUMULATED_INTEREST = "accumulatedInterest";
    private static final String SAVINGS_SHARE = "savingsShare";

    private static final String ASSET_DB_PREFIX = "asset";
    private static final String BORROWER_DB_PREFIX = "borrowers";

    private static final BranchDB<String, DictDB<String, BigInteger>> badDebts = Context.newBranchDB(
            "multi_collateral_bad_debts", BigInteger.class);
    private static final BranchDB<String, DictDB<String, BigInteger>> liquidationPools = Context.newBranchDB(
            "multi_collateral_liquidation_pools", BigInteger.class);

    private static final DictDB<String, BigInteger> debtCeiling = Context.newDictDB(DEBT_CEILINGS, BigInteger.class);
    private static final BranchDB<String, DictDB<String, BigInteger>> totalPerCollateralDebts =
            Context.newBranchDB(TOTAL_COLLATERAL_DEBTS, BigInteger.class);
    private static final DictDB<String, BigInteger> totalPerCollateralDebtShares =
            Context.newDictDB(TOTAL_COLLATERAL_DEBT_SHARES, BigInteger.class);
    private static final DictDB<String, BigInteger> interestRates =
            Context.newDictDB(INTEREST_RATES, BigInteger.class);
    private static final DictDB<String, BigInteger> lastInterestCompoundTimestamp =
            Context.newDictDB(LAST_INTEREST_COMPOUND, BigInteger.class);
    private static final VarDB<BigInteger> accumulatedInterest =
            Context.newVarDB(ACCUMULATED_INTEREST, BigInteger.class);
    private static final VarDB<BigInteger> savingsShare =
            Context.newVarDB(SAVINGS_SHARE, BigInteger.class);


    public static void migrate() {
        ArrayDB<String> collateralList = CollateralDB.collateralList;
        int len = collateralList.size();
        for (int i = 0; i < len; i++) {
            String symbol = collateralList.get(i);
            Context.require(totalPerCollateralDebtShares.get(symbol) == null, "Already migrated");
            totalPerCollateralDebtShares.set(symbol, getCollateralDebt(symbol));
        }
    }

    public static String getDBKey() {
        return ASSET_DB_PREFIX + "|" + getBnusd().toString();
    }

    public static BigInteger getTotalDebt() {
        ArrayDB<String> collateralList = CollateralDB.collateralList;
        int len = collateralList.size();
        BigInteger total = BigInteger.ZERO;
        for (int i = 0; i < len; i++) {
            total = total.add(getCollateralDebt(collateralList.get(i)));
        }

        return total;
    }

    public static void setCollateralDebt(String collateralSymbol, BigInteger debt) {
        totalPerCollateralDebts.at(collateralSymbol).set(BNUSD_SYMBOL, debt);
    }

    public static BigInteger getCollateralDebt(String collateralSymbol) {
        return totalPerCollateralDebts.at(collateralSymbol).getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
    }

    public static void setCollateralDebtShares(String collateralSymbol, BigInteger shares) {
        totalPerCollateralDebtShares.set(collateralSymbol, shares);
    }

    public static BigInteger getCollateralDebtShares(String collateralSymbol) {
        return totalPerCollateralDebtShares.getOrDefault(collateralSymbol, BigInteger.ZERO);
    }

    public static void setDebtCeiling(String collateralSymbol, BigInteger ceiling) {
        debtCeiling.set(collateralSymbol, ceiling);
    }

    public static BigInteger getDebtCeiling(String collateralSymbol) {
        return debtCeiling.get(collateralSymbol);
    }

    public static void setBadDebt(String symbol, BigInteger badDebt) {
        badDebts.at(getDBKey()).set(symbol, badDebt);
    }

    public static BigInteger getBadDebt(String symbol) {
        return badDebts.at(getDBKey()).getOrDefault(symbol, BigInteger.ZERO);
    }

    public static void setLiquidationPool(String collateralSymbol, BigInteger liquidationPool) {
        liquidationPools.at(getDBKey()).set(collateralSymbol, liquidationPool);
    }

    public static BigInteger getLiquidationPool(String collateralSymbol) {
        return liquidationPools.at(getDBKey()).getOrDefault(collateralSymbol, BigInteger.ZERO);
    }

    public static void setInterestRate(String collateralSymbol, BigInteger rate) {
        Context.require(rate.compareTo(BigInteger.ZERO) > 0, "Rate must positive");
        interestRates.set(collateralSymbol, rate);
    }

    public static BigInteger getInterestRate(String collateralSymbol) {
        return interestRates.getOrDefault(collateralSymbol, BigInteger.ZERO);
    }

    public static void setSavingsRateShare(BigInteger share) {
        Context.require(share.compareTo(BigInteger.ZERO) >= 0 && share.compareTo(POINTS) <= 0,
            "Share must be between 0 and " + POINTS);
        savingsShare.set(share);
    }

    public static BigInteger getSavingsRateShare() {
        return savingsShare.getOrDefault(BigInteger.ZERO);
    }

    private static void setLastInterestCompoundTimestamp(String collateralSymbol, BigInteger debt) {
        lastInterestCompoundTimestamp.set(collateralSymbol, debt);
    }

    public static BigInteger getLastInterestCompoundTimestamp(String collateralSymbol) {
        BigInteger time =  lastInterestCompoundTimestamp.get(collateralSymbol);
        if (time == null) {
            time = BigInteger.valueOf(Context.getBlockTimestamp());
        }

        return time;
    }

    public static void applyInterest(String collateralSymbol) {
        BigInteger totalDebt = getCollateralDebt(collateralSymbol);
        BigInteger lastUpdate = getLastInterestCompoundTimestamp(collateralSymbol);
        BigInteger now = BigInteger.valueOf(Context.getBlockTimestamp());
        setLastInterestCompoundTimestamp(collateralSymbol, now);

        BigInteger interestRate = getInterestRate(collateralSymbol);
        BigInteger diff = now.subtract(lastUpdate);
        BigInteger interest = totalDebt.multiply(interestRate).multiply(diff).divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        if (interest.equals(BigInteger.ZERO)) {
            return;
        }

        totalDebt = totalDebt.add(interest);

        setCollateralDebt(collateralSymbol, totalDebt);

        BigInteger currentAmount = accumulatedInterest.getOrDefault(BigInteger.ZERO);
        accumulatedInterest.set(currentAmount.add(interest));
    }

    public static void claimInterest() {
        BigInteger accumulatedAmount = accumulatedInterest.getOrDefault(BigInteger.ZERO);
        if (accumulatedAmount.equals(BigInteger.ZERO)) {
            return;
        }

        accumulatedInterest.set(BigInteger.ZERO);
        BigInteger savingsRateAmount = savingsShare.get().multiply(accumulatedAmount).divide(POINTS);
        TokenUtils.mintAssetTo(BalancedAddressManager.getSavings(), savingsRateAmount);
        TokenUtils.mintAssetTo(BalancedAddressManager.getFeehandler(), accumulatedAmount.subtract(savingsRateAmount));
    }

    public static LinkedListDB getBorrowers(String collateralSymbol) {
        if (collateralSymbol.equals(SICX_SYMBOL)) {
            return new LinkedListDB(BORROWER_DB_PREFIX, getDBKey());
        } else {
            return new LinkedListDB(collateralSymbol + "|" + BORROWER_DB_PREFIX, getDBKey());
        }
    }

    public static List<Map<String, Object>> getBorrowers(Address collateralAddress, int nrOfPositions, int startId) {
        List<Map<String, Object>> data = new ArrayList<>();
        String symbol = CollateralDB.getSymbol(collateralAddress);
        LinkedListDB db = getBorrowers(symbol);

        int tail = db.getTailId();
        int id = startId;
        if (id == 0) {
            id = db.getHeadId();
        }

        Position position;
        Map<String, Object> positionData;
        for (int i = 0; i < nrOfPositions; i++) {
            position = PositionsDB.uncheckedGet(id);
            positionData = new HashMap<>();
            positionData.put(symbol, position.getCollateral(symbol));
            positionData.put("debt", position.getDebt(symbol));
            positionData.put("address", position.getAddress().toString());
            positionData.put("id", id);
            if (id == tail) {
                id = db.getHeadId();
            } else {
                id = db.getNextId(id);
            }

            positionData.put("nextId", id);
            data.add(positionData);
        }

        return data;
    }

    public static Map<String, Object> debtData() {
        Map<String, Object> debtDetails = new HashMap<>();
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

        debtDetails.put("debt_details", loansDetails);
        debtDetails.put("bad_debt", getBadDebt(SICX_SYMBOL));
        debtDetails.put("liquidation_pool", getLiquidationPool(SICX_SYMBOL));
        debtDetails.put("borrowers", getBorrowers(SICX_SYMBOL).size());

        return debtDetails;
    }
}