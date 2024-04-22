package network.balanced.score.lib.utils;

import java.math.BigInteger;

import network.balanced.score.lib.interfaces.FloorLimitedInterface;
import score.Address;
import score.annotation.External;

public abstract class FloorLimited implements FloorLimitedInterface {
    @External
    public void setFloorPercentage(Address token, BigInteger points) {
        Check.onlyGovernance();
        BalancedFloorLimits.setFloorPercentage(token, points);
    }

    @External(readonly = true)
    public BigInteger getFloorPercentage(Address token) {
        return BalancedFloorLimits.getFloorPercentage(token, true);
    }

    @External
    public void setTimeDelayMicroSeconds(Address token, BigInteger us) {
        Check.onlyGovernance();
        BalancedFloorLimits.setTimeDelayMicroSeconds(token, us);
    }

    @External(readonly = true)
    public BigInteger getTimeDelayMicroSeconds(Address token) {
        return BalancedFloorLimits.getTimeDelayMicroSeconds(token, true);
    }

    @External
    public void setMinimumFloor(Address token, BigInteger minFloor) {
        Check.onlyGovernance();
        BalancedFloorLimits.setMinimumFloor(token, minFloor);
    }

    @External(readonly = true)
    public BigInteger getMinimumFloor(Address token) {
        return BalancedFloorLimits.getMinimumFloor(token);
    }

    @External(readonly = true)
    public BigInteger getCurrentFloor(Address tokenAddress) {
        return BalancedFloorLimits.getCurrentFloor(tokenAddress);
    }
}
