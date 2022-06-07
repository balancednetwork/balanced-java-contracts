package network.balanced.score.lib.interfaces;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

public interface RewardsCopy {

    @External(readonly = true)
    Map<Address, BigInteger> getBalnHoldings(Address[] _holders);

    @External(readonly = true)
    BigInteger getBalnHolding(Address _holder);

    @External
    void distribute();

    @External(readonly = true)
    Map<String, Object> distStatus();

    @External
    void setTotalDist();

    @External
    void claimRewards();

}
