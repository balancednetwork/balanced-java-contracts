package network.balanced.score.core.balancedoracle;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.core.balancedoracle.structs.PriceData;
import network.balanced.score.lib.utils.Names;
import score.Context;
import score.DictDB;

import static network.balanced.score.lib.utils.BalancedAddressManager.getOracle;

public class BandOracle {
    public static final DictDB<String, Boolean> availablePrices = Context.newDictDB("available_prices_band", Boolean.class);

    public static String getName(){
        return Names.ORACLE;
    }

    public static void addPrice(String base) {
        Context.call(getOracle(), "get_reference_data", base, "USD");
        availablePrices.set(base, true);
    }

    public static boolean has(String base) {
        return availablePrices.get(base) != null;
    }


    @SuppressWarnings("unchecked")
    public static PriceData getRate(String base) {
        Map<String, BigInteger> priceData = (Map<String, BigInteger>) Context.call(getOracle(), "get_reference_data"
                , base, "USD");
        BigInteger last_update_base = priceData.get("last_update_base");
        BigInteger last_update_quote = priceData.get("last_update_quote");
        return new PriceData(priceData.get("rate"), last_update_quote.min(last_update_base));
    }
}
