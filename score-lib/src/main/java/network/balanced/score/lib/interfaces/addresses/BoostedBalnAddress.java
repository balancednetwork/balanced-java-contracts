package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface BoostedBalnAddress {
    @External
    void setBoostedBaln(Address _address);

    @External(readonly = true)
    Address getBoostedBaln();
}
