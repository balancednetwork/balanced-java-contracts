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

package network.balanced.score.core.dex.librairies;

import static network.balanced.score.core.dex.utils.IntUtils.uint128;

import java.math.BigInteger;
import network.balanced.score.core.dex.structs.pool.Position;
import score.Context;

public class PositionLib {
  /**
   * @notice Credits accumulated fees to a user's position
   * @param posInfo The individual position to update
   * @param liquidityDelta The change in pool liquidity as a result of the position update
   * @param feeGrowthInside0X128 The all-time fee growth in token0, per unit of liquidity, inside the position's tick boundaries
   * @param feeGrowthInside1X128 The all-time fee growth in token1, per unit of liquidity, inside the position's tick boundaries
   */
  public static void update (
    Position.Info posInfo,
    BigInteger liquidityDelta, 
    BigInteger feeGrowthInside0X128,
    BigInteger feeGrowthInside1X128
  ) {
    BigInteger liquidityNext;
    
    if (liquidityDelta.equals(BigInteger.ZERO)) {
      Context.require(posInfo.liquidity.compareTo(BigInteger.ZERO) > 0, 
        "update: pokes aren't allowed for 0 liquidity positions"); // disallow pokes for 0 liquidity positions
      liquidityNext = posInfo.liquidity;
    } else {
      liquidityNext = LiquidityMath.addDelta(posInfo.liquidity, liquidityDelta);
    }

    // calculate accumulated fees
    BigInteger tokensOwed0 = uint128(FullMath.mulDiv(feeGrowthInside0X128.subtract(posInfo.feeGrowthInside0LastX128), posInfo.liquidity, FixedPoint128.Q128));
    BigInteger tokensOwed1 = uint128(FullMath.mulDiv(feeGrowthInside1X128.subtract(posInfo.feeGrowthInside1LastX128), posInfo.liquidity, FixedPoint128.Q128));

    // update the position
    if (!liquidityDelta.equals(BigInteger.ZERO)) {
      posInfo.liquidity = liquidityNext;
    }

    posInfo.feeGrowthInside0LastX128 = feeGrowthInside0X128;
    posInfo.feeGrowthInside1LastX128 = feeGrowthInside1X128;

    if (tokensOwed0.compareTo(BigInteger.ZERO) > 0 || tokensOwed1.compareTo(BigInteger.ZERO) > 0) {
      // overflow is acceptable, have to withdraw before you hit type(uint128).max fees
      posInfo.tokensOwed0 = uint128(posInfo.tokensOwed0.add(tokensOwed0));
      posInfo.tokensOwed1 = uint128(posInfo.tokensOwed1.add(tokensOwed1));
    }
  }
}
