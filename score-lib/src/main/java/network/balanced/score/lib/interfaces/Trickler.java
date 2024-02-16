/*
 * Copyright (c) 2024-2024 Balanced.network.
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
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;

@ScoreClient
@ScoreInterface
public interface Trickler extends Version, TokenFallback, Name, AddressManager {

    @External
    void setDistributionPeriod(BigInteger blocks);

    @External(readonly = true)
    BigInteger getDistributionPeriod();

    @External
    void addAllowedToken(Address token);

    @External
    void removeAllowedToken(Address token);

    @External(readonly = true)
    List<Address> getAllowListTokens();

    @External
    void claimRewards(Address token);

    @External
    void claimAllRewards();

    @External(readonly = true)
    BigInteger getRewards(Address token);
}
