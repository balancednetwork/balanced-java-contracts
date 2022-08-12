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

package network.balanced.score.core.dividends;

import java.math.BigInteger;

public class Constants {
    static final String TAG = "Balanced Dividends";

    static final String GOVERNANCE = "governance";
    static final String ADMIN = "admin";
    static final String DAOFUND = "daofund";

    static final String ACCEPTED_TOKENS = "accepted_tokens";
    public static final String AMOUNT_TO_DISTRIBUTE = "amount_to_distribute";
    public static final String AMOUNT_BEING_DISTRIBUTED = "amount_being_distributed";
    public static final String BALN_DIST_INDEX = "baln_dist_index";

    public static final String STAKED_BALN_HOLDERS = "staked_baln_holders";
    public static final String STAKED_DIST_INDEX = "staked_dist_index";

    public static final String BALN_IN_DEX = "baln_in_dex";
    public static final String TOTAL_LP_TOKENS = "total_lp_tokens";
    public static final String LP_HOLDERS_INDEX = "lp_holders_index";
    public static final String USERS_BALANCE = "users_balance";

    public static final String DIVIDENDS_DISTRIBUTION_STATUS = "dividends_distribution_status";
    static final String SNAPSHOT_ID = "snapshot_id";
    static final String AMOUNT_RECEIVED_STATUS = "amount_received_status";
    static final String DAILY_FEES = "daily_fees";

    static final String MAX_LOOP_COUNT = "max_loop_count";
    static final String MIN_ELIGIBLE_DEBT = "minimum_eligible_debt";
    static final String DIVIDENDS_CATEGORIES = "dividends_categories";
    static final String DIVIDENDS_PERCENTAGE = "dividends_percentage";

    static final String DISTRIBUTION_ACTIVATE = "distribution_activate";
    static final String DIVIDENDS_BATCH_SIZE = "dividends_batch_size";

    static final String CLAIMED_BIT_MAP = "claimed_bit_map_";
    static final String TIME_OFFSET = "time_offset";

    static final String DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY = "dividends_enabled_to_staked_baln_only_day";
    static final String CONTINUOUS_DIVIDENDS_DAY = "continuous_dividednds_day";

    static final String SNAPSHOT_DIVIDENDS = "snapshot_dividends";
    static final String TOTAL_SNAPSHOT = "total_snapshots";
    static final String COMPLETE_DIVIDENDS_CATEGORIES = "complete_dividends_categories";

    static final String ACCRUED_DIVIDENDS = "accrued_dividends";

    static final BigInteger TWO_FIFTY_SIX = BigInteger.valueOf(256);


    static BigInteger MAX_LOOP = BigInteger.valueOf(50);
    static String BALN_HOLDERS = "baln_holders";
    static String DAO_FUND = "daofund";

    static BigInteger BALNBNUSD_ID = BigInteger.valueOf(3);
    static BigInteger BALNSICX_ID = BigInteger.valueOf(4);

    static final String IDS = "ids";
    static final String AMOUNT = "amount";
}
