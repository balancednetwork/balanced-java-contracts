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

package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.VarDB;

public class Check {

    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void only(VarDB<Address> authorizedCaller) {
        Address caller = Context.getCaller();
        Address authorizedCallerAddress = authorizedCaller.get();
        Context.require(authorizedCallerAddress != null, "Authorization Check: Address not set");
        Context.require(caller.equals(authorizedCallerAddress),
                "Authorization Check: Authorization failed. Caller: " + caller + " Authorized Caller: " + authorizedCallerAddress);
    }

    public static void isContract(Address address) {
        Context.require(address.isContract(), "Address Check: Address provided is an EOA address. A contract address " +
                "is required.");
    }
} 