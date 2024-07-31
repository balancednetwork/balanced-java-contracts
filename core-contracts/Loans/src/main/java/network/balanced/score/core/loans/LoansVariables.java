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

package network.balanced.score.core.loans;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;

public class LoansVariables {

    private static final String LOANS_ON = "loans_on";
    private static final String GOVERNANCE = "governance";

    private static final String TIME_OFFSET = "time_offset";

    private static final String LOCKING_RATIO = "locking_ratio";
    private static final String PER_COLLATERAL_LOCKING_RATIO = "per_collateral_locking_ratio";

    private static final String PER_COLLATERAL_LIQUIDATION_THRESHOLD = "per_collateral_liquidation_threshold";
    private static final String PER_COLLATERAL_LIQUIDATOR_FEE = "per_collateral_liquidator_fee";
    private static final String PER_COLLATERAL_DAOFUND_FEE = "per_collateral_liquidation_dao_fund_fee";

    private static final String ORIGINATION_FEE = "origination_fee";
    private static final String REDEMPTION_FEE = "redemption_fee";
    private static final String REDEMPTION_DAO_FEE = "redemption_dao_fee";
    private static final String RETIREMENT_BONUS = "retirement_bonus";
    private static final String NEW_LOAN_MINIMUM = "new_loan_minimum";

    private static final String REDEEM_BATCH_SIZE = "redeem_batch_size";
    private static final String MAX_RETIRE_PERCENT = "max_retire_percent";

    private static final String EXPECTED_TOKEN = "expectedToken";
    private static final String AMOUNT_RECEIVED = "amountReceived";
    private static final String VERSION = "version";

    public static final VarDB<Boolean> loansOn = Context.newVarDB(LOANS_ON, Boolean.class);
    static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);

    static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> lockingRatioSICX = Context.newVarDB(LOCKING_RATIO, BigInteger.class);
    public static final DictDB<String, BigInteger> lockingRatio = Context.newDictDB(PER_COLLATERAL_LOCKING_RATIO,
            BigInteger.class);

    public static final DictDB<String, BigInteger> liquidationThreshold = Context.newDictDB(PER_COLLATERAL_LIQUIDATION_THRESHOLD, BigInteger.class);
    public static final DictDB<String, BigInteger> liquidatorFee = Context.newDictDB(PER_COLLATERAL_LIQUIDATOR_FEE, BigInteger.class);
    public static final DictDB<String, BigInteger> liquidationDaoFundFee = Context.newDictDB(PER_COLLATERAL_DAOFUND_FEE, BigInteger.class);

    static final VarDB<BigInteger> originationFee = Context.newVarDB(ORIGINATION_FEE, BigInteger.class);
    static final VarDB<BigInteger> redemptionFee = Context.newVarDB(REDEMPTION_FEE, BigInteger.class);
    static final VarDB<BigInteger> redemptionDaoFee = Context.newVarDB(REDEMPTION_DAO_FEE, BigInteger.class);
    static final VarDB<BigInteger> retirementBonus = Context.newVarDB(RETIREMENT_BONUS, BigInteger.class);
    static final VarDB<BigInteger> newLoanMinimum = Context.newVarDB(NEW_LOAN_MINIMUM, BigInteger.class);
    static final VarDB<Integer> redeemBatch = Context.newVarDB(REDEEM_BATCH_SIZE, Integer.class);
    static final VarDB<BigInteger> maxRetirePercent = Context.newVarDB(MAX_RETIRE_PERCENT, BigInteger.class);

    static final VarDB<Address> expectedToken = Context.newVarDB(EXPECTED_TOKEN, Address.class);
    static final VarDB<BigInteger> amountReceived = Context.newVarDB(AMOUNT_RECEIVED, BigInteger.class);

    public static final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);


}
