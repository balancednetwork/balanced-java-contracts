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

package network.balanced.score.core.staking.utils;

import score.Address;

import java.math.BigInteger;

public class Constant {
    public static final String TAG = "Staked ICX Manager";
    public static final Address SYSTEM_SCORE_ADDRESS = Address.fromString("cx0000000000000000000000000000000000000000");
    public static final BigInteger DENOMINATOR = BigInteger.valueOf(1000000000000000000L);
    public static final BigInteger HUNDRED = BigInteger.valueOf(100L);
    public static final BigInteger TOP_PREP_COUNT = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_UNSTAKE_BATCH_LIMIT = BigInteger.valueOf(200L);
    public static final BigInteger MAX_ITERATION_LOOP = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18L);

    public static final String SICX_SUPPLY = "sICX_supply";
    public static final String RATE = "_rate";
    public static final String SICX_ADDRESS = "sICX_address";
    public static final String BLOCK_HEIGHT_WEEK = "_block_height_week";
    public static final String BLOCK_HEIGHT_DAY = "_block_height_day";
    public static final String TOTAL_STAKE = "_total_stake";
    public static final String DAILY_REWARD = "_daily_reward";
    public static final String TOTAL_LIFETIME_REWARD = "_total_lifetime_reward";
    public static final String DISTRIBUTING = "_distributing";
    public static final String LINKED_LIST_VAR = "_linked_list_var";
    public static final String TOP_PREPS = "_top_preps";
    public static final String PREP_LIST = "_prep_list";
    public static final String ADDRESS_DELEGATIONS = "_address_delegations";
    public static final String PREP_DELEGATIONS = "_prep_delegations";
    public static final String TOTAL_UNSTAKE_AMOUNT = "_total_unstake_amount";
    public static final String UNSTAKE_BATCH_LIMIT = "_unstake_batch_limit";
    public static final String STAKING_ON = "staking_on";
    public static final String ICX_PAYABLE = "icx_payable";
    public static final String ICX_TO_CLAIM = "icx_to_claim";
}
