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

//TODO: Generate Java doc
public class WorkerToken extends IRC2 {
    private static String TOKEN_NAME = "Balanced Worker Token";
    private static String SYMBOL_NAME = "BALW";
    private static BigInteger INITIAL_SUPPLY = BigInteger.valueOf(100);
    private static BigInteger DECIMALS = BigInteger.valueOf(6);
    public static String TAG = "BALW";
    private static String _ACCOUNTS = "accounts";
    private static String _GOVERNANCE = "governance";
    private static String _BALN_TOKEN = "baln_token";
    private static String _BALN = "baln";
    
    public static final VarDB<Address> _governance = Context.newVarDB(_GOVERNANCE, Address.class);
    private VarDB<Address> _baln_token = Context.newVarDB(_BALN_TOKEN, Address.class);
    private VarDB<BigInteger> _baln = Context.newVarDB(_BALN, BigInteger.class);

    public WorkerToken(Address _governance){
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        WorkerToken._governance.set(_governance);
    }

    /**
     *
     * @param _address: Address that we need to set to governance
     */
    @External
    public void setGovernance(Address _address){
        Checks.onlyOwner();
        Context.require( _address.isContract(), TAG + "Address provided is an EOA address. A contract address is required.");
        _governance.set(_address);
    }

    /**
     *
     * @return Governance address
     */
    @External(readonly = true)
    public Address getGovernance(){
        return _governance.get();
    }

    /**
     *
     * @param _admin: Sets the authorized access
     */
    @External
    public void setAdmin(Address _admin){
        onlyGovernance();
        WorkerToken._admin.set(_admin);
    }

    @External
    public void setBaln(Address _address){
        onlyAdmin();
        _baln_token.set(_address);
    }

    @External(readonly = true)
    public Address getBaln(){
        return _baln_token.get();
    }

    @External
    public void adminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data){
        onlyAdmin();
        if(_data != null){
            _data = new byte[0];
        }

        _transfer(_from, _to, _value, _data);
    }

    @External
    public void distribute(){
        final int size = _addresses.size();
        BigInteger tokens = totalSupply();

        BigInteger dist = (BigInteger) Context.call(_baln_token.get(), "balanceOf", Context.getAddress());
        for(int i = 0; i < size; i++){
            Address address = _addresses.get(i);
            BigInteger balance = _balances.getOrDefault(address, BigInteger.ZERO);
            if (balance.compareTo(BigInteger.ZERO) > 0){
                BigInteger amount = dist.multiply(balance.divide(tokens));
                dist = dist.subtract(amount);
                tokens = tokens.subtract(balance);
                Context.call(_baln_token.get(), "transfer", address, amount);

            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data){
        if(Context.getCaller().equals(_baln_token.get())){
            _baln.set(_baln.get().add(_value));
        }
        else{
            Context.require(
                    false,
                    "The Worker Token contract can only accept BALN tokens." +
                            "Deposit not accepted from" + Context.getCaller() +
                            "Only accepted from BALN = " + _baln.get()
                    );
        }
    }

}
