/*
 * Copyright (c) 2021 Balanced.network.
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
import score.Address;

import java.math.BigInteger;

public class Constants {

    //general constants
    public static final Address ZERO_ADDRESS = new Address(new byte[21]);


    //time constants
    public static final BigInteger SECOND = BigInteger.valueOf(1000000L);
    public static final BigInteger MINUTE = BigInteger.valueOf(60L).multiply(SECOND);
    public static final BigInteger HOUR = BigInteger.valueOf(60L).multiply(MINUTE);
    public static final BigInteger DAY = BigInteger.valueOf(24L).multiply(HOUR);
    public static final BigInteger WEEK = BigInteger.valueOf(7L).multiply(DAY);
    public static final BigInteger MONTH = BigInteger.valueOf(30L).multiply(DAY);
    public static final BigInteger YEAR = BigInteger.valueOf(365L).multiply(DAY);
    public static final BigInteger MINUTE_IN_MICRO_SECONDS = BigInteger.valueOf(60L).multiply(SECOND);
    public static final BigInteger HOUR_IN_MICRO_SECONDS = BigInteger.valueOf(60L).multiply(MINUTE_IN_MICRO_SECONDS);
    public static final BigInteger DAY_IN_MICRO_SECONDS = BigInteger.valueOf(24L).multiply(HOUR_IN_MICRO_SECONDS);
    public static final BigInteger DAY_IN_SECONDS = BigInteger.valueOf(60 * 60 * 24);
    public static final BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(DAY_IN_MICRO_SECONDS);
    public static final BigInteger MONTH_IN_MICRO_SECONDS = BigInteger.valueOf(30L).multiply(DAY_IN_MICRO_SECONDS);

    public static final BigInteger DAYS_PER_YEAR = BigInteger.valueOf(365L);
    public static final BigInteger YEAR_IN_MICRO_SECONDS = DAYS_PER_YEAR.multiply(DAY_IN_MICRO_SECONDS);

    public static final UnsignedBigInteger U_WEEK_IN_MICRO_SECONDS = new UnsignedBigInteger(WEEK_IN_MICRO_SECONDS);
}
