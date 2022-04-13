package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface DaofundAddress {

    @External
    void setDaofund(Address address);

    @External(readonly = true)
    Address getDaofund();
}
