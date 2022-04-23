package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

import network.balanced.score.lib.structs.DistributionPercentage;

@ScoreInterface
interface Rewards extends Setter {
    void setDay(BigInteger _day);;

    void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list);

    void addNewDataSource(String _name, Address _address );

    void removeDataSource(String _name);

    void claimRewards();

    void bonusDist(Address[] _addresses,  BigInteger[] _amounts);

    void setTimeOffset(BigInteger _timestamp);

    void setContinuousRewardsDay(BigInteger _continuous_rewards_day);
}