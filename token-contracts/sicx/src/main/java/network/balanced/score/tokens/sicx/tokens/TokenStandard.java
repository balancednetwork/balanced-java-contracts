package network.balanced.score.tokens.sicx.tokens;


import score.Address;
import score.annotation.Optional;

import java.math.BigInteger;

public interface TokenStandard {

    String name();

    String symbol();

    BigInteger decimals();

    BigInteger totalSupply();

    BigInteger balanceOf(Address _owner);

    void transfer(Address _to, BigInteger _value, @Optional byte[] _data);
}