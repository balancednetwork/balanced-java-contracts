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

package network.balanced.score.spoke.asset.manager;

import network.balanced.score.lib.interfaces.SpokeAssetManager;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.SpokeAssetManagerMessages;
import network.balanced.score.lib.interfaces.SpokeAssetManagerXCall;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.FloorLimited;
import network.balanced.score.lib.utils.Versions;
import score.*;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;


import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.BalancedFloorLimits.verifyNativeWithdraw;
import static network.balanced.score.lib.utils.BalancedFloorLimits.verifyWithdraw;

public class SpokeAssetManagerImpl extends FloorLimited implements SpokeAssetManager {

    public static final String VERSION = "version";
    public static final String XCALL = "xcall";
    public static final String XCALL_NETWORK_ADDRESS = "xcall_network_address";
    public static final String ICON_ASSET_MANAGER = "icon_asset_manager";
    public static final String XCALL_MANAGER = "xcall_manager";


    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    private final VarDB<Address> xCall = Context.newVarDB(XCALL, Address.class);
    private final VarDB<String> xCallNetworkAddress = Context.newVarDB(XCALL_NETWORK_ADDRESS, String.class);
    private final VarDB<String> iconAssetManager = Context.newVarDB(ICON_ASSET_MANAGER, String.class);
    private final VarDB<Address> xCallManager = Context.newVarDB(XCALL_MANAGER, Address.class);

    public static final String TAG = Names.SPOKE_ASSET_MANAGER;

    public SpokeAssetManagerImpl(Address _xCall, String _iconAssetManager, Address _xCallManager) {
        if (currentVersion.get() == null) {
            xCall.set(_xCall);
            xCallNetworkAddress.set(Context.call(String.class, _xCall, "getNetworkAddress"));
            iconAssetManager.set(_iconAssetManager);
            xCallManager.set(_xCallManager);
        }

        if (this.currentVersion.getOrDefault("").equals(Versions.SPOKE_ASSET_MANAGER)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.SPOKE_ASSET_MANAGER);

    }

    @External(readonly = true)
    public String name() {
        return Names.SPOKE_ASSET_MANAGER;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    public void WithdrawTo(String from, String tokenAddress, String toAddress, BigInteger amount) {
        Context.require(from.equals(iconAssetManager.get()), "Only ICON Asset Manager");
        withdraw(Address.fromString(tokenAddress), Address.fromString(toAddress), amount);
    }

    public void WithdrawNativeTo(String from, String tokenAddress, String toAddress, BigInteger amount) {
        Context.require(from.equals(iconAssetManager.get()), "Only ICON Asset Manager");
        withdraw(Address.fromString(tokenAddress), Address.fromString(toAddress), amount);
    }

    public void DepositRevert(String from, Address token, Address to, BigInteger amount) {
        Context.require(from.equals(xCallNetworkAddress.get()), "Only XCall");
        withdraw(token, to, amount);
    }

    private void withdraw(Address token, Address to, BigInteger amount) {
        if (token.equals(EOA_ZERO)) {
            verifyNativeWithdraw(amount);
            Context.transfer(to, amount);
        } else {
            verifyWithdraw(token, amount);
            Context.call(to, "transfer", to, amount);
        }
    }

    @External
    public void setXCallManager(Address address) {
        onlyOwner();
        xCallManager.set(address);
    }

    @External(readonly = true)
    public Address getXCallManager() {
        return xCallManager.get();
    }

    @External
    public void setICONAssetManager(String address) {
        onlyOwner();
        iconAssetManager.set(address);
    }

    @External(readonly = true)
    public String getICONAssetManager() {
        return iconAssetManager.get();
    }

    @External
    public void setXCall(Address address) {
        onlyOwner();
        xCall.set(address);
    }

    @External(readonly = true)
    public Address getXCall() {
        return xCall.get();
    }

    @External
    @Payable
    public void deposit(@Optional String _to, @Optional byte[] _data) {
        if (_to == null) {
            _to = "";
        }

        if (_data == null) {
            _data = new byte[0];
        }
        _deposit(EOA_ZERO, Context.getCaller(), _to, BigInteger.ZERO, _data);
    }

    // No way to support token deposit due to no way to recv fees, but not needed for Havah initially.
    // @External
    // public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    //     String unpackedData = new String(_data);
    //     Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");
    //     JsonObject json = Json.parse(unpackedData).asObject();
    //     String to = json.getString("to", "");
    //     byte[] data = json.getString("data", "").getBytes();

    //     _deposit(Context.getCaller(), _from, to, _value, data);
    // }

    private void _deposit(Address token, Address from, String _to, BigInteger amount, byte[] _data) {
        NetworkAddress iconAssetManager = NetworkAddress.valueOf(this.iconAssetManager.get());
        Map<String, String[]> protocols = getProtocols();
        Address xCall = this.xCall.get();
        BigInteger fee = Context.getValue();
        if (isNative(token)) {
            fee  = Context.call(BigInteger.class, xCall, "getFee", iconAssetManager.net(), true, protocols.get("sources"));
            amount = Context.getValue().subtract(fee);
        }

        Context.require(amount.compareTo(BigInteger.ZERO) > 0, "amount must be larger than 0");
        byte[] depositMsg =  AssetManagerMessages.deposit(EOA_ZERO.toString(), from.toString(), _to, amount, _data);
        byte[] revertMsg = SpokeAssetManagerMessages.DepositRevert(EOA_ZERO, from, amount);

        Context.call(fee, xCall, "sendCallMessage", iconAssetManager.toString(), depositMsg, revertMsg, protocols.get("sources"), protocols.get("destinations"));
    }

    private boolean isNative(Address token) {
        return token.equals(EOA_ZERO);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        only(xCall.get());
        Context.call(xCallManager.get(), "verifyProtocols", (Object)_protocols);
        SpokeAssetManagerXCall.process(this, _from, _data);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String[]> getProtocols() {
        return (Map<String, String[]>) Context.call(xCallManager.get(), "getProtocols");
    }
}
