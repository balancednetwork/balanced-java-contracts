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

package network.balanced.score.lib.tokens;

import network.balanced.score.lib.interfaces.tokens.IRC2;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public class IRC2Base implements IRC2 {

    private final static String NAME = "name";
    private final static String SYMBOL = "symbol";
    private final static String DECIMALS = "decimals";
    private final static String TOTAL_SUPPLY = "total_supply";
    private final static String BALANCES = "balances";

    static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);

    private final VarDB<String> name = Context.newVarDB(NAME, String.class);
    private final VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    private final VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    private final VarDB<BigInteger> totalSupply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);
    protected final DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);

    protected IRC2Base(String _tokenName, String _symbolName, @Optional BigInteger _decimals) {
        if (this.name.get() == null) {
            _decimals = _decimals == null ? BigInteger.valueOf(18L) : _decimals;
            Context.require(_decimals.compareTo(BigInteger.ZERO) >= 0, "Decimals cannot be less than zero");

            this.name.set(ensureNotEmpty(_tokenName));
            this.symbol.set(ensureNotEmpty(_symbolName));
            this.decimals.set(_decimals);
        }
    }

    @EventLog(indexed = 3)
    public void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
    }

    private String ensureNotEmpty(String str) {
        Context.require(str != null && !str.trim().isEmpty(), "str is null or empty");
        assert str != null;
        return str.trim();
    }

    @External(readonly = true)
    public String name() {
        return name.get();
    }

    @External(readonly = true)
    public String symbol() {
        return symbol.get();
    }

    @External(readonly = true)
    public BigInteger decimals() {
        return decimals.get();
    }

    @External(readonly = true)
    public BigInteger totalSupply() {
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner) {
        return balances.getOrDefault(_owner, BigInteger.ZERO);
    }

    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        transfer(Context.getCaller(), _to, _value, _data);
    }

    protected void transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0, this.name.get() + ": _value needs to be positive");
        Context.require(balanceOf(_from).compareTo(_value) >= 0, this.name.get() + ": Insufficient balance");

        this.balances.set(_from, balanceOf(_from).subtract(_value));
        this.balances.set(_to, balanceOf(_to).add(_value));

        byte[] dataBytes = (_data == null) ? "None".getBytes() : _data;
        Transfer(_from, _to, _value, dataBytes);

        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", _from, _value, dataBytes);
        }
    }

    protected void mint(Address owner, BigInteger amount) {
        Context.require(!ZERO_ADDRESS.equals(owner), this.name.get() + ": Owner address cannot be zero address");
        Context.require(amount.compareTo(BigInteger.ZERO) >= 0, this.name.get() + ": Amount needs to be positive");

        totalSupply.set(totalSupply().add(amount));
        balances.set(owner, balanceOf(owner).add(amount));
        Transfer(ZERO_ADDRESS, owner, amount, "mint".getBytes());
    }

    protected void burn(Address owner, BigInteger amount) {
        Context.require(!ZERO_ADDRESS.equals(owner), this.name.get() + ": Owner address cannot be zero address");
        Context.require(amount.compareTo(BigInteger.ZERO) >= 0, this.name.get() + ": Amount needs to be positive");
        Context.require(balanceOf(owner).compareTo(amount) >= 0, this.name.get() + ": Insufficient Balance");

        balances.set(owner, balanceOf(owner).subtract(amount));
        totalSupply.set(totalSupply().subtract(amount));
        Transfer(owner, ZERO_ADDRESS, amount, "burn".getBytes());
    }
}
