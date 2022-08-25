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

import network.balanced.score.core.governance.GovernanceImpl;
import network.balanced.score.lib.structs.BalancedAddresses;
import score.Address;
import score.Context;
import score.VarDB;
import scorex.util.HashMap;

import static network.balanced.score.core.governance.utils.GovernanceConstants.*;

import java.util.Map;

public class ContractManager {
    public static final VarDB<Address> loans = Context.newVarDB("loans", Address.class);
    public static final VarDB<Address> dex = Context.newVarDB("dex", Address.class);
    public static final VarDB<Address> staking = Context.newVarDB("staking", Address.class);
    public static final VarDB<Address> rewards = Context.newVarDB("rewards", Address.class);
    public static final VarDB<Address> reserve = Context.newVarDB("reserve", Address.class);
    public static final VarDB<Address> dividends = Context.newVarDB("dividends", Address.class);
    public static final VarDB<Address> daofund = Context.newVarDB("daofund", Address.class);
    public static final VarDB<Address> oracle = Context.newVarDB("oracle", Address.class);
    public static final VarDB<Address> sicx = Context.newVarDB("sicx", Address.class);
    public static final VarDB<Address> bnUSD = Context.newVarDB("bnUSD", Address.class);
    public static final VarDB<Address> baln = Context.newVarDB("baln", Address.class);
    public static final VarDB<Address> bwt = Context.newVarDB("bwt", Address.class);
    public static final VarDB<Address> rebalancing = Context.newVarDB("rebalancing", Address.class);
    public static final VarDB<Address> router = Context.newVarDB("router", Address.class);
    public static final VarDB<Address> feehandler = Context.newVarDB("feehandler", Address.class);
    public static final VarDB<Address> stakedLp = Context.newVarDB("stakedLp", Address.class);
    public static final VarDB<Address> balancedOracle = Context.newVarDB("balancedOracle", Address.class);
    
    private ContractManager() {}

    public static Address get(String key) {
        if (key.equals("governance")) {
            return Context.getAddress();
        }

        return Context.newVarDB(key, Address.class).get();
    }

    public static void setAddresses(BalancedAddresses addresses) {
        loans.set(addresses.loans);
        dex.set(addresses.dex);
        staking.set(addresses.staking);
        rewards.set(addresses.rewards);
        reserve.set(addresses.reserve);
        dividends.set(addresses.dividends);
        daofund.set(addresses.daofund);
        oracle.set(addresses.oracle);
        sicx.set(addresses.sicx);
        bnUSD.set(addresses.bnUSD);
        baln.set(addresses.baln);
        bwt.set(addresses.bwt);
        router.set(addresses.router);
        rebalancing.set(addresses.rebalancing);
        feehandler.set(addresses.feehandler);
        stakedLp.set(addresses.stakedLp);
        balancedOracle.set(addresses.balancedOracle);
    }

    public static Map<String, Address> getAddresses() {
        Map<String, Address> addressData = new HashMap<>();
        addressData.put("loans", loans.get());
        addressData.put("dex", dex.get());
        addressData.put("staking", staking.get());
        addressData.put("rewards", rewards.get());
        addressData.put("reserve", reserve.get());
        addressData.put("dividends", dividends.get());
        addressData.put("daofund", daofund.get());
        addressData.put("oracle", oracle.get());
        addressData.put("sicx", sicx.get());
        addressData.put("bnUSD", bnUSD.get());
        addressData.put("baln", baln.get());
        addressData.put("bwt", bwt.get());
        addressData.put("rebalancing", rebalancing.get());
        addressData.put("router", router.get());
        addressData.put("feehandler", feehandler.get());
        addressData.put("stakedLp", stakedLp.get());
        addressData.put("balancedOracle", balancedOracle.get());

        return addressData;
    }

    public static void setAddress(String contract) {
        Context.require(ADDRESSES.containsKey(contract), contract + " is not defined in the address list");
        for (String contractToBeSet : ADDRESSES.get(contract)) {
            String setMethod = SETTERS.get(contractToBeSet);
            try {
                GovernanceImpl.call(get(contract), setMethod, get(contractToBeSet));
            } catch (Exception e) {
                // to make migration/testing easier

                if (contractToBeSet.equals("bnUSD")) {
                    try {
                        GovernanceImpl.call(get(contract), "setbnUSD", get(contractToBeSet));
                    } catch (Exception ignored) {

                    }
                }
            }
        }
    }

    public static void setContractAddresses() {
        for (String targetContract : ADDRESSES.keySet()) {
            for (String contract : ADDRESSES.get(targetContract)) {
                String setMethod = SETTERS.get(contract);
                try {
                    GovernanceImpl.call(get(targetContract), setMethod, get(contract));
                } catch (Exception e) {
                    // to make migration/testing easier
                    if (contract.equals("bnUSD")) {
                        try {
                            GovernanceImpl.call(get(targetContract), "setbnUSD", get(contract));
                        } catch (Exception ignored) {

                        }
                    }
                }
            }

        }
    }

    public static void setAdmins() {
        for (String targetContract : ADMIN_ADDRESSES.keySet()) {
            String contract = ADMIN_ADDRESSES.get(targetContract);
            GovernanceImpl.call(get(targetContract), "setAdmin", get(contract));
        }
    }
}
