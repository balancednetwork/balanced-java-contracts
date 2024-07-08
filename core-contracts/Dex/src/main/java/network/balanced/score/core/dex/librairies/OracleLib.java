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

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import network.balanced.score.core.dex.structs.pool.Oracle;
import score.Context;

public class OracleLib {
  public static Oracle.Observation transform (
    Oracle.Observation observation,
    BigInteger blockTimestamp,
    int tick,
    BigInteger liquidity
  ) {
    Context.require(blockTimestamp.compareTo(observation.blockTimestamp) >= 0,
      "transform: invalid blockTimestamp");
    BigInteger delta = blockTimestamp.subtract(observation.blockTimestamp);
    BigInteger tickCumulative = observation.tickCumulative.add(BigInteger.valueOf(tick).multiply(delta));
    BigInteger denominator = liquidity.compareTo(ZERO) > 0 ? liquidity : ONE;
    BigInteger secondsPerLiquidityCumulativeX128 = observation.secondsPerLiquidityCumulativeX128.add(delta.shiftLeft(128).divide(denominator));
    return new Oracle.Observation(blockTimestamp, tickCumulative, secondsPerLiquidityCumulativeX128, true);
  }
}
