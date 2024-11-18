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

package network.balanced.score.core.positionmgr.structs;

import java.math.BigInteger;
import java.util.Map;

import score.Address;

public class PositionInformation {
    // The nonce for permits
    public BigInteger nonce;
    // The address that is approved for spending
    public Address operator;
    // The address of the token0 for a specific pool
    public Address token0;
    // The address of the token1 for a specific pool
    public Address token1;
    // The fee associated with the pool
    public int fee;
    // The lower end of the tick range for the position
    public int tickLower;
    // The higher end of the tick range for the position
    public int tickUpper;
    // The liquidity of the position
    public BigInteger liquidity;
    // The fee growth of token0 as of the last action on the individual position
    public BigInteger feeGrowthInside0LastX128;
    // The fee growth of token1 as of the last action on the individual position
    public BigInteger feeGrowthInside1LastX128;
    // The uncollected amount of token0 owed to the position as of the last computation
    public BigInteger tokensOwed0;
    // The uncollected amount of token1 owed to the position as of the last computation
    public BigInteger tokensOwed1;

    public PositionInformation (
      BigInteger nonce,
      Address operator,
      Address token0,
      Address token1,
      int fee,
      int tickLower,
      int tickUpper,
      BigInteger liquidity,
      BigInteger feeGrowthInside0LastX128,
      BigInteger feeGrowthInside1LastX128,
      BigInteger tokensOwed0,
      BigInteger tokensOwed1
    ) {
      this.nonce = nonce;
      this.operator = operator;
      this.token0 = token0;
      this.token1 = token1;
      this.fee = fee;
      this.tickLower = tickLower;
      this.tickUpper = tickUpper;
      this.liquidity = liquidity;
      this.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
      this.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
      this.tokensOwed0 = tokensOwed0;
      this.tokensOwed1 = tokensOwed1;
    }

    public static PositionInformation fromMap (Object call) {
      @SuppressWarnings("unchecked")
      Map<String,Object> map = (Map<String,Object>) call;
      return new PositionInformation(
        (BigInteger) map.get("nonce"),
        (Address) map.get("operator"),
        (Address) map.get("token0"),
        (Address) map.get("token1"),
        ((BigInteger) map.get("fee")).intValue(),
        ((BigInteger) map.get("tickLower")).intValue(),
        ((BigInteger) map.get("tickUpper")).intValue(),
        (BigInteger) map.get("liquidity"),
        (BigInteger) map.get("feeGrowthInside0LastX128"),
        (BigInteger) map.get("feeGrowthInside1LastX128"),
        (BigInteger) map.get("tokensOwed0"),
        (BigInteger) map.get("tokensOwed1")
      );
    }
}