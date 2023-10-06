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

package network.balanced.score.core.asset.manager;

import network.balanced.score.lib.interfaces.AssetManager;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.AssetManagerXCall;
import network.balanced.score.lib.interfaces.SpokeAssetManagerMessages;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.IterableDictDB;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.XCallUtils;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;

public class AssetManagerImpl implements AssetManager {

    public static final String VERSION = "version";
    public static final String SPOKES = "spokes";
    public static final String ASSETS = "assets";
    public static final String NATIVE_ASSET_ADDRESS = "native_asset_address";

    public static String NATIVE_NID;
    public static byte[] tokenBytes;

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    // net -> networkAddress
    private final IterableDictDB<String, String> spokes = new IterableDictDB<>(SPOKES, String.class, String.class, false);
    // networkAddress -> native
    private final IterableDictDB<String, Address> assets = new IterableDictDB<>(ASSETS, Address.class, String.class, false);
    private final DictDB<Address, String> assetNativeAddress = Context.newDictDB(NATIVE_ASSET_ADDRESS, String.class);

    public AssetManagerImpl(Address _governance, byte[] tokenBytes) {
        AssetManagerImpl.tokenBytes = tokenBytes;
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }

        NATIVE_NID = Context.call(String.class, BalancedAddressManager.getXCall(), "getNetworkId");

        if (currentVersion.getOrDefault("").equals(Versions.BALANCED_ASSET_MANAGER)) {
            Context.revert("Can't Update same version of code");
        }

        currentVersion.set(Versions.BALANCED_ASSET_MANAGER);
    }

    @External(readonly = true)
    public String name() {
        return Names.ASSET_MANAGER;
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
    public void deployAsset(String tokenNetworkAddress, String name, String symbol, BigInteger decimals) {
        onlyGovernance();
        NetworkAddress nativeAddress = NetworkAddress.valueOf(tokenNetworkAddress);

        Context.require(spokes.get(nativeAddress.net()) != null);
        Context.require(assets.get(tokenNetworkAddress) == null);
        Address token = Context.deploy(tokenBytes, BalancedAddressManager.getGovernance(), name, symbol, decimals);
        assets.set(tokenNetworkAddress, token);
        assetNativeAddress.set(token, tokenNetworkAddress);
        Address SYSTEM_SCORE_ADDRESS = getSystemScoreAddress();
        Context.call(SYSTEM_SCORE_ADDRESS, "setScoreOwner", token, BalancedAddressManager.getGovernance());
    }

    @External
    public void addSpokeManager(String spokeAssetManager) {
        onlyGovernance();
        NetworkAddress address = NetworkAddress.valueOf(spokeAssetManager);
        spokes.set(address.net(), spokeAssetManager);
    }

    @External(readonly = true)
    public Map<String, String> getAssets() {
        Map<String, String> assetsMap = new HashMap<>();
        List<String> spokeTokensList = assets.keys();
        for (String token : spokeTokensList) {
            assetsMap.put(token, assets.get(token).toString());
        }

        return assetsMap;
    }

    @External(readonly = true)
    public String[] getSpokes() {
        int size = spokes.size();
        String[] spokeAddresses = new String[size];
        List<String> networks = spokes.keys();
        for (int i = 0; i < size; i++) {
            spokeAddresses[i] = spokes.get(networks.get(i));
        }

        return spokeAddresses;
    }

    @External(readonly = true)
    public Address getAssetAddress(String spokeAddress) {
        return assets.get(spokeAddress);
    }

    @External(readonly = true)
    public String getNativeAssetAddress(Address token) {
        return assetNativeAddress.get(token);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(BalancedAddressManager.getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        AssetManagerXCall.process(this, _from, _data);
    }

    @External
    @Payable
    public void withdrawTo(Address asset, String to, BigInteger amount) {
        _withdrawTo(asset, Context.getCaller().toString(), to, amount, Context.getValue());
    }

    @Payable
    public void fallback() {
    }

    public void withdrawRollback(String from, String tokenAddress, String _to, BigInteger _amount) {
        NetworkAddress xCall = NetworkAddress.valueOf(from);
        Context.require(xCall.net().equals(NATIVE_NID));
        Context.require(xCall.account().equals(BalancedAddressManager.getXCall().toString()));
        Address assetAddress = assets.get(tokenAddress.toString());
        Context.call(assetAddress, "mintAndTransfer", _to, _to, _amount, new byte[0]);
    }

    public void deposit(String from, String tokenAddress, String fromAddress, String toAddress, BigInteger _amount, byte[] _data) {
        NetworkAddress spokeAssetManager = NetworkAddress.valueOf(from);
        Context.require(from.equals(spokes.get(spokeAssetManager.net())), "Asset manager needs to be whitelisted");
        NetworkAddress spokeTokenAddress = new NetworkAddress(spokeAssetManager.net(), tokenAddress);
        Address assetAddress = assets.get(spokeTokenAddress.toString());
        Context.require(assetAddress != null, "Token is not yet deployed");
        NetworkAddress fromNetworkAddress = new NetworkAddress(spokeAssetManager.net(), fromAddress);
        if (toAddress.isEmpty()) {
            toAddress = fromNetworkAddress.toString();
        } else {
            NetworkAddress.valueOf(toAddress);
        }

        Context.call(assetAddress, "mintAndTransfer", fromNetworkAddress.toString(), toAddress, _amount, _data);
    }

    public void xWithdraw(String from, Address tokenAddress, BigInteger amount) {
        NetworkAddress _from = NetworkAddress.valueOf(from);
        BigInteger xCallFee = Context.call(BigInteger.class, BalancedAddressManager.getDaofund(), "claimXCallFee", _from.net(), true);
        _withdrawTo(tokenAddress, from, from, amount, xCallFee);
    }

    private void _withdrawTo(Address asset, String from, String to, BigInteger amount, BigInteger fee) {
        checkStatus();
        Context.call(asset, "burnFrom", from, amount);
        NetworkAddress tokenAddress = NetworkAddress.valueOf(assetNativeAddress.get(asset));
        NetworkAddress targetAddress = NetworkAddress.valueOf(to);
        Context.require(targetAddress.net().equals(tokenAddress.net()), "Wrong network");
        NetworkAddress spoke = NetworkAddress.valueOf(spokes.get(tokenAddress.net()));
        byte[] msg = SpokeAssetManagerMessages.WithdrawTo(tokenAddress.account(), targetAddress.account(), amount);
        byte[] rollback = AssetManagerMessages.withdrawRollback(tokenAddress.toString(), to, amount);
        XCallUtils.sendCall(fee, spoke, msg, rollback);
    }

    public static Address getSystemScoreAddress() {
        byte[] rawAddress = new byte[Address.LENGTH];
        rawAddress[0] = 1;
        return new Address(rawAddress);
    }
}
