package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.VarDB;
import score.DictDB;

public class BalancedAddressManager {
    public static String TAG = "BalancedAddressManager";
    private static final Address mainnetGovernance = Address.fromString("cx44250a12074799e26fdeee75648ae47e2cc84219");
    private static final VarDB<Address> governanceAddress = Context.newVarDB(TAG + "GovernanceAddress", Address.class);
    public static final DictDB<String, Address> contractAddresses = Context.newDictDB(TAG + "ContractAddresses", Address.class);
        
    public static void setGovernance(Address address) {
        governanceAddress.set(address);
    }

    public static Address getAddress(String name) {
        Address address = contractAddresses.get(name);
        if (name == null) {
            address = fetchAddress(name);
            setAddress(name, address);
        }

        return address;
    }

    public static void resetAddress(String name) {
        Address address = fetchAddress(name);
        setAddress(name, address);
    }

    public static void setAddress(String name, Address address) {
        contractAddresses.set(name, address);
    }

    public static Address fetchAddress(String name) {
        return Context.call(Address.class, governanceAddress.getOrDefault(mainnetGovernance), "getAddress", name);
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

    public static Address getGovernance() {
        return getAddress(Names.GOVERNANCE);
    }
}