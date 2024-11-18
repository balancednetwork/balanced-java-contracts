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

package network.balanced.score.core.dex;

import static java.math.BigInteger.ZERO;
import static network.balanced.score.core.dex.utils.IntUtils.uint256;
import java.math.BigInteger;
import network.balanced.score.core.dex.interfaces.factory.IBalancedFactory;
import network.balanced.score.core.dex.interfaces.irc2.IIRC2ICX;
import network.balanced.score.core.dex.interfaces.pool.IConcentratedLiquidityPoolCallee;
import network.balanced.score.core.dex.libs.FixedPoint128;
import network.balanced.score.core.dex.libs.FullMath;
import network.balanced.score.core.dex.libs.LiquidityMath;
import network.balanced.score.core.dex.libs.PositionLib;
import network.balanced.score.core.dex.libs.SqrtPriceMath;
import network.balanced.score.core.dex.libs.SwapMath;
import network.balanced.score.core.dex.libs.TickLib;
import network.balanced.score.core.dex.libs.TickMath;
import network.balanced.score.core.dex.models.Observations;
import network.balanced.score.core.dex.models.Positions;
import network.balanced.score.core.dex.models.TickBitmap;
import network.balanced.score.core.dex.models.Ticks;
import network.balanced.score.core.dex.structs.factory.Parameters;
import network.balanced.score.core.dex.structs.pool.ModifyPositionParams;
import network.balanced.score.core.dex.structs.pool.ModifyPositionResult;
import network.balanced.score.core.dex.structs.pool.NextInitializedTickWithinOneWordResult;
import network.balanced.score.core.dex.structs.pool.ObserveResult;
import network.balanced.score.core.dex.structs.pool.Oracle;
import network.balanced.score.core.dex.structs.pool.PairAmounts;
import network.balanced.score.core.dex.structs.pool.PoolSettings;
import network.balanced.score.core.dex.structs.pool.Position;
import network.balanced.score.core.dex.structs.pool.PositionStorage;
import network.balanced.score.core.dex.structs.pool.ProtocolFees;
import network.balanced.score.core.dex.structs.pool.Slot0;
import network.balanced.score.core.dex.structs.pool.SnapshotCumulativesInsideResult;
import network.balanced.score.core.dex.structs.pool.StepComputations;
import network.balanced.score.core.dex.structs.pool.SwapCache;
import network.balanced.score.core.dex.structs.pool.SwapState;
import network.balanced.score.core.dex.structs.pool.Tick;
import network.balanced.score.core.dex.utils.TimeUtils;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

public class ConcentratedLiquidityPool {
  // ================================================
  // Consts
  // ================================================
  // Contract class name
  public static final String NAME = Names.CONCENTRATED_LIQUIDITY_POOL;

  // Observations default cardinality
  public static final int DEFAULT_OBSERVATIONS_CARDINALITY = 1024;

  // Pool settings
  private final PoolSettings settings;

  // ================================================
  // DB Variables
  // ================================================
  // The 0th storage slot in the pool stores many values, and is exposed as a single method to save steps when accessed externally.
  protected final VarDB<Slot0> slot0 = Context.newVarDB(NAME + "_slot0", Slot0.class);

  // The fee growth as a Q128.128 fees of token0 collected per unit of liquidity for the entire life of the pool
  protected final VarDB<BigInteger> feeGrowthGlobal0X128 = Context.newVarDB(NAME + "_feeGrowthGlobal0X128", BigInteger.class);

  // The fee growth as a Q128.128 fees of token1 collected per unit of liquidity for the entire life of the pool
  protected final VarDB<BigInteger> feeGrowthGlobal1X128 = Context.newVarDB(NAME + "_feeGrowthGlobal1X128", BigInteger.class);

  // The amounts of token0 and token1 that are owed to the protocol
  protected final VarDB<ProtocolFees> protocolFees = Context.newVarDB(NAME + "_protocolFees", ProtocolFees.class);

  // The amounts of token0 and token1 that are owed to the protocol
  protected final VarDB<BigInteger> liquidity = Context.newVarDB(NAME + "_liquidity", BigInteger.class);

  // Implements IObservations
  // Returns data about a specific observation index
  protected final Observations observations = new Observations();

  // Implements IPositions
  // Returns the information about a position by the position's key
  protected final Positions positions = new Positions();

  // Implements ITickBitmap
  // Returns 256 packed tick initialized boolean values. See TickBitmap for more information
  protected final TickBitmap tickBitmap = new TickBitmap();

  // Implements ITicks
  // Look up information about a specific tick in the pool
  protected final Ticks ticks = new Ticks();

  // ================================================
  // Event Logs
  // ================================================
  /**
   * @notice Emitted by the pool for increases to the number of observations that can be stored
   * @dev observationCardinalityNext is not the observation cardinality until an observation is written at the index
   * just before a mint/swap/burn.
   * @param observationCardinalityNextOld The previous value of the next observation cardinality
   * @param observationCardinalityNextNew The updated value of the next observation cardinality
   */
  @EventLog
  public void IncreaseObservationCardinalityNext (
    int observationCardinalityNextOld,
    int observationCardinalityNextNew
  ) {}
  
  /**
   * @notice Emitted exactly once by a pool when #initialize is first called on the pool
   * @dev Mint/Burn/Swap cannot be emitted by the pool before Initialize
   * @param sqrtPriceX96 The initial sqrt price of the pool, as a Q64.96
   * @param tick The initial tick of the pool, i.e. log base 1.0001 of the starting price of the pool
   */
  @EventLog
  public void Initialized (
    BigInteger sqrtPriceX96,
    int tick
  ) {}
  
  
  /**
   * @notice Emitted whenever pool intrinsics are updated
   * @param sqrtPriceX96 The sqrt price of the pool, as a Q64.96
   * @param tick The current tick of the pool, i.e. log base 1.0001 of the starting price of the pool
   * @param liquidity The current liquidity of the pool
   */
  @EventLog
  public void PoolIntrinsicsUpdate (
    BigInteger sqrtPriceX96,
    int tick,
    BigInteger liquidity
  ) {}
  
  /**
   * @notice Emitted when liquidity is minted for a given position
   * @param sender The address that minted the liquidity
   * @param owner The owner of the position and recipient of any minted liquidity
   * @param tickLower The lower tick of the position
   * @param tickUpper The upper tick of the position
   * @param amount The amount of liquidity minted to the position range
   * @param amount0 How much token0 was required for the minted liquidity
   * @param amount1 How much token1 was required for the minted liquidity
   */
  @EventLog(indexed = 3)
  public void Mint (
    Address recipient, 
    int tickLower, 
    int tickUpper, 
    Address sender, 
    BigInteger amount,
    BigInteger amount0, 
    BigInteger amount1
  ) {}

  /**
   * @notice Emitted when fees are collected by the owner of a position
   * @dev Collect events may be emitted with zero amount0 and amount1 when the caller chooses not to collect fees
   * @param owner The owner of the position for which fees are collected
   * @param tickLower The lower tick of the position
   * @param tickUpper The upper tick of the position
   * @param amount0 The amount of token0 fees collected
   * @param amount1 The amount of token1 fees collected
   */
  @EventLog(indexed = 3)
  public void Collect (
    Address caller, 
    int tickLower, 
    int tickUpper, 
    Address recipient, 
    BigInteger amount0,
    BigInteger amount1
  ) {}
  
