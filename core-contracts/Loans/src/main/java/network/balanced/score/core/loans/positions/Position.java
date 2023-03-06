/*
 * Copyright (c) 2022-2023 Balanced.network.
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
import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.debt.DebtDB;
import network.balanced.score.core.loans.utils.Standing;
import network.balanced.score.core.loans.utils.TokenUtils;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.Check.readonly;
import static network.balanced.score.lib.utils.Math.pow;

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

    private final BranchDB<String, BranchDB<Integer, DictDB<String, BigInteger>>> assets = Context.newBranchDB("assets",
            BigInteger.class);
    private final BranchDB<String, DictDB<String, Boolean>> dataMigrationStatus = Context.newBranchDB("data_migration "
            + "_status", Boolean.class);
    private final BranchDB<String, ArrayDB<Integer>> snaps = Context.newBranchDB("snaps", Integer.class);

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

    private void setLoansPosition(String collateral, BigInteger value) {
        debt.at(dbKey).at(collateral).set(BNUSD_SYMBOL, value);
    }

    public BigInteger getDebt(String collateral) {
        return debt.at(dbKey).at(collateral).getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
    }

    private void setPositionTotalDebt(BigInteger value) {
        totalDebt.at(dbKey).set(BNUSD_SYMBOL, value);
    }

    public BigInteger getTotalDebt() {
        BigInteger totalDebt = this.totalDebt.at(dbKey).get(BNUSD_SYMBOL);
        if (totalDebt == null) {
            totalDebt = getDebt(SICX_SYMBOL);
        }

        return totalDebt;
    }

    public void setCollateral(String symbol, BigInteger value) {
        collateral.at(dbKey).set(symbol, value);
    }

    public BigInteger getCollateral(String symbol) {
        return getCollateral(symbol, false);
    }

    public BigInteger getCollateral(String symbol, boolean readonly) {
        if (symbol.equals(SICX_SYMBOL)) {
            if (!dataMigrationStatus.at(dbKey).getOrDefault(SICX_SYMBOL, false) &&
                    collateral.at(dbKey).getOrDefault(SICX_SYMBOL, BigInteger.ZERO).equals(BigInteger.ZERO)) {

                int lastSnapIndex = snaps.at(dbKey).size() - 1;
                int lastSnap = snaps.at(dbKey).get(lastSnapIndex);
                BigInteger collateralAmount = assets.at(dbKey).at(lastSnap).getOrDefault(SICX_SYMBOL, BigInteger.ZERO);
                if (readonly() || readonly) {
                    return collateralAmount;
                }

                if (collateralAmount.compareTo(BigInteger.ZERO) > 0) {
                    setCollateral(SICX_SYMBOL, collateralAmount);
                }

                dataMigrationStatus.at(dbKey).set(SICX_SYMBOL, true);
                return collateralAmount;
            }
        }

        return collateral.at(dbKey).getOrDefault(symbol, BigInteger.ZERO);
    }

    public void setDataMigrationStatus(String symbol, Boolean value) {
        dataMigrationStatus.at(dbKey).set(symbol, value);
    }

    public void setDebt(String collateralSymbol, BigInteger value) {
        BigInteger previousDebt = getDebt(collateralSymbol);
        BigInteger previousUserDebt = getTotalDebt();

        BigInteger previousTotalDebt = DebtDB.getTotalDebt();
        BigInteger previousTotalPerCollateralDebt = DebtDB.getCollateralDebt(collateralSymbol);
        BigInteger currentValue = BigInteger.ZERO;
        if (value != null) {
            currentValue = value;
        }

        BigInteger debtChange = currentValue.subtract(previousDebt);
        BigInteger newTotalDebt = previousTotalDebt.add(debtChange);
        BigInteger newTotalPerCollateralDebt = previousTotalPerCollateralDebt.add(debtChange);

        BigInteger debtAndBadDebtPerCollateral = newTotalPerCollateralDebt.add(DebtDB.getBadDebt(collateralSymbol));
        BigInteger debtCeiling = DebtDB.getDebtCeiling(collateralSymbol);
        Context.require(debtCeiling == null
                        || debtChange.signum() != 1
                        || debtAndBadDebtPerCollateral.compareTo(debtCeiling) <= 0,
                TAG + ": Cannot mint more " + BNUSD_SYMBOL + " on collateral " + collateralSymbol);

        DebtDB.setTotalDebt(newTotalDebt);
        DebtDB.setCollateralDebt(collateralSymbol, newTotalPerCollateralDebt);

        setLoansPosition(collateralSymbol, value);
        setPositionTotalDebt(previousUserDebt.add(debtChange));

        if (value == null) {
            DebtDB.getBorrowers(collateralSymbol).remove(getId());
        } else {
            DebtDB.getBorrowers(collateralSymbol).set(getId(), currentValue);
        }
    }

    public boolean hasDebt() {
        return !getTotalDebt().equals(BigInteger.ZERO);
    }

    public BigInteger totalCollateralInUSD(String collateralSymbol) {
        return totalCollateralInUSD(collateralSymbol, false);
    }

    public BigInteger totalCollateralInUSD(String collateralSymbol, boolean readonly) {
        Address collateralAddress = CollateralDB.getAddress(collateralSymbol);

        BigInteger amount = getCollateral(collateralSymbol, readonly);
        BigInteger decimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());
        BigInteger price = TokenUtils.getPriceInUSD(collateralSymbol);

        return amount.multiply(price).divide(decimals);
    }

    public Standing getStanding(String collateralSymbol) {
        return getStanding(collateralSymbol, false);
    }

    public Standing getStanding(String collateralSymbol, boolean readonly) {
        Standing standing = new Standing();
        standing.totalDebt = getDebt(collateralSymbol);
        standing.collateral = totalCollateralInUSD(collateralSymbol, readonly);

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
        BigInteger liquidationRatio = LoansVariables.liquidationRatio.get(collateralSymbol);
        Context.require(liquidationRatio != null && liquidationRatio.compareTo(BigInteger.ZERO) > 0, "Liquidation " +
                "ratio for " + collateralSymbol + " is not set");
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
        //     "SICX" : 4000,
        // "BALN":
        //     "bnUSD" : 1000,
        //     "BALN" : 4000,
        // }

        BigInteger loopPrice = TokenUtils.getPriceInUSD("ICX");

        Map<String, Map<String, BigInteger>> holdings = new HashMap<>();
        Map<String, Map<String, Object>> standings = new HashMap<>();
        int collateralSymbolsCount = CollateralDB.collateralList.size();
        for (int i = 0; i < collateralSymbolsCount; i++) {
            Map<String, BigInteger> collateralAmounts = new HashMap<>();
            String collateralSymbol = CollateralDB.collateralList.get(i);

            collateralAmounts.put(BNUSD_SYMBOL, getDebt(collateralSymbol));

            BigInteger amount = getCollateral(collateralSymbol, true);

            collateralAmounts.put(collateralSymbol, amount);
            holdings.put(collateralSymbol, collateralAmounts);

            Standing standing = getStanding(collateralSymbol, true);
            Map<String, Object> standingMap = new HashMap<>();
            standingMap.put("total_debt", standing.totalDebt.multiply(EXA).divide(loopPrice));
            standingMap.put("collateral", standing.collateral.multiply(EXA).divide(loopPrice));
            standingMap.put("total_debt_in_USD", standing.totalDebt);
            standingMap.put("collateral_in_USD", standing.collateral);
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
        positionDetails.put("total_debt", sICXstanding.totalDebt.multiply(EXA).divide(loopPrice));
        positionDetails.put("collateral", sICXstanding.collateral.multiply(EXA).divide(loopPrice));
        positionDetails.put("ratio", sICXstanding.ratio);
        positionDetails.put("standing", StandingsMap.get(sICXstanding.standing));

        return positionDetails;
    }
}
