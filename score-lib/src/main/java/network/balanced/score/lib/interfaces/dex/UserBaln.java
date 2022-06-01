package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface UserBaln {

    @External
    void transfer(Address _to, BigInteger _value, byte[] _data);

    @External(readonly = true)
    BigInteger availableBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger totalSupply();

}
