package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface StabilityFundAddress {
    @External
    void setStabilityFund(Address _address);

    @External(readonly = true)
    Address getStabilityFund();
}