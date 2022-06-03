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

package network.balanced.score.core.loans;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;

public class LoansVariables {

    private static final String LOANS_ON = "loans_on";
    private static final String GOVERNANCE = "governance";
    private static final String REBALANCE = "rebalance";
    private static final String DEX = "dex";
    private static final String DIVIDENDS = "dividends";
    private static final String RESERVE = "reserve";
    private static final String REWARDS = "rewards";
    private static final String STAKING = "staking";
    private static final String FEEHANDLER = "feehandler";
    private static final String ADMIN = "admin";

    private static final String TIME_OFFSET = "time_offset";

    private static final String MINING_RATIO = "mining_ratio";
    private static final String LOCKING_RATIO = "locking_ratio";
    private static final String LIQUIDATION_RATIO = "liquidation_ratio";
    private static final String ORIGINATION_FEE = "origination_fee";
    private static final String REDEMPTION_FEE = "redemption_fee";
    private static final String RETIREMENT_BONUS = "retirement_bonus";
    private static final String LIQUIDATION_REWARD = "liquidation_reward";
    private static final String NEW_LOAN_MINIMUM = "new_loan_minimum";
    private static final String MAX_DEBTS_LIST_LENGTH = "max_debts_list_length";
    private static final String TOTAL_DEBT = "totalDebts";

    private static final String REDEEM_BATCH_SIZE = "redeem_batch_size";
    private static final String MAX_RETIRE_PERCENT = "max_retire_percent";

    private static final String EXPECTED_TOKEN = "expectedToken";
    private static final String AMOUNT_RECEIVED = "amountReceived";

    public static final VarDB<Boolean> loansOn = Context.newVarDB(LOANS_ON, Boolean.class);

    static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    static final VarDB<Address> dex = Context.newVarDB(DEX, Address.class);
    static final VarDB<Address> rebalancing = Context.newVarDB(REBALANCE, Address.class);
    static final VarDB<Address> dividends = Context.newVarDB(DIVIDENDS, Address.class);
    static final VarDB<Address> reserve = Context.newVarDB(RESERVE, Address.class);
    static final VarDB<Address> rewards = Context.newVarDB(REWARDS, Address.class);
    static final VarDB<Address> staking = Context.newVarDB(STAKING, Address.class);
    static final VarDB<Address> feehandler = Context.newVarDB(FEEHANDLER, Address.class);

    static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> miningRatio = Context.newVarDB(MINING_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> lockingRatio = Context.newVarDB(LOCKING_RATIO, BigInteger.class);
    public static final DictDB<String, BigInteger> totalDebts = Context.newDictDB(TOTAL_DEBT, BigInteger.class);

    public static final VarDB<BigInteger> liquidationRatio = Context.newVarDB(LIQUIDATION_RATIO, BigInteger.class);
    static final VarDB<BigInteger> originationFee = Context.newVarDB(ORIGINATION_FEE, BigInteger.class);
    static final VarDB<BigInteger> redemptionFee = Context.newVarDB(REDEMPTION_FEE, BigInteger.class);
    static final VarDB<BigInteger> retirementBonus = Context.newVarDB(RETIREMENT_BONUS, BigInteger.class);
    static final VarDB<BigInteger> liquidationReward = Context.newVarDB(LIQUIDATION_REWARD, BigInteger.class);
    static final VarDB<BigInteger> newLoanMinimum = Context.newVarDB(NEW_LOAN_MINIMUM, BigInteger.class);
    static final VarDB<Integer> maxDebtsListLength = Context.newVarDB(MAX_DEBTS_LIST_LENGTH, Integer.class);
    static final VarDB<Integer> redeemBatch = Context.newVarDB(REDEEM_BATCH_SIZE, Integer.class);
    static final VarDB<BigInteger> maxRetirePercent = Context.newVarDB(MAX_RETIRE_PERCENT, BigInteger.class);

    static final VarDB<Address> expectedToken = Context.newVarDB(EXPECTED_TOKEN, Address.class);
    static final VarDB<BigInteger> amountReceived = Context.newVarDB(AMOUNT_RECEIVED, BigInteger.class);
}
