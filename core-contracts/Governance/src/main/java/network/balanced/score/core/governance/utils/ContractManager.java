/*
 * Copyright (c) 2022 Balanced.network.
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

import static network.balanced.score.core.governance.GovernanceImpl.call;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;

import java.util.Map;

import network.balanced.score.core.governance.GovernanceImpl;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.HashMap;

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

    public static final DictDB<String, Address> contractAddresses = Context.newDictDB("BalancedContractAddresses", Address.class);
    public static final ArrayDB<String> balancedContractNames = Context.newArrayDB("BalancedContractNames", String.class);
    
    private ContractManager() {}

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

    public static void migrateAddresses() {
        Address loansAddress = loans.get();
        String loansName = getName(loansAddress);
        balancedContractNames.add(loansName);
        contractAddresses.set(loansName, loansAddress);

        Address dexAddress = dex.get();
        String dexName = getName(dexAddress);
        balancedContractNames.add(dexName);
        contractAddresses.set(dexName, dexAddress);

        Address stakingAddress = staking.get();
        String stakingName = getName(stakingAddress);
        balancedContractNames.add(stakingName);
        contractAddresses.set(stakingName, stakingAddress);

        Address rewardsAddress = rewards.get();
        String rewardsName = getName(rewardsAddress);
        balancedContractNames.add(rewardsName);
        contractAddresses.set(rewardsName, rewardsAddress);

        Address reserveAddress = reserve.get();
        String reserveName = getName(reserveAddress);
        balancedContractNames.add(reserveName);
        contractAddresses.set(reserveName, reserveAddress);

        Address dividendsAddress = dividends.get();
        String dividendsName = getName(dividendsAddress);
        balancedContractNames.add(dividendsName);
        contractAddresses.set(dividendsName, dividendsAddress);

        Address daofundAddress = daofund.get();
        String daofundName = getName(daofundAddress);
        balancedContractNames.add(daofundName);
        contractAddresses.set(daofundName, daofundAddress);

        Address oracleAddress = oracle.get();
        String oracleName = Names.ORACLE;
        balancedContractNames.add(oracleName);
        contractAddresses.set(oracleName, oracleAddress);

        Address sicxAddress = sicx.get();
        String sicxName = getName(sicxAddress);
        balancedContractNames.add(sicxName);
        contractAddresses.set(sicxName, sicxAddress);

        Address bnUSDAddress = bnUSD.get();
        String bnUSDName = getName(bnUSDAddress);
        balancedContractNames.add(bnUSDName);
        contractAddresses.set(bnUSDName, bnUSDAddress);

        Address balnAddress = baln.get();
        String balnName = getName(balnAddress);
        balancedContractNames.add(balnName);
        contractAddresses.set(balnName, balnAddress);

        Address bwtAddress = bwt.get();
        String bwtName = getName(bwtAddress);
        balancedContractNames.add(bwtName);
        contractAddresses.set(bwtName, bwtAddress);

        Address rebalancingAddress = rebalancing.get();
        String rebalancingName = getName(rebalancingAddress);
        balancedContractNames.add(rebalancingName);
        contractAddresses.set(rebalancingName, rebalancingAddress);

        Address routerAddress = router.get();
        String routerName = getName(routerAddress);
        balancedContractNames.add(routerName);
        contractAddresses.set(routerName, routerAddress);

        Address feehandlerAddress = feehandler.get();
        String feehandlerName = getName(feehandlerAddress);
        balancedContractNames.add(feehandlerName);
        contractAddresses.set(feehandlerName, feehandlerAddress);

        Address stakedLpAddress = stakedLp.get();
        String stakedLpName = getName(stakedLpAddress);
        balancedContractNames.add(stakedLpName);
        contractAddresses.set(stakedLpName, stakedLpAddress);

        Address balancedOracleAddress = balancedOracle.get();
        String balancedOracleName = getName(balancedOracleAddress);
        balancedContractNames.add(balancedOracleName);
        contractAddresses.set(balancedOracleName, balancedOracleAddress);
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
        balancedContractNames.add(name);
        contractAddresses.set(name, address); 
    }

    public static void updateContract(Address targetContract, byte[] contractData, String params) {
        Object[] parsedParams = ArbitraryCallManager.getConvertedParams(params);
        Context.deploy(targetContract, contractData, parsedParams);
    }

    public static void newContract(byte[] contractData, String params) {
        Object[] parsedParams = ArbitraryCallManager.getConvertedParams(params);
        Address contractAddress = Context.deploy(contractData, parsedParams);
        String name = getName(contractAddress);
        balancedContractNames.add(name);
        contractAddresses.set(name, contractAddress); 
    }

    private static String getName(Address address) {
        return call(String.class, address, "name");
    }
}
