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
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Dividends extends AdminAddress, GovernanceAddress, LoansAddress, DaoFundAddress, BalnAddress,
        Name, DexAddress, TokenFallback {

    @External(readonly = true)
    boolean getDistributionActivationStatus();

    @External
    void setDistributionActivationStatus(boolean _status);

    @External
    void setTimeOffset(BigInteger deltaTime);

    @External(readonly = true)
    BigInteger getDividendsOnlyToStakedBalnDay();
       
    @External(readonly = true)
    BigInteger getContinuousDividendsDay();
    
    @External(readonly = true)
    Map<String, BigInteger> getBalances();

    @External(readonly = true)
    Map<String, BigInteger> getDailyFees(BigInteger _day);

    @External(readonly = true)
    List<Address> getAcceptedTokens();

    @External
    void addAcceptedTokens(Address _token);

    @External(readonly = true)
    List<String> getDividendsCategories();

    @External
    void addDividendsCategory(String _category);

    @External
    void removeDividendsCategory(String _category);

    @External(readonly = true)
    Map<String, BigInteger> getDividendsPercentage();

    @External
    void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list);

    @External(readonly = true)
    BigInteger getDividendsBatchSize();

    @External
    void setDividendsBatchSize(BigInteger _size);

    @External(readonly = true)
    BigInteger getSnapshotId();

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    void delegate(PrepDelegations[] prepDelegations);

    @External
    boolean distribute();

    @External
    void transferDaofundDividends(@Optional int _start, @Optional int _end);

    @External(readonly = true)
    Map<String, BigInteger> getUnclaimedDividends(Address user);

    @External
    void claimDividends();

    @External
    void claim(@Optional  int _start,@Optional int _end);

    @External
    void accumulateDividends(Address user, @Optional int _start, @Optional int _end);

    @External
    void updateBalnStake(Address user, BigInteger prevStakedBalance, BigInteger currentTotalSupply);

    @External(readonly = true)
    Map<String, BigInteger> getUserDividends(Address _account, @Optional int _start, @Optional int _end);

    @External(readonly = true)
    Map<String, BigInteger> getDaoFundDividends(@Optional int _start, @Optional int _end);

    @External(readonly = true)
    Map<String, BigInteger> dividendsAt(BigInteger _day);

}
