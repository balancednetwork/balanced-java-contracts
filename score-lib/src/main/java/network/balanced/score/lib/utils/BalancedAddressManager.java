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
import score.DictDB;

import static network.balanced.score.lib.utils.Check.readonly;
public class BalancedAddressManager {
    private static final String TAG = "BalancedAddressManager";
    private static final Address mainnetGovernance = Address.fromString("cx44250a12074799e26fdeee75648ae47e2cc84219");
    public static final DictDB<String, Address> contractAddresses = Context.newDictDB(TAG + "ContractAddresses",
            Address.class);

    public static void setGovernance(Address address) {
        contractAddresses.set(Names.GOVERNANCE, address);
    }

    public static Address getAddressByName(String name) {
        return contractAddresses.get(name);
    }

    public static void resetAddress(String name) {
        Address address = fetchAddress(name);
        contractAddresses.set(name, address);
    }

    public static Address fetchAddress(String name) {
        return Context.call(Address.class, contractAddresses.getOrDefault(Names.GOVERNANCE, mainnetGovernance),
                "getAddress", name);
    }

    private static Address getAddress(String name) {
        Address address = contractAddresses.get(name);
        if (address == null) {
            address = fetchAddress(name);
            if (!readonly()) {
                contractAddresses.set(name, address);
            }
        }

        return address;
    }

    public static Address getBaln() {
        return getAddress(Names.BALN);
    }

    public static Address getBnusd() {
        return getAddress(Names.BNUSD);
    }

    public static Address getBwt() {
        return getAddress(Names.WORKERTOKEN);
    }

    public static Address getDaofund() {
        return getAddress(Names.DAOFUND);
    }

    public static Address getStaking() {
        return getAddress(Names.STAKING);
    }

    public static Address getStakedLp() {
        return getAddress(Names.STAKEDLP);
    }

    public static Address getStabilityFund() {
        return getAddress(Names.STABILITY);
    }

    public static Address getSicx() {
        return getAddress(Names.SICX);
    }

    public static Address getRewards() {
        return getAddress(Names.REWARDS);
    }

    public static Address getReserve() {
        return getAddress(Names.RESERVE);
    }

    public static Address getRebalance() {
        return getAddress(Names.REBALANCING);
    }

    public static Address getOracle() {
        return getAddress(Names.ORACLE);
    }

    public static Address getBalancedOracle() {
        return getAddress(Names.BALANCEDORACLE);
    }

    public static Address getLoans() {
        return getAddress(Names.LOANS);
    }

    public static Address getFeehandler() {
        return getAddress(Names.FEEHANDLER);
    }

    public static Address getDividends() {
        return getAddress(Names.DIVIDENDS);
    }

    public static Address getDex() {
        return getAddress(Names.DEX);
    }

    public static Address getBoostedBaln() {
        return getAddress(Names.BOOSTED_BALN);
    }

    public static Address getRouter() {
        return getAddress(Names.ROUTER);
    }

    public static Address getGovernance() {
        return contractAddresses.getOrDefault(Names.GOVERNANCE, mainnetGovernance);
    }
}