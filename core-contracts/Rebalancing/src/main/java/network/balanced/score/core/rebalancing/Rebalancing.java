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
import score.DictDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import scorex.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static network.balanced.score.core.rebalancing.Checks.*;

public class Rebalancing {

    public static final String TAG = "Rebalancing";
    
    private static final String BNUSD_ADDRESS = "bnUSD_address";
    private static final String SICX_ADDRESS = "sicx_address";
    private static final String DEX_ADDRESS = "dex_address";
    private static final String LOANS_ADDRESS = "loans_address";
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String ADMIN = "admin";
    private static final String PRICE_THRESHOLD = "_price_threshold";

    private static final BigInteger EXA = BigInteger.TEN.pow(18); //TODO: move to constants

    private static final VarDB<Address> bnusd = Context.newVarDB(BNUSD_ADDRESS, Address.class);
    private static final VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    private static final VarDB<Address> dex = Context.newVarDB(DEX_ADDRESS, Address.class);
    private static final VarDB<Address> loans = Context.newVarDB(LOANS_ADDRESS, Address.class);
    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<BigInteger> priceThreshold = Context.newVarDB(PRICE_THRESHOLD, BigInteger.class);    

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
        governance.set(_address);
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
        return (_price.multiply(_baseSupply).multiply(_quoteSupply).divide(EXA).subtract(_baseSupply).sqrt());
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
        Map<String, BigInteger> poolStats = (Map) Context.call(dexScore, "getPoolStats", 2);
        BigInteger sicxLastPrice = (BigInteger) Context.call(sicxScore, "lastPriceInLoop");
        
        BigInteger price = bnusdLastPrice.multiply(EXA).divide(sicxLastPrice);
        BigInteger dexPrice = poolStats.get("base").multiply(EXA).divide(poolStats.get("quote"));
        
        BigInteger diff = price.subtract(dexPrice).multiply(EXA).divide(price);
        BigInteger tokensToSell = calculateTokensToSell(price, poolStats.get("base"), poolStats.get("quote"));
        
        results.add(diff.compareTo(minDiff) == 1);
        results.add(tokensToSell);
        results.add(diff.compareTo(minDiff.negate()) == -1);

        return results;
    }

    @External
    public void rebalance() {
        // Calls the retireRedeem method or generateBnusd on loans to balance the sICX/bnUSD price on the DEX.
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