package network.balanced.score.lib.structs;

import java.math.BigInteger;

import score.Address;
import score.annotation.Keep;

public class PrepDelegations {
    @Keep
    public Address _address;
    @Keep
    public BigInteger _votes_in_per;
}