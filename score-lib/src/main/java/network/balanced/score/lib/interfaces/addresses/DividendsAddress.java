package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface DividendsAddress {
    @External
    void setDividends(Address _address);

    @External(readonly = true)
    Address getDividends();
}
