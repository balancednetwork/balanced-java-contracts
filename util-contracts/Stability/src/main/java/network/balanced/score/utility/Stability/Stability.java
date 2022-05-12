package network.balanced.score.utility.Stability;

import score.*;
import score.annotation.External;
import score.annotation.Optional;
import static network.balanced.score.lib.utils.Check.*;

import java.math.BigInteger;

//TODO: only owner insertion
//TODO: Stability constructor
//TODO: token fallback implementation
public class Stability {
    private static final String IUSDC_ADDRESS = "iusds_address";
	private static final String USDS_ADDRESS = "usds_address";
	private static final String FEEHANDLER_ADDRESS = "feehandler_address";


    public DictDB<Address, BigInteger> limits = Context.newDictDB("limit", BigInteger.class);
    public DictDB<Address, BigInteger> balances = Context.newDictDB("balances", BigInteger.class);
    public VarDB<Address> feehandler = Context.newVarDB(FEEHANDLER_ADDRESS, Address.class);

    public Stability(@Optional Address _feehandler) {
        if (_feehandler != null) {
            feehandler.set(_feehandler);
        }
        
    }


    @External
    public void setFeehandler(Address _address) {
        onlyOwner();
        feehandler.set(_address);
    }

    @External
    public Address getFeeHandler() {
        return feehandler.get();
    }

    @External
    public void setWhitelistTokens(Address address, BigInteger limit) {
        onlyOwner();
        limits.set(address, limit);
    }

    @External
    public void setLimit(Address address, BigInteger limit) {
        onlyOwner();
        if (limits.get(address) == null) {
			Context.revert("Address not found in white list");
		}
		limits.set(address, limit);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address caller = Context.getCaller();
        BigInteger limit = limits.get(caller);

        if(limits != null) {

        } else {

        }
    }
}
