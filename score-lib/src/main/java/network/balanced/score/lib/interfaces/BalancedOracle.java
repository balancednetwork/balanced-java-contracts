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
import score.annotation.External;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface BalancedOracle extends
        Name,
        GovernanceAddress,
        AdminAddress,
        DexAddress,
        OracleAddress,
        StakingAddress {

    @External
    BigInteger getPriceInLoop(String symbol);

    @External(readonly = true)
    BigInteger getLastPriceInLoop(String symbol);

    @External
    void addDexPricedAsset(String symbol, BigInteger dexBnusdPoolId);

    @External
    void removeDexPricedAsset(String symbol);

    @External(readonly = true)
    BigInteger getAssetBnusdPoolId(String symbol);

    @External
    void setPeg(String symbol, String peg);

    @External(readonly = true)
    String getPeg(String symbol);

    @External
    void setDexPriceEMADecay(BigInteger decay);

    @External(readonly = true)
    BigInteger getDexPriceEMADecay();

    @External
    void setOraclePriceEMADecay(BigInteger decay);

    @External(readonly = true)
    BigInteger getOraclePriceEMADecay();
}
