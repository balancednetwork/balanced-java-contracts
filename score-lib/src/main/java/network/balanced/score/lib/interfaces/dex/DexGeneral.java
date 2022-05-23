package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface DexGeneral {

    @External(readonly = true)
    String name();

    @External(readonly = true)
    Address getAdmin();

    @External(readonly = true)
    Address getSicx();

    @External(readonly = true)
    Address getDividends();

    @External(readonly = true)
    Address getStaking();

    @External(readonly = true)
    Address getGovernance();

    @External(readonly = true)
    Address getRewards();

    @External(readonly = true)
    Address getbnUSD();

    @External(readonly = true)
    Address getBaln();

    @External(readonly = true)
    Address getFeehandler();

    @External(readonly = true)
    Address getStakedLp();

    @External(readonly = true)
    boolean getDexOn();

    @External(readonly = true)
    boolean isQuoteCoinAllowed(Address _address);

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External(readonly = true)
    BigInteger getContinuousRewardsDay() ;

    @External(readonly = true)
    BigInteger getDeposit(Address _tokenAddress, Address _user);

    @External(readonly = true)
    BigInteger getSicxEarnings(Address _user);

    @External(readonly = true)
    BigInteger getWithdrawLock(BigInteger _id, Address _owner);

    @External(readonly = true)
    BigInteger getPoolId(Address _token1Address, Address _token2Address);

    @External(readonly = true)
    BigInteger getNonce();

    @External(readonly = true)
    List<String> getNamedPools() ;

    @External(readonly = true)
    BigInteger lookupPid(String _name);

    @External(readonly = true)
    BigInteger getPoolTotal(BigInteger _id, Address _token);

    @External(readonly = true)
    BigInteger totalSupply(BigInteger _id);

    @External(readonly = true)
    BigInteger balanceOf(Address _owner, BigInteger _id);

    @External(readonly = true)
    Map<String, BigInteger> getFees();

    @External(readonly = true)
    Address getPoolBase(BigInteger _id);

    @External(readonly = true)
    Address getPoolQuote(BigInteger _id);

    @External(readonly = true)
    BigInteger getQuotePriceInBase(BigInteger _id);

    @External(readonly = true)
    BigInteger getBasePriceInQuote(BigInteger _id);

    @External(readonly = true)
    BigInteger getPrice(BigInteger _id);

    @External(readonly = true)
    BigInteger getBalnPrice();

    @External(readonly = true)
    BigInteger getSicxBnusdPrice();

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

    @External(readonly = true)
    BigInteger getPriceByName(String _name);

    @External(readonly = true)
    BigInteger getICXBalance(Address _address);

    @External(readonly = true)
    String getPoolName(BigInteger _id);

    @External(readonly = true)
    Map<String, Object> getPoolStats(BigInteger _id);

    @External(readonly = true)
    int totalDexAddresses(BigInteger _id);

    @External(readonly = true)
    Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner);

    @External(readonly = true)
    BigInteger balanceOfAt(Address _account, BigInteger _id, BigInteger _snapshot_id, @score.annotation.Optional boolean _twa);
    
    @External(readonly = true)
    BigInteger totalSupplyAt(BigInteger _id, BigInteger _snapshot_id, @score.annotation.Optional boolean _twa);

    @External(readonly = true)
    BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id, @score.annotation.Optional boolean _twa);

    @External(readonly = true)
    BigInteger getTotalValue(String _name, BigInteger _snapshot_id);
    @External(readonly = true)
    BigInteger getBalnSnapshot(String _name, BigInteger _snapshot_id);

    @External(readonly = true)
    Map<String, Object> loadBalancesAtSnapshot(BigInteger _id, BigInteger _snapshot_id, BigInteger _limit, @score.annotation.Optional BigInteger _offset);

    @External(readonly = true)
    Map<String, Object> getDataBatch(String _name, BigInteger _snapshot_id, BigInteger _limit, @score.annotation.Optional BigInteger _offset);


}
