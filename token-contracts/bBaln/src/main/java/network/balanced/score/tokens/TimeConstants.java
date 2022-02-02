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

import java.math.BigInteger;

public class TimeConstants {
    public static final BigInteger SECOND = BigInteger.valueOf(1000000L);
    public static final BigInteger MINUTE = BigInteger.valueOf(60L).multiply(SECOND);
    public static final BigInteger HOUR = BigInteger.valueOf(60L).multiply(MINUTE);
    public static final BigInteger DAY = BigInteger.valueOf(24L).multiply(HOUR);
    public static final BigInteger WEEK = BigInteger.valueOf(7L).multiply(DAY);
    public static final UnsignedBigInteger U_WEEK = new UnsignedBigInteger(WEEK);
    public static final BigInteger MONTH = BigInteger.valueOf(30L).multiply(DAY);
    public static final BigInteger YEAR = BigInteger.valueOf(365L).multiply(DAY);
}
