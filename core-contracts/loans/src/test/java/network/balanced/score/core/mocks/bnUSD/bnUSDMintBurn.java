/*
 * Copyright 2021 ICONLOOP Inc.
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
package network.balanced.score.core;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;
import network.balanced.score.core.bnUSD;

public class bnUSDMintBurn extends bnUSD {

    private final VarDB<Address> minter = Context.newVarDB("minter", Address.class);
    private final VarDB<BigInteger> price = Context.newVarDB("priceInLoop", BigInteger.class);
    private final VarDB<BigInteger> lastPrice = Context.newVarDB("lastPriceInLoop", BigInteger.class);

    public bnUSDMintBurn(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
        super(_name, _symbol, _decimals);
        // By default, set the minter role to the owner
        if (minter.get() == null) {
            minter.set(Context.getOwner());
        }

        mintTo(Context.getCaller(), _totalSupply);
        setPrice(BigInteger.valueOf(1).multiply(BigInteger.TEN.pow(18)));
    }

    @External
    public void setPrice(BigInteger price) {
        this.price.set(price);
        lastPrice.set(this.price.get());
    }

    @External
    public String getPeg() {
        return "";

    }

    @External
    public BigInteger priceInLoop() {
        lastPrice.set(price.get());
        return price.get();

    }

    @External(readonly=true)
    public BigInteger lastPriceInLoop() {
        return lastPrice.get();
    }

    /**
     * Creates _amount number of tokens, and assigns to caller.
     * Increases the balance of that account and the total supply.
     */
    @External
    public void mint(BigInteger _amount) {
        // simple access control - only the minter can mint new token
        Context.require(Context.getCaller().equals(minter.get()));
        _mint(Context.getCaller(), _amount);
    }

    /**
     * Creates _amount number of tokens, and assigns to _account.
     * Increases the balance of that account and the total supply.
     */
    @External
    public void mintTo(Address _account, BigInteger _amount) {
        // simple access control - only the minter can mint new token
        Context.require(Context.getCaller().equals(minter.get()));
        _mint(_account, _amount);
    }

    @External
    public void setMinter(Address _minter) {
        // simple access control - only the contract owner can set new minter
        Context.require(Context.getCaller().equals(Context.getOwner()));
        minter.set(_minter);
    }

    @External
    public void burnFrom(Address address, BigInteger _amount) {
        _burn(address, _amount);
    }
}