package network.balanced.score.tokens.utils;

import com.iconloop.score.token.irc2.IRC2Mintable;
import score.Context;

import java.math.BigInteger;

public class IRC2Token extends IRC2Mintable {

    public IRC2Token(BigInteger _totalSupply) {
        super("BALN Token", "BALN", 18);
        _mint(Context.getCaller(), _totalSupply);
    }
}
