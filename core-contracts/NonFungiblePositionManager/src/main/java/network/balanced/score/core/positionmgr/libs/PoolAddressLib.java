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

package network.balanced.score.core.positionmgr.libs;

import network.balanced.score.core.dex.interfaces.factory.IBalancedFactory;
import network.balanced.score.core.dex.structs.pool.PoolAddress.PoolKey;
import network.balanced.score.core.dex.utils.AddressUtils;
import score.Address;

public class PoolAddressLib {
  /**
   * @notice Returns PoolKey: the ordered tokens with the matched fee levels
   * @param tokenA The first token of a pool, unsorted
   * @param tokenB The second token of a pool, unsorted
   * @param fee The fee level of the pool
   * @return Poolkey The pool details with ordered token0 and token1 assignments
   */
  public static PoolKey getPoolKey (Address tokenA, Address tokenB, int fee) {
    Address token0 = tokenA;
    Address token1 = tokenB;

    if (AddressUtils.compareTo(tokenA, tokenB) > 0) {
        token0 = tokenB;
        token1 = tokenA;
    }

    return new PoolKey(token0, token1, fee);
  }

  public static Address getPool (Address factory, PoolKey key) {
    return IBalancedFactory.getPool(factory, key.token0, key.token1, key.fee);
  }
}
