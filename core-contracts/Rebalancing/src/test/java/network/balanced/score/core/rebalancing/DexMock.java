/*
 * Copyright (c) 2022-2022 Balanced.network.
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

package network.balanced.score.core.rebalancing;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

public class DexMock {

    @External(readonly = true)
    public BigInteger getPoolId(Address _token1Address, Address _token2Address) {
        return BigInteger.ONE;
    }

    @External(readonly = true)
    public Map<String, ?> getPoolStats(BigInteger _id) {
        return Map.of("base_token", "cx0000000000000000000000000000000000000032",
                "quote_token", "None",
                "base", 0,
                "quote", BigInteger.valueOf(1000L),
                "total_supply", BigInteger.valueOf(10000000000000L),
                "price", BigInteger.TEN,
                "name", "sICX/ICX",
                "base_decimals", 18,
                "quote_decimals", 18,
                "min_quote", BigInteger.TEN);
    }
}
