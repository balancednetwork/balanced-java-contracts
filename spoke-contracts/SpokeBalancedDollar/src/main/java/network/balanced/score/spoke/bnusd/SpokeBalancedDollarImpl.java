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

package network.balanced.score.spoke.bnusd;

import network.balanced.score.lib.interfaces.SpokeBalancedDollarMessages;
import network.balanced.score.lib.interfaces.SpokeBalancedDollarXCall;
import network.balanced.score.lib.interfaces.SpokeBalancedDollar;
import network.balanced.score.lib.tokens.IRC2Base;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.*;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;

public class SpokeBalancedDollarImpl extends IRC2Base implements SpokeBalancedDollar {

    public static final String VERSION = "version";
    public static final String XCALL = "xcall";
    public static final String XCALL_NETWORK_ADDRESS = "xcall_network_address";
    public static final String NETWORK_ID = "network_id";
    public static final String ICON_BNUSD = "icon_bnusd";
    public static final String XCALL_MANAGER = "xcall_manager";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    private final VarDB<Address> xCall = Context.newVarDB(XCALL, Address.class);
    private final VarDB<String> xCallNetworkAddress = Context.newVarDB(XCALL_NETWORK_ADDRESS, String.class);
    private final VarDB<String> nid = Context.newVarDB(NETWORK_ID, String.class);
    private final VarDB<String> iconBnUSD = Context.newVarDB(ICON_BNUSD, String.class);
    private final VarDB<Address> xCallManager = Context.newVarDB(XCALL_MANAGER, Address.class);

    private static final String SYMBOL_NAME = "bnUSD";

    public SpokeBalancedDollarImpl(Address _xCall, String _iconBnUSD, Address _xCallManager) {
        super(Names.BNUSD, SYMBOL_NAME, null);
        if (currentVersion.get() == null) {
            xCall.set(_xCall);
            NetworkAddress _xCallNetworkAddress = NetworkAddress.valueOf(Context.call(String.class, _xCall, "getNetworkAddress"));
            xCallNetworkAddress.set(_xCallNetworkAddress.toString());
            nid.set(_xCallNetworkAddress.net());
            iconBnUSD.set(_iconBnUSD);
            xCallManager.set(_xCallManager);
        } else {
            NetworkAddress _xCallNetworkAddress = NetworkAddress.valueOf(Context.call(String.class, xCall.get(), "getNetworkAddress"));
            xCallNetworkAddress.set(_xCallNetworkAddress.toString());
            nid.set(_xCallNetworkAddress.net());
        }

        if (this.currentVersion.getOrDefault("").equals(Versions.SPOKE_BNUSD)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.SPOKE_BNUSD);

    }

    @External(readonly = true)
    public String name() {
        return Names.BNUSD;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
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
    public void setICONBnUSD(String address) {
        onlyOwner();
        iconBnUSD.set(address);
    }

    @External(readonly = true)
    public String getICONBnUSD() {
        return iconBnUSD.get();
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
    public void crossTransfer(String _to, BigInteger _value, @Optional byte[] _data) {
        burn(Context.getCaller(), _value);
        if (_data == null) {
            _data = new byte[0];
        }

        Map<String, String[]> protocols = getProtocols();
        String from = new NetworkAddress(nid.get(), Context.getCaller()).toString();
        byte[] transferMsg =  SpokeBalancedDollarMessages.xCrossTransfer(from, _to, _value, _data);
        byte[] revertMsg = SpokeBalancedDollarMessages.xCrossTransferRevert(Context.getCaller(), _value);

        Context.call(Context.getValue(), xCall.get(), "sendCallMessage", iconBnUSD.get(), transferMsg, revertMsg, protocols.get("sources"), protocols.get("destinations"));
    }

    public void xCrossTransfer(String from, String _from, String _to, BigInteger _value, byte[] _data) {
        Context.require(from.equals(iconBnUSD.get()), "Only ICON Balanced dollar");
        super.mint(Address.fromString(NetworkAddress.valueOf(_to).account()), _value);
    }

    public void xCrossTransferRevert(String from, Address _to, BigInteger _value) {
        Context.require(from.equals(xCallNetworkAddress.get()), "Only XCall");
        super.mint(_to, _value);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        only(xCall.get());
        Context.call(xCallManager.get(), "verifyProtocols", (Object)_protocols);
        SpokeBalancedDollarXCall.process(this, _from, _data);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String[]> getProtocols() {
        return (Map<String, String[]>) Context.call(xCallManager.get(), "getProtocols");
    }
}
