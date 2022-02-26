package network.balanced.score.tokens.tokens;

import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public class IRC2Burnable extends IRC2{
    /**
     * @param _tokenName     : The name of the token.
     * @param _symbolName    : The symbol of the token.
     * @param _initialSupply :The total number of tokens to initialize with.
     *                       It is set to total supply in the beginning, 0 by default.
     * @param _decimals      The number of decimals. Set to 18 by default.
     */
    public IRC2Burnable(String _tokenName, String _symbolName, BigInteger _initialSupply, BigInteger _decimals) {
        super(_tokenName, _symbolName, _initialSupply, _decimals);
    }

    @External
    public void burn(BigInteger _amount, @Optional byte[] _data) {

    }

    @External
    public void burnTo(Address _account, BigInteger _amount, @Optional byte[] _data) {

    }
}
