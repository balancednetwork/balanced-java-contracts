package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface OwnerDexTest {

    @External
    void mintTo(Address _account, BigInteger _amount);

}
