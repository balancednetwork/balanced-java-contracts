package network.balanced.score.core.interfaces;

import score.*;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface StakingInterface {

    @External(readonly = true)
    public String name();

    @External
    public void setBlockHeightWeek(BigInteger _height);

    @External(readonly = true)
    public BigInteger getBlockHeightWeek();

    @External(readonly = true)
    public BigInteger getTodayRate();

    @External
    public void toggleStakingOn();

    @External(readonly = true)
    public Address getSicxAddress();

    @External
    public void setUnstakeBatchLimit(BigInteger _limit);

    @External(readonly = true)
    public BigInteger getUnstakeBatchLimit();

    @External(readonly = true)
    public List<Address> getPrepList();

    @External(readonly = true)
    public BigInteger getUnstakingAmount();

    @External(readonly = true)
    public BigInteger getTotalStake();

    @External(readonly = true)
    public BigInteger getLifetimeReward();

    @External(readonly = true)
    public List<Address> getTopPreps();

    @External(readonly = true)
    public Map<String, BigInteger> getPrepDelegations();

    @External
    public void setSicxAddress(Address _address);

    public BigInteger percentToIcx(BigInteger votingPercentage, BigInteger totalAmount);

    public void setAddressDelegations(Address to, Address prep, BigInteger votesInPer, BigInteger totalIcxHold);

    public void setPrepDelegations(Address prep, BigInteger value);

    @External(readonly = true)
    public BigInteger claimableICX(Address _address);

    @External(readonly = true)
    public BigInteger totalClaimableIcx();

    @Payable
    public void fallback() throws Exception;

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) throws Exception;


    // Created only for test
    @External(readonly = true)
    public boolean getDistributing();

    @SuppressWarnings("unchecked")

    @External
    public void claimUnstakedICX(@Optional Address _to);


    @SuppressWarnings("unchecked")

    @External(readonly = true)
    public Map<String, BigInteger> getAddressDelegations(Address _address);

        @External
        public void delegate(PrepDelegations[] _user_delegations) throws Exception ;


    @External
    @Payable
    public BigInteger stakeICX(@Optional Address _to, @Optional byte[] _data) throws Exception;

    @External(readonly = true)
    public List<List<Object>> getUnstakeInfo() throws Exception;

    @External(readonly = true)
    public List<Map<String, Object>> getUserUnstakeInfo(Address _address) throws Exception;

}
