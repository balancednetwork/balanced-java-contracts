package network.balanced.score.tokens.tokens;

import score.Address;

import java.math.BigInteger;

public interface TokenStandard {
    public String name();

    public String symbol();

    public BigInteger decimals();

    public BigInteger totalSupply();

    public BigInteger balanceOf(Address _owner);

    public void transfer(Address _to, BigInteger _value, byte[] _data);
}