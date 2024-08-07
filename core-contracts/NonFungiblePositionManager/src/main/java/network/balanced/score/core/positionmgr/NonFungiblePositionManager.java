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

package network.balanced.score.core.positionmgr;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import network.balanced.score.core.dex.interfaces.pool.IBalancedMintCallback;
import network.balanced.score.core.dex.libs.FixedPoint128;
import network.balanced.score.core.dex.libs.FullMath;
import network.balanced.score.core.dex.models.Positions;
import team.iconation.standards.token.irc721.IRC721Enumerable;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.io.Reader;
import scorex.io.StringReader;

import static network.balanced.score.core.dex.utils.IntUtils.uint128;
import network.balanced.score.core.positionmgr.interfaces.IBalancedLiquidityManagement;
import network.balanced.score.core.positionmgr.libs.PoolAddressLib;
import network.balanced.score.core.positionmgr.structs.AddLiquidityParams;
import network.balanced.score.core.positionmgr.implementation.BalancedLiquidityManagement;
import network.balanced.score.core.dex.interfaces.pool.IConcentratedLiquidityPool;
import network.balanced.score.core.dex.structs.pool.PairAmounts;
import network.balanced.score.core.dex.structs.pool.Position;
import network.balanced.score.core.dex.structs.pool.PoolAddress.PoolKey;
import network.balanced.score.core.positionmgr.interfaces.INonfungibleTokenPositionDescriptor;
import network.balanced.score.core.positionmgr.structs.CollectParams;
import network.balanced.score.core.positionmgr.structs.DecreaseLiquidityParams;
import network.balanced.score.core.positionmgr.structs.IncreaseLiquidityParams;
import network.balanced.score.core.positionmgr.structs.IncreaseLiquidityResult;
import network.balanced.score.core.positionmgr.structs.MintParams;
import network.balanced.score.core.positionmgr.structs.MintResult;
import network.balanced.score.core.positionmgr.structs.NFTPosition;
import network.balanced.score.core.positionmgr.structs.PositionInformation;
import network.balanced.score.core.dex.utils.TimeUtils;

