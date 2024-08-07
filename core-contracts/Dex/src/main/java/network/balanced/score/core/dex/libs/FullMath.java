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

import static network.balanced.score.core.dex.utils.IntUtils.MAX_UINT256;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import score.Context;

public class FullMath {

  public static BigInteger mulDivRoundingUp (BigInteger a, BigInteger b, BigInteger denominator) {
    BigInteger result = mulDiv(a, b, denominator);

    if (mulmod(a, b, denominator).compareTo(ZERO) > 0) {
        Context.require(result.compareTo(MAX_UINT256) < 0);
        result = result.add(ONE);
    }

    return result;
  }

  private static BigInteger mulmod (BigInteger x, BigInteger y, BigInteger m) {
    return x.multiply(y).mod(m);
  }

  /**
   * @notice Calculates floor(a×b÷denominator) with full precision. Throws if result overflows a uint256 or denominator == 0
   * @param a The multiplicand
   * @param b The multiplier
   * @param denominator The divisor
   * @return result The 256-bit result
   * @dev Credit to Remco Bloemen under MIT license https://xn--2-umb.com/21/muldiv
   */
  public static BigInteger mulDiv (BigInteger a, BigInteger b, BigInteger denominator) {
    // BigInteger can reach 512 bits
    return a.multiply(b).divide(denominator);
    /*
    // 512-bit multiply [prod1 prod0] = a * b
    // Compute the product mod 2**256 and mod 2**256 - 1
    // then use the Chinese Remainder Theorem to reconstruct
    // the 512 bit result. The result is stored in two 256
    // variables such that product = prod1 * 2**256 + prod0

    final BigInteger THREE = BigInteger.valueOf(3);

    BigInteger prod0; // Least significant 256 bits of the product
    BigInteger prod1; // Most significant 256 bits of the product

    a = uint256(a);
    b = uint256(b);
    denominator = uint256(denominator);

    BigInteger mm = mulmod(a, b, MAX_UINT256);
    prod0 = mulmod256(a, b);
    prod1 = sub256(sub256(mm, prod0), lt(mm, prod0));

    // Handle non-overflow cases, 256 by 256 division
    if (prod1.equals(ZERO)) {
      Context.require(denominator.compareTo(ZERO) > 0,
        "mulDiv: denominator > 0");
      
      return prod0.divide(denominator);
    }

    // Make sure the result is less than 2**256.
    // Also prevents denominator == 0
    Context.require(denominator.compareTo(prod1) > 0,
      "mulDiv: denominator > prod1");

    ///////////////////////////////////////////////
    // 512 by 256 division.
    ///////////////////////////////////////////////

    // Make division exact by subtracting the remainder from [prod1 prod0]
    // Compute remainder using mulmod
    BigInteger remainder = mulmod(a, b, denominator);

    // Subtract 256 bit number from 512 bit number
    prod1 = sub256(prod1, gt(remainder, prod0));
    prod0 = sub256(prod0, remainder);

    // Factor powers of two out of denominator
    // Compute largest power of two divisor of denominator.
    // Always >= 1.
    BigInteger twos = denominator.negate().and(denominator);

    // Divide denominator by power of two
    denominator = denominator.divide(twos);

    // Divide [prod1 prod0] by the factors of two
    prod0 = prod0.divide(twos);

    // Shift in bits from prod1 into prod0. For this we need
    // to flip `twos` such that it is 2**256 / twos.
    // If twos is zero, then it becomes one
    twos = sub256(ZERO, twos).divide(twos).add(ONE);

    prod0 = prod0.or(mulmod256(prod1, twos));

    // Invert denominator mod 2**256
    // Now that denominator is an odd number, it has an inverse
    // modulo 2**256 such that denominator * inv = 1 mod 2**256.
    // Compute the inverse by starting with a seed that is correct
    // correct for four bits. That is, denominator * inv = 1 mod 2**4
    BigInteger inv = mulmod256(denominator, THREE).xor(TWO);

    // Now use Newton-Raphson iteration to improve the precision.
    // Thanks to Hensel's lifting lemma, this also works in modular
    // arithmetic, doubling the correct bits in each step.
    inv = newtonRaphson(denominator, inv); // inverse mod 2**8
    inv = newtonRaphson(denominator, inv); // inverse mod 2**16
    inv = newtonRaphson(denominator, inv); // inverse mod 2**32
    inv = newtonRaphson(denominator, inv); // inverse mod 2**64
    inv = newtonRaphson(denominator, inv); // inverse mod 2**128
    inv = newtonRaphson(denominator, inv); // inverse mod 2**256

    // Because the division is now exact we can divide by multiplying
    // with the modular inverse of denominator. This will give us the
    // correct result modulo 2**256. Since the precoditions guarantee
    // that the outcome is less than 2**256, this is the final result.
    // We don't need to compute the high bits of the result and prod1
    // is no longer required.
    return mulmod256(prod0, inv);
    */
  }

  // private static BigInteger mulmod256(BigInteger x, BigInteger y) {
  //   return x.multiply(y).mod(TWO_POW_256);
  // }

  // private static BigInteger sub256 (BigInteger a, BigInteger b) {
  //   BigInteger c = a.subtract(b);
  //   if (c.compareTo(ZERO) < 0) {
  //     c = c.add(TWO_POW_256);
  //   }
  //   return c;
  // }

  // private static BigInteger newtonRaphson (BigInteger denominator, BigInteger inv) {
  //   BigInteger a = mulmod256(denominator, inv);
  //   BigInteger b = sub256(TWO, a);
  //   return mulmod256(inv, b);
  // }
}
