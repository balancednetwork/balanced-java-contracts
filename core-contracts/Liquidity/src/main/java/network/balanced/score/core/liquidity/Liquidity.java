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

import java.math.BigInteger;
import java.util.Map;

import scorex.util.HashMap;

public class Liquidity {
    public final VarDB<Address> dexAddress = Context.newVarDB("dexAddress", Address.class);
    public final VarDB<Address> daofundAddress = Context.newVarDB("daofundAddress", Address.class);
    public final VarDB<Address> governanceAddress = Context.newVarDB("governanceAddress", Address.class);
    public final VarDB<Address> stakedLPAddress = Context.newVarDB("stakedLPAddress", Address.class);

    private final EnumerableSet<Address> balanceAddresses = new EnumerableSet<>("balanceAddresses", Address.class);
    private final VarDB<Boolean> withdrawingLiquidity = Context.newVarDB("withdrawingLiquidity", Boolean.class);

    public Liquidity() {
        // this.dexAddress.set(Address.fromString("cx648a6d9c5f231f6b86c0caa9cc9eff8bd6040999"));
        // this.daofundAddress.set(Address.fromString("cx648a6d9c5f231f6b86c0caa9cc9eff8bd6040999"));
        // this.governanaceAddress.set(Address.fromString("cx648a6d9c5f231f6b86c0caa9cc9eff8bd6040999"));
        // this.stakedLPAddress.set(Address.fromString("cx648a6d9c5f231f6b86c0caa9cc9eff8bd6040999"));
    }

    private void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), this.name() + ": Caller is not the owner");
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Liquidity";
    }

    @External(readonly = true)
    public Address getDex() {
        return dexAddress.get();
    }

    @External
    public void setDex(Address dex) {
        onlyOwner();
        Context.require(dex.isContract(), this.name() + ": Dex parameter is not contract address");
        this.dexAddress.set(dex);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return dexAddress.get();
    }

    @External
    public void setDaofund(Address daofund) {
        onlyOwner();
        Context.require(daofund.isContract(), this.name() + ": Daofund parameter is not contract address");
        this.dexAddress.set(daofund);
    }

    @External(readonly = true)
    public Address getStakedLP() {
        return stakedLPAddress.get();
    }

    @External
    public void setStakedLP(Address stakedLP) {
        onlyOwner();
        Context.require(stakedLP.isContract(), this.name() + ": Daofund parameter is not contract address");
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
        Context.require(Context.getCaller() == this.governanceAddress.get());

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
        Context.require(Context.getCaller() == this.governanceAddress.get());
        Context.call(this.stakedLPAddress.get(), "unstake", id, amount);
    }

    private void depositToken(Address token, BigInteger amount) {
        this.transferToken(token, this.dexAddress.get(), amount, this.createLiquidityDepositData());
    }
    
    // Add optional parameter to identify tokens with symbol? --> Call to symbol method as well.
    @External(readonly = true)
    public Map<Address, BigInteger> getTokenBalances() {
        Integer tokenAddressNumber = this.balanceAddresses.length();

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
        this.balanceAddresses.add(caller);

        // Send incomming tokens to daofund if withdrawing LP tokens.
        if (this.withdrawingLiquidity.get()) {
            this.transferToken(caller, this.daofundAddress.get(), _value, new byte[0]);
        }
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

