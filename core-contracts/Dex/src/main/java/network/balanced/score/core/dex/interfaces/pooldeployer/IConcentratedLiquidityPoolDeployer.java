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

package network.balanced.score.core.dex.interfaces.pooldeployer;

import network.balanced.score.core.dex.structs.factory.Parameters;
import score.annotation.External;

public interface IConcentratedLiquidityPoolDeployer {

  // ================================================
  // Methods
  // ================================================
  /**
   * @notice Get the parameters to be used in constructing the pool, set transiently during pool creation.
   * @dev Called by the pool constructor to fetch the parameters of the pool
   * 
   * Returns factory The factory address
   * Returns token0 The first token of the pool by address sort order
   * Returns token1 The second token of the pool by address sort order
   * Returns fee The fee collected upon every swap in the pool, denominated in hundredths of a bip
   * Returns tickSpacing The minimum number of ticks between initialized ticks
   * @return
   */
  @External(readonly = true)
  public Parameters parameters ();
}
