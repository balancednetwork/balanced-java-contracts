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

import java.math.BigInteger;
import java.util.Map;

public class SnapshotCumulativesInsideResult {
    public BigInteger tickCumulativeInside;
    public BigInteger secondsPerLiquidityInsideX128;
    public BigInteger secondsInside;

    public SnapshotCumulativesInsideResult (BigInteger tickCumulativeInside, BigInteger secondsPerLiquidityInsideX128, BigInteger secondsInside) {
      this.tickCumulativeInside = tickCumulativeInside;
      this.secondsPerLiquidityInsideX128 = secondsPerLiquidityInsideX128;
      this.secondsInside = secondsInside;
    }

    public static SnapshotCumulativesInsideResult fromMap(Object call) {
      @SuppressWarnings("unchecked")
      Map<String,Object> map = (Map<String,Object>) call;
      return new SnapshotCumulativesInsideResult (
          (BigInteger) map.get("tickCumulativeInside"), 
          (BigInteger) map.get("secondsPerLiquidityInsideX128"), 
          (BigInteger) map.get("secondsInside")
      );
    }
}
