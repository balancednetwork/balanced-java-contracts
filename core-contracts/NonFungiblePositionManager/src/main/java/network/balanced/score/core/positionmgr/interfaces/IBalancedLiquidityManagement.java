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

package network.balanced.score.core.positionmgr.interfaces;

import java.math.BigInteger;
import score.Address;

public interface IBalancedLiquidityManagement
{
  /**
   * @notice Add IRC2 funds to the liquidity manager
   */
  // @External - this method is external through tokenFallback
  public void deposit (
    Address caller, 
    Address tokenIn, 
    BigInteger amountIn
  );

  
  /**
   * @notice Add ICX funds to the liquidity manager
   */
  // @External
  // @Payable
  public void depositIcx ();
  
  /**
   * @notice Remove funds from the liquidity manager
   */
  public void withdraw (Address token);
  
  /**
   * @notice Remove all funds from the liquidity manager
   */
  public void withdraw_all ();

  
  // ================================================
  // Public variable getters
  // ================================================
  // @External(readonly = true)
  public BigInteger deposited (Address user, Address token);

  // @External(readonly = true)
  public int depositedTokensSize (Address user);

  // @External(readonly = true)
  public Address depositedToken (Address user, int index);
}