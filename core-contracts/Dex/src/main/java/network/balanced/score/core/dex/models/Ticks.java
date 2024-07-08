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

import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import network.balanced.score.core.dex.librairies.LiquidityMath;
import network.balanced.score.core.dex.structs.pool.Tick;
import network.balanced.score.core.dex.structs.pool.Tick.Info;
import network.balanced.score.core.dex.utils.EnumerableMap;
import network.balanced.score.core.dex.utils.EnumerableSet;
import score.Context;

public class Ticks {
  // ================================================
  // Consts
  // ================================================
  // Class name
  private static final String NAME = "TicksDB";

  // ================================================
  // DB Variables
  // ================================================
  // Look up information about a specific tick in the pool
  private final EnumerableMap<Integer, Tick.Info> ticks = new EnumerableMap<>(NAME + "_ticks", Integer.class, Tick.Info.class);
  private final EnumerableSet<Integer> initialized = new EnumerableSet<>(NAME + "_initialized", Integer.class);

  // ================================================
  // Methods
  // ================================================
  public Info get (int key) {
    var result = this.ticks.get(key);
    return result == null ? Tick.Info.empty(key) : result;
  }

  public int initializedSize () {
    return this.initialized.length();
  }

  public int initialized (int index) {
    return this.initialized.get(index);
  }

  private void set (int key, Tick.Info value) {
    this.ticks.set(key, value);
    if (value != null && value.initialized) {
      this.initialized.add(key);
    } else {
      // either deleted or uninitialized
      this.initialized.remove(key);
    }
  }

  public class UpdateResult {
    public Info info;
    public boolean flipped;
    public UpdateResult(Info info, boolean flipped) {
      this.info = info;
      this.flipped = flipped;
    }
  } 

  /**
   * @notice Updates a tick and returns true if the tick was flipped from initialized to uninitialized, or vice versa
   * @param tick The tick that will be updated
   * @param tickCurrent The current tick
   * @param liquidityDelta A new amount of liquidity to be added (subtracted) when tick is crossed from left to right (right to left)
   * @param feeGrowthGlobal0X128 The all-time global fee growth, per unit of liquidity, in token0
   * @param feeGrowthGlobal1X128 The all-time global fee growth, per unit of liquidity, in token1
   * @param secondsPerLiquidityCumulativeX128 The all-time seconds per max(1, liquidity) of the pool
   * @param tickCumulative The tick * time elapsed since the pool was first initialized
   * @param time The current block timestamp cast to a uint32
   * @param upper true for updating a position's upper tick, or false for updating a position's lower tick
   * @param maxLiquidity The maximum liquidity allocation for a single tick
   * @return flipped Whether the tick was flipped from initialized to uninitialized, or vice versa
   */
  public UpdateResult update (
      int tick,
      int tickCurrent,
      BigInteger liquidityDelta,
      BigInteger feeGrowthGlobal0X128,
      BigInteger feeGrowthGlobal1X128,
      BigInteger secondsPerLiquidityCumulativeX128,
      BigInteger tickCumulative,
      BigInteger time,
      boolean upper,
      BigInteger maxLiquidity
  ) {
      Tick.Info info = this.get(tick);
      BigInteger liquidityGrossBefore = info.liquidityGross;
      BigInteger liquidityGrossAfter = LiquidityMath.addDelta(liquidityGrossBefore, liquidityDelta);

      Context.require(liquidityGrossAfter.compareTo(maxLiquidity) <= 0, 
        "update: liquidityGrossAfter <= maxLiquidity");

      boolean flipped = (liquidityGrossAfter.equals(ZERO)) != (liquidityGrossBefore.equals(ZERO));

      if (liquidityGrossBefore.equals(ZERO)) {
          // by convention, we assume that all growth before a tick was initialized happened _below_ the tick
          if (tick <= tickCurrent) {
              info.feeGrowthOutside0X128 = feeGrowthGlobal0X128;
              info.feeGrowthOutside1X128 = feeGrowthGlobal1X128;
              info.secondsPerLiquidityOutsideX128 = secondsPerLiquidityCumulativeX128;
              info.tickCumulativeOutside = tickCumulative;
              info.secondsOutside = time;
          }
          info.initialized = true;
      }

      info.liquidityGross = liquidityGrossAfter;

      // when the lower (upper) tick is crossed left to right (right to left), liquidity must be added (removed)
      info.liquidityNet = upper
          ? info.liquidityNet.subtract(liquidityDelta)
          : info.liquidityNet.add(liquidityDelta);

      this.set(tick, info);
      return new UpdateResult(info, flipped);
  }
  
