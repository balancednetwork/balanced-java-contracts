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
import network.balanced.score.lib.annotations.XCall;
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface BoostedBaln extends AddressManager, TokenFallback, Version {
    @External
    void setMinimumLockingAmount(BigInteger value);

    @External(readonly = true)
    BigInteger getMinimumLockingAmount();

    @External
    void setPenaltyAddress(Address penaltyAddress);

    @External(readonly = true)
    Map<String, BigInteger> getLocked(Address _owner);

    @External(readonly = true)
    BigInteger getTotalLocked();

    @External(readonly = true)
    List<String> getUsers(int start, int end);

    @External(readonly = true)
    boolean hasLocked(Address _owner);

    @External(readonly = true)
    BigInteger getLastUserSlope(Address address);

    @External(readonly = true)
    BigInteger getLastUserSlopeV2(String address);

    @External(readonly = true)
    BigInteger userPointHistoryTimestamp(Address address, BigInteger index);

    @External(readonly = true)
    BigInteger lockedEnd(Address address);

    @External(readonly = true)
    BigInteger lockedEndV2(String address);

    @External
    void checkpoint();

    @External
    void increaseUnlockTime(BigInteger unlockTime);

    @XCall
    void xIncreaseUnlockTime(String from, BigInteger unlockTime);

    @External
    void kick(Address user);

    @External
    void withdraw();

    @External
    void withdrawEarly();

    @XCall
    void xKick(String from);

    @XCall
    void xWithdrawEarly(String from);

    @XCall
    void xWithdraw(String from);

    @XCall
    void checkpoint(String from);

    @External(readonly = true)
    BigInteger balanceOf(Address _owner, @Optional BigInteger timestamp);

    @External(readonly = true)
    BigInteger xBalanceOf(String _owner, @Optional BigInteger timestamp);

    @External(readonly = true)
    BigInteger balanceOfAt(Address _owner, BigInteger block);

    @External(readonly = true)
    BigInteger xBalanceOfAt(String _owner, BigInteger block);

    @External(readonly = true)
    BigInteger totalSupply(@Optional BigInteger time);

    @External(readonly = true)
    BigInteger totalSupplyAt(BigInteger block);

    @External(readonly = true)
    String name();

    @External(readonly = true)
    String symbol();

    @External(readonly = true)
    BigInteger decimals();

    @External(readonly = true)
    BigInteger userPointEpoch(Address address);

    @External(readonly = true)
    BigInteger xUserPointEpoch(String address);
}
