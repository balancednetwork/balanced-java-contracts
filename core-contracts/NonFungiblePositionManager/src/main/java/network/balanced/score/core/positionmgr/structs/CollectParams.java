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

public class CollectParams {
    // The ID of the NFT for which tokens are being collected
    public BigInteger tokenId;
    // The account that should receive the tokens
    public Address recipient;
    // The maximum amount of token0 to collect
    public BigInteger amount0Max;
    // The maximum amount of token1 to collect
    public BigInteger amount1Max;

    public CollectParams() {}

    public CollectParams (
        BigInteger tokenId,
        Address recipient,
        BigInteger amount0Max,
        BigInteger amount1Max
    ) {
        this.tokenId = tokenId;
        this.recipient = recipient;
        this.amount0Max = amount0Max;
        this.amount1Max = amount1Max;
    }
}