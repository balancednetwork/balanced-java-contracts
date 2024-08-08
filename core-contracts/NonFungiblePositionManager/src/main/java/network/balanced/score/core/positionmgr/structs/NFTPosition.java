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

import static network.balanced.score.core.dex.utils.AddressUtils.ZERO_ADDRESS;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

// details about the Balanced position
public class NFTPosition {
    // the nonce for permits
    public BigInteger nonce;
    // the address that is approved for spending this token
    public Address operator;
    // the ID of the pool with which this token is connected
    public BigInteger poolId;
    // the tick range of the position
    public int tickLower;
    public int tickUpper;
    // the liquidity of the position
    public BigInteger liquidity;
    // the fee growth of the aggregate position as of the last action on the individual position
    public BigInteger feeGrowthInside0LastX128;
    public BigInteger feeGrowthInside1LastX128;
    // how many uncollected tokens are owed to the position, as of the last computation
    public BigInteger tokensOwed0;
    public BigInteger tokensOwed1;

    public NFTPosition (
        BigInteger nonce,
        Address operator,
        BigInteger poolId,
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
        this.poolId = poolId;
        this.tickLower = tickLower;
        this.tickUpper = tickUpper;
        this.liquidity = liquidity;
        this.feeGrowthInside0LastX128 = feeGrowthInside0LastX128;
        this.feeGrowthInside1LastX128 = feeGrowthInside1LastX128;
        this.tokensOwed0 = tokensOwed0;
        this.tokensOwed1 = tokensOwed1;
    }
    
    public static NFTPosition readObject(ObjectReader reader) {
        return new NFTPosition(
            reader.readBigInteger(),
            reader.readAddress(),
            reader.readBigInteger(),
            reader.readInt(),
            reader.readInt(),
            reader.readBigInteger(),
            reader.readBigInteger(),
            reader.readBigInteger(),
            reader.readBigInteger(),
            reader.readBigInteger()
        );
      }
  
      public static void writeObject(ObjectWriter w, NFTPosition obj) {
        w.write(obj.nonce);
        w.write(obj.operator);
        w.write(obj.poolId);
        w.write(obj.tickLower);
        w.write(obj.tickUpper);
        w.write(obj.liquidity);
        w.write(obj.feeGrowthInside0LastX128);
        w.write(obj.feeGrowthInside1LastX128);
        w.write(obj.tokensOwed0);
        w.write(obj.tokensOwed1);
      }

    public static NFTPosition empty() {
        return new NFTPosition (
            ZERO,
            ZERO_ADDRESS,
            ZERO,
            0, 0,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO
        );
    }
}