package network.balanced.score.core.governance;

import static java.util.Map.entry;
import static network.balanced.score.core.governance.GovernanceConstants.ADDRESSES;
import static network.balanced.score.core.governance.GovernanceConstants.ADMIN_ADDRESSES;
import static network.balanced.score.core.governance.GovernanceConstants.SETTERS;

import java.util.Map;

import network.balanced.score.lib.structs.BalancedAddresses;
import score.Address;
import score.Context;
import score.VarDB;

public class Addresses {
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
    
    private Addresses() {}

    public static Address get(String key) {
        if (key == "governance") {
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
        router .set(addresses.router);
        rebalancing.set(addresses.rebalancing);
        feehandler.set(addresses.feehandler);
        stakedLp.set(addresses.stakedLp);
    }

    public static Map<String, Address> getAddresses() {
        return Map.ofEntries(
            entry("loans", loans.get()),
            entry("dex", dex.get()),
            entry("staking", staking.get()),
            entry("rewards", rewards.get()),
            entry("reserve", reserve.get()),
            entry("dividends", dividends.get()),
            entry("daofund", daofund.get()),
            entry("oracle", oracle.get()),
            entry("sicx", sicx.get()),
            entry("bnUSD", bnUSD.get()),
            entry("baln", baln.get()),
            entry("bwt", bwt.get()),
            entry("rebalancing", rebalancing.get()),
            entry("router", router.get()),
            entry("feehandler", feehandler.get()),
            entry("stakedLp", stakedLp.get())
        );
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
                    // to make migration/testing easier
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
}
