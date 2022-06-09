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

package network.balanced.score.core.loans.utils;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.utils.Constants;

public class LoansConstants extends Constants {
    private static final BigInteger SECOND = BigInteger.valueOf(1_000_000);

    public static final BigInteger POINTS = BigInteger.valueOf(10_000);
    public static final BigInteger MINING_RATIO = BigInteger.valueOf(50_000);
    public static final BigInteger LOCKING_RATIO = BigInteger.valueOf(40_000);
    public static final BigInteger LIQUIDATION_RATIO = BigInteger.valueOf(15_000);
    public static final BigInteger LIQUIDATION_REWARD = BigInteger.valueOf(67);
    public static final BigInteger ORIGINATION_FEE = BigInteger.valueOf(100);
    public static final BigInteger REDEMPTION_FEE = BigInteger.valueOf(50);
    public static final BigInteger BAD_DEBT_RETIREMENT_BONUS = BigInteger.valueOf(1_000);
    public static final BigInteger MAX_RETIRE_PERCENT = BigInteger.valueOf(100);
    
     //In USD
    public static final BigInteger NEW_BNUSD_LOAN_MINIMUM = BigInteger.TEN.multiply(EXA);
    public static final BigInteger MIN_BNUSD_MINING_DEBT = BigInteger.valueOf(50).multiply(EXA); 
    
    public static final int MAX_DEBTS_LIST_LENGTH = 400;
    public static final int SNAP_BATCH_SIZE = 50;
    public static final int REDEEM_BATCH_SIZE = 50;

    public static final String continuousRewardsErrorMessage = "BalancedLoansPosition: The continuous rewards is " +
            "already active.";
    public static final String SICX_SYMBOL = "sICX";
    public static final String BNUSD_SYMBOL = "bnUSD";

    public enum Standings {
        INDETERMINATE,
        ZERO,
        LIQUIDATE,
        LOCKED,
        NOT_MINING,
        MINING,
        NO_DEBT
    }

    public static Map<Standings, String> StandingsMap = Map.of(
        Standings.INDETERMINATE, "Indeterminate",
        Standings.ZERO, "Zero",
        Standings.LIQUIDATE, "Liquidate",
        Standings.LOCKED, "Locked",
        Standings.NOT_MINING, "Not Mining",
        Standings.MINING, "Mining",
        Standings.NO_DEBT, "No Debt"
    );
}

