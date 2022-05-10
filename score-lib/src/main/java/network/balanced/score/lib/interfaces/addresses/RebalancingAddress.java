package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface RebalancingAddress {
    @External
    void setRebalancing(Address _address);

    @External(readonly = true)
    Address getRebalancing();
}
