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

package network.balanced.score.lib.utils;

import score.Address;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Math.pow;

public class Constants {
    public final static BigInteger EXA = pow(BigInteger.TEN, 18);
    public final static BigInteger POINTS = BigInteger.valueOf(10000);
    public static final BigInteger MICRO_SECONDS_IN_A_SECOND = BigInteger.valueOf(1_000_000);
    public static final BigInteger MICRO_SECONDS_IN_A_DAY =
            BigInteger.valueOf(86400).multiply(MICRO_SECONDS_IN_A_SECOND);
    public static final BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);
    public static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365L);
    public static final BigInteger YEAR_IN_MICRO_SECONDS = DAYS_PER_YEAR.multiply(MICRO_SECONDS_IN_A_DAY);
    public final static Address EOA_ZERO = new Address(new byte[Address.LENGTH]);
    public static final BigInteger MAX_LOCK_TIME = BigInteger.valueOf(4L).multiply(YEAR_IN_MICRO_SECONDS);

}
