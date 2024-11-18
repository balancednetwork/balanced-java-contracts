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

public class Oracle {
  public static class Observation {
    // the block timestamp of the observation
    public BigInteger blockTimestamp;
    // the tick accumulator, i.e. tick * time elapsed since the pool was first initialized
    public BigInteger tickCumulative;
    // the seconds per liquidity, i.e. seconds elapsed / max(1, liquidity) since the pool was first initialized
    public BigInteger secondsPerLiquidityCumulativeX128;
    // whether or not the observation is initialized
    public Boolean initialized;

    public Observation (
      BigInteger blockTimestamp,
      BigInteger tickCumulative,
      BigInteger secondsPerLiquidityCumulativeX128,
      Boolean initialized
    ) {
      this.blockTimestamp = blockTimestamp;
      this.tickCumulative = tickCumulative;
      this.secondsPerLiquidityCumulativeX128 = secondsPerLiquidityCumulativeX128;
      this.initialized = initialized;
    }

    public static void writeObject(ObjectWriter w, Observation obj) {
      w.write(obj.blockTimestamp);
      w.write(obj.tickCumulative);
      w.write(obj.secondsPerLiquidityCumulativeX128);
      w.write(obj.initialized);
    }

    public static Observation readObject(ObjectReader r) {
      return new Observation(
        r.readBigInteger(), // blockTimestamp, 
        r.readBigInteger(), // tickCumulative, 
        r.readBigInteger(), // secondsPerLiquidityCumulativeX128, 
        r.readBoolean()     // initialized,
      );
    }

    public static Observation fromMap(Object call) {
      @SuppressWarnings("unchecked")
      Map<String,Object> map = (Map<String,Object>) call;
      return new Observation(
          (BigInteger) map.get("blockTimestamp"),
          (BigInteger) map.get("tickCumulative"),
          (BigInteger) map.get("secondsPerLiquidityCumulativeX128"),
          (Boolean) map.get("initialized")
      );
    }

    public static Oracle.Observation empty () {
      return new Oracle.Observation(ZERO, ZERO, ZERO, false);
    }
  }
}
