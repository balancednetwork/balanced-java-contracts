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

package network.balanced.score.core.positionmgr.libs;

import static network.balanced.score.core.dex.utils.IntUtils.uint128;

import java.math.BigInteger;
import network.balanced.score.core.dex.libs.FixedPoint96;
import network.balanced.score.core.dex.libs.FullMath;

public class LiquidityAmounts {

  /**
   * @notice Computes the maximum amount of liquidity received for a given amount of token0, token1, the current
   * pool prices and the prices at the tick boundaries
   * @param sqrtRatioX96 A sqrt price representing the current pool prices
   * @param sqrtRatioAX96 A sqrt price representing the first tick boundary
   * @param sqrtRatioBX96 A sqrt price representing the second tick boundary
   * @param amount0 The amount of token0 being sent in
   * @param amount1 The amount of token1 being sent in
   * @return liquidity The maximum amount of liquidity received
   */
  public static BigInteger getLiquidityForAmounts (
    BigInteger sqrtRatioX96, 
    BigInteger sqrtRatioAX96,
    BigInteger sqrtRatioBX96, 
    BigInteger amount0, 
    BigInteger amount1
  ) {
    BigInteger liquidity = BigInteger.ZERO;

    if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
      BigInteger tmp = sqrtRatioAX96;
      sqrtRatioAX96 = sqrtRatioBX96;
      sqrtRatioBX96 = tmp;
    }

    if (sqrtRatioX96.compareTo(sqrtRatioAX96) <= 0) {
      liquidity = getLiquidityForAmount0(sqrtRatioAX96, sqrtRatioBX96, amount0);
    } else if (sqrtRatioX96.compareTo(sqrtRatioBX96) < 0) {
      BigInteger liquidity0 = getLiquidityForAmount0(sqrtRatioX96, sqrtRatioBX96, amount0);
      BigInteger liquidity1 = getLiquidityForAmount1(sqrtRatioAX96, sqrtRatioX96, amount1);
      liquidity = liquidity0.compareTo(liquidity1) < 0 ? liquidity0 : liquidity1;
    } else {
      liquidity = getLiquidityForAmount1(sqrtRatioAX96, sqrtRatioBX96, amount1);
    }

    return liquidity;
  }

  /**
   * @notice Computes the amount of token0 for a given amount of liquidity and a price range
   * @param sqrtRatioAX96 A sqrt price representing the first tick boundary
   * @param sqrtRatioBX96 A sqrt price representing the second tick boundary
   * @param liquidity The liquidity being valued
   * @return amount0 The amount of token0
   */
  private static BigInteger getLiquidityForAmount0(
    BigInteger sqrtRatioAX96, 
    BigInteger sqrtRatioBX96,
    BigInteger amount0
  ) {
    if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
      BigInteger tmp = sqrtRatioAX96;
      sqrtRatioAX96 = sqrtRatioBX96;
      sqrtRatioBX96 = tmp;
    }

    BigInteger intermediate = FullMath.mulDiv(sqrtRatioAX96, sqrtRatioBX96, FixedPoint96.Q96);
    return uint128(FullMath.mulDiv(amount0, intermediate, sqrtRatioBX96.subtract(sqrtRatioAX96)));
  }

  /**
   * @notice Computes the amount of token1 for a given amount of liquidity and a price range
   * @param sqrtRatioAX96 A sqrt price representing the first tick boundary
   * @param sqrtRatioBX96 A sqrt price representing the second tick boundary
   * @param liquidity The liquidity being valued
   * @return amount1 The amount of token1
   */
  private static BigInteger getLiquidityForAmount1(
    BigInteger sqrtRatioAX96,
    BigInteger sqrtRatioBX96,
    BigInteger amount1
  ) {
    if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
      BigInteger tmp = sqrtRatioAX96;
      sqrtRatioAX96 = sqrtRatioBX96;
      sqrtRatioBX96 = tmp;
    }

    return uint128(FullMath.mulDiv(amount1, FixedPoint96.Q96, sqrtRatioBX96.subtract(sqrtRatioAX96)));
  }
}
