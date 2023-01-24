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

package network.balanced.score.core.governance.utils;

import java.util.Map;

import network.balanced.score.lib.utils.IterableDictDB;
import score.*;
import scorex.util.HashMap;

public class EmergencyManager {
    private static final IterableDictDB<Address, Boolean> authorizedCallersBlacklist = new IterableDictDB<> ("authorized_black_list_callers", Boolean.class, Address.class, false);
    private static final IterableDictDB<Address, Boolean> authorizedCallersShutdown = new IterableDictDB<> ("authorized_shutdown_callers", Boolean.class, Address.class, false);
    private static final IterableDictDB<String, Boolean> blacklist = new IterableDictDB<> ("balanced_black_list", Boolean.class, String.class, false);

    public static void addAuthorizedCallerBlacklist(Address address) {
        authorizedCallersBlacklist.set(address, true);
    }

    public static void removeAuthorizedCallerBlacklist(Address address) {
        authorizedCallersBlacklist.remove(address);
    }

    public static void addAuthorizedCallerShutdown(Address address) {
        authorizedCallersShutdown.set(address, true);
    }

    public static void removeAuthorizedCallerShutdown(Address address) {
        authorizedCallersShutdown.remove(address);
    }

    public static void disable() {
        setStatus("disable");
    }

    public static void enable() {
        setStatus("enable");
    }

    public static void blacklist(String address) {
        authorizeBlacklist();
        blacklist.set(address, true);
        updateBlacklist();
    }

    public static void removeBlacklist(String address) {
        authorizeBlacklist();
        blacklist.remove(address);
        updateBlacklist();
    }

    public static Map<String, Boolean> getBlacklist() {
        Map<String, Boolean>  blacklisted = new HashMap<>();
        for (String address : blacklist.keys()) {
            blacklisted.put(address, true);
        }

        return blacklisted;
    }

    public static void updateBlacklist() {
        int nrBalancedContracts = ContractManager.balancedContractNames.size();
        for (int i = 0; i < nrBalancedContracts; i++) {
            Address contract = ContractManager.contractAddresses.get(ContractManager.balancedContractNames.get(i));
            try {
                Context.call(contract, "updateBlacklist");
            } catch (Exception e) {}
        }
    }

    private static void setStatus(String status) {
        int nrBalancedContracts = ContractManager.balancedContractNames.size();
        for (int i = 0; i < nrBalancedContracts; i++) {
            Address contract = ContractManager.contractAddresses.get(ContractManager.balancedContractNames.get(i));
            try {
                Context.call(contract, status);
            } catch (Exception e) {}
        }
    }

    private static boolean isOwnerOrContract() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Address contract = Context.getAddress();
        return caller.equals(owner) || caller.equals(contract);
    }

    public static void authorizeEnableAndDisable() {
        if (isOwnerOrContract()) {
            return;
        }

        Address caller = Context.getCaller();
        Context.require(authorizedCallersShutdown.getOrDefault(caller, false), "Not authorized");
        authorizedCallersShutdown.remove(caller);
    }

    public static void authorizeBlacklist() {
        if (isOwnerOrContract()) {
            return;
        }

        Context.require(authorizedCallersBlacklist.getOrDefault(Context.getCaller(), false), "Not authorized");
    }

}
