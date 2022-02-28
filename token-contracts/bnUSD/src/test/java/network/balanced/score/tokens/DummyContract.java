package network.balanced.score.tokens;

import network.balanced.score.tokens.tokens.IRC2;
import network.balanced.score.tokens.tokens.IRC2Mintable;
import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;

public class DummyContract extends IRC2Mintable {

    public DummyContract() {
        super("Dummy", "Dummy", BigInteger.valueOf(30), BigInteger.valueOf(18));
    }

    @External
    public BigInteger testPriceInLoop(Address address) {
        BigInteger result = (BigInteger) Context.call(address, "priceInLoop");
        return result;
    }
}
