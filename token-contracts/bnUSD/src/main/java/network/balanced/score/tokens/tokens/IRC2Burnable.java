package network.balanced.score.tokens.tokens;

import score.Address;
import score.Context;
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

    /**
     * Destroys `_amount` number of tokens from the caller account.
     * 		Decreases the balance of that account and total supply.
     * @param _amount Number of tokens to be destroyed.
     */
    @External
    public void burn(BigInteger _amount) {
        burn(Context.getCaller(), _amount);
    }

    /**
     * Destroys `_amount` number of tokens from the specified `_account` account.
     * 		Decreases the balance of that account and total supply.
     * 		See {IRC2-_burn}
     * @param _account The account at which token is to be destroyed.
     * @param _amount Number of tokens to be destroyed at the `_account`.
     */
    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        burn(_account, _amount);
    }
}
