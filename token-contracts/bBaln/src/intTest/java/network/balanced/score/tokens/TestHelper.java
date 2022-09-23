/*
 * Copyright (c) 2022 Balanced.network.
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

import java.math.BigInteger;

import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;
import static network.balanced.score.tokens.Constants.YEAR_IN_MICRO_SECONDS;

public class TestHelper {

    static BigInteger getExpectedBalance(BigInteger amount, long unlockTime) {
        BigInteger MAX_TIME = BigInteger.valueOf(4L).multiply(YEAR_IN_MICRO_SECONDS);
        BigInteger slope = amount.divide(MAX_TIME);
        unlockTime =
                (BigInteger.valueOf(unlockTime).divide(WEEK_IN_MICRO_SECONDS).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        BigInteger delta =
                BigInteger.valueOf(unlockTime).subtract(BigInteger.valueOf(System.currentTimeMillis() * 1000));
        BigInteger bias = slope.multiply(delta);
        System.out.println("expected balance is: " + bias);
        return bias;
    }

    static BigInteger getSlope(BigInteger amount, long unlockTime) {
        BigInteger MAX_TIME = BigInteger.valueOf(4L).multiply(YEAR_IN_MICRO_SECONDS);
        return amount.divide(MAX_TIME);
    }

    static long getLockEnd(long unlockTime) {
        return (BigInteger.valueOf(unlockTime).divide(WEEK_IN_MICRO_SECONDS).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
    }
}
