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

import score.Address;

public class AddLiquidityParams {
    public Address token0;
    public Address token1;
    public int fee;
    public Address recipient;
    public int tickLower;
    public int tickUpper;
    public BigInteger amount0Desired;
    public BigInteger amount1Desired;
    public BigInteger amount0Min;
    public BigInteger amount1Min;

    public AddLiquidityParams() {}

    public AddLiquidityParams(
        Address token0,
        Address token1,
        int fee,
        Address recipient, 
        int tickLower,
        int tickUpper,
        BigInteger amount0Desired, 
        BigInteger amount1Desired, 
        BigInteger amount0Min,
        BigInteger amount1Min
    ) {
        this.token0 = token0;
        this.token1 = token1;
        this.fee = fee;
        this.recipient = recipient;
        this.tickLower = tickLower;
        this.tickUpper = tickUpper;
        this.amount0Desired = amount0Desired;
        this.amount1Desired = amount1Desired;
        this.amount0Min = amount0Min;
        this.amount1Min = amount1Min;
    }
}