package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface BalnAddress {
    @External
    void setBaln(Address _address);

    @External(readonly = true)
    Address getBaln();
}
