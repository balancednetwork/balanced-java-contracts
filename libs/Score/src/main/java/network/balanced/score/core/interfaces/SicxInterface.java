package network.balanced.score.core.interfaces;

import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public interface SicxInterface {

    @External
    void setStakingAddress(Address _address);

    @External(readonly = true)
    Address getStakingAddress();

    @External(readonly = true)
    String name();

    @External(readonly = true)
    String symbol();

    @External(readonly = true)
    BigInteger decimals();

    @External(readonly = true)
    BigInteger totalSupply();

    @External(readonly = true)
    BigInteger balanceOf(Address _owner);

    @External
    void transfer(Address _to, BigInteger _value, @Optional byte[] _data);

}
