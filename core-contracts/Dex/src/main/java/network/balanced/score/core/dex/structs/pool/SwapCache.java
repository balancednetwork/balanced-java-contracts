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

public class SwapCache {
    // the protocol fee for the input token
    public int feeProtocol;
    // liquidity at the beginning of the swap
    public BigInteger liquidityStart;
    // the timestamp of the current block
    public BigInteger blockTimestamp;
    // the current value of the tick accumulator, computed only if we cross an initialized tick
    public BigInteger tickCumulative;
    // the current value of seconds per liquidity accumulator, computed only if we cross an initialized tick
    public BigInteger secondsPerLiquidityCumulativeX128;
    // whether we've computed and cached the above two accumulators
    public boolean computedLatestObservation;

    public SwapCache(
        BigInteger liquidityStart, 
        BigInteger blockTimestamp, 
        int feeProtocol, 
        BigInteger secondsPerLiquidityCumulativeX128, 
        BigInteger tickCumulative, 
        boolean computedLatestObservation
    ) {
        this.liquidityStart = liquidityStart;
        this.blockTimestamp = blockTimestamp;
        this.feeProtocol = feeProtocol;
        this.secondsPerLiquidityCumulativeX128 = secondsPerLiquidityCumulativeX128;
        this.tickCumulative = tickCumulative;
        this.computedLatestObservation = computedLatestObservation;
    }
}