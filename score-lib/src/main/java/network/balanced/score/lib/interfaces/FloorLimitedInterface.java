package network.balanced.score.lib.interfaces;

import java.math.BigInteger;

import score.Address;
import score.annotation.External;

public interface FloorLimitedInterface {
    @External
    void setFloorPercentage(BigInteger points);

    @External(readonly = true)
    BigInteger getFloorPercentage();

    @External
    void setTimeDelayMs(BigInteger ms);

    @External(readonly = true)
    BigInteger getTimeDelayMs();

    @External
    void setDisabled(Address token, boolean disabled);

    @External(readonly = true)
    boolean isDisabled(Address token);

    @External(readonly = true)
    BigInteger getCurrentFloor(Address tokenAddress);
}
