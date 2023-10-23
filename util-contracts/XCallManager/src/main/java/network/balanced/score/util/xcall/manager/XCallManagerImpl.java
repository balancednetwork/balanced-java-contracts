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

package network.balanced.score.util.xcall.manager;

import network.balanced.score.lib.interfaces.XCallManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.structs.ProtocolConfig;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.setGovernance;
import static network.balanced.score.lib.utils.BalancedAddressManager.resetAddress;
import static network.balanced.score.lib.utils.BalancedAddressManager.getAddressByName;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.checkStatus;

public class XCallManagerImpl implements XCallManager {
    public static final String VERSION = "version";
    public static final String PROTOCOLS = "protocols";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    DictDB<String, ProtocolConfig> protocols = Context.newDictDB(PROTOCOLS, ProtocolConfig.class);

    public static final String TAG = Names.XCALL_MANAGER;

    public XCallManagerImpl(Address _governance) {
        if (this.currentVersion.get() == null) {
            setGovernance(_governance);
        }

        if (this.currentVersion.getOrDefault("").equals(Versions.XCALL_MANAGER)) {
            Context.revert("Can't Update same version of code");
        }

        this.currentVersion.set(Versions.XCALL_MANAGER);
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External
    public void configureProtocols(String nid, String[] sources, String destinations[]) {
        onlyOwner();
        ProtocolConfig cfg = new ProtocolConfig(sources, destinations);
        protocols.set(nid, cfg);
    }

    @External(readonly = true)
    public Map<String, String[]> getProtocols(String nid) {
        ProtocolConfig cfg = protocols.get(nid);
        Context.require(cfg != null, TAG + ": Network is not configured");

        return Map.of("sources", cfg.sources, "destinations", cfg.destinations);
    }

    @External(readonly = true)
    public void verifyProtocols(String nid, @Optional String[] protocols) {
        ProtocolConfig cfg = this.protocols.get(nid);
        Context.require(cfg != null, TAG + ": Network is not configured");
        if (cfg.sources.length == 0) {
            Context.require(protocols == null || protocols.length == 0, "Invalid protocols used to deliver message");
            return;
        }

        for (String source : cfg.sources) {
            Context.require(hasSource(source, protocols), TAG + "Invalid protocols used to deliver message");
        }
    }

    private boolean hasSource(String source, String[] protocols) {
        for (String protocol : protocols) {
            if (protocol.equals(source)) {
                return true;
            }
        }

        return false;
    }
}
