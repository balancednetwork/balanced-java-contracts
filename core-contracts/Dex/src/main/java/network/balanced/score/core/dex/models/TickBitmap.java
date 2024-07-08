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

package network.balanced.score.core.dex.models;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static network.balanced.score.core.dex.utils.IntUtils.uint8;
import java.math.BigInteger;
import network.balanced.score.core.dex.librairies.BitMath;
import network.balanced.score.core.dex.structs.pool.NextInitializedTickWithinOneWordResult;
import network.balanced.score.core.dex.utils.IntUtils;
import score.Context;
import score.DictDB;

public class TickBitmap {
  
  // ================================================
  // Consts
  // ================================================
  // Class name
  private static final String NAME = "TickBitmapDB";

  // ================================================
  // DB Variables
  // ================================================
  // Returns 256 packed tick initialized boolean values. See TickBitmap for more information
  private final DictDB<Integer, BigInteger> tickBitmap = Context.newDictDB(NAME + "_tickBitmap", BigInteger.class);
  
  // ================================================
  // Methods 
  // ================================================
  public class PositionResult {
    public int wordPos;
    public int bitPos;
    
    public PositionResult (int wordPos, int bitPos) {
      this.wordPos = wordPos;
      this.bitPos = bitPos;
    }
  }

  public BigInteger get (int index) {
    return this.tickBitmap.getOrDefault(index, ZERO);
  }

  /**
   * @notice Computes the position in the mapping where the initialized bit for a tick lives
   * @param tick The tick for which to compute the position
   * @return wordPos The key in the mapping containing the word in which the bit is stored
   * @return bitPos The bit position in the word where the flag is stored
   */
  private PositionResult position (int tick) {
    int wordPos = tick >> 8;
    int bitPos = uint8(tick % 256);
    return new PositionResult(wordPos, bitPos);
  }

  /**
   * @notice Flips the initialized state for a given tick from false to true, or vice versa
   * @param tick The tick to flip
   * @param tickSpacing The spacing between usable ticks
   */
  public void flipTick (
    int tick,
    int tickSpacing
  ) {
      // ensure that the tick is spaced
      Context.require(tick % tickSpacing == 0, 
        "flipTick: tick isn't spaced");

      var result = position(tick / tickSpacing);
      int wordPos = result.wordPos;
      int bitPos = result.bitPos;

      BigInteger mask = ONE.shiftLeft(bitPos);
      BigInteger packedTick = this.get(wordPos);

      this.tickBitmap.set(wordPos, packedTick.xor(mask));
  }

  public NextInitializedTickWithinOneWordResult nextInitializedTickWithinOneWord (
    int tick, 
    int tickSpacing, 
    boolean lte
  ) {

    int compressed = tick / tickSpacing;

    if (tick < 0 && tick % tickSpacing != 0) {
      compressed--; // round towards negative infinity
    }

    if (lte) {
      var position = position(compressed);
      int wordPos = position.wordPos;
      int bitPos  = position.bitPos;

      var oneShifted = ONE.shiftLeft(bitPos);
      // all the 1s at or to the right of the current bitPos
      BigInteger mask = oneShifted.subtract(ONE).add(oneShifted);
      BigInteger masked = this.get(wordPos).and(mask);

      // if there are no initialized ticks to the right of or at the current tick, return rightmost in the word
      boolean initialized = !masked.equals(ZERO);
      // overflow/underflow is possible, but prevented externally by limiting both tickSpacing and tick
      int tickNext = initialized
        ? (compressed - (bitPos - BitMath.mostSignificantBit(masked))) * tickSpacing
        : (compressed - bitPos) * tickSpacing;

      return new NextInitializedTickWithinOneWordResult (tickNext, initialized);
    } else {
      // start from the word of the next tick, since the current tick state doesn't matter
      var position = position(compressed + 1);
      int wordPos = position.wordPos;
      int bitPos  = position.bitPos;
      // all the 1s at or to the left of the bitPos
      BigInteger mask = ONE.shiftLeft(bitPos).subtract(ONE).not();
      BigInteger masked = this.get(wordPos).and(mask);

      // if there are no initialized ticks to the left of the current tick, return leftmost in the word
      boolean initialized = !masked.equals(ZERO);
      // overflow/underflow is possible, but prevented externally by limiting both tickSpacing and tick
      int tickNext = initialized
        ? (compressed + 1 + BitMath.leastSignificantBit(masked) - bitPos) * tickSpacing
        : (compressed + 1 + IntUtils.MAX_UINT8.intValue() - bitPos) * tickSpacing;
      
      return new NextInitializedTickWithinOneWordResult (tickNext, initialized);
    }
  }
}
