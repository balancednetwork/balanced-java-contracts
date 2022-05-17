/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.Map;

@ScoreInterface
public interface Loans extends
        RewardsAddress, 
        DividendsAddress,
        StakingAddress,
        ReserveAddress,
        RebalancingAddress,
        DexAddress,
        AdminAddress {

    void turnLoansOn();

    void toggleLoansOn();

    void setContinuousRewardsDay(BigInteger _day);

    void delegate(PrepDelegations[] prepDelegations);

    Map<String, Boolean> getDistributionsDone();

    void addAsset(Address _token_address, boolean _active, boolean _collateral);

    void toggleAssetActive(String _symbol);

    @Payable
    @External
    void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value);

    @External(readonly = true)
    Map<String, Object> getPositionStanding(Address _address, BigInteger _snapshot);

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

    @External(readonly = true)
    Map<String, Address> getAssetTokens();

}
