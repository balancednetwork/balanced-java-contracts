package network.balanced.score.core.governance.interfaces;


import score.Address;
import score.annotation.Optional;
import score.annotation.External;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.structs.PrepDelegations;

@ScoreInterface
public interface Loans extends Setter {
    void turnLoansOn();

    void toggleLoansOn();

    void setContinuousRewardsDay(BigInteger _day);

    void delegate(PrepDelegations[] prepDelegations);

    Map<String, Boolean> getDistributionsDone();

    Map<String, Address> getAssetTokens();

    void addAsset(Address _token_address, boolean _active, boolean _collateral);

    void toggleAssetActive(String _symbol);

    @Payable
    @External
    void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value);

    void setMiningRatio(BigInteger _ratio);

    void setLockingRatio(BigInteger _ratio);

    void setLiquidationRatio(BigInteger _ratio);

    void setOriginationFee(BigInteger _fee);

    void setRedemptionFee(BigInteger _fee);

    void setRetirementBonus(BigInteger _points);

    void setLiquidationReward(BigInteger _points);  

    void setNewLoanMinimum(BigInteger _minimum);

    void setMinMiningDebt(BigInteger _minimum);

    void setTimeOffset(BigInteger deltaTime);

    void setMaxRetirePercent(BigInteger _value);

    void setRedeemBatchSize(BigInteger _value);

    @External(readonly= true)
    Map<String, Object> getParameters();
}