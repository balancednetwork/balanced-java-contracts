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

package network.balanced.score.lib.utils;

import network.balanced.score.lib.interfaces.base.Emergency;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.getGovernance;
import static network.balanced.score.lib.utils.Check.only;

public class BalancedEmergencyHandling implements Emergency {
    private static final String ENABLED = "BalancedStatus-balanced_contract_status";

    static Map<String, Boolean> blacklist = null;
    private static final VarDB<Boolean> enabled = Context.newVarDB(ENABLED, Boolean.class);

    @External
    public void enable() {
        only(getEmergencyManager());
        enabled.set(true);
    }

    @External
    public void disable() {
        only(getEmergencyManager());
        enabled.set(false);
    }

    @External
    public void updateBlacklist() {
        fetchBlacklist();
    }

    @External(readonly = true)
    public boolean isEnabled() {
        return enabled.getOrDefault(true);
    }

    @External(readonly = true)
    public Address getEmergencyManager() {
        return getGovernance();
    }

    public void checkStatus() {
        Address caller = Context.getCaller();
        checkStatus(caller.toString());
    }

    public void checkStatus(String address) {
        Context.require(isEnabled(), "Balanced is currently disabled");
        Context.require(!isBlacklisted(address), "This address is blacklisted");
        Context.require(!isBlacklisted(Context.getOrigin().toString()), "This origin caller is blacklisted");
    }

    @SuppressWarnings("unchecked")
    public void fetchBlacklist() {
        BalancedEmergencyHandling.blacklist = (Map<String, Boolean>) Context.call(getEmergencyManager(),
                "getBlacklist");
        if (BalancedEmergencyHandling.blacklist == null) {
            BalancedEmergencyHandling.blacklist = Map.of();
        }
    }

    public boolean isBlacklisted(String address) {
        if (blacklist == null) {
            try {
                fetchBlacklist();
            } catch (Exception e) {
                return false;
            }
        }

        Boolean blacklisted = blacklist.get(address);
        if (blacklisted == null) {
            return false;
        }

        return blacklisted;
    }
}