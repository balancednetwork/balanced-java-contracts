package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface ReserveAddress {
    @External
    void setReserve(Address _address);

    @External(readonly = true)
    Address getReserve();
}
