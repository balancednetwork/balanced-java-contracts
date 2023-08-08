package network.balanced.score.lib.utils;

import java.math.BigInteger;

import score.Address;
import score.annotation.External;

public abstract class FloorLimited {
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
    public void setTimeDelayMs(BigInteger ms) {
        Check.onlyGovernance();
        BalancedFloorLimits.setTimeDelayMs(ms);
    }

    @External(readonly = true)
    public BigInteger getTimeDelayMs() {
        return BalancedFloorLimits.getTimeDelayMs();
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
