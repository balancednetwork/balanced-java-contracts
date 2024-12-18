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

public interface IBalancedFlashCallback {
  /**
   * @param fee0 The fee from calling flash for token0
   * @param fee1 The fee from calling flash for token1
   * @param data The data needed in the callback passed as FlashCallbackData from `initFlash`
   * @notice implements the callback called from flash
   * @dev fails if the flash is not profitable, meaning the amountOut from the flash is less than the amount borrowed
   */
  public void balancedFlashCallback (
    BigInteger fee0,
    BigInteger fee1,
    byte[] data
  );
}
