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

package network.balanced.score.tokens.balancedtoken;

import network.balanced.score.lib.utils.Names;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.utils.Math.pow;

public interface Constants {

    BigInteger INITIAL_PRICE_ESTIMATE = pow(BigInteger.TEN, 17); //# loop
    BigInteger MIN_UPDATE_TIME = BigInteger.TWO.multiply(MICRO_SECONDS_IN_A_SECOND); //2 seconds
    BigInteger MINIMUM_STAKE_AMOUNT = EXA;
    BigInteger DEFAULT_UNSTAKING_PERIOD = BigInteger.valueOf(3).multiply(MICRO_SECONDS_IN_A_DAY);

    String IDS = "ids";
    String AMOUNT = "amount";

    String TAG = "BALN";
    String TOKEN_NAME = Names.BALN;
    String SYMBOL_NAME = "BALN";
    String DEFAULT_ORACLE_NAME = "Balanced DEX";

    String PRICE_UPDATE_TIME = "price_update_time";
    String LAST_PRICE = "last_price";
    String MIN_INTERVAL = "min_interval";

    String STAKING_ENABLED = "staking_enabled";

    String STAKED_BALANCES = "staked_balances";
    String MINIMUM_STAKE = "minimum_stake";
    String UNSTAKING_PERIOD = "unstaking_period";
    String TOTAL_STAKED_BALANCE = "total_staked_balance";

    String DIVIDENDS_SCORE = "dividends_score";
    String GOVERNANCE = "governance";

    String DEX_SCORE = "dex_score";
    String BNUSD_SCORE = "bnUSD_score";
    String ORACLE = "oracle";
    String ORACLE_NAME = "oracle_name";

    String TIME_OFFSET = "time_offset";
    String STAKE_SNAPSHOTS = "stake_snapshots";
    String TOTAL_SNAPSHOTS = "total_snapshots";
    String TOTAL_STAKED_SNAPSHOT = "total_staked_snapshot";
    String TOTAL_STAKED_SNAPSHOT_COUNT = "total_staked_snapshot_count";

    String ENABLE_SNAPSHOTS = "enable_snapshots";
    String ADMIN = "admin_address";
    String VERSION = "version";

}
