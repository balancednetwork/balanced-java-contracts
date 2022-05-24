package network.balanced.score.lib.structs;

import java.math.BigInteger;
import score.Address;
import score.annotation.Keep;

public class RewardsDataEntry {
    @Keep
    public Address _user;
    @Keep
    public BigInteger _balance;
}
