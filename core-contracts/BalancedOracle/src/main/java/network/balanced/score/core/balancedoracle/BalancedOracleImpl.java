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

import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.admin;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.assetPeg;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.dex;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.dexPricedAssets;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.governance;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.lastPriceInLoop;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.oracle;
import static network.balanced.score.core.balancedoracle.BalancedOracleConstants.staking;
import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Constants.EXA;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.interfaces.BalancedOracle;
import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;

public class BalancedOracleImpl implements BalancedOracle {
    public static final String TAG = "Balanced Oracle";

    public BalancedOracleImpl(@Optional Address _governance) {
        if (governance.get() != null){
            return;
        }

        governance.set(_governance);
        assetPeg.set("bnUSD", "USD");
        assetPeg.set("iETH", "ETH");
        assetPeg.set("iBTC", "BTC");
    }
    
    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External
    public BigInteger getPriceInLoop(String symbol) {
        symbol = assetPeg.getOrDefault(symbol, symbol);
        BigInteger priceInLoop;
        if (symbol.equals("sICX")) {
            priceInLoop = Context.call(BigInteger.class, staking.get(), "getTodayRate");
        } else if (dexPricedAssets.get(symbol) != null) {
            BigInteger poolID = dexPricedAssets.get(symbol);
            BigInteger bnusdPriceInAsset = Context.call(BigInteger.class, dex.get(), "getQuotePriceInBase", poolID);

            BigInteger loopRate = getLoopRate("USD");
            
            priceInLoop = loopRate.multiply(bnusdPriceInAsset).divide(EXA);
        } else {
            priceInLoop = getLoopRate(symbol);
        }

        lastPriceInLoop.set(symbol, priceInLoop);
        return priceInLoop;
    }

    @External(readonly = true)
    public BigInteger getLastPriceInLoop(String symbol) {
        symbol = assetPeg.getOrDefault(symbol, symbol);
        BigInteger priceInLoop  = lastPriceInLoop.getOrDefault(symbol, BigInteger.ZERO);
        Context.require(priceInLoop.compareTo(BigInteger.ZERO) > 0, TAG + ": No price data exists for symbol");

        return priceInLoop;
    }

    @External
    public void addDexPricedAsset(String symbol, BigInteger dexBnusdPoolId) {
        onlyOwner();
        dexPricedAssets.set(symbol, dexBnusdPoolId);
    }

    @External
    public void removeDexPricedAsset(String symbol) {
        onlyOwner();
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
        onlyOwner();
        assetPeg.set(symbol, peg);
    }

    @External(readonly = true)
    public String getPeg(String symbol) {
        return assetPeg.get(symbol);
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
    public void setStaking(Address _address){
        only(admin);
        isContract(_address);
        staking.set(_address);
    }

    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    private BigInteger getLoopRate(String Symbol) {
        // Ask multiple oracles when it exists
        Map<String, Object> priceData = (Map<String, Object>) Context.call(oracle.get(), "get_reference_data", Symbol, "ICX");
        return (BigInteger) priceData.get("rate");
    }
}

