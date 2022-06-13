package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface DexTest {

    @External
    void mintTo(Address _account, BigInteger _amount);

    @External
    void transfer(Address _to, BigInteger _value, byte[] _data);

    @External(readonly = true)
    BigInteger balanceOf(Address _owner);

}
