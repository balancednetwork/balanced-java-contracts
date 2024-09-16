package network.balanced.score.core.balancedoracle;

import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.externalPriceData;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.externalPriceProtectionConfig;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.priceProvider;
import static network.balanced.score.lib.utils.Constants.WEEK_IN_MICRO_SECONDS;

import java.math.BigInteger;

import network.balanced.score.core.balancedoracle.structs.PriceData;
import network.balanced.score.lib.structs.PriceProtectionConfig;
import network.balanced.score.lib.structs.PriceProtectionParameter;
import score.Context;
import score.annotation.Optional;

public class ExternalOracle {

    public static String getName(){
        return "External Oracle";
    }

    public static void updatePriceData(String from, String symbol, BigInteger rate, BigInteger timestamp) {

        Context.require(from.equals(priceProvider.get(symbol)), from + " is not allowed to update the price of " + symbol);
        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
        Context.require(timestamp.compareTo(currentTime) < 0, "Time cannot be in the future");
        PriceData currentPriceData = externalPriceData.get(symbol);
        if (currentPriceData == null) {
            Context.require(timestamp.compareTo(currentTime.subtract(WEEK_IN_MICRO_SECONDS)) >= 0, "First timestamp can't be older than a week old");
            externalPriceData.set(symbol, new PriceData(rate, timestamp));
            return;
        }

        Context.require(currentPriceData.timestamp.compareTo(timestamp) < 0, "Price must be more recent than the current one");
        PriceProtectionConfig priceProtectionConfig = externalPriceProtectionConfig.get(symbol);
        if (priceProtectionConfig != null) {
            priceProtectionConfig.verifyPriceUpdate(currentPriceData.timestamp, currentPriceData.rate, timestamp, rate);
        }

        externalPriceData.set(symbol, new PriceData(rate, timestamp));
    }

    public static void addExternalPriceProxy(String symbol, String address, @Optional PriceProtectionParameter priceProtectionConfig) {
        priceProvider.set(symbol, address);
        if (priceProtectionConfig != null) {
            externalPriceProtectionConfig.set(symbol, new PriceProtectionConfig(priceProtectionConfig));
        }
    }


    public static void removeExternalPriceProxy(String symbol, String address) {
        priceProvider.set(symbol, null);
        externalPriceProtectionConfig.set(symbol, null);
    }

    public static PriceData getRate(String base) {
        return externalPriceData.get(base);
    }
}
