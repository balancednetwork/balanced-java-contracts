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
import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.BnusdAddress;
import network.balanced.score.lib.interfaces.addresses.DexAddress;
import network.balanced.score.lib.interfaces.addresses.DividendsAddress;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Baln extends IRC2, AdminAddress, DividendsAddress, BnusdAddress, DexAddress {

    @External
    void stake(BigInteger _value);

    @External(readonly = true)
    BigInteger totalStakedBalance();

    @External(readonly = true)
    BigInteger stakedBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day);

    @External(readonly = true)
    BigInteger totalStakedBalanceOfAt(BigInteger _day);

    void setOracle(Address _address);

    void setOracleName(String _name);

    @External
    void setTimeOffset();

    @External
    void toggleStakingEnabled();

    @External
    void toggleEnableSnapshot();

    void setMinimumStake(BigInteger _amount);

    void setUnstakingPeriod(BigInteger _time);

    void setMinInterval(BigInteger _interval);

    @External(readonly = true)
    Map<String, BigInteger> detailsBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger unstakedBalanceOf(Address _owner);

    @External(readonly = true)
    BigInteger availableBalanceOf(Address _owner);

    @External(readonly = true)
    boolean getStakingEnabled();

    @External(readonly = true)
    BigInteger getMinimumStake();

    @External(readonly = true)
    BigInteger getUnstakingPeriod();

    @External(readonly = true)
    boolean getSnapshotEnabled();
}
