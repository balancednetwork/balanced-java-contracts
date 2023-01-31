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
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Staking {

    @External(readonly = true)
    String name();

    @External
    void setEmergencyManager(Address _address);

    @External(readonly = true)
    Address getEmergencyManager();

    @External
    void setBlockHeightWeek(BigInteger _height);

    @External(readonly = true)
    BigInteger getBlockHeightWeek();

    @External(readonly = true)
    BigInteger getTodayRate();

    @External
    void toggleStakingOn();

    @External(readonly = true)
    boolean getStakingOn();

    @External(readonly = true)
    Address getSicxAddress();

    @External
    void setUnstakeBatchLimit(BigInteger _limit);

    @External(readonly = true)
    BigInteger getUnstakeBatchLimit();

    @External(readonly = true)
    List<Address> getPrepList();

    @External(readonly = true)
    BigInteger getUnstakingAmount();

    @External(readonly = true)
    BigInteger getTotalStake();

    @External(readonly = true)
    BigInteger getLifetimeReward();

    @External(readonly = true)
    List<Address> getTopPreps();

    @External(readonly = true)
    Map<String, BigInteger> getPrepDelegations();

    @External(readonly = true)
    Map<String, BigInteger> getActualPrepDelegations();

    @External(readonly = true)
    Map<String, BigInteger> getActualUserDelegationPercentage(Address user);

    @External
    void setSicxAddress(Address _address);

    @External(readonly = true)
    BigInteger claimableICX(Address _address);

    @External(readonly = true)
    BigInteger totalClaimableIcx();

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);

    @External
    void claimUnstakedICX(@Optional Address _to);

    @External(readonly = true)
    Map<String, BigInteger> getAddressDelegations(Address _address);

    @External
    void delegate(PrepDelegations[] _user_delegations);

    @External
    @Payable
    BigInteger stakeICX(@Optional Address _to, @Optional byte[] _data);

    @External
    void transferUpdateDelegations(Address _from, Address _to, BigInteger _value);

    @External(readonly = true)
    List<List<Object>> getUnstakeInfo();

    @External(readonly = true)
    List<Map<String, Object>> getUserUnstakeInfo(Address _address);
}
