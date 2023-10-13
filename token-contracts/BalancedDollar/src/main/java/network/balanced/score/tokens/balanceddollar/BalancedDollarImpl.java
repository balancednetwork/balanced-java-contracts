/*
 * Copyright (c) 2022-2023 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *s
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.tokens.balanceddollar;

import network.balanced.score.lib.interfaces.BalancedDollar;
import network.balanced.score.lib.tokens.HubTokenImpl;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import xcall.score.lib.util.NetworkAddress;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.getLoans;
import static network.balanced.score.lib.utils.BalancedAddressManager.getStabilityFund;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBalancedOracle;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDaofund;

public class BalancedDollarImpl extends HubTokenImpl implements BalancedDollar {
    private static final String TOKEN_NAME = Names.BNUSD;
    private static final String SYMBOL_NAME = "bnUSD";
    private final String USD_BASE = "USD";
    public static final String VERSION = "version";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public BalancedDollarImpl(Address _governance) {
        super("", TOKEN_NAME, SYMBOL_NAME, null);
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }

        NATIVE_NID = Context.call(String.class, BalancedAddressManager.getXCall(), "getNetworkId");

        if (this.currentVersion.getOrDefault("").equals(Versions.BNUSD)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.BNUSD);
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External(readonly = true)
    public String getPeg() {
        return USD_BASE;
    }

    @External
    public void updateAddress(String name) {
        BalancedAddressManager.resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return BalancedAddressManager.getAddressByName(name);
    }
    @External
    public BigInteger priceInLoop() {
        return Context.call(BigInteger.class, getBalancedOracle(), "getPriceInLoop", USD_BASE);
    }

    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        return Context.call(BigInteger.class, getBalancedOracle(), "getLastPriceInLoop", USD_BASE);
    }

    @External
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyGovernance();
        _transfer(new NetworkAddress(NATIVE_NID, _from), new NetworkAddress(NATIVE_NID, _to), _value, _data);
    }

    @External
    public void govHubTransfer(String _from, String _to, BigInteger _value, @Optional byte[] _data) {
        onlyGovernance();
        _transfer(NetworkAddress.valueOf(_from, NATIVE_NID), NetworkAddress.valueOf(_to, NATIVE_NID), _value, _data);
    }

    @External
    public void burn(BigInteger _amount) {
        burnFrom(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        checkStatus();
        onlyEither(getLoans(), getStabilityFund());
        super.burn(new NetworkAddress(NATIVE_NID, _account), _amount);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        mintTo(Context.getCaller(), _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        checkStatus();
        onlyEither(getLoans(), getStabilityFund());
        mintWithTokenFallback(_account, _amount, _data);
    }

    protected void mintWithTokenFallback(Address _to, BigInteger _amount, byte[] _data) {
        mint(new NetworkAddress(NATIVE_NID, _to), _amount);
        byte[] data = (_data == null) ? new byte[0] : _data;
        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", new Address(new byte[Address.LENGTH]), _amount, data);
        }
    }

    @Override
    @External
    public void handleCallMessage(String _from, byte[] _data) {
        checkStatus();
        super.handleCallMessage(_from, _data);
    }

    @Override
    public BigInteger getHopFee(String net) {
        if (!canWithdraw(net)) {
            return BigInteger.ONE.negate();
        }
        return Context.call(BigInteger.class, getDaofund(), "claimXCallFee", net, true);
    }

    private boolean canWithdraw(String net) {
        return Context.call(Boolean.class, getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus();
        super.transfer( _to, _value, _data);
    }

    @Payable
    public void fallback() {
    }
}