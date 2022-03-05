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

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static network.balanced.score.core.rebalancing.Checks.*;
import static network.balanced.score.core.rebalancing.Constants.*;

public class Rebalancing {

    public static final String TAG = "Rebalancing";
    
    private final String BNUSD_ADDRESS = "bnUSD_address";
    private final String SICX_ADDRESS = "sicx_address";
    private final String DEX_ADDRESS = "dex_address";
    private final String LOANS_ADDRESS = "loans_address";
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String ADMIN = "admin";
    private final String PRICE_THRESHOLD = "_price_threshold";

    private final BigInteger SICX_BNUSD_POOL_ID = BigInteger.TWO; 

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<Address> bnusd = Context.newVarDB(BNUSD_ADDRESS, Address.class);
    private final VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    private final VarDB<Address> dex = Context.newVarDB(DEX_ADDRESS, Address.class);
    private final VarDB<Address> loans = Context.newVarDB(LOANS_ADDRESS, Address.class);
    private final VarDB<BigInteger> priceThreshold = Context.newVarDB(PRICE_THRESHOLD, BigInteger.class);    

    public Rebalancing(@Optional Address governance) {
        if (governance != null) {
            Context.require(governance.isContract(), TAG + ": Governance address should be a contract");
            Rebalancing.governance.set(governance); 
        }
    }

    @External
    public void setBnusd(Address _address) {
        onlyAdmin();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is required.");
        bnusd.set(_address);
    }
    
    @External
    public void setLoans(Address _address) {
        onlyAdmin();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is required.");
        loans.set(_address);
    }

    @External
    public void setSicx(Address _address){
        onlyAdmin();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is required.");
        sicx.set(_address);
    }
    
    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is required.");
        governance.set(_address);
    }

    @External
    public void setDex(Address _address) {
        onlyAdmin();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is required.");
        dex.set(_address);
    }

    @External
    public void setAdmin(Address _address) {
        onlyGovernance();
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
    public Address getLoans(){
        return loans.get();
    }

    @External(readonly = true)
    public Address getBnusd(){
        return bnusd.get();
    }

    @External(readonly = true)
    public Address getSicx(){
        return sicx.get();
    }

    private BigInteger calculateTokensToSell(BigInteger _price, BigInteger _baseSupply, BigInteger _quoteSupply) {
        return _price.multiply(_baseSupply).multiply(_quoteSupply).divide(EXA).sqrt().subtract(_baseSupply);
    }

    @External
    public void setPriceDiffThreshold(BigInteger _value) {
        onlyGovernance();
        priceThreshold.set(_value);
    }

    @External(readonly = true)
    public BigInteger getPriceChangeThreshold() {
        return priceThreshold.get();
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public List<Object> getRebalancingStatus() {
        List<Object> results = new ArrayList<Object>();
        /* 
         Checks the Rebalancing status of the pool i.e. whether the difference between
        oracle price and dex pool price are more than threshold or not. If it is more
        than the threshold then the function returns a list .
        If the first element of the list is True then it's forward rebalancing and if the
        last element of the list is True, it's the reverse rebalancing .
        The second element of the list specifies the amount of tokens required to balance the pool.
        */
        Address bnusdScore = bnusd.get();
        Address dexScore = dex.get();
        Address sicxScore = sicx.get();
        BigInteger minDiff = priceThreshold.get();

        BigInteger bnusdLastPrice = (BigInteger) Context.call(bnusdScore, "lastPriceInLoop");
        Map<String, Object> poolStats =  (Map<String, Object>) Context.call(dexScore, "getPoolStats", SICX_BNUSD_POOL_ID);
        BigInteger sicxLastPrice = (BigInteger) Context.call(sicxScore, "lastPriceInLoop");
        
        BigInteger price = bnusdLastPrice.multiply(EXA).divide(sicxLastPrice);
        BigInteger poolBase = (BigInteger) poolStats.get("base");
        BigInteger poolQuote = (BigInteger) poolStats.get("quote");
        BigInteger dexPrice = poolBase.multiply(EXA).divide(poolQuote);
        
        BigInteger diff = price.subtract(dexPrice).multiply(EXA).divide(price);
        BigInteger tokensToSell = calculateTokensToSell(price, poolBase, poolQuote);
        
        results.add(diff.compareTo(minDiff) == 1);
        results.add(tokensToSell);
        results.add(diff.compareTo(minDiff.negate()) == -1);

        return results;
    }

    @External
    public void rebalance() {
        // Calls the raise/lower price on loans to balance the sICX/bnUSD price on the DEX.
        Address loansScore = loans.get();
        List<Object> status = getRebalancingStatus();
        boolean higher = (boolean) status.get(0);
        BigInteger tokenAmount = (BigInteger) status.get(1);
        boolean lower = (boolean) status.get(2);
        if (tokenAmount.compareTo(BigInteger.ZERO) == 1) {
            if (higher) {
                Context.call(loansScore, "raisePrice", tokenAmount);
            }
        }
        else {
            if (lower) {
                Context.call(loansScore, "lowerPrice", tokenAmount.abs());
            }
        } 
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {

    }
    
}