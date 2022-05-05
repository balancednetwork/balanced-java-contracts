package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface DaoFundAddress {

    @External
    void setDaofund(Address _address);

    @External(readonly = true)
    Address getDaofund();
}
