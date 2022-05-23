package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

public interface DexOwner {

    @External
    void setGovernance(Address _address);

}
