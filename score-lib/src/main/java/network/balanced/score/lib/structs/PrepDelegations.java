package network.balanced.score.lib.structs;

import score.Address;
import score.annotation.Keep;

import java.math.BigInteger;

public class PrepDelegations {
    @Keep
    public Address _address;
    @Keep
    public BigInteger _votes_in_per;
}