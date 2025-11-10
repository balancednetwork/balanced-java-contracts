/*
 * Copyright (c) 2024 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.positionmgr.implementation;

import network.balanced.score.core.dex.interfaces.irc2.IIRC2ICX;
import network.balanced.score.core.dex.libs.TickMath;
import network.balanced.score.core.positionmgr.interfaces.IBalancedLiquidityManagement;
import network.balanced.score.core.positionmgr.interfaces.IBalancedLiquidityManagementAddLiquidity;
import network.balanced.score.core.positionmgr.libs.CallbackValidation;
import network.balanced.score.core.positionmgr.libs.LiquidityAmounts;
import network.balanced.score.core.positionmgr.libs.PeripheryPayments;
import network.balanced.score.core.positionmgr.libs.PoolAddressLib;
import score.Address;
import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import network.balanced.score.core.dex.interfaces.pool.IConcentratedLiquidityPool;
import network.balanced.score.core.dex.structs.pool.MintCallbackData;
import network.balanced.score.core.dex.structs.pool.PairAmounts;
import network.balanced.score.core.dex.structs.pool.PoolAddress.PoolKey;
import network.balanced.score.core.dex.utils.EnumerableMap;
import network.balanced.score.core.dex.utils.ICX;
import network.balanced.score.core.positionmgr.structs.AddLiquidityParams;
import network.balanced.score.core.positionmgr.structs.AddLiquidityResult;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

public class BalancedLiquidityManagement 
  implements IBalancedLiquidityManagement,
             IBalancedLiquidityManagementAddLiquidity
{
  // ================================================
  // Consts
  // ================================================
  // Contract class name
  private static final String NAME = "BalancedLiquidityManagement";

  // address of the Balanced factory
  private final Address factory;

  // ================================================
  // DB Variables
  // ================================================
  // User => Token => Amount
  private EnumerableMap<Address, BigInteger> depositedMap (Address user) {
    return new EnumerableMap<>(NAME + "_deposited_" + user, Address.class, BigInteger.class);
  }

  // ================================================
  // Event Logs
  // ================================================

  // ================================================
  // Methods
  // ================================================
  /**
   *  @notice Contract constructor
   */
  public BalancedLiquidityManagement(
    Address _factory
  ) {
    this.factory = _factory;
  }

  /**
   * @notice Called to `Context.getCaller()` after minting liquidity to a position from ConcentratedLiquidityPool#mint.
   * @dev In the implementation you must pay the pool tokens owed for the minted liquidity.
   * The caller of this method must be checked to be a ConcentratedLiquidityPool deployed by the canonical BalancedFactory.
   * @param amount0Owed The amount of token0 due to the pool for the minted liquidity
   * @param amount1Owed The amount of token1 due to the pool for the minted liquidity
   * @param data Any data passed through by the caller via the mint call
   */
  // @External
  public void balancedMintCallback (
    BigInteger amount0Owed,
    BigInteger amount1Owed,
    byte[] data
  ) {
    ObjectReader reader = Context.newByteArrayObjectReader("RLPn", data);
    MintCallbackData decoded = reader.read(MintCallbackData.class);
    CallbackValidation.verifyCallback(this.factory, decoded.poolKey);

    if (amount0Owed.compareTo(ZERO) > 0) {
      pay(decoded.payer, decoded.poolKey.token0, amount0Owed);
    }

    if (amount1Owed.compareTo(ZERO) > 0) {
      pay(decoded.payer, decoded.poolKey.token1, amount1Owed);
    }
  }

  private void pay (Address payer, Address token, BigInteger owed) {
    final Address caller = Context.getCaller();
    checkEnoughDeposited(payer, token, owed);
    
    // Remove funds from deposited
    var depositedUser = this.depositedMap(payer);
    BigInteger oldBalance = depositedUser.getOrDefault(token, ZERO);
    if (oldBalance.equals(owed)) {
      // All funds were payed
      depositedUser.remove(token);
    } else {
      // Only a portion of the deposit funds were payed
      depositedUser.set(token, oldBalance.subtract(owed));
    }
    
    // Actually transfer the tokens
    PeripheryPayments.pay(token, caller, owed);
  }

  /**
   * @notice Add liquidity to an initialized pool
   * @dev Liquidity must have been provided beforehand
   */
  public AddLiquidityResult addLiquidity (AddLiquidityParams params) {
    PoolKey poolKey = new PoolKey(params.token0, params.token1, params.fee);

    Address pool = PoolAddressLib.getPool(this.factory, poolKey);
    Context.require(pool != null, "addLiquidity: pool doesn't exist");

    // compute the liquidity amount
    BigInteger sqrtPriceX96 = IConcentratedLiquidityPool.slot0(pool).sqrtPriceX96;
    BigInteger sqrtRatioAX96 = TickMath.getSqrtRatioAtTick(params.tickLower);
    BigInteger sqrtRatioBX96 = TickMath.getSqrtRatioAtTick(params.tickUpper);

    BigInteger liquidity = LiquidityAmounts.getLiquidityForAmounts(
      sqrtPriceX96,
      sqrtRatioAX96,
      sqrtRatioBX96,
      params.amount0Desired,
      params.amount1Desired
    );

    ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
    writer.write(new MintCallbackData(poolKey, Context.getCaller()));

    PairAmounts amounts = IConcentratedLiquidityPool.mint (
      pool, 
      params.recipient, 
      params.tickLower, 
      params.tickUpper, 
      liquidity, 
      writer.toByteArray()
    );

    Context.require(
        amounts.amount0.compareTo(params.amount0Min) >= 0
    &&  amounts.amount1.compareTo(params.amount1Min) >= 0,
      "addLiquidity: Price slippage check"
    );

    return new AddLiquidityResult (liquidity, amounts.amount0, amounts.amount1, pool);
  }

  
  // @External
  // @Payable
  public void depositIcx () {
    deposit(Context.getCaller(), ICX.getAddress(), Context.getValue());
  }

  /**
   * @notice Add funds to the liquidity manager
   */
  // @External - this method is external through tokenFallback
  public void deposit (
    Address caller, 
    Address tokenIn, 
    BigInteger amountIn
  ) {
    // --- Checks ---
    Context.require(amountIn.compareTo(ZERO) > 0, 
      "deposit: Deposit amount cannot be less or equal to 0");

    // --- OK from here ---
    var depositedUser = this.depositedMap(caller);
    BigInteger oldBalance = depositedUser.getOrDefault(tokenIn, ZERO);
    depositedUser.set(tokenIn, oldBalance.add(amountIn));
  }

  /**
   * @notice Remove funds from the liquidity manager
   */
  // @External
  public void withdraw (Address token) {
    final Address caller = Context.getCaller();

    var depositedUser = this.depositedMap(caller);
    BigInteger amount = depositedUser.getOrDefault(token, ZERO);
    depositedUser.remove(token);

    if (amount.compareTo(ZERO) > 0) {
      IIRC2ICX.transfer(token, caller, amount, "withdraw");
    }
  }

  /**
   * @notice Remove all funds from the liquidity manager
   */
  // @External
  public void withdraw_all () {
    final Address caller = Context.getCaller();

    var depositedUser = this.depositedMap(caller);
    int size = depositedUser.size();

    for (int i = 0; i < size; i++) {
      Address token = depositedUser.getKey(0);
      BigInteger amount = depositedUser.getOrDefault(token, ZERO);
      depositedUser.remove(token);
  
      if (amount.compareTo(ZERO) > 0) {
        IIRC2ICX.transfer(token, caller, amount, "withdraw");
      }
    }
  }

  // ================================================
  // Checks
  // ================================================
  private void checkEnoughDeposited (Address address, Address token, BigInteger amount) {
    var userBalance = this.deposited(address, token);
    // Context.println("[Callee][checkEnoughDeposited][" + IIRC2ICX.symbol(token) + "] " + userBalance + " / " + amount);
    Context.require(userBalance.compareTo(amount) >= 0,
      NAME + "::checkEnoughDeposited: user didn't deposit enough funds (" + userBalance + " / " + amount + ")");
  }

  // ================================================
  // Public variable getters
  // ================================================
  /**
   * Returns the amount of tokens previously deposited for a given user and token
   * 
   * @param user A user address who made a deposit
   * @param token A token address
   */
  // @External(readonly = true)
  public BigInteger deposited (Address user, Address token) {
    return this.depositedMap(user).getOrDefault(token, ZERO);
  }

  /**
   * Returns the size of the token list deposited
   * 
   * @param user A user address who made a deposit
   */
  // @External(readonly = true)
  public int depositedTokensSize (Address user) {
    return this.depositedMap(user).size();
  }

  /**
   * Returns the token address in the list given an index
   * 
   * @param user A user address who made a deposit
   * @param index The deposited token list index
   */
  // @External(readonly = true)
  public Address depositedToken (Address user, int index) {
    return this.depositedMap(user).getKey(index);
  }
}