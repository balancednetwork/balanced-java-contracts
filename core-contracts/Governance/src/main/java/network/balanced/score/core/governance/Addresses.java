package network.balanced.score.core.governance;

import static java.util.Map.entry;
import static network.balanced.score.core.governance.GovernanceConstants.ADDRESSES;
import static network.balanced.score.core.governance.GovernanceConstants.ADMIN_ADDRESSES;

import java.util.Map;

import network.balanced.score.core.governance.interfaces.*;
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

    public static Object getInterface(String key) {
        switch (key) {
            case "loans":
                return new LoansScoreInterface(get(key));
            case "dex":
                return new DexScoreInterface(get(key));
            case "staking":
                return new StakingScoreInterface(get(key));
            case "rewards":
                return new RewardsScoreInterface(get(key));
            case "reserve":
                return new ReserveScoreInterface(get(key));
            case "dividends":
                return new DividendsScoreInterface(get(key));
            case "daofund":
                return new DaofundScoreInterface(get(key));
            case "oracle":
                return new AssetScoreInterface(get(key));
            case "sicx":
                return new AssetScoreInterface(get(key));
            case "bnUSD":
                return new BnUSDScoreInterface(get(key));
            case "baln":
                return new BalnScoreInterface(get(key));
            case "bwt":
                return new BwtScoreInterface(get(key));
            case "rebalancing":
                return new RebalancingScoreInterface(get(key));
            case "router":
                return new RouterScoreInterface(get(key));
            case "feehandler":
                return new FeehandlerScoreInterface(get(key));
            case "stakedLp":
                return new StakedLpScoreInterface(get(key));
        }

    return null;
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
        for (String targetContract : ADDRESSES.keySet()) {
            if (ADDRESSES.get(targetContract).contains(contract)) {
                try {
                    setAddress(targetContract, contract);
                } catch (Exception e) {}
            }
        }
    }

    public static void setContractAddresses() {
        for (String targetContract : ADDRESSES.keySet()) {
            for (String contract : ADDRESSES.get(targetContract)) {
                try {
                    setAddress(targetContract, contract);
                } catch (Exception e) {}
            }
            
        }
    }

    public static void setAdmins() {
        for (String targetContract : ADMIN_ADDRESSES.keySet()) {
            String contract = ADMIN_ADDRESSES.get(targetContract);
            Context.call(get(targetContract), "setAdmin", get(contract));        
        }
    }

    public static void setAddress(String target, String contractToBeSet) {
        SetterScoreInterface setterInterface = (SetterScoreInterface) getInterface(target);
        switch(target) {
            case "loans":
                setterInterface.setLoans(Addresses.get(contractToBeSet));
                break;
            case "dex":
                setterInterface.setDex(Addresses.get(contractToBeSet));
                break;
            case "staking":
                setterInterface.setStaking(Addresses.get(contractToBeSet));
                break;
            case "rewards":
                setterInterface.setRewards(Addresses.get(contractToBeSet));
                break;
            case "reserve":
                setterInterface.setReserve(Addresses.get(contractToBeSet));
                break;
            case "dividends":
                setterInterface.setDividends(Addresses.get(contractToBeSet));
                break;
            case "daofund":
                setterInterface.setDaofund(Addresses.get(contractToBeSet));
                break;
            case "oracle":
                setterInterface.setOracle(Addresses.get(contractToBeSet));
                break;
            case "sicx":
                setterInterface.setSicx(Addresses.get(contractToBeSet));
                break;
            case "bnUSD":
                setterInterface.setBnusd(Addresses.get(contractToBeSet));
                break;
            case "baln":
                setterInterface.setBaln(Addresses.get(contractToBeSet));
                break;
            case "bwt":
                setterInterface.setBwt(Addresses.get(contractToBeSet));
                break;
            case "router":
                setterInterface.setRouter(Addresses.get(contractToBeSet));
                break;
            case "rebalancing":
                setterInterface.setRebalancing(Addresses.get(contractToBeSet));
                break;
            case "feehandler":
                setterInterface.setFeehandler(Addresses.get(contractToBeSet));
                break;
            case "stakedLp":
                setterInterface.setStakedLp(Addresses.get(contractToBeSet));
                break;
        }
    }
}
