package network.balanced.score.core.router;

import com.iconloop.score.token.irc2.IRC2Basic;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public class Token extends IRC2Basic {
    public Token() {
        super("TestToken", "TestToken", 18);
    }


    @External
    public void fallback() {

    }
}