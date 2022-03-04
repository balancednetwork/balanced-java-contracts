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

package network.balanced.score.core.daofund.utils;

import network.balanced.score.core.daofund.DAOfund;
import score.Address;
import score.Context;

public class Checks {

    public static Address defaultAddress = new Address(new byte[Address.LENGTH]);

    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void onlyGovernance() {
        Address governance = DAOfund.governance.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!governance.equals(defaultAddress), DAOfund.TAG + ": Governance address not set");
        Context.require(sender.equals(governance), DAOfund.TAG + ": Sender not governance contract");
    }

    public static void onlyAdmin() {
        Address admin = DAOfund.admin.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!admin.equals(defaultAddress), DAOfund.TAG + ": Admin address not set");
        Context.require(sender.equals(admin), DAOfund.TAG + ": Sender not admin");
    }
}
