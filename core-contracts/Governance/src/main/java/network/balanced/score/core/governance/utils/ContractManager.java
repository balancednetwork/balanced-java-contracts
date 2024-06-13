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
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.ArbitraryCallManager;
import score.*;
import scorex.util.HashMap;

import java.util.Arrays;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceImpl.call;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;

public class ContractManager {
    public static final DictDB<String, byte[]> contractHashes = Context.newDictDB("CONTRACT_DEPLOYMENT_HASHES", byte[].class);
    public static final DictDB<byte[], String> contractParams = Context.newDictDB("CONTRACT_DEPLOYMENT_PARAMS", String.class);

    private static final VarDB<Address> loans = Context.newVarDB("loans", Address.class);
    private static final VarDB<Address> dex = Context.newVarDB("dex", Address.class);
    private static final VarDB<Address> staking = Context.newVarDB("staking", Address.class);
    private static final VarDB<Address> rewards = Context.newVarDB("rewards", Address.class);
    private static final VarDB<Address> reserve = Context.newVarDB("reserve", Address.class);
    private static final VarDB<Address> dividends = Context.newVarDB("dividends", Address.class);
    private static final VarDB<Address> daofund = Context.newVarDB("daofund", Address.class);
    private static final VarDB<Address> oracle = Context.newVarDB("oracle", Address.class);
    private static final VarDB<Address> sicx = Context.newVarDB("sicx", Address.class);
    private static final VarDB<Address> bnUSD = Context.newVarDB("bnUSD", Address.class);
    private static final VarDB<Address> baln = Context.newVarDB("baln", Address.class);
    private static final VarDB<Address> bwt = Context.newVarDB("bwt", Address.class);
    private static final VarDB<Address> rebalancing = Context.newVarDB("rebalancing", Address.class);
    private static final VarDB<Address> router = Context.newVarDB("router", Address.class);
    private static final VarDB<Address> feehandler = Context.newVarDB("feehandler", Address.class);
    private static final VarDB<Address> stakedLp = Context.newVarDB("stakedLp", Address.class);
    private static final VarDB<Address> bBaln = Context.newVarDB("bBaln", Address.class);
    private static final VarDB<Address> balancedOracle = Context.newVarDB("balancedOracle", Address.class);

    public static final DictDB<String, Address> contractAddresses = Context.newDictDB("BalancedContractAddresses",
            Address.class);
    public static final ArrayDB<String> balancedContractNames = Context.newArrayDB("BalancedContractNames",
            String.class);

    private ContractManager() {
    }

    public static Address getAddress(String name) {
        if (name.equals(Names.GOVERNANCE)) {
            return Context.getAddress();
        }

        return contractAddresses.get(name);
    }

    public static Address get(String key) {
        if (key.equals("governance")) {
            return Context.getAddress();
        }

        return getAddress(oldNamesMap.get(key));
    }

    public static Map<String, Address> getAddresses() {
        Map<String, Address> addressData = new HashMap<>();
        int numberOfContracts = balancedContractNames.size();
        for (int i = 0; i < numberOfContracts; i++) {
            String name = balancedContractNames.get(i);
            Address address = getAddress(name);
            addressData.put(name, address);
        }

        return addressData;
    }

    public static void setAddress(String contract) {
        Context.require(ADDRESSES.containsKey(contract), contract + " is not defined in the address list");
        for (String contractToBeSet : ADDRESSES.get(contract)) {
            String setMethod = SETTERS.get(contractToBeSet);
            try {
                GovernanceImpl.call(get(contract), setMethod, get(contractToBeSet));
            } catch (Exception e) {
                if (contractToBeSet.equals("bnUSD")) {
                    try {
                        GovernanceImpl.call(get(contract), "setbnUSD", get(contractToBeSet));
                    } catch (Exception e2) {

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
                    if (contract.equals("bnUSD")) {
                        try {
                            GovernanceImpl.call(get(targetContract), "setbnUSD", get(contract));
                        } catch (Exception e2) {

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

    public static void addContract(String name, Address address) {
        Context.require(contractAddresses.get(name) == null, "Contract already exists");
        balancedContractNames.add(name);
        contractAddresses.set(name, address);
    }

    public static void updateContract(Address targetContract, byte[] contractData, String params) {
        Object[] parsedParams = ArbitraryCallManager.getConvertedParameters(params);
        String name = getName(targetContract);
        Context.deploy(targetContract, contractData, parsedParams);
        Context.require(name.equals(getName(targetContract)), "Invalid contract upgrade");
    }

    public static String deployStoredContract(String name, byte[] contractData) {
        byte[] hash = Context.hash("keccak-256", contractData);
        byte[] storedHash = contractHashes.get(name);
        Context.require(Arrays.equals(storedHash, hash), "Hash mismatch");
        String params = contractParams.get(storedHash);
        contractHashes.set(name, null);
        contractParams.set(storedHash, null);
        try {
            updateContract(getAddress(name), contractData, params);
            return "";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static void storeContract(String name, byte[] dataHash, String params) {
        contractHashes.set(name, dataHash);
        contractParams.set(dataHash, params);
    }

    public static void newContract(byte[] contractData, String params) {
        Object[] parsedParams = ArbitraryCallManager.getConvertedParameters(params);
        Address contractAddress = Context.deploy(contractData, parsedParams);
        String name = getName(contractAddress);
        Context.require(contractAddresses.get(name) == null, "Contract already exists");
        balancedContractNames.add(name);
        contractAddresses.set(name, contractAddress);
    }

    private static String getName(Address address) {
        return call(String.class, address, "name");
    }
}
