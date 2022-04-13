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

package network.balanced.score.tokens;

import score.annotation.External;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

public class DummyOracle {

    public DummyOracle() {
    }

    @External
    public Map<String, BigInteger> get_reference_data(String _base, String _quote) {
        Map<String, BigInteger> result = new HashMap<>();
        if (_base.equals("USD") && _quote.equals("ICX")) {
            result.put("rate", BigInteger.valueOf(597955725813433531L));
            result.put("last_update_base", BigInteger.valueOf(1602202275702605L));
            result.put("last_update_quote", BigInteger.valueOf(1602202190000000L));
        }
        if (_base.equals("DOGE") && _quote.equals("USD")) {
            result.put("rate", BigInteger.valueOf(50784000000000000L));
            result.put("last_update_base", BigInteger.valueOf(1616643098000000L));
            result.put("last_update_quote", BigInteger.valueOf(1616643311790241L));
        }
        if (_base.equals("XLM") && _quote.equals("USD")) {
            result.put("rate", BigInteger.valueOf(360358450000000000L));
            result.put("last_update_base", BigInteger.valueOf(1616650080000000L));
            result.put("last_update_quote", BigInteger.valueOf(1616650390762201L));
        }

        return result;
    }
}
