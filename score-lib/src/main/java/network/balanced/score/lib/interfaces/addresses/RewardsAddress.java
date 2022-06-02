package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface RewardsAddress {
    @External
    void setRewards(Address _address);

    @External(readonly = true)
    Address getRewards();
}
