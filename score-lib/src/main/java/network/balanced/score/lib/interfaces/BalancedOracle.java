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
import network.balanced.score.lib.structs.PriceProtectionConfig;
import network.balanced.score.lib.structs.PriceProtectionParameter;
import score.annotation.External;
import score.annotation.Optional;
import network.balanced.score.lib.annotations.XCall;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface BalancedOracle extends
        Name,
        AddressManager,
        Version {

    @External
    BigInteger getPriceInLoop(String symbol);

    @External(readonly = true)
    BigInteger getLastPriceInLoop(String symbol);

    @External
    BigInteger getPriceInUSD(String symbol);

    @External(readonly = true)
    BigInteger getLastPriceInUSD(String symbol);

    @External(readonly = true)
    Map<String, BigInteger> getPriceDataInUSD(String symbol);

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

    @XCall
    void updatePriceData(String from, String symbol, BigInteger rate, BigInteger timestamp);

    @External
    void addExternalPriceProxy(String symbol, String address, @Optional PriceProtectionParameter priceProtectionConfig);

    @External
    void removeExternalPriceProxy(String symbol, String address);

    @External(readonly = true)
    String getExternalPriceProvider(String symbol);

    @External(readonly = true)
    PriceProtectionConfig getExternalPriceProtectionConfig(String symbol);
}
