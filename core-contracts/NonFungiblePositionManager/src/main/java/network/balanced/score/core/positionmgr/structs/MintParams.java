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

public class MintParams {
    // The first token of a pool, unsorted
    public Address token0;
    // The second token of a pool, unsorted
    public Address token1;
    // The fee level of the pool
    public int fee;
    // The lower tick of the position
    public int tickLower;
    // The upper tick of the position
    public int tickUpper;
    // The desired amount of token0 to be spent,
    public BigInteger amount0Desired;
    // The desired amount of token1 to be spent,
    public BigInteger amount1Desired;
    // The minimum amount of token0 to spend, which serves as a slippage check,
    public BigInteger amount0Min;
    // The minimum amount of token1 to spend, which serves as a slippage check,
    public BigInteger amount1Min;
    // The address that received the output of the swap
    public Address recipient;
    // The unix time after which a mint will fail, to protect against long-pending transactions and wild swings in prices
    public BigInteger deadline;

    public MintParams (
        Address token0, 
        Address token1, 
        int fee, 
        int tickLower, 
        int tickUpper,
        BigInteger amount0Desired,
        BigInteger amount1Desired,
        BigInteger amount0Min,
        BigInteger amount1Min,
        Address recipient,
        BigInteger deadline
    ) {
        this.token0 = token0;
        this.token1 = token1;
        this.fee = fee;
        this.tickLower = tickLower;
        this.tickUpper = tickUpper;
        this.amount0Desired = amount0Desired;
        this.amount1Desired = amount1Desired;
        this.amount0Min = amount0Min;
        this.amount1Min = amount1Min;
        this.recipient = recipient;
        this.deadline = deadline;
    }

    public MintParams() {}

    public Map<String, ?> toMap() {
        return Map.ofEntries(
            Map.entry("token0", this.token0),
            Map.entry("token1", this.token1),
            Map.entry("fee", this.fee),
            Map.entry("tickLower", this.tickLower),
            Map.entry("tickUpper", this.tickUpper),
            Map.entry("amount0Desired", this.amount0Desired),
            Map.entry("amount1Desired", this.amount1Desired),
            Map.entry("amount0Min", this.amount0Min),
            Map.entry("amount1Min", this.amount1Min),
            Map.entry("recipient", this.recipient),
            Map.entry("deadline", this.deadline)
        );
    }
}