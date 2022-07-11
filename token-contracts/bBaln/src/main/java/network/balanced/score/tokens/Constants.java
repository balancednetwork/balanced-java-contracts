/*
 * Copyright (c) 2021-2022 Balanced.network.
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

package network.balanced.score.tokens;

import network.balanced.score.tokens.utils.UnsignedBigInteger;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

public class Constants {
    //time constants
    public static final BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);
    public static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365L);
    public static final BigInteger YEAR_IN_MICRO_SECONDS = DAYS_PER_YEAR.multiply(MICRO_SECONDS_IN_A_DAY);

    public static final UnsignedBigInteger U_WEEK_IN_MICRO_SECONDS = new UnsignedBigInteger(WEEK_IN_MICRO_SECONDS);
}
