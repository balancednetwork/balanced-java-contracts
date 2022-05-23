package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

public interface DexAdmin {

    @External
    void setSicx(Address _address);

    @External
    void setDividends(Address _address);

    @External
    void setStaking(Address _address);

    @External
    void setRewards(Address _address);

    @External
    void setbnUSD(Address _address);

    @External
    void setBaln(Address _address);

    @External
    void setFeehandler(Address _address);
    
}
