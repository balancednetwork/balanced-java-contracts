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

import score.Address;
import score.Context;
import score.VarDB;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.BalancedAddressManager.getGovernance;

public class Check {

    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void checkStatus() {
        checkStatus(getGovernance());
    }

    public static void checkStatus(VarDB<Address> address) {
        Address handler = address.get();
        if (handler == null) {
            return;
        }

        checkStatus(handler);
    }

    public static void checkStatus(Address handler) {
        String caller = Context.getCaller().toString();
        Context.call(handler, "checkStatus", caller);
    }

    public static void onlyGovernance() {
        Address governance = getGovernance();
        only(governance);
    }

    public static void onlyOwnerOrContract() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Address contract = Context.getAddress();
        Context.require(caller.equals(owner) || caller.equals(contract),
                "SenderNotScoreOwnerOrContract: Sender=" + caller + " Owner=" + owner + " Contract=" + contract);
    }

    public static void only(VarDB<Address> authorizedCaller) {
        only(authorizedCaller.get());
    }

    public static void only(Address authorizedCallerAddress) {
        Address caller = Context.getCaller();
        Context.require(authorizedCallerAddress != null, "Authorization Check: Address not set");
        Context.require(caller.equals(authorizedCallerAddress),
                "Authorization Check: Authorization failed. Caller: " + caller + " Authorized Caller: " + authorizedCallerAddress);
    }

    public static void onlyEither(VarDB<Address> authorizedCaller, VarDB<Address> authorizedCaller2) {
        Address caller = Context.getCaller();
        Address authorizedCallerAddress = authorizedCaller.get();
        Address authorizedCaller2Address = authorizedCaller2.get();
        Context.require(authorizedCallerAddress != null ||
                        authorizedCaller2Address != null,
                "Authorization Check: Address not set");
        Context.require(caller.equals(authorizedCallerAddress) ||
                        caller.equals(authorizedCaller2Address),
                "Authorization Check: Authorization failed. Caller: " + caller + " Authorized Caller: " + authorizedCallerAddress + " or " + authorizedCaller2Address);
    }

    public static void isContract(Address address) {
        Context.require(address.isContract(), "Address Check: Address provided is an EOA address. A contract address " +
                "is required.");
    }

    public static BigInteger optionalDefault(BigInteger value, BigInteger base) {
        if (value.equals(BigInteger.ZERO)) {
            return base;
        }

        return value;
    }

    public static Address optionalDefault(Address value, Address base) {
        if (value == null) {
            return base;
        }

        return value;
    }

    public static String optionalDefault(String value, String base) {
        if (value == null) {
            return base;
        }

        return value;
    }

    /**
     * Note:
     * This method does not work for non readonly interscore calls to readonly methods.
     * In this case there will be a transactions hash but the interscore call will still be readonly.
     * If anything is written Access denied error will be raised. Both variables and databases.
     * @return Whether a call is readonly or not.
     */
    public static boolean readonly() {
        return Context.getTransactionHash() == null;
    }
}
