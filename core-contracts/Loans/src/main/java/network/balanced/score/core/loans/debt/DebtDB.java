/*
 * Copyright (c) 2023-2023 Balanced.network.
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
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.BNUSD_SYMBOL;
import static network.balanced.score.core.loans.utils.LoansConstants.SICX_SYMBOL;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;

public class DebtDB {
    private static final String TOTAL_DEBT = "totalDebts";
    private static final String TOTAL_COLLATERAL_DEBTS = "totalCollateralDebts";
    private static final String DEBT_CEILINGS = "debt_ceilings";

    private static final String ASSET_DB_PREFIX = "asset";
    private static final String BORROWER_DB_PREFIX = "borrowers";

    private static final BranchDB<String, DictDB<String, BigInteger>> badDebts = Context.newBranchDB(
            "multi_collateral_bad_debts", BigInteger.class);
    private static final BranchDB<String, DictDB<String, BigInteger>> liquidationPools = Context.newBranchDB(
            "multi_collateral_liquidation_pools", BigInteger.class);

    private static final DictDB<String, BigInteger> totalDebts = Context.newDictDB(TOTAL_DEBT, BigInteger.class);
    private static final DictDB<String, BigInteger> debtCeiling = Context.newDictDB(DEBT_CEILINGS, BigInteger.class);
    private static final BranchDB<String, DictDB<String, BigInteger>> totalPerCollateralDebts =
            Context.newBranchDB(TOTAL_COLLATERAL_DEBTS, BigInteger.class);

    public static void setTotalDebt(BigInteger debt) {
        totalDebts.set(BNUSD_SYMBOL, debt);
    }

    public static String getDBKey() {
        return ASSET_DB_PREFIX + "|" + getBnusd().toString();
    }

    public static BigInteger getTotalDebt() {
        return totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
    }

    public static void setCollateralDebt(String collateralSymbol, BigInteger debt) {
        totalPerCollateralDebts.at(collateralSymbol).set(BNUSD_SYMBOL, debt);
    }

    public static BigInteger getCollateralDebt(String collateralSymbol) {
        return totalPerCollateralDebts.at(collateralSymbol).getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
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