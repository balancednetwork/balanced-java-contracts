/*
 * Copyright (c) 2024 Balanced.network.
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

import java.math.BigInteger;

import score.Context;

public class TimeUtils {

  public static final BigInteger TO_SECONDS = BigInteger.valueOf(1000 * 1000);

  public static final BigInteger ONE_SECOND = BigInteger.valueOf(1);
  public static final BigInteger ONE_MINUTE = BigInteger.valueOf(60).multiply(ONE_SECOND);
  public static final BigInteger ONE_HOUR   = BigInteger.valueOf(60).multiply(ONE_MINUTE);
  public static final BigInteger ONE_DAY    = BigInteger.valueOf(24).multiply(ONE_HOUR);
  public static final BigInteger ONE_WEEK   = BigInteger.valueOf(7).multiply(ONE_DAY);
  public static final BigInteger ONE_MONTH  = BigInteger.valueOf(30).multiply(ONE_DAY);
  public static final BigInteger ONE_YEAR   = BigInteger.valueOf(365).multiply(ONE_DAY);

  public static BigInteger timestampToSeconds (long timestamp) {
    return BigInteger.valueOf(timestamp).divide(TO_SECONDS);
  }

  public static BigInteger now () {
    return timestampToSeconds(Context.getBlockTimestamp());
  }
}
