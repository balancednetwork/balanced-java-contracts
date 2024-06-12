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

package network.balanced.score.spoke.xcall.manager;

import network.balanced.score.lib.interfaces.SpokeXCallManager;
import network.balanced.score.lib.interfaces.SpokeXCallManagerXCall;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.structs.ProtocolConfig;
import network.balanced.score.lib.utils.ArbitraryCallManager;

import score.Context;
import score.ObjectReader;
import score.Address;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.util.Map;

import static network.balanced.score.lib.utils.Check.onlyOwnerOrContract;
import static network.balanced.score.lib.utils.Check.only;

public class SpokeXCallManagerImpl implements SpokeXCallManager {

    public static final String VERSION = "version";
    public static final String XCALL = "xcall";
    public static final String XCALL_NETWORK_ADDRESS = "xcall_network_address";
    public static final String ICON_GOVERNANCE = "icon_governance";
    public static final String XCALL_MANAGER = "xcall_manager";
    public static final String PROTOCOL_CONFIG = "protocol_config";
    public static final String PROPOSED_REMOVAL = "proposed_removal";
    public static final String ADMIN = "admin";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    private final VarDB<Address> xCall = Context.newVarDB(XCALL, Address.class);
    private final VarDB<String> xCallNetworkAddress = Context.newVarDB(XCALL_NETWORK_ADDRESS, String.class);
    private final VarDB<String> iconGovernance = Context.newVarDB(ICON_GOVERNANCE, String.class);
    private final VarDB<ProtocolConfig> protocolConfig = Context.newVarDB(PROTOCOL_CONFIG, ProtocolConfig.class);
    private final VarDB<String> proposedRemoval = Context.newVarDB(PROPOSED_REMOVAL, String.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    public static final String TAG = Names.SPOKE_XCALL_MANAGER;

    public SpokeXCallManagerImpl(Address _xCall, String _iconGovernance, ProtocolConfig _protocolConfig) {
        if (currentVersion.get() == null) {
            xCall.set(_xCall);
            xCallNetworkAddress.set(Context.call(String.class, _xCall, "getNetworkAddress"));
            iconGovernance.set(_iconGovernance);
            protocolConfig.set(_protocolConfig);
            admin.set(Context.getCaller());
        }

        if (this.currentVersion.getOrDefault("").equals(Versions.SPOKE_XCALL_MANAGER)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.SPOKE_XCALL_MANAGER);
    }

    @External(readonly = true)
    public String name() {
        return Names.SPOKE_XCALL_MANAGER;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    public void execute(String from, String transactions) {
        ArbitraryCallManager.executeTransactions(transactions);
    }

    public void configureProtocols(String from, String[] sources, String[] destinations) {
        protocolConfig.set(new ProtocolConfig(sources, destinations));
        proposedRemoval.set(null);
    }

    @External(readonly = true)
    public Map<String, String[]> getProtocols() {
        ProtocolConfig cfg = protocolConfig.get();
        Context.require(cfg != null, TAG + ": Network is not configured");

        return Map.of("sources", cfg.sources, "destinations", cfg.destinations);
    }

    @External(readonly = true)
    public void verifyProtocols(String[] protocols) {
        Context.require(_verifyProtocols(protocols), "Invalid protocols used to deliver message");
    }

    public boolean _verifyProtocols(String[] protocols) {
        ProtocolConfig cfg = protocolConfig.get();
        if (cfg.sources.length == 0) {
            return protocols == null || protocols.length == 0;
        }

        for (String source : cfg.sources) {
            if (!hasSource(source, protocols)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasSource(String source, String[] protocols) {
        for (String protocol : protocols) {
            if (protocol.equals(source)) {
                return true;
            }
        }

        return false;
    }

    @External
    public void proposeRemoval(String address) {
        only(admin);
        proposedRemoval.set(address);
    }

    @External(readonly = true)
    public String getProposedRemoval() {
        return proposedRemoval.get();
    }

    @External
    public void setAdmin(Address _admin) {
        only(admin);
        admin.set(_admin);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setXCall(Address address) {
        onlyOwnerOrContract();
        xCall.set(address);
    }

    @External(readonly = true)
    public Address getXCall() {
        return xCall.get();
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        only(xCall.get());
        Context.require(_from.equals(iconGovernance.get()), "Only ICON Governance");
        if (!_verifyProtocols(_protocols)) {
            ObjectReader reader = Context.newByteArrayObjectReader("RLPn", _data);
            reader.beginList();
            String method = reader.readString().toLowerCase();
            Context.require(method.equals("configureprotocols"), "Invalid protocols used to deliver message");
            Context.require(_verifyProtocolsWithProposal(_protocols), "Invalid protocols used to deliver message");
        }

        SpokeXCallManagerXCall.process(this, _from, _data);
    }

    public boolean _verifyProtocolsWithProposal(String[] protocols) {
        String proposedRemoval = this.proposedRemoval.get();
        Context.require(proposedRemoval != null, "Invalid protocols used to deliver message");
        ProtocolConfig cfg = protocolConfig.get();
        if (cfg.sources.length == 1) {
            Context.require(cfg.sources[0].equals(proposedRemoval), "Invalid protocols used to deliver message");
            return protocols == null || protocols.length == 0;
        }

        for (String source : cfg.sources) {
            if (source.equals(proposedRemoval)) {
                continue;
            }

            if (!hasSource(source, protocols)) {
                return false;
            }
        }

        return true;
    }
}
