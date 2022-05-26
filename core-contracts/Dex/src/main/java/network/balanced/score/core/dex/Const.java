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

package network.balanced.score.core.dex;

import score.Address;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EOA_ZERO;

public class Const {

    protected static final String SICXICX_MARKET_NAME = "sICX/ICX";

    protected static final BigInteger SICXICX_POOL_ID = BigInteger.valueOf(1L);
    protected static final BigInteger U_SECONDS_DAY = BigInteger.valueOf(86_400_000_000L);
    protected static final BigInteger MIN_LIQUIDITY = BigInteger.valueOf(1_000);
    protected static final BigInteger FEE_SCALE = BigInteger.valueOf(10_000);
    protected static final BigInteger FIRST_NON_BALANCED_POOL = BigInteger.valueOf(6L);
    protected static final Integer ICX_QUEUE_FILL_DEPTH = 50;
    // TODO Hardcoding some poolIDs should not be done, it should be dynamic. creates issues in testnet as they have
    //  different environment
    protected static final BigInteger USDS_BNUSD_ID = BigInteger.valueOf(10L);
    protected static final BigInteger IUSDT_BNUSD_ID = BigInteger.valueOf(15L);
    protected static final BigInteger WITHDRAW_LOCK_TIMEOUT = U_SECONDS_DAY;
    protected static final Address MINT_ADDRESS = EOA_ZERO;
    protected static final Address DEX_ZERO_SCORE_ADDRESS = Address.fromString("cxf000000000000000000000000000000000000000");
    protected static final String TAG = "Balanced DEX";
}
