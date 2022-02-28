package network.balanced.score.tokens;

import score.annotation.External;

import java.math.BigInteger;
import java.util.HashMap;

public class DummyOracle {
    public DummyOracle() {

    }


    @External
    public HashMap<String, BigInteger> get_reference_data(String _base, String _quote) {
        HashMap <String, BigInteger> result = new HashMap<>();
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
