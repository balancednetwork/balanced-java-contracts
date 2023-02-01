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
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Version;
import network.balanced.score.lib.interfaces.tokens.IRC2BurnableInterface;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import score.Address;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface BalancedToken extends IRC2Mintable, IRC2BurnableInterface, GovernanceAddress, AdminAddress,
        BnusdAddress, OracleAddress, DexAddress, DividendsAddress, Version {

    @External
    void setOracleName(String _name);

    @External(readonly = true)
    String getOracleName();

    @External(readonly = true)
    String getPeg();

    @External
    void setMinInterval(BigInteger _interval);

    @External(readonly = true)
    BigInteger getMinInterval();

    @External(readonly = true)
    BigInteger getPriceUpdateTime();

    @External
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();

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

    @External(readonly = true)
    boolean getSnapshotEnabled();

    @External
    void toggleStakingEnabled();

    @External
    void stake(BigInteger _value);

    @External
    void setMinimumStake(BigInteger _amount);

    @External
    void setUnstakingPeriod(BigInteger _time);

    @External
    void setTimeOffset();

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day);

    @External(readonly = true)
    BigInteger totalStakedBalanceOfAt(BigInteger _day);

    @EventLog(indexed = 3)
    void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price);
}
