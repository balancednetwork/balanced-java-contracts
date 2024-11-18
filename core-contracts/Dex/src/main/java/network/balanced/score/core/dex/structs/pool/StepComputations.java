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

public class StepComputations {
    // the price at the beginning of the step
    public BigInteger sqrtPriceStartX96;
    // the next tick to swap to from the current tick in the swap direction
    public int tickNext;
    // whether tickNext is initialized or not
    public boolean initialized;
    // sqrt(price) for the next tick (1/0)
    public BigInteger sqrtPriceNextX96;
    // how much is being swapped in in this step
    public BigInteger amountIn;
    // how much is being swapped out
    public BigInteger amountOut;
    // how much fee is being paid in
    public BigInteger feeAmount;
}