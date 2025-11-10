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

package network.balanced.score.core.dex.structs.pool;

import static java.math.BigInteger.ZERO;
import java.math.BigInteger;
import java.util.Map;

import score.ObjectReader;
import score.ObjectWriter;

public class Tick {
  public static class Info {
    // the tick index
    public Integer index;

    // the total position liquidity that references this tick
    public BigInteger liquidityGross;

    // amount of net liquidity added (subtracted) when tick is crossed from left to right (right to left),
    public BigInteger liquidityNet;

    // fee growth per unit of liquidity on the _other_ side of this tick (relative to the current tick)
    // only has relative meaning, not absolute — the value depends on when the tick is initialized
    public BigInteger feeGrowthOutside0X128;
    public BigInteger feeGrowthOutside1X128;

    // the cumulative tick value on the other side of the tick
    public BigInteger tickCumulativeOutside;

    // the seconds per unit of liquidity on the _other_ side of this tick (relative to the current tick)
    // only has relative meaning, not absolute — the value depends on when the tick is initialized
    public BigInteger secondsPerLiquidityOutsideX128;

    // the seconds spent on the other side of the tick (relative to the current tick)
    // only has relative meaning, not absolute — the value depends on when the tick is initialized
    public BigInteger secondsOutside;

    // true if the tick is initialized, i.e. the value is exactly equivalent to the expression liquidityGross != 0
    // these 8 bits are set to prevent fresh sstores when crossing newly initialized ticks
    // Outside values can only be used if the tick is initialized, i.e. if liquidityGross is greater than 0.
    // In addition, these values are only relative and must be used only in comparison to previous snapshots for
    // a specific position.
    public boolean initialized;

    public Info (
      Integer index,
      BigInteger liquidityGross,
      BigInteger liquidityNet,
      BigInteger feeGrowthOutside0X128,
      BigInteger feeGrowthOutside1X128,
      BigInteger tickCumulativeOutside,
      BigInteger secondsPerLiquidityOutsideX128,
      BigInteger secondsOutside,
      boolean initialized
    ) {
        this.index = index;
        this.liquidityGross = liquidityGross;
        this.liquidityNet = liquidityNet;
        this.feeGrowthOutside0X128 = feeGrowthOutside0X128;
        this.feeGrowthOutside1X128 = feeGrowthOutside1X128;
        this.tickCumulativeOutside = tickCumulativeOutside;
        this.secondsPerLiquidityOutsideX128 = secondsPerLiquidityOutsideX128;
        this.secondsOutside = secondsOutside;
        this.initialized = initialized;
    }
  
    public static void writeObject(ObjectWriter w, Info obj) {
      w.write(obj.index);
      w.write(obj.liquidityGross);
      w.write(obj.liquidityNet);
      w.write(obj.feeGrowthOutside0X128);
      w.write(obj.feeGrowthOutside1X128);
      w.write(obj.tickCumulativeOutside);
      w.write(obj.secondsPerLiquidityOutsideX128);
      w.write(obj.secondsOutside);
      w.write(obj.initialized);
    }

    public static Info readObject(ObjectReader r) {
      return new Info(
        r.readInt(), // index
        r.readBigInteger(), // liquidityGross
        r.readBigInteger(), // liquidityNet
        r.readBigInteger(), // feeGrowthOutside0X128
        r.readBigInteger(), // feeGrowthOutside1X128
        r.readBigInteger(), // tickCumulativeOutside
        r.readBigInteger(), // secondsPerLiquidityOutsideX128
        r.readBigInteger(), // secondsOutside
        r.readBoolean()  // initialized
      );
    }

    public static Info fromMap(Object call) {
      @SuppressWarnings("unchecked")
      Map<String,Object> map = (Map<String,Object>) call;
      return new Info(
        ((BigInteger) map.get("index")).intValueExact(), 
        (BigInteger) map.get("liquidityGross"), 
        (BigInteger) map.get("liquidityNet"), 
        (BigInteger) map.get("feeGrowthOutside0X128"), 
        (BigInteger) map.get("feeGrowthOutside1X128"), 
        (BigInteger) map.get("tickCumulativeOutside"), 
        (BigInteger) map.get("secondsPerLiquidityOutsideX128"), 
        (BigInteger) map.get("secondsOutside"), 
        (Boolean) map.get("initialized")
      );
    }

    public static Info empty(int index) {
      return new Tick.Info(index, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, false);
    }
  }
}
