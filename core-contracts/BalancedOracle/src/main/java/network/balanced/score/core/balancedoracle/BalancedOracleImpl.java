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

package network.balanced.score.core.balancedoracle;

import network.balanced.score.lib.interfaces.BalancedOracle;
import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;

public class BalancedOracleImpl implements BalancedOracle {
    public static final String TAG = "Balanced Oracle";

    public BalancedOracleImpl(@Optional Address _governance) {
        if (governance.get() != null) {
            return;
        }

        governance.set(_governance);
        assetPeg.set("bnUSD", "USD");
        dexPricedAssets.set("BALN", BigInteger.valueOf(3));

        dexPriceEMADecay.set(EXA);
        oraclePriceEMADecay.set(EXA);
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External
    public BigInteger getPriceInLoop(String symbol) {
        String pegSymbol = assetPeg.getOrDefault(symbol, symbol);
        BigInteger priceInLoop;
        if (pegSymbol.equals("sICX")) {
            priceInLoop = getSICXPriceInLoop();
            lastPriceInLoop.set(pegSymbol, priceInLoop);
        } else if (dexPricedAssets.get(pegSymbol) != null) {
            priceInLoop = getDexPriceInLoop(pegSymbol);
        } else {
            priceInLoop = getLoopRate(pegSymbol);
            lastPriceInLoop.set(pegSymbol, priceInLoop);
        }

        return priceInLoop;
    }

    @External(readonly = true)
    public BigInteger getLastPriceInLoop(String symbol) {
        String pegSymbol = assetPeg.getOrDefault(symbol, symbol);
        BigInteger priceInLoop; 

        if (dexPricedAssets.get(pegSymbol) != null) {
            priceInLoop = EMACalculator.calculateEMA(pegSymbol, getDexPriceEMADecay());
        } else {
            priceInLoop = lastPriceInLoop.getOrDefault(pegSymbol, BigInteger.ZERO);
        }
        
        Context.require(priceInLoop.compareTo(BigInteger.ZERO) > 0, TAG + ": No price data exists for symbol");

        return priceInLoop;
    }

    @External
    public void addDexPricedAsset(String symbol, BigInteger dexBnusdPoolId) {
        only(admin);
        dexPricedAssets.set(symbol, dexBnusdPoolId);
    }

    @External
    public void removeDexPricedAsset(String symbol) {
        only(admin);
        dexPricedAssets.set(symbol, null);
    }

    @External(readonly = true)
    public BigInteger getAssetBnusdPoolId(String symbol) {
        BigInteger poolID = dexPricedAssets.get(symbol);
        Context.require(poolID != null, symbol + " is not listed as a dex priced asset");
        return poolID;
    }

    @External
    public void setPeg(String symbol, String peg) {
        only(admin);
        assetPeg.set(symbol, peg);
    }

    @External(readonly = true)
    public String getPeg(String symbol) {
        return assetPeg.get(symbol);
    }

    @External
    public void setDexPriceEMADecay(BigInteger decay) {
        only(admin);
        Context.require(decay.compareTo(EXA) <= 0, TAG + ": decay has to be less than " + EXA);
        Context.require(decay.compareTo(BigInteger.ZERO) > 0, TAG + ": decay has to be larger than zero");
        dexPriceEMADecay.set(decay);
    }

    @External(readonly = true)
    public BigInteger getDexPriceEMADecay() {
        return dexPriceEMADecay.get();
    }

    @External
    public void setOraclePriceEMADecay(BigInteger decay) {
        only(admin);
        Context.require(decay.compareTo(EXA) <= 0, TAG + ": decay has to be less than " + EXA);
        Context.require(decay.compareTo(BigInteger.ZERO) > 0, TAG + ": decay has to be larger than zero");
        oraclePriceEMADecay.set(decay);
    }

    @External(readonly = true)
    public BigInteger getOraclePriceEMADecay() {
        return oraclePriceEMADecay.get();
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setOracle(Address _address) {
        only(admin);
        isContract(_address);
        oracle.set(_address);
    }

    @External(readonly = true)
    public Address getOracle() {
        return oracle.get();
    }

    @External
    public void setDex(Address _address) {
        only(admin);
        isContract(_address);
        dex.set(_address);
    }

    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    @External
    public void setStaking(Address _address) {
        only(admin);
        isContract(_address);
        staking.set(_address);
    }

    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    private BigInteger getSICXPriceInLoop() {
        return Context.call(BigInteger.class, staking.get(), "getTodayRate");
    }

    private BigInteger getDexPriceInLoop(String symbol) {
        BigInteger poolID = dexPricedAssets.get(symbol);
        BigInteger bnusdPriceInAsset = Context.call(BigInteger.class, dex.get(), "getQuotePriceInBase", poolID);

        BigInteger loopRate = getLoopRate("USD");
        BigInteger priceInLoop = loopRate.multiply(bnusdPriceInAsset).divide(EXA);
        return EMACalculator.updateEMA(symbol, priceInLoop, getDexPriceEMADecay());
    }

    private BigInteger getLoopRate(String symbol) {
        Map<String, Object> priceData = (Map<String, Object>) Context.call(oracle.get(), "get_reference_data", symbol
                , "ICX");

        return (BigInteger) priceData.get("rate");
        // return EMACalculator.updateEMA(symbol, priceInLoop, getOraclePriceEMADecay());
    }
}