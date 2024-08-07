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

import network.balanced.score.lib.interfaces.tokens.HSP20;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;

// Special HSP20 version to be compatible to upgrade from a IRC20
public class IRC2HSP20 implements HSP20 {
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
    private final BranchDB<Address, DictDB<Address, BigInteger>> allowances = Context.newBranchDB("allowances",
            BigInteger.class);

    public IRC2HSP20(String _name, String _symbol, int _decimals) {
        // initialize values only at first deployment
        if (this.name.get() == null) {
            this.name.set(ensureNotEmpty(_name));
            this.symbol.set(ensureNotEmpty(_symbol));

            // decimals must be larger than 0 and less than 21
            Context.require(_decimals >= 0, "decimals needs to be positive");
            Context.require(_decimals <= 21, "decimals needs to be equal or lower than 21");
            this.decimals.set(BigInteger.valueOf(_decimals));
        }
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

    private void safeSetBalance(Address owner, BigInteger value) {
        balances.set(owner, value);
    }

    private void transfer(Address from, Address to, BigInteger value) {
        // check some basic requirements
        Context.require(value.compareTo(BigInteger.ZERO) >= 0, "_value needs to be positive");
        Context.require(balanceOf(from).compareTo(value) >= 0, "Insufficient balance");

        // adjust the balances
        safeSetBalance(from, balanceOf(from).subtract(value));
        safeSetBalance(to, balanceOf(to).add(value));

        // emit Transfer event first
        Transfer(from, to, value);
    }

    @External
    public boolean transfer(Address _to, BigInteger _value) {
        Address _from = Context.getCaller();
        transfer(_from, _to, _value);
        return true;
    }

    /**
     * Creates `value` tokens and assigns them to `owner`, increasing the total
     * supply.
     */
    protected void mint(Address owner, BigInteger value) {
        Context.require(!ZERO_ADDRESS.equals(owner), "Owner address cannot be zero address");
        Context.require(value.compareTo(BigInteger.ZERO) >= 0, "value needs to be positive");

        totalSupply.set(totalSupply().add(value));
        safeSetBalance(owner, balanceOf(owner).add(value));
        Transfer(ZERO_ADDRESS, owner, value);
    }

    /**
     * Destroys `value` tokens from `owner`, reducing the total supply.
     */
    protected void burn(Address owner, BigInteger value) {
        Context.require(!ZERO_ADDRESS.equals(owner), "Owner address cannot be zero address");
        Context.require(value.compareTo(BigInteger.ZERO) >= 0, "value needs to be positive");
        Context.require(balanceOf(owner).compareTo(value) >= 0, "Insufficient balance");

        safeSetBalance(owner, balanceOf(owner).subtract(value));
        totalSupply.set(totalSupply().subtract(value));
        Transfer(owner, ZERO_ADDRESS, value);
    }

    private void _approve(Address owner, Address spender, BigInteger value) {
        Context.require(!ZERO_ADDRESS.equals(owner), "approve from the zero address");
        Context.require(!ZERO_ADDRESS.equals(spender), "approve to the zero address");

        allowances.at(owner).set(spender, value);
        Approval(owner, spender, value);
    }

    @External
    public boolean approve(Address _spender, BigInteger _value) {
        Address owner = Context.getCaller();
        _approve(owner, _spender, _value);
        return true;
    }

    @External(readonly = true)
    public BigInteger allowance(Address _owner, Address _spender) {
        return allowances.at(_owner).getOrDefault(_spender, BigInteger.ZERO);
    }

    protected void _spendAllowance(Address owner, Address spender, BigInteger value) {
        BigInteger currentAllowance = allowance(owner, spender);
        BigInteger reminder = currentAllowance.subtract(value);
        Context.require(reminder.signum() >= 0, "insufficient allowance");
        _approve(owner, spender, reminder);
    }

    @External
    public boolean transferFrom(Address _from, Address _to, BigInteger _value) {
        Address spender = Context.getCaller();
        _spendAllowance(_from, spender, _value);
        transfer(_from, _to, _value);
        return true;
    }

    @EventLog(indexed = 2)
    public void Transfer(Address _from, Address _to, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void Approval(Address _owner, Address _spender, BigInteger _value) {
    }
}