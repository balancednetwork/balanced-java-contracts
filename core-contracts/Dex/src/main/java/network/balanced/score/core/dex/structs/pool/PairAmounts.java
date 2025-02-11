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

package network.balanced.score.core.dex.structs.pool;

import java.math.BigInteger;
import java.util.Map;

public class PairAmounts {
    // Amount of token0
    public BigInteger amount0;
    // Amount of token1
    public BigInteger amount1;

    public PairAmounts (BigInteger amount0, BigInteger amount1) {
        this.amount0 = amount0;
        this.amount1 = amount1;
    }

    public static PairAmounts fromMap (Object call) {
        @SuppressWarnings("unchecked")
        Map<String,Object> map = (Map<String,Object>) call;
        return new PairAmounts (
          (BigInteger) map.get("amount0"),
          (BigInteger) map.get("amount1")
        );
    }
}