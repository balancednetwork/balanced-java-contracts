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
import network.balanced.score.lib.interfaces.addresses.RewardsAddress;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface Bribing extends RewardsAddress {
    @External(readonly=true)
    Address getRewards();

    @External
    void setRewards(Address address);

    @External(readonly=true)
    BigInteger getActivePeriod(String source, Address bribeToken);

    @External(readonly=true)
    Address[] bribesPerSource(String source);

    @External(readonly=true)
    String[] sourcesPerBribe(Address reward);

    @External(readonly=true)
    BigInteger getTotalBribes(String source, Address bribeToken);

    @External(readonly=true)
    BigInteger getClaimedBribes(String source, Address bribeToken);

    @External(readonly=true)
    BigInteger getFutureBribe(String source, Address bribeToken, BigInteger period);

    @External(readonly=true)
    BigInteger claimable(Address user, String source, Address bribeToken);

    @External
    void updatePeriod(String source, Address bribeToken);

    @External
    void claimBribe(String source, Address bribeToken);

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);
}
