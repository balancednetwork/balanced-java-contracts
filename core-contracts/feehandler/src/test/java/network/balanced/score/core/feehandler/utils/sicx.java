package network.balanced.score.core.feehandler.utils;

import com.iconloop.score.token.irc2.IRC2Mintable;
import score.Address;

import java.math.BigInteger;

public class sicx extends IRC2Mintable {
    public sicx(String _name, String _symbol, int _decimals) {
        super(_name, _symbol, _decimals);
    }
    public void transfer(Address _to, BigInteger _amount, byte[] data){
    }
}
