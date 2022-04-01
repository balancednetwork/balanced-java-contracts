package network.balanced.score.token;

import java.math.BigInteger;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

public class MockDexScore {

	private VarDB<Boolean> firstCall = Context.newVarDB("first_call_dex", Boolean.class);

    @External(readonly=true)
    public String name() {
        return "DEX Token";
    }

    @External
    public void transfer(Address _to, BigInteger _value) {
    	Context.println(name() +"| transferred: " +  _value + " to: " + _to );
    }
 
    @External
    public BigInteger getTimeOffset() {
    	if(firstCall.getOrDefault(true)) {
    		firstCall.set(false);
    		return BigInteger.valueOf(30*60*1000);
    	}else {
    		return BigInteger.valueOf(1*60*60*1000);
    	}
    	
    }
}
