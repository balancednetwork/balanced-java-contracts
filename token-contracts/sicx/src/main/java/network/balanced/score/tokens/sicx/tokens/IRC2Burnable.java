package network.balanced.score.tokens.sicx.tokens;

import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;

public class IRC2Burnable extends IRC2{

    public IRC2Burnable(String _tokenName, String _symbolName,BigInteger _initialSupply,BigInteger _decimals) {
        super(_tokenName, _symbolName, _initialSupply, _decimals);
    }

    @External
    public void burn(BigInteger _amount) {
        _burn(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        _burn(_account, _amount);
    }
}





