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
import score.VarDB;

import java.math.BigInteger;

public class LoansVariables {

    private static final String _LOANS_ON = "loans_on";
    private static final String _GOVERNANCE = "governance";
    private static final String _REBALANCE = "rebalance";
    private static final String _DEX = "dex";
    private static final String _DIVIDENDS = "dividends";
    private static final String _RESERVE = "reserve";
    private static final String _REWARDS = "rewards";
    private static final String _STAKING = "staking";
    private static final String _ADMIN = "admin";
    private static final String _SNAP_BATCH_SIZE = "snap_batch_size";
    private static final String _GLOBAL_INDEX = "global_index";
    private static final String _GLOBAL_BATCH_INDEX = "global_batch_index";

    private static final String _REWARDS_DONE = "rewards_done";
    private static final String _DIVIDENDS_DONE = "dividends_done";
    private static final String _CURRENT_DAY = "current_day";
    private static final String _TIME_OFFSET = "time_offset";

    private static final String _MINING_RATIO = "mining_ratio";
    private static final String _LOCKING_RATIO = "locking_ratio";
    private static final String _LIQUIDATION_RATIO = "liquidation_ratio";
    private static final String _ORIGINATION_FEE = "origination_fee";
    private static final String _REDEMPTION_FEE = "redemption_fee";
    private static final String _RETIREMENT_BONUS = "retirement_bonus";
    private static final String _LIQUIDATION_REWARD = "liquidation_reward";
    private static final String _NEW_LOAN_MINIMUM = "new_loan_minimum";
    private static final String _MIN_MINING_DEBT = "min_mining_debt";
    private static final String _MAX_DEBTS_LIST_LENGTH = "max_debts_list_length";

    private static final String _REDEEM_BATCH_SIZE = "redeem_batch_size";
    private static final String _MAX_RETIRE_PERCENT = "max_retire_percent";
    private static final String _CONTINUOUS_REWARD_DAY = "continuous_reward_day";

    public static final VarDB<Boolean> loansOn = Context.newVarDB(_LOANS_ON, Boolean.class);

    public static final VarDB<Address> admin = Context.newVarDB(_ADMIN, Address.class);
    public static final VarDB<Address> governance = Context.newVarDB(_GOVERNANCE, Address.class);
    public static final VarDB<Address> dex = Context.newVarDB(_DEX, Address.class);
    public static final VarDB<Address> rebalancing = Context.newVarDB(_REBALANCE, Address.class);
    public static final VarDB<Address> dividends = Context.newVarDB(_DIVIDENDS, Address.class);
    public static final VarDB<Address> reserve = Context.newVarDB(_RESERVE, Address.class);
    public static final VarDB<Address> rewards = Context.newVarDB(_REWARDS, Address.class);
    public static final VarDB<Address> staking = Context.newVarDB(_STAKING, Address.class);

    public static final VarDB<Integer> snapBatchSize = Context.newVarDB(_SNAP_BATCH_SIZE, Integer.class);
    public static final VarDB<BigInteger> globalIndex = Context.newVarDB(_GLOBAL_INDEX, BigInteger.class);
    public static final VarDB<BigInteger> globalBatchIndex = Context.newVarDB(_GLOBAL_BATCH_INDEX, BigInteger.class);

    public static final VarDB<Boolean> rewardsDone = Context.newVarDB(_REWARDS_DONE, Boolean.class);
    public static final VarDB<Boolean> dividendsDone = Context.newVarDB(_DIVIDENDS_DONE, Boolean.class);
    public static final VarDB<BigInteger> continuousRewardDay = Context.newVarDB(_CONTINUOUS_REWARD_DAY, BigInteger.class);
    public static final VarDB<BigInteger> currentDay = Context.newVarDB(_CURRENT_DAY, BigInteger.class);
    public static final VarDB<BigInteger> timeOffset = Context.newVarDB(_TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> miningRatio = Context.newVarDB(_MINING_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> lockingRatio  = Context.newVarDB(_LOCKING_RATIO, BigInteger.class);

    public static final VarDB<BigInteger> liquidationRatio = Context.newVarDB(_LIQUIDATION_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> originationFee = Context.newVarDB(_ORIGINATION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> redemptionFee = Context.newVarDB(_REDEMPTION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> retirementBonus = Context.newVarDB(_RETIREMENT_BONUS, BigInteger.class);
    public static final VarDB<BigInteger> liquidationReward = Context.newVarDB(_LIQUIDATION_REWARD, BigInteger.class);
    public static final VarDB<BigInteger> newLoanMinimum = Context.newVarDB(_NEW_LOAN_MINIMUM, BigInteger.class);
    public static final VarDB<BigInteger> minMiningDebt = Context.newVarDB(_MIN_MINING_DEBT, BigInteger.class);
    public static final VarDB<Integer> maxDebtsListLength = Context.newVarDB(_MAX_DEBTS_LIST_LENGTH, Integer.class);
    public static final VarDB<Integer> redeemBatch = Context.newVarDB(_REDEEM_BATCH_SIZE, Integer.class);
    public static final VarDB<BigInteger> maxRetirePercent = Context.newVarDB(_MAX_RETIRE_PERCENT, BigInteger.class);

    public static final VarDB<Address> expectedToken = Context.newVarDB("expectedToken", Address.class);
    public static final VarDB<BigInteger> amountReceived = Context.newVarDB("amountReceived", BigInteger.class);
}