  public class GetFeeGrowthInsideResult {
    public BigInteger feeGrowthInside0X128;
    public BigInteger feeGrowthInside1X128;
    public GetFeeGrowthInsideResult (BigInteger feeGrowthInside0X128, BigInteger feeGrowthInside1X128) {
      this.feeGrowthInside0X128 = feeGrowthInside0X128;
      this.feeGrowthInside1X128 = feeGrowthInside1X128;
    }
  }

  public GetFeeGrowthInsideResult getFeeGrowthInside (
    int tickLower, 
    int tickUpper, 
    int tickCurrent, 
    BigInteger feeGrowthGlobal0X128,
    BigInteger feeGrowthGlobal1X128
  ) {
    Tick.Info lower = this.get(tickLower);
    Tick.Info upper = this.get(tickUpper);
    
    // calculate fee growth below
    BigInteger feeGrowthBelow0X128;
    BigInteger feeGrowthBelow1X128;
    
    if (tickCurrent >= tickLower) {
      feeGrowthBelow0X128 = lower.feeGrowthOutside0X128;
      feeGrowthBelow1X128 = lower.feeGrowthOutside1X128;
    } else {
      feeGrowthBelow0X128 = feeGrowthGlobal0X128.subtract(lower.feeGrowthOutside0X128);
      feeGrowthBelow1X128 = feeGrowthGlobal1X128.subtract(lower.feeGrowthOutside1X128);
    }
    
    // calculate fee growth above
    BigInteger feeGrowthAbove0X128;
    BigInteger feeGrowthAbove1X128;
    
    if (tickCurrent < tickUpper) {
      feeGrowthAbove0X128 = upper.feeGrowthOutside0X128;
      feeGrowthAbove1X128 = upper.feeGrowthOutside1X128;
    } else {
      feeGrowthAbove0X128 = feeGrowthGlobal0X128.subtract(upper.feeGrowthOutside0X128);
      feeGrowthAbove1X128 = feeGrowthGlobal1X128.subtract(upper.feeGrowthOutside1X128);
    }

    return new GetFeeGrowthInsideResult(
      feeGrowthGlobal0X128.subtract(feeGrowthBelow0X128).subtract(feeGrowthAbove0X128),
      feeGrowthGlobal1X128.subtract(feeGrowthBelow1X128).subtract(feeGrowthAbove1X128)
    );
  }

  /**
   * @notice Clears tick data
   * @param tick The tick that will be cleared
   */
  public void clear(int tick) {
    this.set(tick, null);
  }

  /**
   * @notice Transitions to next tick as needed by price movement
   * @param tick The destination tick of the transition
   * @param feeGrowthGlobal0X128 The all-time global fee growth, per unit of liquidity, in token0
   * @param feeGrowthGlobal1X128 The all-time global fee growth, per unit of liquidity, in token1
   * @param secondsPerLiquidityCumulativeX128 The current seconds per liquidity
   * @param tickCumulative The tick * time elapsed since the pool was first initialized
   * @param time The current block.timestamp
   * @return liquidityNet The amount of liquidity added (subtracted) when tick is crossed from left to right (right to left)
   */
  public Info cross (
    int tick, 
    BigInteger feeGrowthGlobal0X128, 
    BigInteger feeGrowthGlobal1X128, 
    BigInteger secondsPerLiquidityCumulativeX128,
    BigInteger tickCumulative, 
    BigInteger time
  ) {
    Tick.Info info = this.get(tick);
    info.feeGrowthOutside0X128 = feeGrowthGlobal0X128.subtract(info.feeGrowthOutside0X128);
    info.feeGrowthOutside1X128 = feeGrowthGlobal1X128.subtract(info.feeGrowthOutside1X128);
    info.secondsPerLiquidityOutsideX128 = secondsPerLiquidityCumulativeX128.subtract(info.secondsPerLiquidityOutsideX128);
    info.tickCumulativeOutside = tickCumulative.subtract(info.tickCumulativeOutside);
    info.secondsOutside = time.subtract(info.secondsOutside);
    this.set(tick, info);
    return info;
  }
}
