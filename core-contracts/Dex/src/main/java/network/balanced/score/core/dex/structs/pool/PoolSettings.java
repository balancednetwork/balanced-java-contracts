package network.balanced.score.core.dex.structs.pool;

import java.math.BigInteger;
import java.util.Map;
import score.Address;

public class PoolSettings {
  // Contract name
  public String name;

  // The contract that deployed the pool
  public Address factory;

  // The first of the two tokens of the pool, sorted by address
  public Address token0;

  // The second of the two tokens of the pool, sorted by address
  public Address token1;

  // The pool's fee in hundredths of a bip, i.e. 1e-6
  public int fee;

  // The pool tick spacing
  // @dev Ticks can only be used at multiples of this value, minimum of 1 and always positive
  // e.g.: a tickSpacing of 3 means ticks can be initialized every 3rd tick, i.e., ..., -6, -3, 0, 3, 6, ...
  // This value is an int to avoid casting even though it is always positive.
  public int tickSpacing;

  // The maximum amount of position liquidity that can use any tick in the range
  // @dev This parameter is enforced per tick to prevent liquidity from overflowing an int at any point, and
  // also prevents out-of-range liquidity from being used to prevent adding in-range liquidity to a pool
  // @return The max amount of liquidity per tick
  public BigInteger maxLiquidityPerTick;

  public PoolSettings (
    Address factory,
    Address token0,
    Address token1,
    Integer fee,
    Integer tickSpacing,
    BigInteger maxLiquidityPerTick,
    String name
  ) {
    this.factory = factory;
    this.token0 = token0;
    this.token1 = token1;
    this.fee = fee;
    this.tickSpacing = tickSpacing;
    this.maxLiquidityPerTick = maxLiquidityPerTick;
    this.name = name;
  }

  public PoolSettings () {}

  public static PoolSettings fromMap(Object call) {
    @SuppressWarnings("unchecked")
    Map<String,Object> map = (Map<String,Object>) call;
    return new PoolSettings (
      (Address) map.get("factory"),
      (Address) map.get("token0"),
      (Address) map.get("token1"),
      ((BigInteger) map.get("fee")).intValue(),
      ((BigInteger) map.get("tickSpacing")).intValue(),
      (BigInteger) map.get("maxLiquidityPerTick"),
      (String) map.get("name")
    );
  }
}