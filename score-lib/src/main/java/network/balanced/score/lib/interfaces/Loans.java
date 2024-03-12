/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import icon.xcall.lib.annotation.XCall;
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.Version;
import network.balanced.score.lib.interfaces.tokens.XTokenReceiver;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Loans extends Name, AddressManager, Version, XTokenReceiver, FloorLimitedInterface, DataSource {
    @External(readonly = true)
    BigInteger getDay();

    @External
    void delegate(PrepDelegations[] prepDelegations);

    @External(readonly = true)
    String getPositionAddress(int _index);

    @External(readonly = true)
    Map<String, String> getAssetTokens();

    @External(readonly = true)
    Map<String, String> getCollateralTokens();

    @External(readonly = true)
    BigInteger getTotalCollateral();

    @External(readonly = true)
    Map<String, Object> getAccountPositions(String _owner);

    @External(readonly = true)
    Map<String, Map<String, Object>> getAvailableAssets();

    @External(readonly = true)
    int assetCount();

    @External(readonly = true)
    int borrowerCount();

    @External(readonly = true)
    boolean hasDebt(String _owner);

    @External
    void addAsset(Address _token_address, boolean _active, boolean _collateral);

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

    @External
    void borrow(String _collateralToBorrowAgainst, String _assetToBorrow, BigInteger _amountToBorrow);

    @External
    @Payable
    void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from,
                          @Optional BigInteger _value);

    @External
    void retireBadDebt(String _symbol, BigInteger _value);

    @External
    void retireBadDebtForCollateral(String _symbol, BigInteger _value, String _collateralSymbol);

    @External
    void returnAsset(String _symbol, BigInteger _value, @Optional String _collateralSymbol);

    @External
    void withdrawAndUnstake(BigInteger _value);

    @External
    void sellCollateral(BigInteger collateralAmountToSell, String collateralSymbol, BigInteger minimumDebtRepaid);

    @External
    void withdrawCollateral(BigInteger _value, @Optional String _collateralSymbol);

    @External
    void liquidate(String _owner, @Optional String _collateralSymbol);

    @External
    void redeemCollateral(Address _collateralAddress, BigInteger _amount);

    @External(readonly = true)
    BigInteger getRedeemableAmount(Address _collateralAddress, @Optional int nrOfPositions);

    @External
    void setLockingRatio(String _symbol, BigInteger _ratio);

    @External(readonly = true)
    BigInteger getLockingRatio(String _symbol);

    @External
    void setLiquidationRatio(String _symbol, BigInteger _ratio);

    @External(readonly = true)
    BigInteger getLiquidationRatio(String _symbol);

    @External
    void setOriginationFee(BigInteger _fee);

    @External
    void setRedemptionFee(BigInteger _fee);

    @External(readonly = true)
    BigInteger getRedemptionFee();

    @External
    void setRedemptionDaoFee(BigInteger _fee);

    @External(readonly = true)
    BigInteger getRedemptionDaoFee();

    @External
    void setRetirementBonus(BigInteger _points);

    @External
    void setLiquidationReward(BigInteger _points);

    @External
    void setNewLoanMinimum(BigInteger _minimum);

    @External
    void setDebtCeiling(String symbol, BigInteger limit);

    @External(readonly = true)
    BigInteger getDebtCeiling(String symbol);

    @External
    void setInterestRate(String symbol, BigInteger rate);

    @External(readonly = true)
    BigInteger getInterestRate(String symbol);

    @External
    void setSavingsRateShare(BigInteger share);

    @External(readonly = true)
    BigInteger getSavingsRateShare();

    @External
    void applyInterest();

    @External
    void claimInterest();

    @External(readonly = true)
    BigInteger getTotalDebt(String assetSymbol);

    @External(readonly = true)
    BigInteger getTotalCollateralDebt(String collateral, String assetSymbol);

    @External
    void setTimeOffset(BigInteger deltaTime);

    @External
    void setMaxRetirePercent(BigInteger _value);

    @External(readonly = true)
    BigInteger getMaxRetirePercent();

    @External(readonly = true)
    Map<String, Object> getParameters();

    @XCall
    void xBorrow(String from, String _collateralToBorrowAgainst, BigInteger _amountToBorrow);

    @XCall
    void xWithdraw(String from, BigInteger _value, String _collateralSymbol);
}
