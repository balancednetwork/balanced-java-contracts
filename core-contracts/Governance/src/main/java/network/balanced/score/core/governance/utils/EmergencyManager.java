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

package network.balanced.score.core.governance.utils;

import network.balanced.score.lib.utils.IterableDictDB;
import score.Address;
import score.Context;
import score.VarDB;
import score.DictDB;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

public class EmergencyManager {
    private static final IterableDictDB<Address, BigInteger> authorizedCallersShutdown = new IterableDictDB<>(
            "authorized_shutdown_callers", BigInteger.class, Address.class, false);
    private static final DictDB<Address, Boolean> disableOnly = Context.newDictDB("disable_only", Boolean.class);
    private static final IterableDictDB<String, Boolean> blacklist = new IterableDictDB<>("balanced_black_list",
            Boolean.class, String.class, false);
    private static final VarDB<BigInteger> enableDisableTimeLock = Context.newVarDB("enable_disable_time_lock",
            BigInteger.class);
    private static final VarDB<Boolean> enabled = Context.newVarDB("balanced_status", Boolean.class);


    public static void addAuthorizedCallerShutdown(Address address, boolean onlyDisable) {
        authorizedCallersShutdown.set(address, BigInteger.ZERO);
        disableOnly.set(address, onlyDisable);
    }

    public static void removeAuthorizedCallerShutdown(Address address) {
        authorizedCallersShutdown.remove(address);
        disableOnly.set(address, null);
    }

    public static boolean canOnlyDisable(Address address) {
        return disableOnly.getOrDefault(address, false);
    }

    public static Map<String, BigInteger> getShutdownCallers() {
        Map<String, BigInteger>  shutdownCallers = new HashMap<>();
        for (Address address : authorizedCallersShutdown.keys()) {
            shutdownCallers.put(address.toString(), authorizedCallersShutdown.getOrDefault(address, BigInteger.ZERO));
        }

        return shutdownCallers;
    }

    public static void setShutdownPrivilegeTimeLock(BigInteger days) {
        Context.require(BigInteger.ONE.compareTo(days) <= 0, "Invalid time lock, it must be between 1 and 30 days");
        Context.require(BigInteger.valueOf(30).compareTo(days) >= 0, "Invalid time lock, it must be between 1 and 30 " +
                "days");
        enableDisableTimeLock.set(days.multiply(MICRO_SECONDS_IN_A_DAY));
    }

    public static BigInteger getShutdownPrivilegeTimeLock() {
        return enableDisableTimeLock.get().divide(MICRO_SECONDS_IN_A_DAY);
    }

    public static void disable() {
        enabled.set(false);
    }

    public static void enable() {
        enabled.set(true);
    }

    public static boolean getStatus() {
        return enabled.getOrDefault(true);
    }

    public static void blacklist(String address) {
        blacklist.set(address, true);
    }

    public static void removeBlacklist(String address) {
        blacklist.remove(address);
    }

    public static Map<String, Boolean> getBlacklist() {
        Map<String, Boolean> blacklisted = new HashMap<>();
        for (String address : blacklist.keys()) {
            blacklisted.put(address, true);
        }

        return blacklisted;
    }

    public static boolean isBlacklisted(String address) {
        return blacklist.getOrDefault(address, false);
    }

    public static void checkStatus(String address) {
        Context.require(enabled.getOrDefault(true), "Balanced is currently disabled");
        Context.require(!isBlacklisted(address), "This address is blacklisted");
        Context.require(!isBlacklisted(Context.getOrigin().toString()), "This origin caller is blacklisted");
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
        BigInteger nextCallTime = authorizedCallersShutdown.get(caller);
        Context.require(nextCallTime != null, "Not authorized");
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        Context.require(nextCallTime.compareTo(blockTime) < 0, "Your privileges are disabled until " + nextCallTime);

        authorizedCallersShutdown.set(caller, blockTime.add(enableDisableTimeLock.get()));
    }
}
