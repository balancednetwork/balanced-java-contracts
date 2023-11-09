/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.tokens.bsr;

import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.BalancedSavingsRate;
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

import java.math.BigInteger;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.*;

public class BalancedSavingRateImpl extends HubTokenImpl implements BalancedSavingsRate {
    private static final String TOKEN_NAME = Names.BSR;
    private static final String SYMBOL_NAME = "BSR";
    public static final String VERSION = "version";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public BalancedSavingRateImpl(Address _governance) {
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

    @External
    public void updateAddress(String name) {
        BalancedAddressManager.resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return BalancedAddressManager.getAddressByName(name);
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
        only(getSavings());
        super.burn(new NetworkAddress(NATIVE_NID, _account), _amount);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        mintTo(Context.getCaller().toString(), _amount, _data);
    }

    @External
    public void mintTo(String _account, BigInteger _amount, @Optional byte[] _data) {
        checkStatus();
        only(getSavings());
        mintWithTokenFallback(_account, _amount, _data);
    }

    protected void mintWithTokenFallback(String _to, BigInteger _amount, byte[] _data) {
        NetworkAddress to = NetworkAddress.valueOf(_to, NATIVE_NID);
        mint(to, _amount);
        byte[] data = (_data == null) ? new byte[0] : _data;
        if (isNative(to) && Address.fromString(to.account()).isContract()) {
            Context.call(Address.fromString(to.account()), "tokenFallback", new Address(new byte[Address.LENGTH]), _amount, data);
        }
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
        super.transfer(_to, _value, _data);
    }

    @Payable
    public void fallback() {
    }
}