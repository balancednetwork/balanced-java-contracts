package network.balanced.score.lib.interfaces.addresses;

import score.Address;
import score.annotation.External;

public interface AddressManager {
    @External
    void updateAddress(String name);

    @External(readonly = true)
    Address getAddress(String name);
}
