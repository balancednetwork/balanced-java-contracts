package network.balanced.score.lib.utils;

import java.math.BigInteger;

import network.balanced.score.lib.interfaces.FloorLimitedInterface;
import score.Address;
import score.annotation.External;

public abstract class FloorLimited implements FloorLimitedInterface {
    @External
    public void setFloorPercentage(BigInteger points) {
        Check.onlyGovernance();
        BalancedFloorLimits.setFloorPercentage(points);
    }

    @External(readonly = true)
    public BigInteger getFloorPercentage() {
        return BalancedFloorLimits.getFloorPercentage();
    }

    @External
    public void setTimeDelayMicroSeconds(BigInteger us) {
        Check.onlyGovernance();
        BalancedFloorLimits.setTimeDelayMicroSeconds(us);
    }

    @External(readonly = true)
    public BigInteger getTimeDelayMicroSeconds() {
        return BalancedFloorLimits.getTimeDelayMicroSeconds();
    }

    @External
    public void enableFloors(Address[] tokens) {
        Check.onlyGovernance();
        for (Address token: tokens) {
            BalancedFloorLimits.setDisabled(token, false);
        }
    }

    @External
    public void disableFloors(Address[] tokens) {
        Check.onlyGovernance();
        for (Address token: tokens) {
            BalancedFloorLimits.setDisabled(token, true);
        }
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

    @External
    public void setDisabled(Address token, boolean disabled) {
        Check.onlyGovernance();
        BalancedFloorLimits.setDisabled(token, disabled);
    }

    @External(readonly = true)
    public boolean isDisabled(Address token) {
        return BalancedFloorLimits.isDisabled(token);
    }

    @External(readonly = true)
    public BigInteger getCurrentFloor(Address tokenAddress) {
        return BalancedFloorLimits.getCurrentFloor(tokenAddress);
    }
}
