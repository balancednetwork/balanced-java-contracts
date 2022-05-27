package network.balanced.score.lib.structs;

import score.Address;
import score.annotation.Keep;

import java.math.BigInteger;

public class RewardsDataEntry {

    @Keep
    public Address _user;
    @Keep
    public BigInteger _balance;

}
