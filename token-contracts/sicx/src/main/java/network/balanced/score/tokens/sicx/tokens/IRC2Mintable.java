package network.balanced.score.tokens.sicx.tokens;

import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public class IRC2Mintable extends IRC2Burnable {

    public IRC2Mintable(String _tokenName, String _symbolName, BigInteger _initialSupply, BigInteger _decimals) {
        super(_tokenName, _symbolName, _initialSupply, _decimals);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        if (_data == null) {
            String data = "None";
            _data = data.getBytes();
        }
        _mint(Context.getCaller(), _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        if (_data == null) {
            String data = "None";
            _data = data.getBytes();
        }
        _mint(_account, _amount, _data);
    }
}
