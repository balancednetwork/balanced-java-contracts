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

public class Liquidity {
    private final VarDB<Address> governanceAddress = Context.newVarDB("governanceAddress", Address.class);
    private final VarDB<Address> adminAddress = Context.newVarDB("adminAddress", Address.class);
    private final VarDB<Address> dexAddress = Context.newVarDB("dexAddress", Address.class);
    private final VarDB<Address> daofundAddress = Context.newVarDB("daofundAddress", Address.class);
    private final VarDB<Address> stakedLPAddress = Context.newVarDB("stakedLPAddress", Address.class);

    private final EnumerableSet<Address> balanceAddresses = new EnumerableSet<>("balanceAddresses", Address.class);
    private final VarDB<Boolean> withdrawingLiquidity = Context.newVarDB("withdrawingLiquidity", Boolean.class);

    public Liquidity(Address governance, Address admin) {
        this.governanceAddress.set(governance);
        this.adminAddress.set(admin);
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Liquidity";
    }

    @External
    public void setAdmin(Address admin) {
        only(this.governanceAddress);
        this.adminAddress.set(admin);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return this.adminAddress.get();
    }

    @External(readonly = true)
    public Address getDex() {
        return this.dexAddress.get();
    }

    @External
    public void setDex(Address dex) {
        only(this.adminAddress);
        isContract(dex);
        this.dexAddress.set(dex);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return dexAddress.get();
    }

    @External
    public void setDaofund(Address daofund) {
        only(this.adminAddress);
        isContract(daofund);
        this.dexAddress.set(daofund);
    }

    @External(readonly = true)
    public Address getStakedLP() {
        return stakedLPAddress.get();
    }

    @External
    public void setStakedLP(Address stakedLP) {
        only(this.adminAddress);
        isContract(stakedLP);
        this.stakedLPAddress.set(stakedLP);
    }

    @External
    public void supplyLiquidity(Address baseToken, Address quoteToken, BigInteger baseValue, BigInteger quoteValue) {
        this.depositToken(baseToken, baseValue);
        this.depositToken(quoteToken, quoteValue);
        Context.call(this.dexAddress.get(), "add", baseToken, quoteToken, baseValue, quoteValue);
    }

    @External
    public void withdrawLiquidity(Integer poolID, BigInteger lptokens) {
        only(this.governanceAddress);

        // Let the contract know that tokenFallback method should treat the
        // incomming tokens as withdrawn liquidity.
        this.withdrawingLiquidity.set(true);
        Context.call(this.dexAddress.get(), "remove", poolID, lptokens, true);
        this.withdrawingLiquidity.set(false);
    }

    @External
    public void stakeLPTokens(BigInteger id, BigInteger amount) {
        Context.call(this.dexAddress.get(), "transfer", this.stakedLPAddress.get(), amount, id, this.createLPStakingData());
    }

    @External
    public void unstakeLPTokens(BigInteger id, BigInteger amount) {
        only(this.governanceAddress);
        Context.call(this.stakedLPAddress.get(), "unstake", id, amount);
    }

    private void depositToken(Address token, BigInteger amount) {
        this.transferToken(token, this.dexAddress.get(), amount, this.createLiquidityDepositData());
    }
    
    // Add optional parameter to identify tokens with symbol? --> Call to symbol method as well.
    @External(readonly = true)
    public Map<Address, BigInteger> getTokenBalances() {
        Integer tokenAddressNumber = this.balanceAddresses.length();
        System.out.println(tokenAddressNumber);

        Map<Address, BigInteger> tokenBalances = new HashMap<>();
        for (Integer i = 0; i < tokenAddressNumber; i++) {
            Address tokenAddress = this.balanceAddresses.at(i);
            BigInteger tokenBalance = getTokenBalance(tokenAddress);
            tokenBalances.put(tokenAddress, tokenBalance);
        }

        return tokenBalances;
    }

    @External(readonly = true) 
    public Map<Integer, BigInteger> getLPTokenBalances() {
        Address dexAddress =this.dexAddress.get();
        Integer numberOfPools = (Integer) Context.call(dexAddress, "getNonce");

        Map<Integer, BigInteger> tokenBalances = new HashMap<>();
        for (Integer i = 1; i <= numberOfPools; i++) {
            BigInteger lpTokenBalance = (BigInteger) Context.call(dexAddress, "balanceOf", Context.getAddress(), i);
            tokenBalances.put(i, lpTokenBalance);
        }

        return tokenBalances;
    }

    @External
    public void claimFunding() {
        Context.call(this.daofundAddress.get(), "claim");
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        // Add token address to set if it is not already there.
        // Used for tracking contract balance.
        Address caller = Context.getCaller();
        System.out.println("Caller_1: " + caller);
        this.balanceAddresses.add(caller);

        // Send incomming tokens to daofund if withdrawing LP tokens.
        //if (this.withdrawingLiquidity.get()) {
        //    this.transferToken(caller, this.daofundAddress.get(), _value, new byte[0]);
        //}
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
    }

    @External(readonly = true)
    public BigInteger getTokenBalance(Address token) {
        return (BigInteger) Context.call(token, "balanceOf", Context.getAddress());
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
}

