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

package network.balanced.score.core.liquidity;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import com.iconloop.score.util.EnumerableSet;
import static network.balanced.score.lib.utils.Check.*;

import java.math.BigInteger;
import java.util.Map;

import scorex.util.HashMap;

public class LiquidityImpl {
    private final VarDB<Address> governance = Context.newVarDB("governance", Address.class);
    private final VarDB<Address> admin = Context.newVarDB("admin", Address.class);
    private final VarDB<Address> dex = Context.newVarDB("dex", Address.class);
    private final VarDB<Address> daofund = Context.newVarDB("daofund", Address.class);
    private final VarDB<Address> stakedLP = Context.newVarDB("stakedLP", Address.class);

    private final VarDB<Boolean> initialWhitelistedPoolIdsSet = Context.newVarDB("initialWhitelistedPoolIdsSet", Boolean.class);
    private final EnumerableSet<BigInteger> whitelistedPoolIds = new EnumerableSet<>("whitelistedPoolIds", BigInteger.class);
    private final EnumerableSet<Address> tokenAddresses = new EnumerableSet<>("tokenAddresses", Address.class);
    private final VarDB<Boolean> withdrawToDaofund = Context.newVarDB("withdrawToDaofund", Boolean.class);

    public LiquidityImpl(Address governance) {
        this.governance.set(governance);
        this.withdrawToDaofund.set(false);

        // Initial whitelisted pools are set with the function "setInitialWhitelistedPoolIds".
        // It's only callable once. Setting it with a function after deployment keeps some of the 
        // tests cleaner. "InitialWhiteListedPoolIdsSet" and "setInitialWhitelistedPoolIds"
        // should be removed when the if the contract ever is updated.
        this.initialWhitelistedPoolIdsSet.set(false);
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Liquidity";
    }

    @External(readonly = true)
    public Address getGovernance() {
        return this.governance.get();
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External
    public void setAdmin(Address admin) {
        only(this.governance);
        this.admin.set(admin);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return this.admin.get();
    }

    @External
    public void setDex(Address dex) {
        only(this.admin);
        isContract(dex);
        this.dex.set(dex);
    }

    @External(readonly = true)
    public Address getDex() {
        return this.dex.get();
    }

    @External
    public void setDaofund(Address daofund) {
        only(this.admin);
        isContract(daofund);
        this.daofund.set(daofund);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return daofund.get();
    }

    @External
    public void setStakedLP(Address stakedLP) {
        only(this.admin);
        isContract(stakedLP);
        this.stakedLP.set(stakedLP);
    }

    @External(readonly = true)
    public Address getStakedLP() {
        return stakedLP.get();
    }

    @External
    public void addPoolsToWhitelist(BigInteger[] ids) {
        only(this.governance);
        for (BigInteger id : ids) {
            this.whitelistedPoolIds.add(id);
        }
    }

    @External
    public void removePoolsFromWhitelist(BigInteger[] ids) {
        for (BigInteger id : ids) {
            this.whitelistedPoolIds.remove(id);
        }
    }

    @External(readonly = true)
    public BigInteger[] getWhitelistedPoolIds() {
        Integer numberOfIds = this.whitelistedPoolIds.length();
        BigInteger[] poolIds = new BigInteger[numberOfIds];
        for (Integer i = 0; i < numberOfIds; i++) {
            poolIds[i] = this.whitelistedPoolIds.at(i);
        }
        return poolIds;
    }

    @External
    public void supplyLiquidity(Address baseToken, Address quoteToken, BigInteger baseValue, BigInteger quoteValue) {
        this.depositToken(baseToken, baseValue);
        this.depositToken(quoteToken, quoteValue);
        Context.call(this.dex.get(), "add", baseToken, quoteToken, baseValue, quoteValue);
    }

    @External
    public void withdrawLiquidity(BigInteger poolID, BigInteger lptokens, boolean withdrawToDaofund) {
        only(this.governance);
        if (withdrawToDaofund) {
            this.withdrawToDaofund.set(true);
            Context.call(this.dex.get(), "remove", poolID, lptokens, true);
            this.withdrawToDaofund.set(false);
        }
        else {
            Context.call(this.dex.get(), "remove", poolID, lptokens, true);
        }     
    }

    @External
    public void stakeLPTokens(BigInteger id, BigInteger amount) {
        Context.call(this.dex.get(), "transfer", this.stakedLP.get(), amount, id, this.createLPStakingData());
    }

    @External
    public void unstakeLPTokens(BigInteger id, BigInteger amount) {
        only(this.governance);
        Context.call(this.stakedLP.get(), "unstake", id, amount);
    }

    private void depositToken(Address token, BigInteger amount) {
        this.transferToken(token, this.dex.get(), amount, this.createLiquidityDepositData());
    }
    
    @External(readonly = true)
    public Map<String, BigInteger> getTokenBalances(boolean symbolsAsKeys) {
        Integer tokenAddressNumber = this.tokenAddresses.length();

        Map<String, BigInteger> tokenBalances = new HashMap<>();
        for (Integer i = 0; i < tokenAddressNumber; i++) {
            Address tokenAddress = this.tokenAddresses.at(i);
            BigInteger tokenBalance = (BigInteger) Context.call(tokenAddress, "balanceOf", Context.getAddress());

            if (symbolsAsKeys) {
                String symbol = (String) Context.call(tokenAddress, "symbol");
                tokenBalances.put(symbol, tokenBalance);
            }
            else {
                tokenBalances.put(tokenAddress.toString(), tokenBalance);
            }
        }
        return tokenBalances;
    }

    @External
    public void claimFunding() {
        Context.call(this.daofund.get(), "claim");
    }

    @External 
    public void transferToDaofund(Address token, BigInteger amount) {
        this.transferToken(token, this.daofund.get(), amount, new byte[0]);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        // Used for tracking contract balances.
        Address caller = Context.getCaller();
        this.tokenAddresses.add(caller);

        // Send incomming tokens to daofund if flag is true.
        // Used as an option when withdrawing liquidity from a liquidity pool.
        if (this.withdrawToDaofund.get()) {
            this.transferToken(caller, this.daofund.get(), _value, new byte[0]);
        }
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
    }

    private void transferToken(Address token, Address to, BigInteger amount, byte[] data) {
        Context.call(token, "transfer", to, amount, data);
    }

    private byte[] createLiquidityDepositData() {
        JsonObject data = Json.object();
        data.add("method", "_deposit");
        return data.toString().getBytes();
    }

    private byte[] createLPStakingData() {
        JsonObject data = Json.object();
        data.add("method", "stake");
        return data.toString().getBytes();
    }

    @External
    public void setInitialWhitelistedPoolIds() {
        if(initialWhitelistedPoolIdsSet.get()) {
            Context.revert("Initial poolids have already been set.");
        }
        else {
            // sicx/bnusd: 2, baln/bnusd: 3, baln/sicx: 4, usds/bnusd: 10, iusdc/bnusd: 5.
            this.whitelistedPoolIds.add(BigInteger.valueOf(2));
            this.whitelistedPoolIds.add(BigInteger.valueOf(3));
            this.whitelistedPoolIds.add(BigInteger.valueOf(4));
            this.whitelistedPoolIds.add(BigInteger.valueOf(10));
            this.whitelistedPoolIds.add(BigInteger.valueOf(5));
            this.initialWhitelistedPoolIdsSet.set(true);
        }
    }  
}

