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

package network.balanced.score.util.dummyoracle;

import score.Context;
import score.DictDB;
import score.annotation.External;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;

public class DummyOracle {
    public static final DictDB<String, BigInteger> icxRates = Context.newDictDB("rates", BigInteger.class);

    public DummyOracle() {
        icxRates.set("USD", BigInteger.valueOf(597955725813433531L));
        icxRates.set("BTC", new BigInteger("32c6eaee89097750da0", 16));
        icxRates.set("ETH", new BigInteger("2f723e28a3d2f1eddb84", 16));
    }

    @External(readonly = true)
    public Map<String, BigInteger> get_reference_data(String _base, String _quote) {
        Map<String, BigInteger> result = new HashMap<>();
        if (_base.equals("USD") && _quote.equals("ICX")) {
            result.put("rate", icxRates.get("USD"));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }
        if (_base.equals("ICX") && _quote.equals("USD")) {
            result.put("rate", BigInteger.valueOf(1672364619704314298L));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }
        if (_base.equals("DOGE") && _quote.equals("USD")) {
            result.put("rate", BigInteger.valueOf(50784000000000000L));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        if (_base.equals("XLM") && _quote.equals("USD")) {
            result.put("rate", BigInteger.valueOf(360358450000000000L));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        if (_base.equals("BTC") && _quote.equals("ICX")) {
            result.put("rate", icxRates.get("BTC"));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        if (_base.equals("ETH") && _quote.equals("ICX")) {
            result.put("rate", icxRates.get("ETH"));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        if (_base.equals("BTC") && _quote.equals("USD")) {
            result.put("rate", icxRates.get("BTC").multiply(icxRates.get("USD")).divide(EXA));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        if (_base.equals("ETH") && _quote.equals("USD")) {
            result.put("rate", icxRates.get("ETH").multiply(icxRates.get("USD")).divide(EXA));
            result.put("last_update_base", BigInteger.valueOf(Context.getBlockTimestamp()));
            result.put("last_update_quote", BigInteger.valueOf(Context.getBlockTimestamp()));
        }

        return result;
    }

    @External
    public void setICXRate(String symbol, BigInteger rate) {
        icxRates.set(symbol, rate);
    }
}
