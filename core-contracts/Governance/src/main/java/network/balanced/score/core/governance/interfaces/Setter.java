package network.balanced.score.core.governance.interfaces;

import score.Address;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Setter extends Admin {
    void setLoans(Address _address);

    void setDex(Address _address);

    void setRewards(Address _address);

    void setStaking(Address _address);

    void setDividends(Address _address);

    void setDaofund(Address _address);

    void setReserve(Address _address);

    void setOracle(Address _address);

    void setSicx(Address _address);

    void setBnusd(Address _address);

    void setBaln(Address _address);

    void setBwt(Address _address);

    void setRouter(Address _address);

    void setRebalancing(Address _address);

    void setFeehandler(Address _address);

    void setStakedLp(Address _address);
}