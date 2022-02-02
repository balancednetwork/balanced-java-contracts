package network.balanced.score.tokens;

import score.Address;

import java.math.BigInteger;

public interface TokenStandard {
    String name();

    String symbol();

    BigInteger decimals();

    BigInteger totalSupply();

    BigInteger balanceOf(Address _owner);

    void transfer(Address _to, BigInteger _value, byte[] _data);
}
