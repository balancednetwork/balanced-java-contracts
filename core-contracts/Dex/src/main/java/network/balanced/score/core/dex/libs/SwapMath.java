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

import network.balanced.score.core.dex.structs.pool.ComputeSwapStepResult;
import static network.balanced.score.core.dex.utils.IntUtils.uint256;

public class SwapMath {
  /**
   * @notice Computes the result of swapping some amount in, or amount out, given the parameters of the swap
   * @dev The fee, plus the amount in, will never exceed the amount remaining if the swap's `amountSpecified` is positive
   * @param sqrtRatioCurrentX96 The current sqrt price of the pool
   * @param sqrtRatioTargetX96 The price that cannot be exceeded, from which the direction of the swap is inferred
   * @param liquidity The usable liquidity
   * @param amountRemaining How much input or output amount is remaining to be swapped in/out
   * @param feePips The fee taken from the input amount, expressed in hundredths of a bip
   * @return sqrtRatioNextX96 The price after swapping the amount in/out, not to exceed the price target
   * @return amountIn The amount to be swapped in, of either token0 or token1, based on the direction of the swap
   * @return amountOut The amount to be received, of either token0 or token1, based on the direction of the swap
   * @return feeAmount The amount of input that will be taken as a fee
   */
  public static ComputeSwapStepResult computeSwapStep (
    BigInteger sqrtRatioCurrentX96, 
    BigInteger sqrtRatioTargetX96, 
    BigInteger liquidity,
    BigInteger amountRemaining, 
    int feePips
  ) {
    boolean zeroForOne = sqrtRatioCurrentX96.compareTo(sqrtRatioTargetX96) >= 0;
    boolean exactIn = amountRemaining.compareTo(ZERO) >= 0;
    final BigInteger TEN_E6 = BigInteger.valueOf(1000000);

    BigInteger sqrtRatioNextX96 = ZERO;
    BigInteger amountIn = ZERO;
    BigInteger amountOut = ZERO;
    BigInteger feeAmount = ZERO;

    if (exactIn) {
      BigInteger amountRemainingLessFee = FullMath.mulDiv(amountRemaining, TEN_E6.subtract(BigInteger.valueOf(feePips)), TEN_E6);
      amountIn = zeroForOne
          ? SqrtPriceMath.getAmount0Delta(sqrtRatioTargetX96, sqrtRatioCurrentX96, liquidity, true)
          : SqrtPriceMath.getAmount1Delta(sqrtRatioCurrentX96, sqrtRatioTargetX96, liquidity, true);

      if (amountRemainingLessFee.compareTo(amountIn) >= 0) {
        sqrtRatioNextX96 = sqrtRatioTargetX96;
      }
      else {
        sqrtRatioNextX96 = SqrtPriceMath.getNextSqrtPriceFromInput(
          sqrtRatioCurrentX96,
          liquidity,
          amountRemainingLessFee,
          zeroForOne
        );
      }
  } else {
    amountOut = zeroForOne
          ? SqrtPriceMath.getAmount1Delta(sqrtRatioTargetX96, sqrtRatioCurrentX96, liquidity, false)
          : SqrtPriceMath.getAmount0Delta(sqrtRatioCurrentX96, sqrtRatioTargetX96, liquidity, false);

      if (uint256(amountRemaining.negate()).compareTo(amountOut) >= 0) {
        sqrtRatioNextX96 = sqrtRatioTargetX96;
      }
      else {
        sqrtRatioNextX96 = SqrtPriceMath.getNextSqrtPriceFromOutput(
          sqrtRatioCurrentX96,
          liquidity,
          uint256(amountRemaining.negate()),
          zeroForOne
        );
      }
    }


    boolean max = sqrtRatioTargetX96.equals(sqrtRatioNextX96);

    // get the input/output amounts
    if (zeroForOne) {
        amountIn = max && exactIn
            ? amountIn
            : SqrtPriceMath.getAmount0Delta(sqrtRatioNextX96, sqrtRatioCurrentX96, liquidity, true);
        amountOut = max && !exactIn
            ? amountOut
            : SqrtPriceMath.getAmount1Delta(sqrtRatioNextX96, sqrtRatioCurrentX96, liquidity, false);
    } else {
        amountIn = max && exactIn
            ? amountIn
            : SqrtPriceMath.getAmount1Delta(sqrtRatioCurrentX96, sqrtRatioNextX96, liquidity, true);
        amountOut = max && !exactIn
            ? amountOut
            : SqrtPriceMath.getAmount0Delta(sqrtRatioCurrentX96, sqrtRatioNextX96, liquidity, false);
    }

    // cap the output amount to not exceed the remaining output amount
    if (!exactIn && amountOut.compareTo(uint256(amountRemaining.negate())) > 0) {
        amountOut = uint256(amountRemaining.negate());
    }

    if (exactIn && sqrtRatioNextX96 != sqrtRatioTargetX96) {
        // we didn't reach the target, so take the remainder of the maximum input as fee
        feeAmount = uint256(amountRemaining).subtract(amountIn);
    } else {
        feeAmount = FullMath.mulDivRoundingUp(amountIn, BigInteger.valueOf(feePips), TEN_E6.subtract(BigInteger.valueOf(feePips)));
    }

    return new ComputeSwapStepResult(sqrtRatioNextX96, amountIn, amountOut, feeAmount);
  }
}
