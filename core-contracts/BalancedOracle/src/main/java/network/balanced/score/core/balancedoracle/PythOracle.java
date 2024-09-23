package network.balanced.score.core.balancedoracle;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.core.balancedoracle.structs.PriceData;
import network.balanced.score.lib.utils.Math;
import network.balanced.score.lib.utils.Names;
import score.Context;
import score.DictDB;

import static network.balanced.score.lib.utils.BalancedAddressManager.getPythOracle;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;

public class PythOracle {
    public static final DictDB<String, byte[]> priceMapping = Context.newDictDB("pyth-price-mapping", byte[].class);

    public static String getName(){
        return Names.PYTH_ORACLE;
    }

    public static void configurePythPriceId(String base, byte[] id) {
        priceMapping.set(base, id);
    }

    public static boolean has(String base) {
        return priceMapping.get(base) != null;
    }

    @SuppressWarnings("unchecked")
    public static PriceData getRate(String base) {
        Map<String, BigInteger> priceData = (Map<String, BigInteger>) Context.call(getPythOracle(), "getPrice", priceMapping.get(base));
        int decimalTranslation = 18+priceData.get("expo").intValue();
        BigInteger rate = priceData.get("price");
        if (decimalTranslation != 0) {
            rate = priceData.get("price").multiply(Math.pow(BigInteger.TEN, decimalTranslation));
        }

        return new PriceData(rate, priceData.get("publishTime").multiply(MICRO_SECONDS_IN_A_SECOND));
    }
}
