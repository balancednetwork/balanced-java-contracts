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
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreInterface
public interface Loans extends Name, TokenFallback, AdminAddress, DexAddress, GovernanceAddress, StakingAddress,
        RebalancingAddress, DividendsAddress, ReserveAddress, RewardsAddress {

    @External
    void turnLoansOn();

    @External
    void toggleLoansOn();

    @External(readonly = true)
    boolean getLoansOn();

    @External(readonly = true)
    BigInteger getDay();

    @External
    void delegate(PrepDelegations[] prepDelegations);

    @External(readonly = true)
    List<String> checkDeadMarkets();

    @External(readonly = true)
    int getNonzeroPositionCount();

    @External(readonly = true)
    Address getPositionAddress(int _index);

    @External(readonly = true)
    Map<String, String> getAssetTokens();

    @External(readonly = true)
    Map<String, String> getCollateralTokens();

    @External(readonly = true)
    BigInteger getTotalCollateral();

    @External(readonly = true)
    Map<String, Object> getAccountPositions(Address _owner);
 
    @External(readonly = true)
    Map<String, Map<String, Object>> getAvailableAssets();

    @External(readonly = true)
    int assetCount();

    @External(readonly = true)
    int borrowerCount();

    @External(readonly = true)
    boolean hasDebt(Address _owner);

    @External
    void addAsset(Address _token_address, boolean _active, boolean _collateral);

    @External
    void toggleAssetActive(String _symbol);

    @External(readonly = true)
    Map<String, BigInteger> getBalanceAndSupply(String _name, Address __owner);

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

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
    void setTimeOffset(BigInteger deltaTime);

    @External
    void setMaxRetirePercent(BigInteger _value);

    @External
    void setRedeemBatchSize(int _value);

    @External(readonly= true)
    Map<String, Object> getParameters();
}
