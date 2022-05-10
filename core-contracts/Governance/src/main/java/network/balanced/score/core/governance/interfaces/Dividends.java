package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;

@ScoreInterface
public interface Dividends extends Setter {
    void setDistributionActivationStatus(boolean _status);

    void addAcceptedTokens(Address _token);

    void setDividendsOnlyToStakedBalnDay(BigInteger _day);

    void setContinuousRewardsDay(BigInteger _day );

    void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list);
}
