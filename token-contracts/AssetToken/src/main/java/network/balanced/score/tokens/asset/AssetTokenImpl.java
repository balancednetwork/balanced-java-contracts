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

package network.balanced.score.tokens.asset;

import network.balanced.score.lib.interfaces.tokens.AssetToken;
import network.balanced.score.lib.tokens.SpokeTokenImpl;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import xcall.score.lib.util.NetworkAddress;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.getAssetManager;

public class AssetTokenImpl extends SpokeTokenImpl implements AssetToken {
    public static final String VERSION = "version";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public AssetTokenImpl(Address _governance, String name, String symbol) {
        super("", name, symbol, null);
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }

        NATIVE_NID = Context.call(String.class, BalancedAddressManager.getXCall(), "getNetworkId");

        if (this.currentVersion.getOrDefault("").equals(Versions.BALANCED_ASSETS)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.BALANCED_ASSETS);
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
    public void govHubTransfer(String _from, String _to, BigInteger _value, @Optional byte[] _data) {
        onlyGovernance();
        _transfer(NetworkAddress.valueOf(_from, NATIVE_NID), NetworkAddress.valueOf(_to, NATIVE_NID), _value, _data);
    }

    @External
    public void burn(BigInteger _amount) {
        burnFrom(Context.getCaller().toString(), _amount);
    }

    @External
    public void burnFrom(String _account, BigInteger _amount) {
        checkStatus();
        only(getAssetManager());
        super.burn(NetworkAddress.valueOf(_account, NATIVE_NID), _amount);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        only(getAssetManager());
        super.mint(new NetworkAddress(NATIVE_NID ,Context.getAddress()), _amount);
    }

    @External
    public void mintAndTransfer(String _from, String _to, BigInteger _amount, @Optional byte[] _data) {
        only(getAssetManager());
        NetworkAddress from = NetworkAddress.valueOf(_from);
        NetworkAddress to = NetworkAddress.valueOf(_to, NATIVE_NID);
        super.mint(from, _amount);
        if (_from.equals(_to)) {
            return;
        }

        super._transfer(from, to, _amount, _data);
    }

    @Override
    @External
    public void handleCallMessage(String _from, byte[] _data) {
        checkStatus();
        super.handleCallMessage(_from, _data);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus();
        super.transfer( _to, _value, _data);
    }
}