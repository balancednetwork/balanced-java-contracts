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
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;

@ScoreClient
@ScoreInterface
public interface Rebalancing extends AdminAddress, BnusdAddress, DexAddress, GovernanceAddress, LoansAddress,
        Name, SicxAddress, OracleAddress, Version {

    @External(readonly = true)
    BigInteger getPriceChangeThreshold();

    @External
    void setPriceDiffThreshold(BigInteger _value);

    @External(readonly = true)
    List<Object> getRebalancingStatusFor(Address collateralAddress);

    @External
    void rebalance(@Optional Address collateralAddress);
}