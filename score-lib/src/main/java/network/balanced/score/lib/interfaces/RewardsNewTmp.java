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

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Rewards extends 
        Name, 
        TokenFallback,
        GovernanceAddress,
        AdminAddress,
        BalnAddress,
        BwtAddress,
        DaoFundAddress,
        ReserveAddress,
        BoostedBalnAddress {
   
    @External(readonly = true)
    BigInteger getEmission(BigInteger _day);

    @External(readonly = true)
    Map<String, BigInteger> getBalnHoldings(Address[] _holders);

    @External(readonly = true)
    BigInteger getBalnHolding(Address _holder, @Optional String[] sourcesToUpdate);

    @External
    void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list);

    @External
    void addNewDataSource(String _name, Address _dataProvider, @Optional Address _contractAddress);

    @External(readonly = true)
    Map<String, Object> getSourceData(String _name);

    @External
    boolean distribute();

    @External(readonly = true)
    Map<String, BigInteger> recipientAt(BigInteger _day);

    @External
    void boost(String _name);

    @External(readonly = true)
    String getBoost(Address user);

    @External
    void claimRewards(@Optional String[] sourcesToUpdate);

    @External(readonly = true)
    BigInteger getAPY(String _name);

    @External
    void updateRewardsData(String _name, BigInteger _totalSupply, Address _user, BigInteger _balance);

    @External
    void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data);

    @External
    void addDataProvider(Address _source);

    @External(readonly = true)
    List<Address> getDataProviders();

    @External
    void setTimeOffset(BigInteger _timestamp);

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    void setContinuousRewardsDay(BigInteger _continuous_rewards_day);

    @External(readonly = true)
    BigInteger getContinuousRewardsDay();

    @External
    void onKick(Address user, BigInteger bBalnUserBalance, @Optional byte[] data);

    @External
    void onBalanceUpdate(Address user);
}
