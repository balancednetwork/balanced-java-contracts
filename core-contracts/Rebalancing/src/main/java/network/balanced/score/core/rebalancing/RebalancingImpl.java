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

import static network.balanced.score.core.rebalancing.Constants.ADMIN;
import static network.balanced.score.core.rebalancing.Constants.BNUSD_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.DEX_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.GOVERNANCE_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.LOANS_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.ORACLE_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.PRICE_THRESHOLD;
import static network.balanced.score.core.rebalancing.Constants.SICX_ADDRESS;
import static network.balanced.score.core.rebalancing.Constants.TAG;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.optionalDefault;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.pow;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import network.balanced.score.lib.interfaces.Rebalancing;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;

public class RebalancingImpl implements Rebalancing {

    private final VarDB<Address> bnusd = Context.newVarDB(BNUSD_ADDRESS, Address.class);
    private final VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    private final VarDB<Address> dex = Context.newVarDB(DEX_ADDRESS, Address.class);
    private final VarDB<Address> loans = Context.newVarDB(LOANS_ADDRESS, Address.class);
    private final VarDB<Address> oracle = Context.newVarDB(ORACLE_ADDRESS, Address.class);
    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<BigInteger> priceThreshold = Context.newVarDB(PRICE_THRESHOLD, BigInteger.class);

    public RebalancingImpl(Address _governance) {
        if (governance.getOrDefault(null) == null) {
            Context.require(_governance.isContract(), TAG + ": Governance address should be a contract");
            governance.set(_governance);
            // priceThreshold.set(BigInteger.valueOf(100000000000000000L));
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced " + TAG;
    }

    @External
    public void setBnusd(Address _address) {
        only(admin);
        isContract(_address);
        bnusd.set(_address);
    }

    @External
    public void setLoans(Address _address) {
        only(admin);
        isContract(_address);
        loans.set(_address);
    }

    @External
    public void setSicx(Address _address) {
        only(admin);
        isContract(_address);
        sicx.set(_address);
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External
    public void setDex(Address _address) {
        only(admin);
        isContract(_address);
        dex.set(_address);
    }

    @External
    public void setOracle(Address _address) {
        only(admin);
        isContract(_address);
        oracle.set(_address);
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External(readonly = true)
    public Address getLoans() {
        return loans.get();
    }

    @External(readonly = true)
    public Address getBnusd() {
        return bnusd.get();
    }

    @External(readonly = true)
    public Address getSicx() {
        return sicx.get();
    }

    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    @External(readonly = true)
    public Address getOracle() {
        return oracle.get();
    }

    @External
    public void setPriceDiffThreshold(BigInteger _value) {
        only(governance);
        priceThreshold.set(_value);
    }

    @External(readonly = true)
    public BigInteger getPriceChangeThreshold() {
        return priceThreshold.get();
    }

    /**
     * Checks the Rebalancing status of the pool i.e. whether the difference between oracle price and dex pool price
     * are more than threshold or not. If it is more than the threshold then the function returns a list. If the
     * first element of the list is True then it's forward rebalancing and if the last element of the list is True,
     * it's the reverse rebalancing. The second element of the list specifies the amount of tokens required to
     * balance the pool.
     *
     * @return {List<Object> }   [<Positive difference>, <Tokens to sell>, <Negative difference>]
     */

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public List<Object> getRebalancingStatusFor(Address collateralAddress) {

        List<Object> results = new ArrayList<>(3);

        Address bnusdScore = bnusd.get();
        Address dexScore = dex.get();
        Address sicxScore = sicx.get();
        Address oracleScore = oracle.get();
        BigInteger threshold = priceThreshold.get();

        Context.require(bnusdScore != null && dexScore != null && sicxScore != null && threshold != null);
        String symbol = Context.call(String.class, collateralAddress, "symbol");
        BigInteger nrDecimals = Context.call(BigInteger.class, collateralAddress, "decimals");
        BigInteger decimals = pow(BigInteger.TEN, nrDecimals.intValue());
        BigInteger poolID = Context.call(BigInteger.class, dexScore, "getPoolId", collateralAddress, bnusdScore);

        BigInteger usdPriceInIcx = (BigInteger) Context.call(oracleScore, "getPriceInLoop", "USD");
        BigInteger assetPriceInIcx = (BigInteger) Context.call(oracleScore, "getPriceInLoop", symbol);
        BigInteger actualUsdPriceInAsset = usdPriceInIcx.multiply(decimals).divide(assetPriceInIcx);

        Map<String, Object> poolStats = (Map<String, Object>) Context.call(dexScore, "getPoolStats", poolID);
        BigInteger assetLiquidity = ((BigInteger) poolStats.get("base"));
        BigInteger bnusdLiquidity = (BigInteger) poolStats.get("quote");
        BigInteger bnusdPriceInAsset = assetLiquidity.multiply(EXA).divide(bnusdLiquidity);

        BigInteger priceDifferencePercentage =
                actualUsdPriceInAsset.subtract(bnusdPriceInAsset).multiply(EXA).divide(actualUsdPriceInAsset);

        // We can get three conditions with price difference.
        // a. priceDifference > threshold (dex price of bnusd is low),
        // b. priceDifference < -threshold, (dex price of bnusd is high)
        // c. priceDifference within [-threshold, threshold] (dex price is within range)

        // If bnUSD price is less in dex, to increase we would need to add sicx in the pool and get back bnUSD
        // Buy bnUSD from the pool --> Sell sicx.

        // If bnUSD price is more in dex, to reduce we would need to add bnusd in the pool, and get back sicx
        // Sell bnUSD to the pool --> buy sicx.
        BigInteger tokensToSell;
        assert threshold != null;
        boolean forward = priceDifferencePercentage.compareTo(threshold) > 0;
        boolean reverse = priceDifferencePercentage.compareTo(threshold.negate()) < 0;
        if (forward) {
            //Add sicx in the pool i.e. buy bnusd from the pool and sell icx. pair: sicx/bnusd
            tokensToSell = actualUsdPriceInAsset.multiply(assetLiquidity).multiply(bnusdLiquidity).divide(EXA).sqrt().subtract(assetLiquidity);
        } else if (reverse) {
            // Add bnusd in the pool i.e. buy sicx from the pool and sell bnusd. pair bnusd/sicx
            BigInteger actualAssetPriceInBnusd = assetPriceInIcx.multiply(EXA).divide(usdPriceInIcx);
            tokensToSell = actualAssetPriceInBnusd.multiply(bnusdLiquidity).multiply(assetLiquidity).divide(decimals).sqrt().subtract(bnusdLiquidity);
        } else {
            tokensToSell = BigInteger.ZERO;
        }

        results.add(forward);
        results.add(tokensToSell);
        results.add(reverse);
        return results;
    }

    @External
    public void rebalance(@Optional Address collateralAddress) {
        collateralAddress = optionalDefault(collateralAddress, sicx.get());
        Address loansScore = loans.get();
        Context.require(loansScore != null);
        List<Object> status = getRebalancingStatusFor(collateralAddress);
        boolean forward = (boolean) status.get(0);
        BigInteger tokenAmount = (BigInteger) status.get(1);
        boolean reverse = (boolean) status.get(2);
        if (forward && tokenAmount.signum() > 0) {
            Context.call(loansScore, "raisePrice", collateralAddress, tokenAmount);
        } else if (reverse && tokenAmount.signum() > 0) {
            Context.call(loansScore, "lowerPrice", collateralAddress, tokenAmount.abs());
        }
    }
}