  /**
   * @notice Emitted when a position's liquidity is removed
   * @dev Does not withdraw any fees earned by the liquidity position, which must be withdrawn via #collect
   * @param owner The owner of the position for which liquidity is removed
   * @param tickLower The lower tick of the position
   * @param tickUpper The upper tick of the position
   * @param amount The amount of liquidity to remove
   * @param amount0 The amount of token0 withdrawn
   * @param amount1 The amount of token1 withdrawn
   */
  @EventLog(indexed = 3)
  public void Burn (
    Address caller, 
    int tickLower, 
    int tickUpper, 
    BigInteger amount, 
    BigInteger amount0,
    BigInteger amount1
  ) {}

  /**
   * @notice Emitted by the pool for any swaps between token0 and token1
   * @param sender The address that initiated the swap call, and that received the callback
   * @param recipient The address that received the output of the swap
   * @param amount0 The delta of the token0 balance of the pool
   * @param amount1 The delta of the token1 balance of the pool
   * @param sqrtPriceX96 The sqrt(price) of the pool after the swap, as a Q64.96
   * @param liquidity The liquidity of the pool after the swap
   * @param tick The log base 1.0001 of price of the pool after the swap
   */
  @EventLog(indexed = 2)
  public void Swap (
    Address sender,
    Address recipient,
    BigInteger amount0,
    BigInteger amount1,
    BigInteger sqrtPriceX96,
    BigInteger liquidity,
    int tick
  ) {}

  /**
   * @notice Emitted by the pool for any flashes of token0/token1
   * @param sender The address that initiated the swap call, and that received the callback
   * @param recipient The address that received the tokens from flash
   * @param amount0 The amount of token0 that was flashed
   * @param amount1 The amount of token1 that was flashed
   * @param paid0 The amount of token0 paid for the flash, which can exceed the amount0 plus the fee
   * @param paid1 The amount of token1 paid for the flash, which can exceed the amount1 plus the fee
   */
  @EventLog(indexed = 2)
  public void Flash (
    Address sender,
    Address recipient,
    BigInteger amount0,
    BigInteger amount1,
    BigInteger paid0,
    BigInteger paid1
  ) {}

  /**
   * @notice Emitted when the protocol fee is changed by the pool
   * @param feeProtocol0Old The previous value of the token0 protocol fee
   * @param feeProtocol1Old The previous value of the token1 protocol fee
   * @param feeProtocol0New The updated value of the token0 protocol fee
   * @param feeProtocol1New The updated value of the token1 protocol fee
   */  
  @EventLog
  public void SetFeeProtocol (
    int feeProtocol0Old, 
    int feeProtocol1Old, 
    int feeProtocol0New, 
    int feeProtocol1New
  ) {}

  /**
   * @notice Emitted when the collected protocol fees are withdrawn by the factory owner
   * @param sender The address that collects the protocol fees
   * @param recipient The address that receives the collected protocol fees
   * @param amount0 The amount of token0 protocol fees that is withdrawn
   * @param amount0 The amount of token1 protocol fees that is withdrawn
   */
  @EventLog(indexed = 2)
  public void CollectProtocol (
    Address sender, 
    Address recipient, 
    BigInteger amount0, 
    BigInteger amount1
  ) {}

  /**
   * @notice Emitted whenever a tick is modified in the Ticks DB
   * @param index See {@code Tick.Info} for information about the parameters
   */
  @EventLog(indexed = 1)
  public void TickUpdate (
    int index,
    BigInteger liquidityGross,
    BigInteger liquidityNet,
    BigInteger feeGrowthOutside0X128,
    BigInteger feeGrowthOutside1X128,
    BigInteger tickCumulativeOutside,
    BigInteger secondsPerLiquidityOutsideX128,
    BigInteger secondsOutside,
    boolean initialized
  ) {}

  // ================================================
  // Methods
  // ================================================
  /**
   * @notice Contract constructor
   * See {@code ConcentratedLiquidityPoolFactored} constructor for the actual pool deployed on the network
   */
  public ConcentratedLiquidityPool (Parameters parameters) {
    // Initialize settings
    this.settings = new PoolSettings (
      parameters.factory,
      parameters.token0,
      parameters.token1,
      parameters.fee,
      parameters.tickSpacing,
      TickLib.tickSpacingToMaxLiquidityPerTick(parameters.tickSpacing),
      "Balanced Concentrated Liquidity Pool (" + IIRC2ICX.symbol(parameters.token0) + " / " + IIRC2ICX.symbol(parameters.token1) + " " + ((float) parameters.fee / 10000) + "%)"
    );

    // Default values
    if (this.liquidity.get() == null) {
      this.liquidity.set(ZERO);
    }
    if (this.feeGrowthGlobal0X128.get() == null) {
      this.feeGrowthGlobal0X128.set(ZERO);
    }
    if (this.feeGrowthGlobal1X128.get() == null) {
      this.feeGrowthGlobal1X128.set(ZERO);
    }
    if (this.protocolFees.get() == null) {
      this.protocolFees.set(new ProtocolFees(ZERO, ZERO));
    }
  }

  /**
   * @notice Increase the maximum number of price and liquidity observations that this pool will store
   * 
   * Access: Everyone
   * 
   * @dev This method is no-op if the pool already has an observationCardinalityNext greater than or equal to
   * the input observationCardinalityNext.
   * @param observationCardinalityNext The desired minimum number of observations for the pool to store
   */
  @External
  public void increaseObservationCardinalityNext (int observationCardinalityNext) {
    this.unlock(false);

    Slot0 _slot0 = this.slot0.get();
    int observationCardinalityNextOld = _slot0.observationCardinalityNext;
    int observationCardinalityNextNew = this.observations.grow(observationCardinalityNextOld, observationCardinalityNext);

    _slot0.observationCardinalityNext = observationCardinalityNextNew;
    this.slot0.set(_slot0);

    if (observationCardinalityNextOld != observationCardinalityNextNew) {
      this.IncreaseObservationCardinalityNext(observationCardinalityNextOld, observationCardinalityNextNew);
    }

    this.unlock(true);
  }

  /**
   * Enable or disable the lock
   * @param state Lock state
   */
  public void unlock (boolean state) {
    // Check current unlock state
    var slot0 = this.slot0.get();
    Context.require(slot0 != null, 
      "unlock: pool isn't initialized yet"); 
    boolean unlock_state = slot0.unlocked;
    Context.require(state != unlock_state, 
        NAME + "::unlock: wrong lock state: " + unlock_state);

    // OK
    slot0.unlocked = state;
    this.slot0.set(slot0);
  }

