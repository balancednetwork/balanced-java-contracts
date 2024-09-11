/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.core.balancedoracle;

import network.balanced.score.core.balancedoracle.structs.PriceData;
import network.balanced.score.lib.interfaces.BalancedOracle;
import network.balanced.score.lib.interfaces.BalancedOracleXCall;
import network.balanced.score.lib.structs.PriceProtectionConfig;
import network.balanced.score.lib.structs.PriceProtectionParameter;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.XCallUtils;
import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.POINTS;

public class BalancedOracleImpl implements BalancedOracle {
    public static final String TAG = Names.BALANCEDORACLE;

    public BalancedOracleImpl(@Optional Address _governance) {
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            setGovernance(_governance);
            assetPeg.set("bnUSD", "USD");
            lastUpdateThreshold.set(MICRO_SECONDS_IN_A_DAY);
        }
        BandOracle.availablePrices.set("ICX", true);
        BandOracle.availablePrices.set("USDC", true);
        BandOracle.availablePrices.set("BTC", true);
        BandOracle.availablePrices.set("ETH", true);
        BandOracle.availablePrices.set("INJ", true);
        BandOracle.availablePrices.set("BNB", true);
        BandOracle.availablePrices.set("AVAX", true);
        BandOracle.availablePrices.set("SUI", true);
        BandOracle.availablePrices.set("NTRN", true);

        if (currentVersion.getOrDefault("").equals(Versions.BALANCEDORACLE)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.BALANCEDORACLE);
    }

    @External(readonly = true)
    public String name() {
        return Names.BALANCEDORACLE;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External
    public BigInteger getPriceInLoop(String symbol) {
        return getLastPriceInLoop(symbol);
    }

    @External(readonly = true)
    public BigInteger getLastPriceInLoop(String symbol) {
        if (symbol.equals("sICX")) {
            return Context.call(BigInteger.class, getStaking(), "getTodayRate");
        }

        return USDToLoop(getPrice(symbol).rate);
    }

    @External
    public BigInteger getPriceInUSD(String symbol) {
        return getPrice(symbol).rate;
    }

    @External(readonly = true)
    public BigInteger getLastPriceInUSD(String symbol) {
        return getPrice(symbol).rate;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getPriceDataInUSD(String symbol) {
        return getPriceData(symbol).toMap();
    }

    @External
    public void setPeg(String symbol, String peg) {
        onlyOwner();
        assetPeg.set(symbol, peg);
    }

    @External(readonly = true)
    public String getPeg(String symbol) {
        return assetPeg.get(symbol);
    }


    @External(readonly = true)
    public BigInteger getLastUpdateThreshold() {
        return lastUpdateThreshold.get();
    }

    @External
    public void setLastUpdateThreshold(BigInteger threshold) {
        onlyOwner();
        lastUpdateThreshold.set(threshold);
    }

    @External(readonly = true)
    public BigInteger getPriceDiffThreshold() {
        return priceDiffThreshold.get();
    }

    @External
    public void setPriceDiffThreshold(BigInteger threshold) {
        onlyOwner();
        priceDiffThreshold.set(threshold);
    }

    // XCall
    public void updatePriceData(String from, String symbol, BigInteger rate, BigInteger timestamp) {
        ExternalOracle.updatePriceData(from, symbol, rate, timestamp);
    }

    @External
    public void addExternalPriceProxy(String symbol, String address, @Optional PriceProtectionParameter priceProtectionConfig) {
        onlyOwner();
        ExternalOracle.addExternalPriceProxy(symbol, address, priceProtectionConfig);
    }

    @External
    public void removeExternalPriceProxy(String symbol, String address) {
        onlyOwner();
        ExternalOracle.removeExternalPriceProxy(symbol, address);
    }

    @External(readonly = true)
    public String getExternalPriceProvider(String symbol) {
        return priceProvider.get(symbol);
    }

    @External(readonly = true)
    public PriceProtectionConfig getExternalPriceProtectionConfig(String symbol) {
        return externalPriceProtectionConfig.get(symbol);
    }

    @External
    public void configurePythPriceId(String base, byte[] id) {
        onlyOwner();
        PythOracle.configurePythPriceId(base, id);
    }

    @External
    public void configureBandPrice(String base) {
        BandOracle.addPrice(base);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        BalancedOracleXCall.process(this, _from, _data);
    }

    public static PriceData getPrice(String symbol) {
        PriceData data = getPriceData(symbol);
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger threshold = lastUpdateThreshold.getOrDefault(BigInteger.ZERO);
        Context.require(blockTime.subtract(data.timestamp).compareTo(threshold) < 0, "The last price update for " + symbol + " is outdated");

        return data;
    }

    public static PriceData getPriceData(String symbol) {
        String pegSymbol = assetPeg.getOrDefault(symbol, symbol);
        PriceData data = null;
        if (pegSymbol.equals("sICX")) {
                BigInteger ICXPrice = Context.call(BigInteger.class, getStaking(), "getTodayRate");
                data = getPrice("ICX");
                data.rate = ICXPrice.multiply(data.rate).divide(EXA);
        } else if (pegSymbol.equals("ICX")) {
            data = BandOracle.getRate(pegSymbol);
        } else if (pegSymbol.equals("USD")) {
            data = new PriceData(EXA, BigInteger.valueOf(Context.getBlockTimestamp()));
        } else if (PythOracle.has(pegSymbol)) {
            data = PythOracle.getRate(pegSymbol);
            if (BandOracle.has(pegSymbol)) {
                PriceData refData = BandOracle.getRate(pegSymbol);
                BigInteger diff = data.rate.subtract(refData.rate).abs();
                BigInteger avg =  data.rate.add(refData.rate).divide(BigInteger.TWO);
                BigInteger pointsDiff = diff.multiply(POINTS).divide(avg);

                BigInteger diffThreshold = priceDiffThreshold.get();
                Context.require(diffThreshold == null  || pointsDiff.compareTo(diffThreshold) < 0, " Difference between Pyth and Band is bigger than threshold");
            }
        } else if (BandOracle.has(pegSymbol)) {
            data = BandOracle.getRate(pegSymbol);
        } else if (priceProvider.get(pegSymbol) != null) {
            data = ExternalOracle.getRate(pegSymbol);
        }

        return data;
    }


    private BigInteger USDToLoop(BigInteger price) {
        BigInteger ICXPriceInUSD = getPrice("ICX").rate;
        return price.multiply(ICXPriceInUSD).divide(EXA);
    }


}