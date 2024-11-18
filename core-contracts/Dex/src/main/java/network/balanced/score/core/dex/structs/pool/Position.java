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

package network.balanced.score.core.dex.structs.pool;

import static java.math.BigInteger.ZERO;
import java.math.BigInteger;
import java.util.Map;

import score.ObjectReader;
import score.ObjectWriter;

public class Position {
  public static class Info {
    // the amount of liquidity owned by this position
    public BigInteger liquidity;

    // fee growth per unit of liquidity as of the last update to liquidity or fees owed
    public BigInteger feeGrowthInside0LastX128;
    public BigInteger feeGrowthInside1LastX128;

    // the fees owed to the position owner in token0/token1
    public BigInteger tokensOwed0;
    public BigInteger tokensOwed1;

    public Info (
      BigInteger liquidity,
      BigInteger feeGrowthInside0LastX128, 
      BigInteger feeGrowthInside1LastX128,
      BigInteger tokensOwed0,
      BigInteger tokensOwed1
    ) {
      this.liquidity = liquidity;
      this.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
      this.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
      this.tokensOwed0 = tokensOwed0;
      this.tokensOwed1 = tokensOwed1;
    }

    public static void writeObject(ObjectWriter w, Info obj) {
      w.write(obj.liquidity);
      w.write(obj.feeGrowthInside0LastX128);
      w.write(obj.feeGrowthInside1LastX128);
      w.write(obj.tokensOwed0);
      w.write(obj.tokensOwed1);
    }

    public static Info readObject(ObjectReader r) {
      return new Info(
        r.readBigInteger(), // liquidity
        r.readBigInteger(), // feeGrowthInside0LastX128
        r.readBigInteger(), // feeGrowthInside1LastX128
        r.readBigInteger(), // tokensOwed0
        r.readBigInteger()  // tokensOwed1
      );
    }

    public static Info fromMap(Object call) {
      @SuppressWarnings("unchecked")
      Map<String,Object> map = (Map<String,Object>) call;
      return new Info(
        (BigInteger) map.get("liquidity"), 
        (BigInteger) map.get("feeGrowthInside0LastX128"), 
        (BigInteger) map.get("feeGrowthInside1LastX128"), 
        (BigInteger) map.get("tokensOwed0"), 
        (BigInteger) map.get("tokensOwed1")
      );
    }
    
    public static Position.Info empty () {
      return new Position.Info (ZERO, ZERO, ZERO, ZERO, ZERO);
    }
  }
}
