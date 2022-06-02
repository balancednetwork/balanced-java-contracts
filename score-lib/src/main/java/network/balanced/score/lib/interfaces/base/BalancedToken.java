package network.balanced.score.lib.interfaces.base;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

public interface BalancedToken extends IRC2{
    @External(readonly = true)
    Map<String, BigInteger> detailsBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger unstakedBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger stakedBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger availableBalanceOf(Address _owner);

    @External(readonly = true)
    boolean getStakingEnabled();

    @External(readonly = true)
    BigInteger totalStakedBalance();

    @External(readonly = true)
    BigInteger getMinimumStake();

    @External(readonly = true)
    BigInteger getUnstakingPeriod();

    @External
    void toggleEnableSnapshot();

    @External
    void setTimeOffset();

    @External(readonly = true)
    boolean getSnapshotEnabled();

    @External
    void toggleStakingEnabled();

    @External
    void stake(BigInteger _value);

    @External(readonly = true)
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day);

    @External(readonly = true)
    BigInteger totalStakedBalanceOfAt(BigInteger _day);
}