// @title NFT positions
// @notice Wraps Balanced positions in the IRC non-fungible token interface
public class NonFungiblePositionManager extends IRC721Enumerable
  implements IBalancedLiquidityManagement,
             IBalancedMintCallback
{
  // ================================================
  // Consts
  // ================================================
  // Contract class name
  public static final String NAME = "NonFungiblePositionManager";

  // Contract name
  private final String name;

  // Factory
  private final Address factory;

  // Liquidity Manager
  private final BalancedLiquidityManagement liquidityMgr;

  // ================================================
  // DB Variables
  // ================================================
  /// @dev IDs of pools assigned by this contract
  private final DictDB<Address, BigInteger> poolIds = Context.newDictDB(NAME + "_poolIds", BigInteger.class);

  /// @dev Pool keys by pool ID, to save on SSTOREs for position data
  private final DictDB<BigInteger, PoolKey> poolIdToPoolKey = Context.newDictDB(NAME + "_poolIdToPoolKey", PoolKey.class);

  /// @dev The token ID position data
  private final DictDB<BigInteger, NFTPosition> positions = Context.newDictDB(NAME + "_positions", NFTPosition.class);

  /// @dev The ID of the next token that will be minted. Skips 0
  private final VarDB<BigInteger> nextId = Context.newVarDB(NAME + "_nextId", BigInteger.class);
  /// @dev The ID of the next pool that is used for the first time. Skips 0
  private final VarDB<BigInteger> nextPoolId = Context.newVarDB(NAME + "_nextPoolId", BigInteger.class);

  /// @dev The address of the token descriptor contract, which handles generating token URIs for position tokens
  private final Address tokenDescriptor;

  // ================================================
  // Event Logs
  // ================================================ 
  /// @notice Emitted when liquidity is increased for a position NFT
  /// @dev Also emitted when a token is minted
  /// @param tokenId The ID of the token for which liquidity was increased
  /// @param liquidity The amount by which liquidity for the NFT position was increased
  /// @param amount0 The amount of token0 that was paid for the increase in liquidity
  /// @param amount1 The amount of token1 that was paid for the increase in liquidity
  @EventLog
  public void IncreaseLiquidity (
    BigInteger tokenId,
    BigInteger liquidity,
    BigInteger amount0,
    BigInteger amount1
  ) {}

  /// @notice Emitted when liquidity is decreased for a position NFT
  /// @param tokenId The ID of the token for which liquidity was decreased
  /// @param liquidity The amount by which liquidity for the NFT position was decreased
  /// @param amount0 The amount of token0 that was accounted for the decrease in liquidity
  /// @param amount1 The amount of token1 that was accounted for the decrease in liquidity
  @EventLog
  public void DecreaseLiquidity (
    BigInteger tokenId,
    BigInteger liquidity,
    BigInteger amount0,
    BigInteger amount1
  ) {}

  /// @notice Emitted when tokens are collected for a position NFT
  /// @dev The amounts reported may not be exactly equivalent to the amounts transferred, due to rounding behavior
  /// @param tokenId The ID of the token for which underlying tokens were collected
  /// @param recipient The address of the account that received the collected tokens
  /// @param amount0 The amount of token0 owed to the position that was collected
  /// @param amount1 The amount of token1 owed to the position that was collected
  @EventLog
  public void Collect (
    BigInteger tokenId,
    Address recipient,
    BigInteger amount0Collect,
    BigInteger amount1Collect
  ) {}

  // ================================================
  // Methods
  // ================================================
  /**
   *  @notice Contract constructor
   */
  public NonFungiblePositionManager (
    Address factory,
    Address tokenDescriptor
  ) {
    super("Balanced Positions NFT-V1", "BLN-POS");

    this.liquidityMgr = new BalancedLiquidityManagement(factory);
    this.name = "Balanced NFT Position Manager";
    this.factory = factory;
    this.tokenDescriptor = tokenDescriptor;

    if (this.nextId.get() == null) {
      this.nextId.set(ONE);
    }
    if (this.nextPoolId.get() == null) {
      this.nextPoolId.set(ONE);
    }
  }

  /**
   * @notice Returns the position information associated with a given token ID.
   * @dev Throws if the token ID is not valid.
   * 
   * Access: Everyone
   * 
   * @param tokenId The ID of the token that represents the position
   * @return the position
   */
  @External(readonly = true)
  public PositionInformation positions (
    BigInteger tokenId
  ) {
    NFTPosition position = this.positions.getOrDefault(tokenId, NFTPosition.empty());
    Context.require(!position.poolId.equals(ZERO),
      "positions: Invalid token ID");

    PoolKey poolKey = this.poolIdToPoolKey.get(position.poolId);
    return new PositionInformation(
      position.nonce,
      position.operator,
      poolKey.token0,
      poolKey.token1,
      poolKey.fee,
      position.tickLower,
      position.tickUpper,
      position.liquidity,
      position.feeGrowthInside0LastX128,
      position.feeGrowthInside1LastX128,
      position.tokensOwed0,
      position.tokensOwed1
    );
  }
  
  /// @dev Caches a pool key
  private BigInteger cachePoolKey (Address pool, PoolKey poolKey) {
    BigInteger poolId = this.poolIds.get(pool);
    if (poolId == null) {
      // increment poolId
      poolId = this.nextPoolId.get();
      this.nextPoolId.set(poolId.add(ONE));

      this.poolIds.set(pool, poolId);
      this.poolIdToPoolKey.set(poolId, poolKey);
    }

    return poolId;
  }

  /**
   * @notice Creates a new position wrapped in a NFT
   * 
   * Access: Everyone
   * 
   * @dev Call this when the pool does exist and is initialized. Note that if the pool is created but not initialized, the call will fail.
   * @param params The params necessary to mint a position, encoded as `MintParams`
   * @return tokenId The ID of the token that represents the minted position
   * @return liquidity The amount of liquidity for this position
   * @return amount0 The amount of token0
   * @return amount1 The amount of token1
   */
  @External
  public MintResult mint (
    MintParams params
  ) {
    this.checkDeadline(params.deadline);
    var result = this.liquidityMgr.addLiquidity(new AddLiquidityParams(
      params.token0,
      params.token1,
      params.fee,
      Context.getAddress(),
      params.tickLower,
      params.tickUpper,
      params.amount0Desired,
      params.amount1Desired,
      params.amount0Min,
      params.amount1Min
    ));

    BigInteger liquidity = result.liquidity;
    BigInteger amount0 = result.amount0;
    BigInteger amount1 = result.amount1;
    Address pool = result.pool;

    // increment ID
    BigInteger tokenId = this.nextId.get();
    this.nextId.set(tokenId.add(ONE));
    this._mint(params.recipient, tokenId);

    byte[] positionKey = Positions.getKey(Context.getAddress(), params.tickLower, params.tickUpper);
    Position.Info poolPos = IConcentratedLiquidityPool.positions(pool, positionKey);
    
    // idempotent set
    BigInteger poolId = cachePoolKey(pool, PoolAddressLib.getPoolKey(params.token0, params.token1, params.fee));

    this.positions.set(tokenId, new NFTPosition(
      ZERO,
      ZERO_ADDRESS,
      poolId,
      params.tickLower,
      params.tickUpper,
      liquidity,
      poolPos.feeGrowthInside0LastX128,
      poolPos.feeGrowthInside1LastX128,
      ZERO,
      ZERO
    ));

    this.IncreaseLiquidity(tokenId, liquidity, amount0, amount1);

    return new MintResult(tokenId, liquidity, amount0, amount1);
  }

  @External(readonly = true)
  public String tokenURI (BigInteger tokenId) {
    _exists(tokenId);
    return INonfungibleTokenPositionDescriptor.tokenURI(this.tokenDescriptor, Context.getAddress(), tokenId);
  }

  /**
   * @notice Increases the amount of liquidity in an existing position, with tokens paid by the `Context.getCaller()`
   * @param params tokenId The ID of the token for which liquidity is being increased,
   * amount0Desired The desired amount of token0 to be spent,
   * amount1Desired The desired amount of token1 to be spent,
   * amount0Min The minimum amount of token0 to spend, which serves as a slippage check,
   * amount1Min The minimum amount of token1 to spend, which serves as a slippage check,
   * deadline The time by which the transaction must be included to effect the change
   * @return liquidity The new liquidity amount as a result of the increase
   * @return amount0 The amount of token0 to achieve resulting liquidity
   * @return amount1 The amount of token1 to achieve resulting liquidity
   */
  @External
  public IncreaseLiquidityResult increaseLiquidity (
    IncreaseLiquidityParams params
  ) {
    this.checkDeadline(params.deadline);

    // OK
    NFTPosition positionStorage = this.positions.get(params.tokenId);
    PoolKey poolKey = this.poolIdToPoolKey.get(positionStorage.poolId);

    var result = this.liquidityMgr.addLiquidity(new AddLiquidityParams(
      poolKey.token0,
      poolKey.token1,
      poolKey.fee,
      Context.getAddress(),
      positionStorage.tickLower,
      positionStorage.tickUpper,
      params.amount0Desired,
      params.amount1Desired,
      params.amount0Min,
      params.amount1Min
    ));

    BigInteger liquidity = result.liquidity;
    BigInteger amount0 = result.amount0;
    BigInteger amount1 = result.amount1;
    Address pool = result.pool;

    byte[] positionKey = Positions.getKey(Context.getAddress(), positionStorage.tickLower, positionStorage.tickUpper);

    // this is now updated to the current transaction
    Position.Info poolPos = IConcentratedLiquidityPool.positions(pool, positionKey);

    BigInteger feeGrowthInside0LastX128 = poolPos.feeGrowthInside0LastX128;
    BigInteger feeGrowthInside1LastX128 = poolPos.feeGrowthInside1LastX128;

    // calculate accumulated fees
    BigInteger tokensOwed0 = uint128(FullMath.mulDiv(feeGrowthInside0LastX128.subtract(positionStorage.feeGrowthInside0LastX128), positionStorage.liquidity, FixedPoint128.Q128));
    BigInteger tokensOwed1 = uint128(FullMath.mulDiv(feeGrowthInside1LastX128.subtract(positionStorage.feeGrowthInside1LastX128), positionStorage.liquidity, FixedPoint128.Q128));

    positionStorage.tokensOwed0 = positionStorage.tokensOwed0.add(tokensOwed0);
    positionStorage.tokensOwed1 = positionStorage.tokensOwed1.add(tokensOwed1);

    positionStorage.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
    positionStorage.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
    positionStorage.liquidity = positionStorage.liquidity.add(liquidity);

    this.positions.set(params.tokenId, positionStorage);
    this.IncreaseLiquidity(params.tokenId, liquidity, amount0, amount1);

    return new IncreaseLiquidityResult(liquidity, amount0, amount1);
  }

  /**
   * @notice Decreases the amount of liquidity in a position and accounts it to the position
   * @param params tokenId The ID of the token for which liquidity is being decreased,
   * amount The amount by which liquidity will be decreased,
   * amount0Min The minimum amount of token0 that should be accounted for the burned liquidity,
   * amount1Min The minimum amount of token1 that should be accounted for the burned liquidity,
   * deadline The time by which the transaction must be included to effect the change
   * @return amount0 The amount of token0 accounted to the position's tokens owed
   * @return amount1 The amount of token1 accounted to the position's tokens owed
   */
  @External
  public PairAmounts decreaseLiquidity (
    DecreaseLiquidityParams params
  ) {
    this.isAuthorizedForToken(params.tokenId);
    this.checkDeadline(params.deadline);

    // OK
    Context.require(params.liquidity.compareTo(ZERO) > 0, 
      "decreaseLiquidity: liquidity must be superior to zero");

    NFTPosition positionStorage = this.positions.get(params.tokenId);
    BigInteger positionLiquidity = positionStorage.liquidity;
    Context.require(positionLiquidity.compareTo(params.liquidity) >= 0,
      "decreaseLiquidity: invalid liquidity");

    PoolKey poolKey = this.poolIdToPoolKey.get(positionStorage.poolId);
    Address pool = PoolAddressLib.getPool(this.factory, poolKey);

    PairAmounts pairAmounts = IConcentratedLiquidityPool.burn(pool, positionStorage.tickLower, positionStorage.tickUpper, params.liquidity);
    BigInteger amount0 = pairAmounts.amount0;
    BigInteger amount1 = pairAmounts.amount1;

    Context.require(amount0.compareTo(params.amount0Min) >= 0 && amount1.compareTo(params.amount1Min) >= 0,
      "decreaseLiquidity: Price slippage check");

    byte[] positionKey = Positions.getKey(Context.getAddress(), positionStorage.tickLower, positionStorage.tickUpper);
    // this is now updated to the current transaction
    Position.Info poolPos = IConcentratedLiquidityPool.positions(pool, positionKey);

    BigInteger feeGrowthInside0LastX128 = poolPos.feeGrowthInside0LastX128;
    BigInteger feeGrowthInside1LastX128 = poolPos.feeGrowthInside1LastX128;

    // calculate accumulated fees
    BigInteger tokensOwed0 = uint128(amount0).add(uint128(FullMath.mulDiv(feeGrowthInside0LastX128.subtract(positionStorage.feeGrowthInside0LastX128), positionLiquidity, FixedPoint128.Q128)));
    BigInteger tokensOwed1 = uint128(amount1).add(uint128(FullMath.mulDiv(feeGrowthInside1LastX128.subtract(positionStorage.feeGrowthInside1LastX128), positionLiquidity, FixedPoint128.Q128)));

    positionStorage.tokensOwed0 = positionStorage.tokensOwed0.add(tokensOwed0);
    positionStorage.tokensOwed1 = positionStorage.tokensOwed1.add(tokensOwed1);

    positionStorage.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
    positionStorage.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
    // subtraction is safe because we checked positionLiquidity is gte params.liquidity
    positionStorage.liquidity = positionLiquidity.subtract(params.liquidity);

    this.DecreaseLiquidity(params.tokenId, params.liquidity, amount0, amount1);

    this.positions.set(params.tokenId, positionStorage);
    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Collects up to a maximum amount of fees owed to a specific position to the recipient
   * @param params tokenId The ID of the NFT for which tokens are being collected,
   *         recipient The account that should receive the tokens,
   *         amount0Max The maximum amount of token0 to collect,
   *         amount1Max The maximum amount of token1 to collect
   * @return amount0 The amount of fees collected in token0
   * @return amount1 The amount of fees collected in token1
   */
  @External
  public PairAmounts collect (CollectParams params) {
    isAuthorizedForToken(params.tokenId);

    Context.require(params.amount0Max.compareTo(ZERO) > 0 || params.amount1Max.compareTo(ZERO) > 0,
      "collect: amount0Max and amount1Max cannot be both zero");

    // allow collecting to the nft position manager address with address 0
    Address recipient = params.recipient.equals(ZERO_ADDRESS) ? Context.getAddress() : params.recipient;

    NFTPosition positionStorage = this.positions.get(params.tokenId);
    PoolKey poolKey = this.poolIdToPoolKey.get(positionStorage.poolId);
    Address pool = PoolAddressLib.getPool(this.factory, poolKey);

    BigInteger tokensOwed0 = positionStorage.tokensOwed0;
    BigInteger tokensOwed1 = positionStorage.tokensOwed1;

    // trigger an update of the position fees owed and fee growth snapshots if it has any liquidity
    if (positionStorage.liquidity.compareTo(ZERO) > 0) {
      IConcentratedLiquidityPool.burn(pool, positionStorage.tickLower, positionStorage.tickUpper, ZERO);
      var positionKey = Positions.getKey(Context.getAddress(), positionStorage.tickLower, positionStorage.tickUpper);
      Position.Info poolPos = IConcentratedLiquidityPool.positions(pool, positionKey);
      BigInteger feeGrowthInside0LastX128 = poolPos.feeGrowthInside0LastX128;
      BigInteger feeGrowthInside1LastX128 = poolPos.feeGrowthInside1LastX128;

      // calculate accumulated fees
      tokensOwed0 = tokensOwed0.add(uint128(FullMath.mulDiv(feeGrowthInside0LastX128.subtract(positionStorage.feeGrowthInside0LastX128), positionStorage.liquidity, FixedPoint128.Q128)));
      tokensOwed1 = tokensOwed1.add(uint128(FullMath.mulDiv(feeGrowthInside1LastX128.subtract(positionStorage.feeGrowthInside1LastX128), positionStorage.liquidity, FixedPoint128.Q128)));
        
      positionStorage.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
      positionStorage.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
    }
    
    // compute the arguments to give to the pool#collect method
    BigInteger amount0Collect, amount1Collect;
    amount0Collect = (params.amount0Max.compareTo(tokensOwed0) > 0) ? tokensOwed0 : params.amount0Max;
    amount1Collect = (params.amount1Max.compareTo(tokensOwed1) > 0) ? tokensOwed1 : params.amount1Max;
    
    // the actual amounts collected are returned
    PairAmounts collected = IConcentratedLiquidityPool.collect (
      pool,
      recipient,
      positionStorage.tickLower,
      positionStorage.tickUpper,
      amount0Collect,
      amount1Collect
    );
    BigInteger amount0 = collected.amount0;
    BigInteger amount1 = collected.amount1;

    // sometimes there will be a few less loops than expected due to rounding down in core, but we just subtract the full amount expected
    // instead of the actual amount so we can burn the token
    positionStorage.tokensOwed0 = tokensOwed0.subtract(amount0Collect);
    positionStorage.tokensOwed1 = tokensOwed1.subtract(amount1Collect);

    this.Collect(params.tokenId, recipient, amount0Collect, amount1Collect);
    
    this.positions.set(params.tokenId, positionStorage);
    return new PairAmounts(amount0, amount1);
  }

  /**
   * @notice Burns a token ID, which deletes it from the NFT contract. 
   * The token must have 0 liquidity and all tokens must be collected first.
   * @param tokenId The ID of the token that is being burned
   */
  @External
  public void burn (BigInteger tokenId) {
    isAuthorizedForToken(tokenId);

    NFTPosition positionStorage = this.positions.get(tokenId);

    Context.require(
       positionStorage.liquidity.equals(ZERO) 
    && positionStorage.tokensOwed0.equals(ZERO)
    && positionStorage.tokensOwed1.equals(ZERO),
      "burn: Not cleared"
    );

    this.positions.set(tokenId, null);
    this._burn(tokenId);
  }

  // ================================================
  // Implements LiquidityManager
  // ================================================
  /**
   * @notice Called to `Context.getCaller()` after minting liquidity to a position from ConcentratedLiquidityPool#mint.
   * @dev In the implementation you must pay the pool tokens owed for the minted liquidity.
   * The caller of this method must be checked to be a ConcentratedLiquidityPool deployed by the canonical BalancedFactory.
   * @param amount0Owed The amount of token0 due to the pool for the minted liquidity
   * @param amount1Owed The amount of token1 due to the pool for the minted liquidity
   * @param data Any data passed through by the caller via the mint call
   */
  @External
  public void balancedMintCallback (
    BigInteger amount0Owed,
    BigInteger amount1Owed,
    byte[] data
  ) {
    // Context.println("[Callback] paying " + amount0Owed + " / " + amount1Owed + " to " + Context.call(Context.getCaller(), "name"));
    this.liquidityMgr.balancedMintCallback(amount0Owed, amount1Owed, data);
  }

  /**
   * @notice Remove funds from the liquidity manager previously deposited by `Context.getCaller`
   * 
   * @param token The token address to withdraw
   */
  @External
  public void withdraw (Address token) {
    this.liquidityMgr.withdraw(token);
  }

  /**
   * @notice Remove all funds from the liquidity manager previously deposited by `Context.getCaller`
   */
  @External
  public void withdraw_all () {
    this.liquidityMgr.withdraw_all();
  }

  /**
   * @notice Add ICX funds to the liquidity manager
   */
  @External
  @Payable
  public void depositIcx () {
    this.liquidityMgr.depositIcx();
  }
  
  @External
  public void tokenFallback (Address _from, BigInteger _value, @Optional byte[] _data) throws Exception {
    Reader reader = new StringReader(new String(_data));
    JsonValue input = Json.parse(reader);
    JsonObject root = input.asObject();
    String method = root.get("method").asString();
    Address token = Context.getCaller();

    switch (method)
    {
      /**
       * @notice Add IRC2 funds to the liquidity manager
       */
      case "deposit": {
        deposit(_from, token, _value);
        break;
      }

      default:
        Context.revert("tokenFallback: Unimplemented tokenFallback action");
    }
  }

  // @External - this method is external through tokenFallback
  public void deposit(Address caller, Address tokenIn, BigInteger amountIn) {
    this.liquidityMgr.deposit(caller, tokenIn, amountIn);
  }

  // ReadOnly methods
  @External(readonly = true)
  public BigInteger deposited(Address user, Address token) {
    return this.liquidityMgr.deposited(user, token);
  }

  @External(readonly = true)
  public int depositedTokensSize(Address user) {
    return this.liquidityMgr.depositedTokensSize(user);
  }

  @External(readonly = true)
  public Address depositedToken(Address user, int index) {
    return this.liquidityMgr.depositedToken(user, index);
  }

  // ================================================
  // Overrides
  // ================================================
  /// @dev Overrides _approve to use the operator in the position, which is packed with the position permit nonce
  @Override
  protected void _approve (Address to, BigInteger tokenId) {
    var position = this.positions.getOrDefault(tokenId, NFTPosition.empty());
    position.operator = to;
    this.positions.set(tokenId, position);
    this.Approval(ownerOf(tokenId), to, tokenId);
  }

  @Override
  @External(readonly=true)
  public Address getApproved(BigInteger tokenId) {
    Context.require(this._exists(tokenId), 
      "getApproved: approved query for nonexistent token");
    return this.positions.get(tokenId).operator;
  }

  // ================================================
  // Checks
  // ================================================
  /**
   * Check if transaction hasn't reached the deadline
   */
  private void checkDeadline(BigInteger deadline) {
    final BigInteger now = TimeUtils.now();
    Context.require(now.compareTo(deadline) <= 0,
      "checkDeadline: Transaction too old");
  }

  private void isAuthorizedForToken (BigInteger tokenId) {
    Context.require(_isApprovedOrOwner(Context.getCaller(), tokenId), 
      "checkAuthorizedForToken: Not approved");
  }

  // ================================================
  // Public variable getters
  // ================================================
  @External(readonly = true)
  public String name() {
    return this.name;
  }

  @External(readonly = true)
  public Address factory() {
    return this.factory;
  }
}
