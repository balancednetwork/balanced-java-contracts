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
import network.balanced.score.core.dex.structs.factory.Parameters;
import network.balanced.score.core.dex.structs.pool.PairAmounts;
import network.balanced.score.core.dex.structs.pool.PoolSettings;
import network.balanced.score.core.dex.structs.pool.Position;
import network.balanced.score.core.dex.structs.pool.Slot0;
import network.balanced.score.core.dex.structs.pool.SnapshotCumulativesInsideResult;
import network.balanced.score.core.dex.structs.pool.Tick;
import network.balanced.score.core.dex.structs.pool.Oracle.Observation;
import score.Address;
import score.Context;

public class IBalancedPool {

  // Write methods
  public static PairAmounts mint (
    Address pool, 
    Address recipient, 
    int tickLower, 
    int tickUpper,
    BigInteger amount, 
    byte[] data
  ) {
    return PairAmounts.fromMap (
      Context.call(pool, "mint", recipient, tickLower, tickUpper, amount, data)
    );
  }

  public static void flash (
    Address pool,
    Address recipient,
    BigInteger amount0,
    BigInteger amount1,
    byte[] data
  ) {
    Context.call(pool, "flash", recipient, amount0, amount1, data);
  }

  public static PairAmounts swap (
    Address pool,
    Address recipient,
    boolean zeroForOne,
    BigInteger amountSpecified,
    BigInteger sqrtPriceLimitX96,
    byte[] data
  ) {
    return PairAmounts.fromMap (
      Context.call(pool, "swap", recipient, zeroForOne, amountSpecified, sqrtPriceLimitX96, data)
    );
  }

  public static PairAmounts swapReadOnly (
    Address pool,
    Address recipient,
    boolean zeroForOne,
    BigInteger amountSpecified,
    BigInteger sqrtPriceLimitX96,
    byte[] data
  ) {
    return PairAmounts.fromMap (
      Context.call(pool, "swapReadOnly", recipient, zeroForOne, amountSpecified, sqrtPriceLimitX96, data)
    );
  }

  public static PairAmounts collect (
    Address pool,
    Address recipient,
    int tickLower,
    int tickUpper,
    BigInteger amount0Requested,
    BigInteger amount1Requested
  ) {
    return PairAmounts.fromMap (
      Context.call(pool, "collect", recipient, tickLower, tickUpper, amount0Requested, amount1Requested)
    );
  }

  public static void collectProtocol (
    Address pool,
    Address recipient,
    BigInteger amount0Requested,
    BigInteger amount1Requested
  ) {
    Context.call(pool, "collectProtocol", recipient, amount0Requested, amount1Requested);
  }

  public static PairAmounts burn (
    Address pool,
    int tickLower,
    int tickUpper,
    BigInteger amount
  ) {
    return PairAmounts.fromMap (
      Context.call(pool, "burn", tickLower, tickUpper, amount)
    );
  }

  public static void initialize (
    Address pool,
    BigInteger sqrtPriceX96
  ) {
    Context.call(pool, "initialize", sqrtPriceX96);
  }

  // ReadOnly methods
  public static Address token0 (Address pool) {
    return (Address) Context.call(pool, "token0");
  }

  public static Address token1 (Address pool) {
    return (Address) Context.call(pool, "token1");
  }

  public static Parameters parameters(Address pool) {
    return Parameters.fromMap(Context.call(pool, "parameters"));
  }

  public static Slot0 slot0(Address pool) {
    return Slot0.fromMap(Context.call(pool, "slot0"));
  }

  public static Position.Info positions(Address pool, byte[] positionKey) {
    return Position.Info.fromMap(Context.call(pool, "positions", positionKey));
  }

  public static int tickSpacing (Address pool) {
    return ((BigInteger) Context.call(pool, "tickSpacing")).intValue();
  }

  public static BigInteger tickBitmap (Address pool, int pos) {
    return (BigInteger) Context.call(pool, "tickBitmap", pos);
  }

  public static SnapshotCumulativesInsideResult snapshotCumulativesInside (
    Address pool,
    int tickLower,
    int tickUpper
  ) {
    return SnapshotCumulativesInsideResult.fromMap(
      Context.call(pool, "snapshotCumulativesInside", tickLower, tickUpper)
    );
  }

  public static Tick.Info ticks (Address pool, int populatedTick) {
    return Tick.Info.fromMap(
      Context.call(pool, "ticks", populatedTick)
    );
  }

  public static BigInteger liquidity (Address pool) {
    return (BigInteger) Context.call(pool, "liquidity");
  }

  public static int fee (Address pool) {
    return ((BigInteger) Context.call(pool, "fee")).intValue();
  }

  public static BigInteger feeGrowthGlobal0X128 (Address pool) {
    return (BigInteger) Context.call(pool, "feeGrowthGlobal0X128");
  }

  public static BigInteger feeGrowthGlobal1X128 (Address pool) {
    return (BigInteger) Context.call(pool, "feeGrowthGlobal1X128");
  }

  public static PoolSettings settings (Address pool) {
    return PoolSettings.fromMap(Context.call(pool, "settings"));
  }

  public static Observation observations (Address pool, int index) {
    return Observation.fromMap(Context.call(pool, "observations", index));
  }

  public static BigInteger maxLiquidityPerTick (Address pool) {
    return (BigInteger) Context.call(pool, "maxLiquidityPerTick");
  }
}
