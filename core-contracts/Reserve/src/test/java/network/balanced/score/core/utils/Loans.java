package network.balanced.score.core.utils;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class Loans {
    public static final VarDB<Address> reserve = Context.newVarDB("RESERVE", Address.class);

    public Loans(Address address) {
        reserve.set(address);

    }

    @External
    public void redeem(Address _to, BigInteger _amount, BigInteger _sicx_rate) {
        Context.call(reserve.get(), "redeem", _to, _amount, _sicx_rate);
    }

    @External(readonly = true)
    public Map<String, String> getCollateralTokens(){
        Map<String, String> ns = new HashMap<>();
        ns.put("sICX", "cx0000000000000000000000000000000000000004");
        return ns;
    }
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {

    }
}
