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

import java.math.BigInteger;
import network.balanced.score.core.dex.utils.IntUtils;
import score.Context;

public class BitMath {

  /**
   * @notice Returns the index of the most significant bit of the number,
   *     where the least significant bit is at index 0 and the most significant bit is at index 255
   * @dev The function satisfies the property:
   *     x >= 2**mostSignificantBit(x) and x < 2**(mostSignificantBit(x)+1)
   * @param x the value for which to compute the most significant bit, must be greater than 0
   * @return r the index of the most significant bit
   */
  public static int mostSignificantBit(BigInteger x) {
    Context.require(x.compareTo(BigInteger.ZERO) > 0);
    char r = 0;

    if (x.compareTo(new BigInteger("100000000000000000000000000000000", 16)) >= 0) {
        x = x.shiftRight(128);
        r += 128;
    }
    if (x.compareTo(new BigInteger("10000000000000000", 16)) >= 0) {
        x = x.shiftRight(64);
        r += 64;
    }
    if (x.compareTo(new BigInteger("100000000", 16)) >= 0) {
        x = x.shiftRight(32);
        r += 32;
    }
    if (x.compareTo(new BigInteger("10000", 16)) >= 0) {
        x = x.shiftRight(16);
        r += 16;
    }
    if (x.compareTo(new BigInteger("100", 16)) >= 0) {
        x = x.shiftRight(8);
        r += 8;
    }
    if (x.compareTo(new BigInteger("10", 16)) >= 0) {
        x = x.shiftRight(4);
        r += 4;
    }
    if (x.compareTo(new BigInteger("4", 16)) >= 0) {
        x = x.shiftRight(2);
        r += 2;
    }
    if (x.compareTo(new BigInteger("2", 16)) >= 0) {
      r += 1;
    }

    return r;
  }

  public static int leastSignificantBit(BigInteger x) {
    Context.require(x.compareTo(BigInteger.ZERO) > 0);

    char r = 255;

    if (x.and(IntUtils.MAX_UINT128).compareTo(BigInteger.ZERO) > 0) {
        r -= 128;
    } else {
        x = x.shiftRight(128);
    }
    if (x.and(IntUtils.MAX_UINT64).compareTo(BigInteger.ZERO) > 0) {
        r -= 64;
    } else {
        x = x.shiftRight(64);
    }
    if (x.and(IntUtils.MAX_UINT32).compareTo(BigInteger.ZERO) > 0) {
        r -= 32;
    } else {
        x = x.shiftRight(32);
    }
    if (x.and(IntUtils.MAX_UINT16).compareTo(BigInteger.ZERO) > 0) {
        r -= 16;
    } else {
        x = x.shiftRight(16);
    }
    if (x.and(IntUtils.MAX_UINT8).compareTo(BigInteger.ZERO) > 0) {
        r -= 8;
    } else {
        x = x.shiftRight(8);
    }
    if (x.and(BigInteger.valueOf(0xf)).compareTo(BigInteger.ZERO) > 0) {
        r -= 4;
    } else {
        x = x.shiftRight(4);
    }
    if (x.and(BigInteger.valueOf(0x3)).compareTo(BigInteger.ZERO) > 0) {
        r -= 2;
    } else {
        x = x.shiftRight(2);
    }
    if (x.and(BigInteger.valueOf(0x1)).compareTo(BigInteger.ZERO) > 0) {
      r -= 1;
    }

    return r;
  }

}