  /**
   * Sets the initial price for the pool
   * 
   * Access: Everyone
   * 
   * @dev Price is represented as a sqrt(amountToken1/amountToken0) Q64.96 value
   * @param sqrtPriceX96 the initial sqrt price of the pool as a Q64.96
   * @dev not locked because it initializes unlocked
   */
  @External
  public void initialize (BigInteger sqrtPriceX96) {
    Context.require(this.slot0.get() == null, 
      "initialize: this pool is already initialized");

    int tick = TickMath.getTickAtSqrtRatio(sqrtPriceX96);

    var result = this.observations.initialize(TimeUtils.now());

    this.slot0.set(new Slot0(
      sqrtPriceX96,
      tick,
      0,
      result.cardinality,
      result.cardinalityNext,
      0,
      // Unlock the pool
      true
    ));

    // Set observations at 1024 by default
    this.increaseObservationCardinalityNext(DEFAULT_OBSERVATIONS_CARDINALITY);

    this.Initialized(sqrtPriceX96, tick);
    this.PoolIntrinsicsUpdate(sqrtPriceX96, tick, ZERO);
  }

  /**
   * @notice Adds liquidity for the given recipient/tickLower/tickUpper position
   * 
   * Access: Everyone
   * 
   * @dev The caller of this method receives a callback in the form of balancedMintCallback
   * in which they must pay any token0 or token1 owed for the liquidity. The amount of token0/token1 due depends
   * on tickLower, tickUpper, the amount of liquidity, and the current price.
   * @param recipient The address for which the liquidity will be created
   * @param tickLower The lower tick of the position in which to add liquidity
   * @param tickUpper The upper tick of the position in which to add liquidity
   * @param amount The amount of liquidity to mint
   * @param data Any data that should be passed through to the callback
   * @return amount0 The amount of token0 that was paid to mint the given amount of liquidity. Matches the value in the callback
   * @return amount1 The amount of token1 that was paid to mint the given amount of liquidity. Matches the value in the callback
   */
  @External
  public PairAmounts mint (
    Address recipient,
    int tickLower,
    int tickUpper,
    BigInteger amount,
    byte[] data
  ) {
    this.unlock(false);

    final Address caller = Context.getCaller();

    Context.require(amount.compareTo(ZERO) > 0,
      "mint: amount must be superior to 0");

    BigInteger amount0;
    BigInteger amount1;

    var result = _modifyPosition(new ModifyPositionParams(
      recipient,
      tickLower,
      tickUpper,
      amount
    ));

    amount0 = result.amount0;
    amount1 = result.amount1;

    BigInteger balance0Before = ZERO;
    BigInteger balance1Before = ZERO;
    
    if (amount0.compareTo(ZERO) > 0) {
      balance0Before = balance0();
    }
    if (amount1.compareTo(ZERO) > 0) {
      balance1Before = balance1();
    }

    IConcentratedLiquidityPoolCallee.balancedMintCallback(caller, amount0, amount1, data);

    if (amount0.compareTo(ZERO) > 0) {
      BigInteger expected = balance0Before.add(amount0);
      BigInteger balance0 = balance0();
      Context.require(expected.compareTo(balance0) <= 0, 
        "mint: callback didn't send enough of token0, expected " + expected + ", got " + balance0);
    }
    if (amount1.compareTo(ZERO) > 0) {
      BigInteger expected = balance1Before.add(amount1);
      BigInteger balance1 = balance1();
      Context.require(expected.compareTo(balance1()) <= 0, 
        "mint: callback didn't send enough of token1, expected " + expected + ", got " + balance1);
    }

    this.Mint(recipient, tickLower, tickUpper, caller, amount, amount0, amount1);

    this.unlock(true);
    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Collects tokens owed to a position
   * 
   * Access: Everyone
   * 
   * @dev Does not recompute fees earned, which must be done either via mint or burn of any amount of liquidity.
   * Collect must be called by the position owner. To withdraw only token0 or only token1, amount0Requested or
   * amount1Requested may be set to zero. To withdraw all tokens owed, caller may pass any value greater than the
   * actual tokens owed, e.g. type(uint128).max. Tokens owed may be from accumulated swap fees or burned liquidity.
   * @param recipient The address which should receive the fees collected
   * @param tickLower The lower tick of the position for which to collect fees
   * @param tickUpper The upper tick of the position for which to collect fees
   * @param amount0Requested How much token0 should be withdrawn from the fees owed
   * @param amount1Requested How much token1 should be withdrawn from the fees owed
   * @return amount0 The amount of fees collected in token0
   * @return amount1 The amount of fees collected in token1
   */
  @External
  public PairAmounts collect (
    Address recipient,
    int tickLower,
    int tickUpper,
    BigInteger amount0Requested,
    BigInteger amount1Requested
  ) {
    this.unlock(false);

    final Address caller = Context.getCaller();

    // we don't need to checkTicks here, because invalid positions will never have non-zero tokensOwed{0,1}
    byte[] key = Positions.getKey(caller, tickLower, tickUpper);
    Position.Info position = this.positions.get(key);

    BigInteger amount0 = amount0Requested.compareTo(position.tokensOwed0) > 0 ? position.tokensOwed0 : amount0Requested;
    BigInteger amount1 = amount1Requested.compareTo(position.tokensOwed1) > 0 ? position.tokensOwed1 : amount1Requested;

    if (amount0.compareTo(ZERO) > 0) {
      position.tokensOwed0 = position.tokensOwed0.subtract(amount0);
      this.positions.set(key, position);
      pay(this.settings.token0, recipient, amount0);
    }
    if (amount1.compareTo(ZERO) > 0) {
      position.tokensOwed1 = position.tokensOwed1.subtract(amount1);
      this.positions.set(key, position);
      pay(this.settings.token1, recipient, amount1);
    }

    this.Collect(caller, tickLower, tickUpper, recipient, amount0, amount1);

    this.unlock(true);
    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Burn liquidity from the sender and account tokens owed for the liquidity to the position
   * 
   * Access: Everyone
   * 
   * @dev Can be used to trigger a recalculation of fees owed to a position by calling with an amount of 0
   * @dev Fees must be collected separately via a call to #collect
   * @param tickLower The lower tick of the position for which to burn liquidity
   * @param tickUpper The upper tick of the position for which to burn liquidity
   * @param amount How much liquidity to burn
   * @return amount0 The amount of token0 sent to the recipient
   * @return amount1 The amount of token1 sent to the recipient
   */
  @External
  public PairAmounts burn (
    int tickLower,
    int tickUpper,
    BigInteger amount
  ) {
    this.unlock(false);
    final Address caller = Context.getCaller();

    var result = _modifyPosition(new ModifyPositionParams(
      caller,
      tickLower,
      tickUpper,
      amount.negate()
    ));

    BigInteger amount0 = result.amount0.negate();
    BigInteger amount1 = result.amount1.negate();
    Position.Info position = result.positionStorage.position;
    byte[] positionKey = result.positionStorage.key;

    if (amount0.compareTo(ZERO) > 0 || amount1.compareTo(ZERO) > 0) {
      position.tokensOwed0 = position.tokensOwed0.add(amount0);
      position.tokensOwed1 = position.tokensOwed1.add(amount1);
      this.positions.set(positionKey, position);
    }

    this.Burn(caller, tickLower, tickUpper, amount, amount0, amount1);

    this.unlock(true);
    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Swap token0 for token1, or token1 for token0
   * 
   * Access: Everyone
   * 
   * @dev The caller of this method receives a callback in the form of balancedSwapCallback
   * @param recipient The address to receive the output of the swap
   * @param zeroForOne The direction of the swap, true for token0 to token1, false for token1 to token0
   * @param amountSpecified The amount of the swap, which implicitly configures the swap as exact input (positive), or exact output (negative)
   * @param sqrtPriceLimitX96 The Q64.96 sqrt price limit. If zero for one, the price cannot be less than this value after the swap. If one for zero, the price cannot be greater than this value after the swap.
   * @param data Any data to be passed through to the callback
   * @return amount0 The delta of the balance of token0 of the pool, exact when negative, minimum when positive
   * @return amount1 The delta of the balance of token1 of the pool, exact when negative, minimum when positive
   */
  @External
  public PairAmounts swap (
    Address recipient,
    boolean zeroForOne,
    BigInteger amountSpecified,
    BigInteger sqrtPriceLimitX96,
    byte[] data
  ) {
    this.unlock(false);
    final Address caller = Context.getCaller();

    Context.require(!amountSpecified.equals(ZERO),
      "swap: amountSpecified must be different from zero");
    
    Slot0 slot0Start = this.slot0.get();

    Context.require (
      zeroForOne
        ? sqrtPriceLimitX96.compareTo(slot0Start.sqrtPriceX96) < 0 && sqrtPriceLimitX96.compareTo(TickMath.MIN_SQRT_RATIO) > 0
        : sqrtPriceLimitX96.compareTo(slot0Start.sqrtPriceX96) > 0 && sqrtPriceLimitX96.compareTo(TickMath.MAX_SQRT_RATIO) < 0,
      "swap: Wrong sqrtPriceLimitX96"
    );

    SwapCache cache = new SwapCache(
      this.liquidity.get(),
      TimeUtils.now(),
      zeroForOne ? (slot0Start.feeProtocol % 16) : (slot0Start.feeProtocol >> 4),
      ZERO,
      ZERO,
      false
    );

    boolean exactInput = amountSpecified.compareTo(ZERO) > 0;

    SwapState state = new SwapState(
      amountSpecified,
      ZERO,
      slot0Start.sqrtPriceX96,
      slot0Start.tick,
      zeroForOne ? feeGrowthGlobal0X128.get() : feeGrowthGlobal1X128.get(),
      ZERO,
      cache.liquidityStart
    );
    
    // continue swapping as long as we haven't used the entire input/output and haven't reached the price limit
    while (
      !state.amountSpecifiedRemaining.equals(ZERO) 
     && !state.sqrtPriceX96.equals(sqrtPriceLimitX96)
    ) {

      StepComputations step = new StepComputations();
      step.sqrtPriceStartX96 = state.sqrtPriceX96;

      var next = tickBitmap.nextInitializedTickWithinOneWord(
        state.tick,
        this.settings.tickSpacing,
        zeroForOne
      );
      
      step.tickNext = next.tickNext;
      step.initialized = next.initialized;
      
      // ensure that we do not overshoot the min/max tick, as the tick bitmap is not aware of these bounds
      if (step.tickNext < TickMath.MIN_TICK) {
        step.tickNext = TickMath.MIN_TICK;
      } else if (step.tickNext > TickMath.MAX_TICK) {
        step.tickNext = TickMath.MAX_TICK;
      }

      // get the price for the next tick
      step.sqrtPriceNextX96 = TickMath.getSqrtRatioAtTick(step.tickNext);

      // compute values to swap to the target tick, price limit, or point where input/output amount is exhausted
      var swapStep = SwapMath.computeSwapStep(
        state.sqrtPriceX96,
        (zeroForOne ? step.sqrtPriceNextX96.compareTo(sqrtPriceLimitX96) < 0 : step.sqrtPriceNextX96.compareTo(sqrtPriceLimitX96) > 0)
          ? sqrtPriceLimitX96
          : step.sqrtPriceNextX96,
        state.liquidity,
        state.amountSpecifiedRemaining,
        this.settings.fee
      );

      state.sqrtPriceX96 = swapStep.sqrtRatioNextX96;
      step.amountIn = swapStep.amountIn;
      step.amountOut = swapStep.amountOut;
      step.feeAmount = swapStep.feeAmount;

      if (exactInput) {
        state.amountSpecifiedRemaining = state.amountSpecifiedRemaining.subtract(step.amountIn.add(step.feeAmount));
        state.amountCalculated = state.amountCalculated.subtract(step.amountOut);
      } else {
        state.amountSpecifiedRemaining = state.amountSpecifiedRemaining.add(step.amountOut);
        state.amountCalculated = state.amountCalculated.add((step.amountIn.add(step.feeAmount)));
      }
      
      // if the protocol fee is on, calculate how much is owed, decrement feeAmount, and increment protocolFee
      if (cache.feeProtocol > 0) {
        BigInteger delta = step.feeAmount.divide(BigInteger.valueOf(cache.feeProtocol));
        step.feeAmount = step.feeAmount.subtract(delta);
        state.protocolFee = state.protocolFee.add(delta);
      }

      // update global fee tracker
      if (state.liquidity.compareTo(ZERO) > 0) {
        state.feeGrowthGlobalX128 = state.feeGrowthGlobalX128.add(FullMath.mulDiv(step.feeAmount, FixedPoint128.Q128, state.liquidity));
      }
      
      // shift tick if we reached the next price
      if (state.sqrtPriceX96.equals(step.sqrtPriceNextX96)) {
        // if the tick is initialized, run the tick transition
        if (step.initialized) {
          // check for the placeholder value, which we replace with the actual value the first time the swap
          // crosses an initialized tick
          if (!cache.computedLatestObservation) {
            var result = observations.observeSingle(
              cache.blockTimestamp,
              ZERO,
              slot0Start.tick,
              slot0Start.observationIndex,
              cache.liquidityStart,
              slot0Start.observationCardinality
            );
            cache.tickCumulative = result.tickCumulative;
            cache.secondsPerLiquidityCumulativeX128 = result.secondsPerLiquidityCumulativeX128;
            cache.computedLatestObservation = true;
          }
          Tick.Info info = ticks.cross(
            step.tickNext,
            (zeroForOne ? state.feeGrowthGlobalX128 : this.feeGrowthGlobal0X128.get()),
            (zeroForOne ? this.feeGrowthGlobal1X128.get() : state.feeGrowthGlobalX128),
            cache.secondsPerLiquidityCumulativeX128,
            cache.tickCumulative,
            cache.blockTimestamp
          );
          BigInteger liquidityNet = info.liquidityNet;
          this.onTickUpdate(step.tickNext, info);

          // if we're moving leftward, we interpret liquidityNet as the opposite sign
          // safe because liquidityNet cannot be type(int128).min
          if (zeroForOne) liquidityNet = liquidityNet.negate();

          state.liquidity = LiquidityMath.addDelta(state.liquidity, liquidityNet);
        }

        state.tick = zeroForOne ? step.tickNext - 1 : step.tickNext;
      } else if (state.sqrtPriceX96 != step.sqrtPriceStartX96) {
        // recompute unless we're on a lower tick boundary (i.e. already transitioned ticks), and haven't moved
        state.tick = TickMath.getTickAtSqrtRatio(state.sqrtPriceX96);
      }
    }

    // update tick and write an oracle entry if the tick change
    Slot0 _slot0 = this.slot0.get();
    if (state.tick != slot0Start.tick) {
      var result =
        this.observations.write(
          slot0Start.observationIndex,
          cache.blockTimestamp,
          slot0Start.tick,
          cache.liquidityStart,
          slot0Start.observationCardinality,
          slot0Start.observationCardinalityNext
        );
      _slot0.sqrtPriceX96 = state.sqrtPriceX96;
      _slot0.tick = state.tick;
      _slot0.observationIndex = result.observationIndex;
      _slot0.observationCardinality = result.observationCardinality;
    } else {
      // otherwise just update the price
      _slot0.sqrtPriceX96 = state.sqrtPriceX96;
    }
    this.slot0.set(_slot0);

    // update liquidity if it changed
    if (cache.liquidityStart != state.liquidity) {
      this.liquidity.set(state.liquidity);
    }

    // update fee growth global and, if necessary, protocol fees
    // overflow is acceptable, protocol has to withdraw before it hits type(uint128).max fees
    if (zeroForOne) {
      this.feeGrowthGlobal0X128.set(state.feeGrowthGlobalX128);
      if (state.protocolFee.compareTo(ZERO) > 0) {
        var _protocolFees = this.protocolFees.get();
        _protocolFees.token0 = _protocolFees.token0.add(state.protocolFee);
        this.protocolFees.set(_protocolFees);
      }
    } else {
      this.feeGrowthGlobal1X128.set(state.feeGrowthGlobalX128);
      if (state.protocolFee.compareTo(ZERO) > 0) {
        var _protocolFees = this.protocolFees.get();
        _protocolFees.token1 = _protocolFees.token1.add(state.protocolFee);
        this.protocolFees.set(_protocolFees);
      }
    }

    BigInteger amount0;
    BigInteger amount1;

    if (zeroForOne == exactInput) {
      amount0 = amountSpecified.subtract(state.amountSpecifiedRemaining);
      amount1 = state.amountCalculated;
    } else {
      amount0 = state.amountCalculated;
      amount1 = amountSpecified.subtract(state.amountSpecifiedRemaining);
    }

    // do the transfers and collect payment
    if (zeroForOne) {
      if (amount1.compareTo(ZERO) < 0) {
        pay(this.settings.token1, recipient, amount1.negate());
      }

      BigInteger balance0Before = balance0();
      IConcentratedLiquidityPoolCallee.balancedSwapCallback(caller, amount0, amount1, data);

      Context.require(balance0Before.add(amount0).compareTo(balance0()) <= 0, 
        "swap: the callback didn't charge the payment (1)");
    } else {
      if (amount0.compareTo(ZERO) < 0) {
        pay(this.settings.token0, recipient, amount0.negate());
      }

      BigInteger balance1Before = balance1();
      IConcentratedLiquidityPoolCallee.balancedSwapCallback(caller, amount0, amount1, data);

      Context.require(balance1Before.add(amount1).compareTo(balance1()) <= 0, 
        "swap: the callback didn't charge the payment (2)");
    }

    this.PoolIntrinsicsUpdate(state.sqrtPriceX96, state.tick, state.liquidity);
    this.Swap(caller, recipient, amount0, amount1, state.sqrtPriceX96, state.liquidity, state.tick);
    this.unlock(true);

    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Receive token0 and/or token1 and pay it back, plus a fee, in the callback
   * 
   * Access: Everyone
   * 
   * @dev The caller of this method receives a callback in the form of balancedFlashCallback
   * @dev Can be used to donate underlying tokens pro-rata to currently in-range liquidity providers by calling
   * with 0 amount{0,1} and sending the donation amount(s) from the callback
   * @param recipient The address which will receive the token0 and token1 amounts
   * @param amount0 The amount of token0 to send
   * @param amount1 The amount of token1 to send
   * @param data Any data to be passed through to the callback
   */
  @External
  public void flash (
    Address recipient,
    BigInteger amount0,
    BigInteger amount1,
    byte[] data
  ) {
    this.unlock(false);
    final Address caller = Context.getCaller();

    BigInteger _liquidity = this.liquidity.get();
    Context.require(_liquidity.compareTo(ZERO) > 0,
      "flash: no liquidity");
    
    final BigInteger TEN_E6 = BigInteger.valueOf(1000000);
    
    BigInteger fee0 = FullMath.mulDivRoundingUp(amount0, BigInteger.valueOf(this.settings.fee), TEN_E6);
    BigInteger fee1 = FullMath.mulDivRoundingUp(amount1, BigInteger.valueOf(this.settings.fee), TEN_E6);
    BigInteger balance0Before = balance0();
    BigInteger balance1Before = balance1();

    if (amount0.compareTo(ZERO) > 0) {
      pay(this.settings.token0, recipient, amount0);
    }
    if (amount1.compareTo(ZERO) > 0) {
      pay(this.settings.token1, recipient, amount1);
    }

    IConcentratedLiquidityPoolCallee.balancedFlashCallback(caller, fee0, fee1, data);

    BigInteger balance0After = balance0();
    BigInteger balance1After = balance1();

    Context.require(balance0Before.add(fee0).compareTo(balance0After) <= 0, 
      "flash: not enough token0 returned");
    
    Context.require(balance1Before.add(fee1).compareTo(balance1After) <= 0, 
      "flash: not enough token1 returned");

    // sub is safe because we know balanceAfter is gt balanceBefore by at least fee
    BigInteger paid0 = balance0After.subtract(balance0Before);
    BigInteger paid1 = balance1After.subtract(balance1Before);

    Slot0 _slot0 = this.slot0.get();

    if (paid0.compareTo(ZERO) > 0) {
      int feeProtocol0 = _slot0.feeProtocol % 16;
      BigInteger fees0 = feeProtocol0 == 0 ? ZERO : paid0.divide(BigInteger.valueOf(feeProtocol0));
      if (fees0.compareTo(ZERO) > 0) {
        var _protocolFees = protocolFees.get();
        _protocolFees.token0 = _protocolFees.token0.add(fees0);
        protocolFees.set(_protocolFees);
      }
      this.feeGrowthGlobal0X128.set(uint256(this.feeGrowthGlobal0X128.get().add(FullMath.mulDiv(paid0.subtract(fees0), FixedPoint128.Q128, _liquidity))));
    }
    if (paid1.compareTo(ZERO) > 0) {
      int feeProtocol1 = _slot0.feeProtocol >> 4;
      BigInteger fees1 = feeProtocol1 == 0 ? ZERO : paid1.divide(BigInteger.valueOf(feeProtocol1));
      if (fees1.compareTo(ZERO) > 0) {
        var _protocolFees = protocolFees.get();
        _protocolFees.token1 = _protocolFees.token1.add(fees1);
        protocolFees.set(_protocolFees);
      }
      this.feeGrowthGlobal1X128.set(uint256(this.feeGrowthGlobal1X128.get().add(FullMath.mulDiv(paid1.subtract(fees1), FixedPoint128.Q128, _liquidity))));
    }

    this.Flash(caller, recipient, amount0, amount1, paid0, paid1);
  
    this.unlock(true);
  }

  /**
   * @notice Set the denominator of the protocol's % share of the fees
   * 
   * Access: Factory Owner
   * 
   * @param feeProtocol0 new protocol fee for token0 of the pool
   * @param feeProtocol1 new protocol fee for token1 of the pool
   */
  @External
  public void setFeeProtocol (
    int feeProtocol0,
    int feeProtocol1
  ) {
    this.unlock(false);

    // Access control
    this.checkCallerIsFactoryOwner();
    
    // Check user input for protocol fees
    Context.require(
      (feeProtocol0 == 0 || (feeProtocol0 >= 4 && feeProtocol0 <= 10)) &&
      (feeProtocol1 == 0 || (feeProtocol1 >= 4 && feeProtocol1 <= 10)),
      "setFeeProtocol: Bad fees amount"
    );

    // OK
    Slot0 _slot0 = this.slot0.get();
    int feeProtocolOld = _slot0.feeProtocol;
    _slot0.feeProtocol = feeProtocol0 + (feeProtocol1 << 4);
    this.slot0.set(_slot0);

    this.SetFeeProtocol(feeProtocolOld % 16, feeProtocolOld >> 4, feeProtocol0, feeProtocol1);

    this.unlock(true);
  }

  /**
   * @notice Collect the protocol fee accrued to the pool
   * 
   * Access: Factory Owner
   * 
   * @param recipient The address to which collected protocol fees should be sent
   * @param amount0Requested The maximum amount of token0 to send, can be 0 to collect fees in only token1
   * @param amount1Requested The maximum amount of token1 to send, can be 0 to collect fees in only token0
   * @return amount0 The protocol fee collected in token0
   * @return amount1 The protocol fee collected in token1
   */
  @External
  public PairAmounts collectProtocol (
    Address recipient,
    BigInteger amount0Requested,
    BigInteger amount1Requested
  ) {
    this.unlock(false);

    // Access control
    this.checkCallerIsFactoryOwner();

    // OK
    var _protocolFees = protocolFees.get();
    final Address caller = Context.getCaller();

    BigInteger amount0 = amount0Requested.compareTo(_protocolFees.token0) > 0 ? _protocolFees.token0 : amount0Requested;
    BigInteger amount1 = amount1Requested.compareTo(_protocolFees.token1) > 0 ? _protocolFees.token1 : amount1Requested;
    
    if (amount0.compareTo(ZERO) > 0) {
      if (amount0.equals(_protocolFees.token0)) {
        // ensure that the slot is not cleared, for steps savings
        amount0 = amount0.subtract(BigInteger.ONE); 
      }
      _protocolFees.token0 = _protocolFees.token0.subtract(amount0);
      this.protocolFees.set(_protocolFees);
      pay(this.settings.token0, recipient, amount0);
    }
    if (amount1.compareTo(ZERO) > 0) {
      if (amount1.equals(_protocolFees.token1)) {
        // ensure that the slot is not cleared, for steps savings
        amount1 = amount1.subtract(BigInteger.ONE); 
      }
      _protocolFees.token1 = _protocolFees.token1.subtract(amount1);
      this.protocolFees.set(_protocolFees);
      pay(this.settings.token1, recipient, amount1);
    }

    this.CollectProtocol(caller, recipient, amount0, amount1);
    
    this.unlock(true);
    return new PairAmounts(amount0, amount1);
  }

  @External
  @Payable
  public void depositIcx () {
    Context.require(Context.getCaller().isContract(), 
      "depositIcx: Pool shouldn't need to receive ICX from EOA");
  }

  @External
  public void tokenFallback (Address _from, BigInteger _value, @Optional byte[] _data) throws Exception {
    Context.require(_from.isContract(), 
      "tokenFallback: Pool shouldn't need to receive tokens from EOA");
  }

  // ================================================
  // ReadOnly methods
  // ================================================
  /**
   * @notice Returns a snapshot of the tick cumulative, seconds per liquidity and seconds inside a tick range
   * 
   * Access: Everyone
   * 
   * @dev Snapshots must only be compared to other snapshots, taken over a period for which a position existed.
   * I.e., snapshots cannot be compared if a position is not held for the entire period between when the first
   * snapshot is taken and the second snapshot is taken.
   * @param tickLower The lower tick of the range
   * @param tickUpper The upper tick of the range
   * @return tickCumulativeInside The snapshot of the tick accumulator for the range
   * @return secondsPerLiquidityInsideX128 The snapshot of seconds per liquidity for the range
   * @return secondsInside The snapshot of seconds per liquidity for the range
   */
  @External(readonly = true)
  public SnapshotCumulativesInsideResult snapshotCumulativesInside (int tickLower, int tickUpper) {
    checkTicks(tickLower, tickUpper);

    Tick.Info lower = this.ticks.get(tickLower);
    Tick.Info upper = this.ticks.get(tickUpper);

    BigInteger tickCumulativeLower = lower.tickCumulativeOutside;
    BigInteger tickCumulativeUpper = upper.tickCumulativeOutside;
    BigInteger secondsPerLiquidityOutsideLowerX128 = lower.secondsPerLiquidityOutsideX128;
    BigInteger secondsPerLiquidityOutsideUpperX128 = upper.secondsPerLiquidityOutsideX128;
    BigInteger secondsOutsideLower = lower.secondsOutside;
    BigInteger secondsOutsideUpper = upper.secondsOutside;

    Context.require(lower.initialized, 
      "snapshotCumulativesInside: lower not initialized");
    Context.require(upper.initialized, 
      "snapshotCumulativesInside: upper not initialized");

    Slot0 _slot0 = this.slot0.get();

    if (_slot0.tick < tickLower) {
      return new SnapshotCumulativesInsideResult(
        tickCumulativeLower.subtract(tickCumulativeUpper),
        secondsPerLiquidityOutsideLowerX128.subtract(secondsPerLiquidityOutsideUpperX128),
        secondsOutsideLower.subtract(secondsOutsideUpper)
      );
    } else if (_slot0.tick < tickUpper) {
      BigInteger time = TimeUtils.now();
      Observations.ObserveSingleResult result = this.observations.observeSingle(
        time, 
        ZERO, 
        _slot0.tick, 
        _slot0.observationIndex, 
        this.liquidity.get(), 
        _slot0.observationCardinality
      );
      BigInteger tickCumulative = result.tickCumulative;
      BigInteger secondsPerLiquidityCumulativeX128 = result.secondsPerLiquidityCumulativeX128;

      return new SnapshotCumulativesInsideResult(
        tickCumulative.subtract(tickCumulativeLower).subtract(tickCumulativeUpper),
        secondsPerLiquidityCumulativeX128.subtract(secondsPerLiquidityOutsideLowerX128).subtract(secondsPerLiquidityOutsideUpperX128),
        time.subtract(secondsOutsideLower).subtract(secondsOutsideUpper)
      );
    } else {
      return new SnapshotCumulativesInsideResult (
        tickCumulativeUpper.subtract(tickCumulativeLower),
        secondsPerLiquidityOutsideUpperX128.subtract(secondsPerLiquidityOutsideLowerX128),
        secondsOutsideUpper.subtract(secondsOutsideLower)
      );
    }
  }

  /**
   * @notice Returns the cumulative tick and liquidity as of each timestamp `secondsAgo` from the current block timestamp
   * 
   * Access: Everyone
   * 
   * @dev To get a time weighted average tick or liquidity-in-range, you must call this with two values, one representing
   * the beginning of the period and another for the end of the period. E.g., to get the last hour time-weighted average tick,
   * you must call it with secondsAgos = [3600, 0].
   * @dev The time weighted average tick represents the geometric time weighted average price of the pool, in
   * log base sqrt(1.0001) of token1 / token0. The TickMath library can be used to go from a tick value to a ratio.
   * @param secondsAgos From how long ago each cumulative tick and liquidity value should be returned
   * @return tickCumulatives Cumulative tick values as of each `secondsAgos` from the current block timestamp
   * @return secondsPerLiquidityCumulativeX128s Cumulative seconds per liquidity-in-range value as of each `secondsAgos` from the current block
   * timestamp
   */
  @External(readonly = true)
  public ObserveResult observe (BigInteger[] secondsAgos) {
    Slot0 _slot0 = this.slot0.get();
    return this.observations.observe(
      TimeUtils.now(), 
      secondsAgos, 
      _slot0.tick, 
      _slot0.observationIndex, 
      this.liquidity.get(), 
      _slot0.observationCardinality
    );
  }
  
  // ================================================
  // Private methods
  // ================================================
  private void pay (Address token, Address recipient, BigInteger amount) {
    IIRC2ICX.transfer(token, recipient, amount, "deposit");
  }

  /**
   * @dev Gets and updates a position with the given liquidity delta
   * @param owner the owner of the position
   * @param tickLower the lower tick of the position's tick range
   * @param tickUpper the upper tick of the position's tick range
   * @param tick the current tick, passed to avoid sloads
   */
  private PositionStorage _updatePosition (
    Address owner,
    int tickLower,
    int tickUpper,
    BigInteger liquidityDelta,
    int tick
  ) {
    byte[] positionKey = Positions.getKey(owner, tickLower, tickUpper);
    Position.Info position = this.positions.get(positionKey);

    BigInteger _feeGrowthGlobal0X128 = this.feeGrowthGlobal0X128.get();
    BigInteger _feeGrowthGlobal1X128 = this.feeGrowthGlobal1X128.get();
    Slot0 _slot0 = this.slot0.get();

    // if we need to update the ticks, do it
    boolean flippedLower = false;
    boolean flippedUpper = false;
    if (!liquidityDelta.equals(ZERO)) {
      BigInteger time = TimeUtils.now();
      var result = this.observations.observeSingle(
        time, 
        ZERO, 
        _slot0.tick, 
        _slot0.observationIndex, 
        this.liquidity.get(), 
        _slot0.observationCardinality
      );

      BigInteger tickCumulative = result.tickCumulative;
      BigInteger secondsPerLiquidityCumulativeX128 = result.secondsPerLiquidityCumulativeX128;

      Ticks.UpdateResult resultLower  = this.ticks.update(
        tickLower,
        tick,
        liquidityDelta,
        _feeGrowthGlobal0X128,
        _feeGrowthGlobal1X128,
        secondsPerLiquidityCumulativeX128,
        tickCumulative,
        time,
        false,
        this.settings.maxLiquidityPerTick
      );
      flippedLower = resultLower.flipped;
      this.onTickUpdate(tickLower, resultLower.info);
      
      Ticks.UpdateResult resultUpper = this.ticks.update(
        tickUpper,
        tick,
        liquidityDelta,
        _feeGrowthGlobal0X128,
        _feeGrowthGlobal1X128,
        secondsPerLiquidityCumulativeX128,
        tickCumulative,
        time,
        true,
        this.settings.maxLiquidityPerTick
      );
      flippedUpper = resultUpper.flipped;
      this.onTickUpdate(tickUpper, resultUpper.info);
      
      if (flippedLower) {
        this.tickBitmap.flipTick(tickLower, this.settings.tickSpacing);
      }
      if (flippedUpper) {
        this.tickBitmap.flipTick(tickUpper, this.settings.tickSpacing);
      }
    }

    var result = this.ticks.getFeeGrowthInside(tickLower, tickUpper, tick, _feeGrowthGlobal0X128, _feeGrowthGlobal1X128);
    BigInteger feeGrowthInside0X128 = result.feeGrowthInside0X128;
    BigInteger feeGrowthInside1X128 = result.feeGrowthInside1X128;

    PositionLib.update(position, liquidityDelta, feeGrowthInside0X128, feeGrowthInside1X128);

    // clear any tick data that is no longer needed
    if (liquidityDelta.compareTo(ZERO) < 0) {
      if (flippedLower) {
        this.ticks.clear(tickLower);
        this.onTickUpdate(tickLower, null);
      }
      if (flippedUpper) {
        this.ticks.clear(tickUpper);
        this.onTickUpdate(tickUpper, null);
      }
    }

    this.positions.set(positionKey, position);
    return new PositionStorage(position, positionKey);
  }

  private void onTickUpdate (int index, Tick.Info info) {
    BigInteger liquidityGross = info != null ? info.liquidityGross : ZERO;
    BigInteger liquidityNet = info != null ? info.liquidityNet : ZERO;
    BigInteger feeGrowthOutside0X128 = info != null ? info.feeGrowthOutside0X128 : ZERO;
    BigInteger feeGrowthOutside1X128 = info != null ? info.feeGrowthOutside1X128 : ZERO;
    BigInteger tickCumulativeOutside = info != null ? info.tickCumulativeOutside : ZERO;
    BigInteger secondsPerLiquidityOutsideX128 = info != null ? info.secondsPerLiquidityOutsideX128 : ZERO;
    BigInteger secondsOutside = info != null ? info.secondsOutside : ZERO;
    boolean initialized = info != null ? info.initialized : false;

    this.TickUpdate (index, 
      liquidityGross,
      liquidityNet,
      feeGrowthOutside0X128,
      feeGrowthOutside1X128,
      tickCumulativeOutside,
      secondsPerLiquidityOutsideX128,
      secondsOutside,
      initialized
    );
  }

  /**
   * @dev Effect some changes to a position
   * @param params the position details and the change to the position's liquidity to effect
   * @return position a storage pointer referencing the position with the given owner and tick range
   * @return amount0 the amount of token0 owed to the pool, negative if the pool should pay the recipient
   * @return amount1 the amount of token1 owed to the pool, negative if the pool should pay the recipient
   */
  private ModifyPositionResult _modifyPosition (ModifyPositionParams params) {
    checkTicks(params.tickLower, params.tickUpper);

    Slot0 _slot0 = this.slot0.get();

    var positionStorage = _updatePosition(
      params.owner,
      params.tickLower,
      params.tickUpper,
      params.liquidityDelta,
      _slot0.tick
    );

    BigInteger amount0 = ZERO;
    BigInteger amount1 = ZERO;

    if (!params.liquidityDelta.equals(ZERO)) {
      if (_slot0.tick < params.tickLower) {
        // current tick is below the passed range; liquidity can only become in range by crossing from left to
        // right, when we'll need _more_ token0 (it's becoming more valuable) so user must provide it
        amount0 = SqrtPriceMath.getAmount0Delta(
          TickMath.getSqrtRatioAtTick(params.tickLower),
          TickMath.getSqrtRatioAtTick(params.tickUpper),
          params.liquidityDelta
        );
      } else if (_slot0.tick < params.tickUpper) {
        // current tick is inside the passed range
        BigInteger liquidityBefore = this.liquidity.get();

        // write an oracle entry
        var writeResult = this.observations.write(
          _slot0.observationIndex,
          TimeUtils.now(),
          _slot0.tick,
          liquidityBefore,
          _slot0.observationCardinality,
          _slot0.observationCardinalityNext
        );

        _slot0.observationIndex = writeResult.observationIndex;
        _slot0.observationCardinality = writeResult.observationCardinality;
        this.slot0.set(_slot0);

        amount0 = SqrtPriceMath.getAmount0Delta(
          _slot0.sqrtPriceX96,
          TickMath.getSqrtRatioAtTick(params.tickUpper),
          params.liquidityDelta
        );
        amount1 = SqrtPriceMath.getAmount1Delta(
          TickMath.getSqrtRatioAtTick(params.tickLower),
          _slot0.sqrtPriceX96,
          params.liquidityDelta
        );

        BigInteger newLiquidity = LiquidityMath.addDelta(liquidityBefore, params.liquidityDelta);
        this.liquidity.set(newLiquidity);
        this.PoolIntrinsicsUpdate(_slot0.sqrtPriceX96, _slot0.tick, newLiquidity);
      } else {
        // current tick is above the passed range; liquidity can only become in range by crossing from right to
        // left, when we'll need _more_ token1 (it's becoming more valuable) so user must provide it
        amount1 = SqrtPriceMath.getAmount1Delta(
          TickMath.getSqrtRatioAtTick(params.tickLower),
          TickMath.getSqrtRatioAtTick(params.tickUpper),
          params.liquidityDelta
        );
      }
    }

    return new ModifyPositionResult(positionStorage, amount0, amount1);
  }

  /**
   * @notice Get the pool's balance of token0
   */
  private BigInteger balance0 () {
    return IIRC2ICX.balanceOf(this.settings.token0, Context.getAddress());
  }

  /**
   * @notice Get the pool's balance of token1
   */
  private BigInteger balance1 () {
    return IIRC2ICX.balanceOf(this.settings.token1, Context.getAddress());
  }

  // ================================================
  // Checks
  // ================================================
  private void checkTicks (int tickLower, int tickUpper) {
    Context.require(tickLower < tickUpper, 
      "checkTicks: tickLower must be lower than tickUpper");
    Context.require(tickLower >= TickMath.MIN_TICK, 
      "checkTicks: tickLower lower than expected");
    Context.require(tickUpper <= TickMath.MAX_TICK, 
      "checkTicks: tickUpper greater than expected");
  }

  private void checkCallerIsFactoryOwner() {
    final Address factoryOwner = IBalancedFactory.owner(this.settings.factory);
    final Address caller = Context.getCaller();

    Context.require(caller.equals(factoryOwner),
      "checkCallerIsFactoryOwner: Only owner can call this method");
  }

  // ================================================
  // Public variable getters
  // ================================================
  @External(readonly = true)
  public String name() {
    return this.settings.name;
  }

  @External(readonly = true)
  public Address factory() {
    return this.settings.factory;
  }

  @External(readonly = true)
  public Address token0() {
    return this.settings.token0;
  }

  @External(readonly = true)
  public Address token1() {
    return this.settings.token1;
  }

  @External(readonly = true)
  public PoolSettings settings() {
    return this.settings;
  }

  /**
   * The 0th storage slot in the pool stores many values, and is exposed as a single method to save steps when accessed externally.
   */
  @External(readonly = true)
  public Slot0 slot0 () {
    return this.slot0.get();
  }

  @External(readonly = true)
  public ProtocolFees protocolFees () {
    return this.protocolFees.get();
  }

  @External(readonly = true)
  public BigInteger maxLiquidityPerTick () {
    return this.settings.maxLiquidityPerTick;
  }

  @External(readonly = true)
  public BigInteger liquidity () {
    return this.liquidity.get();
  }

  @External(readonly = true)
  public BigInteger fee () {
    return BigInteger.valueOf(this.settings.fee);
  }

  @External(readonly = true)
  public BigInteger tickSpacing () {
    return BigInteger.valueOf(this.settings.tickSpacing);
  }

  @External(readonly = true)
  public BigInteger feeGrowthGlobal0X128 () {
    return feeGrowthGlobal0X128.get();
  }

  @External(readonly = true)
  public BigInteger feeGrowthGlobal1X128 () {
    return feeGrowthGlobal1X128.get();
  }
  
  // Implements Interfaces
  // --- Ticks --- 
  @External(readonly = true)
  public Tick.Info ticks (int tick) {
    return this.ticks.get(tick);
  }

  @External(readonly = true)
  public BigInteger ticksInitializedSize () {
    return BigInteger.valueOf(this.ticks.initializedSize());
  }

  @External(readonly = true)
  public BigInteger ticksInitialized (int index) {
    return BigInteger.valueOf(this.ticks.initialized(index));
  }

  @External(readonly = true)
  public Tick.Info[] ticksInitializedRange (int start, int end) {
    Tick.Info[] result = new Tick.Info[end-start];
    for (int i = start, j = 0; i < end; i++, j++) {
      result[j] = this.ticks.get(this.ticks.initialized(i));
    }
    return result;
  }

  // --- Position --- 
  @External(readonly = true)
  public Position.Info positions (byte[] key) {
    return this.positions.get(key);
  }

  // --- Observations --- 
  @External(readonly = true)
  public Oracle.Observation observations (int index) {
    return this.observations.get(index);
  }
 
  @External(readonly = true)
  public Oracle.Observation oldestObservation () {
    return this.observations.getOldest();
  }

  // --- TickBitmap --- 
  @External(readonly = true)
  public BigInteger tickBitmap (int index) {
    return this.tickBitmap.get(index);
  }
  
  @External(readonly = true)
  public NextInitializedTickWithinOneWordResult nextInitializedTickWithinOneWord (
    int tick, 
    int tickSpacing, 
    boolean zeroForOne
  ) {
    return tickBitmap.nextInitializedTickWithinOneWord(
      tick,
      tickSpacing,
      zeroForOne
    );
  }
}
