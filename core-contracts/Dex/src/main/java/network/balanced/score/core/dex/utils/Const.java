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

package network.balanced.score.core.dex.utils;

import score.Address;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.pow;
public class Const {

    public static final String SICXICX_MARKET_NAME = "sICX/ICX";

    public static final int SICXICX_POOL_ID = 1;
    public final static BigInteger SECOND = pow(BigInteger.TEN,6);
    public final static BigInteger U_SECONDS_DAY = BigInteger.valueOf(86_400).multiply(SECOND);
    public static final BigInteger MIN_LIQUIDITY = BigInteger.valueOf(1_000);
    public static final BigInteger FEE_SCALE = BigInteger.valueOf(10_000);
    public static final int FIRST_NON_BALANCED_POOL = 6;
    public static final Integer ICX_QUEUE_FILL_DEPTH = 50;

    public static final int USDS_BNUSD_ID = 10;
    public static final int IUSDT_BNUSD_ID = 15;
    public static final BigInteger WITHDRAW_LOCK_TIMEOUT = U_SECONDS_DAY;
    public static final Address MINT_ADDRESS = EOA_ZERO;
    public static final String TAG = "Balanced DEX";

    public static final String IDS = "ids";
    public static final String VALUES = "values";
    public static final String LENGTH = "length";
}
