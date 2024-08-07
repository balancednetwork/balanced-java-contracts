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

package network.balanced.score.core.dex.libs;

import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import network.balanced.score.core.dex.utils.IntUtils;
import score.Context;

public class SqrtPriceMath {

  /**
   * @notice Helper that gets signed token0 delta
   * @param sqrtRatioAX96 A sqrt price
   * @param sqrtRatioBX96 Another sqrt price
   * @param liquidity The change in liquidity for which to compute the amount0 delta
   * @return amount0 Amount of token0 corresponding to the passed liquidityDelta between the two prices
   */
  public static BigInteger getAmount0Delta (
    BigInteger sqrtRatioAX96, 
    BigInteger sqrtRatioBX96,
    BigInteger liquidity
  ) {
    return liquidity.compareTo(ZERO) < 0
        ? getAmount0Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity.negate(), false).negate()
        : getAmount0Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity, true);
  }

  public static BigInteger getAmount0Delta (
    BigInteger sqrtRatioAX96, 
    BigInteger sqrtRatioBX96, 
    BigInteger liquidity,
    boolean roundUp
  ) {
    if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
      BigInteger tmp = sqrtRatioAX96;
      sqrtRatioAX96 = sqrtRatioBX96;
      sqrtRatioBX96 = tmp;
    }

    BigInteger numerator1 = liquidity.shiftLeft(FixedPoint96.RESOLUTION);
    BigInteger numerator2 = sqrtRatioBX96.subtract(sqrtRatioAX96);

    Context.require(sqrtRatioAX96.compareTo(ZERO) > 0);

    return roundUp ? 
      UnsafeMath.divRoundingUp (
          FullMath.mulDivRoundingUp(numerator1, numerator2, sqrtRatioBX96),
          sqrtRatioAX96
      )
      : FullMath.mulDiv(numerator1, numerator2, sqrtRatioBX96).divide(sqrtRatioAX96);
  }

  public static BigInteger getAmount1Delta (
    BigInteger sqrtRatioAX96, 
    BigInteger sqrtRatioBX96,
    BigInteger liquidity
  ) {
    return liquidity.compareTo(ZERO) < 0
      ? getAmount1Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity.negate(), false).negate()
      : getAmount1Delta(sqrtRatioAX96, sqrtRatioBX96, liquidity, true);
  }

  public static BigInteger getAmount1Delta (
    BigInteger sqrtRatioAX96, 
    BigInteger sqrtRatioBX96, 
    BigInteger liquidity,
    boolean roundUp
  ) {
    if (sqrtRatioAX96.compareTo(sqrtRatioBX96) > 0) {
      BigInteger tmp = sqrtRatioAX96;
      sqrtRatioAX96 = sqrtRatioBX96;
      sqrtRatioBX96 = tmp;
    }

    return
      roundUp
        ? FullMath.mulDivRoundingUp(liquidity, sqrtRatioBX96.subtract(sqrtRatioAX96), FixedPoint96.Q96)
        : FullMath.mulDiv(liquidity, sqrtRatioBX96.subtract(sqrtRatioAX96), FixedPoint96.Q96);
  }

  /**
   * @notice Gets the next sqrt price given a delta of token0
   * @dev Always rounds up, because in the exact output case (increasing price) we need to move the price at least
   * far enough to get the desired output amount, and in the exact input case (decreasing price) we need to move the
   * price less in order to not send too much output.
   * The most precise formula for this is liquidity * sqrtPX96 / (liquidity +- amount * sqrtPX96),
   * if this is impossible because of overflow, we calculate liquidity / (liquidity / sqrtPX96 +- amount).
   * @param sqrtPX96 The starting price, i.e. before accounting for the token0 delta
   * @param liquidity The amount of usable liquidity
   * @param amount How much of token0 to add or remove from virtual reserves
   * @param add Whether to add or remove the amount of token0
   * @return The price after adding or removing amount, depending on add
   */
  private static BigInteger getNextSqrtPriceFromAmount0RoundingUp (
    BigInteger sqrtPX96, 
    BigInteger liquidity,
    BigInteger amount, 
    boolean add
  ) {
    // we short circuit amount == 0 because the result is otherwise not guaranteed to equal the input price
    if (amount.equals(ZERO)) {
      return sqrtPX96;
    }

    BigInteger numerator1 = liquidity.shiftLeft(FixedPoint96.RESOLUTION);

    if (add) {
      BigInteger product = amount.multiply(sqrtPX96);
      if (product.divide(amount).equals(sqrtPX96)) {
        BigInteger denominator = numerator1.add(product);
        if (denominator.compareTo(numerator1) >= 0) {
          // always fits in 160 bits
          return FullMath.mulDivRoundingUp(numerator1, sqrtPX96, denominator);
        }
      }

      return UnsafeMath.divRoundingUp(numerator1, numerator1.divide(sqrtPX96).add(amount));
    } else {
        // if the product overflows, we know the denominator underflows
        // in addition, we must check that the denominator does not underflow
        BigInteger product = amount.multiply(sqrtPX96);
        Context.require(product.divide(amount).equals(sqrtPX96) && numerator1.compareTo(product) > 0,
          "getNextSqrtPriceFromAmount0RoundingUp: denominator underflow");
        
        BigInteger denominator = numerator1.subtract(product);
        return FullMath.mulDivRoundingUp(numerator1, sqrtPX96, denominator);
    }
  }

  /**
   * @notice Gets the next sqrt price given a delta of token1
   * @dev Always rounds down, because in the exact output case (decreasing price) we need to move the price at least
   * far enough to get the desired output amount, and in the exact input case (increasing price) we need to move the
   * price less in order to not send too much output.
   * The formula we compute is within <1 wei of the lossless version: sqrtPX96 +- amount / liquidity
   * @param sqrtPX96 The starting price, i.e., before accounting for the token1 delta
   * @param liquidity The amount of usable liquidity
   * @param amount How much of token1 to add, or remove, from virtual reserves
   * @param add Whether to add, or remove, the amount of token1
   * @return The price after adding or removing `amount`
   */
  private static BigInteger getNextSqrtPriceFromAmount1RoundingDown (
    BigInteger sqrtPX96, 
    BigInteger liquidity,
    BigInteger amount, 
    boolean add
  ) {
    // if we're adding (subtracting), rounding down requires rounding the quotient down (up)
    // in both cases, avoid a mulDiv for most inputs
    if (add) {
      BigInteger quotient = amount.compareTo(IntUtils.MAX_UINT160) <= 0 
        ? amount.shiftLeft(FixedPoint96.RESOLUTION).divide(liquidity)
        : FullMath.mulDiv(amount, FixedPoint96.Q96, liquidity);
      
      return sqrtPX96.add(quotient);
    } else {
      BigInteger quotient = amount.compareTo(IntUtils.MAX_UINT160) <= 0
        ? UnsafeMath.divRoundingUp(amount.shiftLeft(FixedPoint96.RESOLUTION), liquidity)
        : FullMath.mulDivRoundingUp(amount, FixedPoint96.Q96, liquidity);
        Context.require(sqrtPX96.compareTo(quotient) > 0, "getNextSqrtPriceFromAmount1RoundingDown");
        // always fits 160 bits
        return sqrtPX96.subtract(quotient);
    }
  }

  /**
   * @notice Gets the next sqrt price given an input amount of token0 or token1
   * @dev Throws if price or liquidity are 0, or if the next price is out of bounds
   * @param sqrtPX96 The starting price, i.e., before accounting for the input amount
   * @param liquidity The amount of usable liquidity
   * @param amountIn How much of token0, or token1, is being swapped in
   * @param zeroForOne Whether the amount in is token0 or token1
   * @return sqrtQX96 The price after adding the input amount to token0 or token1
   */
  public static BigInteger getNextSqrtPriceFromInput (
    BigInteger sqrtPX96, 
    BigInteger liquidity,
    BigInteger amountIn, 
    boolean zeroForOne
  ) {
    Context.require(sqrtPX96.compareTo(ZERO) > 0, "sqrtPX96 > 0");
    Context.require(liquidity.compareTo(ZERO) > 0, "liquidity > 0");

    // round to make sure that we don't pass the target price
    return zeroForOne ? 
      getNextSqrtPriceFromAmount0RoundingUp(sqrtPX96, liquidity, amountIn, true)
    : getNextSqrtPriceFromAmount1RoundingDown(sqrtPX96, liquidity, amountIn, true);
  }

  /**
   * @notice Gets the next sqrt price given an output amount of token0 or token1
   * @dev Throws if price or liquidity are 0 or the next price is out of bounds
   * @param sqrtPX96 The starting price before accounting for the output amount
   * @param liquidity The amount of usable liquidity
   * @param amountOut How much of token0, or token1, is being swapped out
   * @param zeroForOne Whether the amount out is token0 or token1
   * @return sqrtQX96 The price after removing the output amount of token0 or token1
   */
  public static BigInteger getNextSqrtPriceFromOutput (
    BigInteger sqrtPX96, 
    BigInteger liquidity,
    BigInteger amountOut,
    boolean zeroForOne
  ) {
    Context.require(sqrtPX96.compareTo(ZERO) > 0, "getNextSqrtPriceFromOutput: sqrtPX96 > 0");
    Context.require(liquidity.compareTo(ZERO) > 0, "getNextSqrtPriceFromOutput: liquidity > 0");

    // round to make sure that we pass the target price
    return zeroForOne ? 
        getNextSqrtPriceFromAmount1RoundingDown(sqrtPX96, liquidity, amountOut, false)
      : getNextSqrtPriceFromAmount0RoundingUp(sqrtPX96, liquidity, amountOut, false);
  }
}
