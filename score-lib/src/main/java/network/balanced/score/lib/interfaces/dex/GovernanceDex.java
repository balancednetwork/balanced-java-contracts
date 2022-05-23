package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface GovernanceDex {

    @External
    void setAddresses(Map<String, Address> _addresses);

    @External(readonly = true)
    Address getContractAddress(String contract);

    @External
    public void configureBalanced();

    @External
    void setStakedLp(Address _address);

    @External
    void setPoolLpFee(BigInteger _value);

    @External
    void setPoolBalnFee(BigInteger _value);

    @External
    void setIcxConversionFee(BigInteger _value);

    @External
    void setIcxBalnFee(BigInteger _value);

    @External
    void setMarketName(BigInteger _id, String _name);

    @External
    void launchBalanced();

    @External
    void permit(BigInteger _id, boolean _permission);

    @External
    void setTimeOffset(BigInteger _delta_time);

    @External
    void setContinuousRewardsDay(BigInteger _continuous_rewards_day);

    @External
    void addLpAddresses(BigInteger _poolId, Address[] _addresses);

    @External
    void dexAddQuoteCoin(Address _address);

}
