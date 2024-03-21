package network.balanced.score.lib.interfaces;

import java.math.BigInteger;

import score.Address;
import score.annotation.External;

public interface FloorLimitedInterface {
    @External
    void setFloorPercentage(Address token, BigInteger points);

    @External(readonly = true)
    BigInteger getFloorPercentage(Address token);

    @External
    void setTimeDelayMicroSeconds(Address token, BigInteger us);

    @External(readonly = true)
    BigInteger getTimeDelayMicroSeconds(Address token);

    @External
    void setMinimumFloor(Address token, BigInteger minFloor);

    @External(readonly = true)
    BigInteger getMinimumFloor(Address token);

    @External(readonly = true)
    BigInteger getCurrentFloor(Address tokenAddress);
}
