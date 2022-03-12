package network.balanced.score.core;

import score.Address;
import score.annotation.Optional;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;

import java.math.BigInteger;
import scorex.util.ArrayList;
import java.util.Map;
import java.util.List;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface LoansInterface {

    @External(readonly = true)
    String name();

    @External
    void turnLoansOn();

    @External
    void toggleLoansOn();

    @External(readonly = true)
    boolean getLoansOn();

    @External
    void setContinuousRewardsDay(int _day);

    @External(readonly = true)
    int getContinuousRewardsDay();

    @External(readonly = true)
    int getDay();

    @External
    void delegate(byte[] prepDelegations);

    @External(readonly = true)
    Map<String, Boolean> getDistributionsDone();

    @External(readonly = true)
    List<String> checkDeadMarkets();

    @External(readonly = true)
    int getNonzeroPositionCount();

    @External(readonly = true)
    Map<String, Object> getPositionStanding(Address _address, @Optional int snapshot);

    @External(readonly = true)
    Address getPositionAddress(int _index);

    @External(readonly = true)
    Map<String, Address> getAssetTokens();

    @External(readonly = true)
    Map<String, Address> getCollateralTokens();

    @External(readonly = true)
    BigInteger getTotalCollateral();

    @External(readonly = true)
    Map<String, Object> getAccountPositions(Address _owner);

    @External(readonly = true)
    Map<String, Object> getPositionByIndex(int _index, int _day);
 
    @External(readonly = true)
    Map<String, Map<String, Object>> getAvailableAssets();

    @External(readonly = true)
    int assetCount();

    @External(readonly = true)
    int borrowerCount();

    @External(readonly = true)
    boolean hasDebt(Address _owner);

    @External(readonly = true)
    Map<String, Object> getSnapshot(@Optional int _snap_id);

    @External
    void addAsset(Address _token_address, boolean _active, boolean _collateral);
    @External
    void toggleAssetActive(String _symbol);

    @External
    boolean precompute(int _snapshot_id, int batch_size);

    @External(readonly = true)
    BigInteger getTotalValue(String _name, int _snapshot_id);

    @External(readonly = true)
    Map<String, BigInteger> getBalanceAndSupply(String _name, Address __owner);

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

    @External(readonly = true)
    Map<Address, BigInteger> getDataBatch(String _name, int _snapshot_id, int _limit, @Optional int _offset);

    @External
    boolean checkForNewDay();    

    @External
    void checkDistributions(int _day, boolean _new_day);

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);

    @External

    @Payable
    void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value);

    @External
    void retireBadDebt(String _symbol, BigInteger _value);

    @External
    void returnAsset(String _symbol, BigInteger _value, boolean _repay);

    @External
    void raisePrice(BigInteger _total_tokens_required);

    @External
    void lowerPrice(BigInteger _total_tokens_required);

    @External
    void withdrawCollateral(BigInteger _value);

    @External
    void liquidate(Address _owner);

    @External
    void setAdmin(Address _address);

    @External
    void setGovernance(Address _address);

    @External
    void setDex(Address _address);    

    @External
    void setRebalance(Address _address);

    @External
    void setDividends(Address _address);

    @External
    void setReserve(Address _address);

    @External
    void setRewards(Address _address);

    @External
    void setStaking(Address _address);

    @External
    void setMiningRatio(BigInteger _ratio);

    @External
    void setLockingRatio(BigInteger _ratio);

    @External
    void setLiquidationRatio(BigInteger _ratio);

    @External
    void setOriginationFee(BigInteger _fee);

    @External
    void setRedemptionFee(BigInteger _fee);

    @External
    void setRetirementBonus(BigInteger _points);

    @External
    void setLiquidationReward(BigInteger _points);  

    @External
    void setNewLoanMinimum(BigInteger _minimum);

    @External
    void setMinMiningDebt(BigInteger _minimum);

    @External
    void setTimeOffset(long deltaTime);

    @External
    void setMaxRetirePercent(BigInteger _value);

    @External
    void setRedeemBatchSize(int _value);

    @External(readonly= true)
    Map<String, Object> getParameters();
}