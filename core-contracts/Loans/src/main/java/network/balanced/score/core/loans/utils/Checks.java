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

package network.balanced.score.core.loans.utils;

import score.Address;
import score.Context;

import network.balanced.score.core.loans.LoansImpl;

public class Checks {

    public static final Address defaultAddress = new Address(new byte[Address.LENGTH]);

    public static void onlyGovernance() {
        Address governance = LoansImpl.governance.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!governance.equals(defaultAddress), "Loans: Governance address not set");
        Context.require(sender.equals(governance), "Loans: Sender not governance contract");
    }

    public static void onlyRewards() {
        Address rewards = LoansImpl.rewards.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!rewards.equals(defaultAddress), "Loans: Rewards address not set");
        Context.require(sender.equals(rewards), "Loans: Sender not rewards contract");
    }

    public static void onlyRebalance() {
        Address rebalance = LoansImpl.rebalancing.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!rebalance.equals(defaultAddress), "Loans: Rebalance address not set");
        Context.require(sender.equals(rebalance), "Loans: Sender not rebalance contract");
    }

    public static void onlyAdmin() {
        Address admin = LoansImpl.admin.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!admin.equals(defaultAddress), "Loans: Admin address not set");
        Context.require(sender.equals(admin), "Loans: Sender not admin");
    }

    public static void loansOn() {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
    }
}