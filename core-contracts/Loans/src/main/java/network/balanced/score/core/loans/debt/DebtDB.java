package network.balanced.score.core.loans.debt;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import score.BranchDB;
import score.Context;
import score.DictDB;
import scorex.util.HashMap;

import static network.balanced.score.core.loans.utils.LoansConstants.*;
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
    private static final BranchDB<String, DictDB<String, BigInteger>> totalPerCollateralDebts = Context.newBranchDB(TOTAL_COLLATERAL_DEBTS, BigInteger.class);

    public static void setTotalDebt(BigInteger debt) {
        totalDebts.set(BNUSD_SYMBOL, debt);
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
        badDebts.at(ASSET_DB_PREFIX + "|" + getBnusd()).set(symbol, badDebt);
    }

    public static BigInteger getBadDebt(String symbol) {
        return badDebts.at(ASSET_DB_PREFIX + "|" + getBnusd()).getOrDefault(symbol, BigInteger.ZERO);
    }

    public static void setLiquidationPool(String collateralSymbol, BigInteger liquidationPool) {
        liquidationPools.at(ASSET_DB_PREFIX + "|" + getBnusd()).set(collateralSymbol, liquidationPool);
    }

    public static BigInteger getLiquidationPool(String collateralSymbol) {
        return liquidationPools.at(ASSET_DB_PREFIX + "|" + getBnusd()).getOrDefault(collateralSymbol, BigInteger.ZERO);
    }

    public static LinkedListDB getBorrowers(String collateralSymbol) {
        if (collateralSymbol.equals(SICX_SYMBOL)) {
            return new LinkedListDB(BORROWER_DB_PREFIX, getBnusd().toString());
        } else {
            return new LinkedListDB(collateralSymbol + "|" + BORROWER_DB_PREFIX, getBnusd().toString());
        }
    }

    public static  Map<String, Object> debtData() {
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