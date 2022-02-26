package network.balanced.score.tokens;

import java.math.BigInteger;


import network.balanced.score.tokens.tokens.Checks;
import network.balanced.score.tokens.tokens.IRC2;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import static network.balanced.score.tokens.tokens.Checks.*;

public class WorkerToken extends IRC2 {
    private static String TOKEN_NAME = "Balanced Worker Token";
    private static String SYMBOL_NAME = "BALW";
    private static BigInteger INITIAL_SUPPLY = BigInteger.valueOf(100);
    private static BigInteger DECIMALS = BigInteger.valueOf(6);
    public static String TAG = "BALW";
    private static String ACCOUNTS = "accounts";
    private static String GOVERNANCE = "governance";
    private static String BALN_TOKEN = "baln_token";
    private static String BALN = "baln";
    
    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private VarDB<Address> balnToken = Context.newVarDB(BALN_TOKEN, Address.class);
    private VarDB<BigInteger> baln = Context.newVarDB(BALN, BigInteger.class);

    public WorkerToken(@Optional Address _governance){
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        if(governance != null){
            WorkerToken.governance.set(_governance);
        }
    }

    /**
     *
     * @param _address: Address that we need to set to governance
     */
    @External
    public void setGovernance(Address _address){
        Checks.onlyOwner();
        Context.require( _address.isContract(), TAG + "Address provided is an EOA address. A contract address is required.");
        governance.set(_address);
    }


    /**
     *
     * @return Governance address
     */
    @External(readonly = true)
    public Address getGovernance(){
        return governance.get();
    }

    /**
     *
     * @param _admin: Sets the authorized access
     */
    @External
    public void setAdmin(Address _admin){
        onlyGovernance();
        WorkerToken.admin.set(_admin);
    }

    @External
    public void setBaln(Address _address){
        onlyAdmin();
        balnToken.set(_address);
    }

    @External(readonly = true)
    public Address getBaln(){
        return balnToken.get();
    }

    @External
    public void adminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data){
        onlyAdmin();
        if(_data != null){
            _data = new byte[0];
        }

        transfer(_from, _to, _value, _data);
    }

    @External
    public void distribute(){
        final int size = addresses.size();
        BigInteger totalTokens = totalSupply();

        // dist = balance of worker token contract
        BigInteger dist = (BigInteger) Context.call(balnToken.get(), "balanceOf", Context.getAddress());
        for(int i = 0; i < size; i++){
            Address address = addresses.get(i);
            BigInteger balance = balances.getOrDefault(address, BigInteger.ZERO);
            if (balance.compareTo(BigInteger.ZERO) > 0){
                // multiply first cause integer division
                BigInteger amount = dist.multiply(balance).divide(totalTokens);
                dist = dist.subtract(amount);
                totalTokens = totalTokens.subtract(balance);
                byte[] data = new byte[0];
                Context.call(balnToken.get(), "transfer", address, amount, data);
            }
        }
    }

    /**
     *
     * @param _from: Token origination address.
     * @param _value: Number of tokens sent.
     * @param _data: unused ignored
     */
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data){
        Context.require(
                Context.getCaller().equals(balnToken.get()),
                "The Worker Token contract can only accept BALN tokens." +
                        "Deposit not accepted from" + Context.getCaller() +
                        "Only accepted from BALN = " + baln.get());
        baln.set(baln.getOrDefault(BigInteger.ZERO).add(_value));
    }

}
