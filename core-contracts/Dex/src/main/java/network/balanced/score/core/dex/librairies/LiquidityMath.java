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

package network.balanced.score.core.dex.librairies;

import static network.balanced.score.core.dex.utils.IntUtils.uint128;

import java.math.BigInteger;

import score.Context;

public class LiquidityMath {
  /**
   * @notice Add a signed liquidity delta to liquidity and revert if it overflows or underflows
   * @param x The liquidity before change
   * @param y The delta by which liquidity should be changed
   * @return z The liquidity delta
   */
  public static BigInteger addDelta (BigInteger x, BigInteger y) {
    BigInteger z;

    if (y.compareTo(BigInteger.ZERO) < 0) {
      z = uint128(x.subtract(y.negate()));
      Context.require(z.compareTo(x) < 0, 
        "addDelta: z < x");
    } else {
      z = uint128(x.add(y));
      Context.require(z.compareTo(x) >= 0, 
        "addDelta: z >= x");
    }

    return z;
  }
}
