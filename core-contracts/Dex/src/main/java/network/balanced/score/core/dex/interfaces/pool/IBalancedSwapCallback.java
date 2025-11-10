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

package network.balanced.score.core.dex.interfaces.pool;

import java.math.BigInteger;

public interface IBalancedSwapCallback {
  /**
   * Called to `msg.sender` after executing a swap via `ConcentratedLiquidityPool::swap`.
   * @dev In the implementation you must pay the pool tokens owed for the swap. The caller of this method must be checked to be a ConcentratedLiquidityPool deployed by the canonical ConcentratedLiquidityPoolFactory. amount0Delta and amount1Delta can both be 0 if no tokens were swapped.
   * @param amount0Delta The amount of token0 that was sent (negative) or must be received (positive) by the pool by the end of the swap. If positive, the callback must send that amount of token0 to the pool.
   * @param amount1Delta The amount of token1 that was sent (negative) or must be received (positive) by the pool by the end of the swap. If positive, the callback must send that amount of token1 to the pool.
   * @param data Any data passed through by the caller via the swap call
   */
  public void balancedSwapCallback (
    BigInteger amount0Delta,
    BigInteger amount1Delta,
    byte[] data
  );
}
