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

import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.pow;

public class IRC2PresetFixedSupply extends IRC2Base {

    private final VarDB<String> name = Context.newVarDB(NAME, String.class);
    private final VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    private final VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    protected final VarDB<BigInteger> totalSupply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);
    protected DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);

    /**
     * @param _tokenName     The name of the token.
     * @param _symbolName    The symbol of the token.
     * @param _initialSupply The total number of tokens to initialize with. It is set to total supply in the
     *                       beginning, 0 by default.
     * @param _decimals      The number of decimals. Set to 18 by default.
     */
    public IRC2PresetFixedSupply(String _tokenName, String _symbolName, @Optional BigInteger _initialSupply,
                                 @Optional BigInteger _decimals) {
        if (this.name.get() == null) {
            BigInteger initialSupply = (_initialSupply == null) ? BigInteger.ZERO : _initialSupply;
            BigInteger decimals = (_decimals == null) ? BigInteger.valueOf(18L) : _decimals;

            Context.require(decimals.compareTo(BigInteger.ZERO) >= 0, "Decimals cannot be less than zero");
            Context.require(initialSupply.compareTo(BigInteger.ZERO) >= 0, "Initial Supply cannot be less than or " +
                    "equal to than zero");

            BigInteger totalSupply = _initialSupply.multiply(pow(BigInteger.TEN, _decimals.intValue()));
            final Address caller = Context.getCaller();
            mint(caller, totalSupply);
        }
    }

    private String ensureNotEmpty(String str) {
        Context.require(str != null && !str.trim().isEmpty(), "str is null or empty");
        assert str != null;
        return str.trim();
    }

    /**
     * @return Name of the token
     */
    @External(readonly = true)
    public String name() {
        return name.get();
    }

    /**
     * @return Symbol of the token
     */
    @External(readonly = true)
    public String symbol() {
        return symbol.get();
    }

    /**
     * @return Number of decimals
     */
    @External(readonly = true)
    public BigInteger decimals() {
        return decimals.get();
    }

    /**
     * @return total number of tokens in existence.
     */
    @External(readonly = true)
    public BigInteger totalSupply() {
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    /**
     * @param _owner: The account whose balance is to be checked.
     * @return Amount of tokens owned by the `account` with the given address.
     */
    @External(readonly = true)
    public BigInteger balanceOf(Address _owner) {
        return balances.getOrDefault(_owner, BigInteger.ZERO);
    }

    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        transfer(Context.getCaller(), _to, _value, _data);
    }

        /**
         *
         * @param _to The account to which the token is to be transferred
         * @param _value The no. of tokens to be transferred
         * @param _data Any information or message
         */
    protected void transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, this.name.get() + ": _value needs to be positive");
        Context.require(balanceOf(_from).compareTo(_value) >= 0, this.name.get() + ": Insufficient balance");

        this.balances.set(_from, balanceOf(_from).subtract(_value));
        this.balances.set(_to, balanceOf(_to).add(_value));

        byte[] dataBytes = (_data == null) ? new byte[0] : _data;
        Transfer(_from, _to, _value, dataBytes);

        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", _from, _value, dataBytes);
        }
    }

    protected void burn(Address account, BigInteger amount) {
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("Invalid Value");
        }

        BigInteger newTotalSupply = totalSupply.getOrDefault(BigInteger.ZERO).subtract(amount);
        BigInteger newUserBalance = balances.getOrDefault(account, BigInteger.ZERO).subtract(amount);
        if (newTotalSupply.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Total Supply can not be set to negative");
        }
        if (newUserBalance.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("User Balance can not be set to negative");
        }

        totalSupply.set(newTotalSupply);
        balances.set(account, newUserBalance);

        Burn(account, amount);

        String data = "None";
        Transfer(account, EOA_ZERO, amount, data.getBytes());
    }

    protected void mint(Address account, BigInteger amount, @Optional byte[] _data) {
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("Invalid Value");
        }
        totalSupply.set(totalSupply.getOrDefault(BigInteger.ZERO).add(amount));
        balances.set(account, balances.getOrDefault(account, BigInteger.ZERO).add(amount));

        Mint(account, amount, _data);

        Transfer(EOA_ZERO, account, amount, _data);
        if (account.isContract()) {
            // If the recipient is SCORE, then calls `tokenFallback` to hand over control.
            Context.call(account, "tokenFallback", EOA_ZERO, amount, _data);
        }
    }

    @EventLog(indexed = 3)
    public void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
    }

    @EventLog(indexed = 1)
    public void Mint(Address account, BigInteger amount, byte[] _data) {

    }

    @EventLog(indexed = 1)
    public void Burn(Address account, BigInteger amount) {

    }
}
