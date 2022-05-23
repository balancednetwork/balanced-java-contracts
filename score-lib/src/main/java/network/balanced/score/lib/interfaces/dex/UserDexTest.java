package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface UserDexTest {

    @External
    void transfer(Address _to, BigInteger _value, byte[] _data);

}
