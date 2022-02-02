package network.balanced.score.tokens;

import java.math.BigInteger;


import score.Address;
import score.Context;
import score.VarDB;

public class WorkerToken extends IRC2 {
    private static String TOKEN_NAME = "Balanced Worker Token";
    private static String SYMBOL_NAME = "BALW";
    private static BigInteger INITIAL_SUPPLY = BigInteger.valueOf(100);
    private static BigInteger DECIMALS = BigInteger.valueOf(6);
    private static String TAG = "BALW";

    private String _ACCOUNTS = "accounts";
    private String _GOVERNANCE = "governance";
    private String _BALN_TOKEN = "baln_token";
    private String _BALN = "baln";
    
    private VarDB<Address> _governance = Context.newVarDB(_GOVERNANCE, Address.class);
    private VarDB<Address> _baln_token = Context.newVarDB(_BALN_TOKEN, Address.class);
    private VarDB<BigInteger> _baln = Context.newVarDB(_BALN, BigInteger.class);

    WorkerToken(Address _governance){
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        this._governance.set(_governance);
    }
}
