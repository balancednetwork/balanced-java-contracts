package network.balanced.score.core;

import score.Context;
import score.Address;
import score.annotation.External;
import score.annotation.Payable;


import java.util.ArrayList;
import java.math.BigInteger;

class PrepDelegations {
    public Address _address;
    public BigInteger _votes_in_per;
}

public class StakingMock {
    private final Address token;
    public StakingMock(Address token) {
        this.token = token;
    }

    @External
    public void delegate(ArrayList<PrepDelegations> delegations) {
    }

    @External
    @Payable
    public void stakeICX(Address from) {   
        BigInteger deposit = Context.getValue(); 
        Context.call(token, "mintTo", from, deposit);
    }
}
