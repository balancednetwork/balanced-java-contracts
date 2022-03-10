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

import java.math.BigInteger;
import java.lang.Math;


public class Constant {
    public static final String TAG = "Staked ICX Manager";
    public static final String SYSTEM_SCORE_ADDRESS = "cx0000000000000000000000000000000000000000";
    public static final BigInteger DENOMINATOR = BigInteger.valueOf(1000000000000000000L);
    public static final BigInteger HUNDRED = BigInteger.valueOf(100L);
    public static final BigInteger TOP_PREP_COUNT = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_UNSTAKE_BATCH_LIMIT = BigInteger.valueOf(200L);
    public static final BigInteger MAX_ITERATION_LOOP = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18L);
    long calc = (long) Math.pow(2, 256);
    long newCalc = calc - 1L;
    public final BigInteger DEFAULT_CAP_VALUE = BigInteger.valueOf(newCalc);

//    DEFAULT_CAP_VALUE = 2 ** 256 -1
}
