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

// the top level state of the swap, the results of which are recorded in storage at the end
public class SwapState {
    // the amount remaining to be swapped in/out of the input/output asset
    public BigInteger amountSpecifiedRemaining;
    // the amount already swapped out/in of the output/input asset
    public BigInteger amountCalculated;
    // current sqrt(price)
    public BigInteger sqrtPriceX96;
    // the tick associated with the current price
    public int tick;
    // the global fee growth of the input token
    public BigInteger feeGrowthGlobalX128;
    // amount of input token paid as protocol fee
    public BigInteger protocolFee;
    // the current liquidity in range
    public BigInteger liquidity;

    public SwapState(
        BigInteger amountSpecifiedRemaining,
        BigInteger amountCalculated,
        BigInteger sqrtPriceX96,
        int tick,
        BigInteger feeGrowthGlobalX128,
        BigInteger protocolFee,
        BigInteger liquidity
    ) {
        this.amountSpecifiedRemaining = amountSpecifiedRemaining;
        this.amountCalculated = amountCalculated;
        this.sqrtPriceX96 = sqrtPriceX96;
        this.tick = tick;
        this.feeGrowthGlobalX128 = feeGrowthGlobalX128;
        this.protocolFee = protocolFee;
        this.liquidity = liquidity;
    }
}