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
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;

@ScoreClient
@ScoreInterface
public interface FeeHandler extends Name, AddressManager, Version {

    @External
    void enable();

    @External
    void disable();

    @External(readonly = true)
    boolean isEnabled();

    @External
    void setSwapFeesAccruedDB();

    @External
    void setAcceptedDividendTokens(Address[] _tokens);

    @External(readonly = true)
    List<Address> getAcceptedDividendTokens();

    @External
    void setFeeProcessingInterval(BigInteger _interval);

    @External(readonly = true)
    BigInteger getFeeProcessingInterval();

    @External(readonly = true)
    BigInteger getLoanFeesAccrued();

    @External(readonly = true)
    BigInteger getStabilityFundFeesAccrued();

    @External(readonly = true)
    BigInteger getSwapFeesAccruedByToken(Address token);

    @External
    void setRoute(Address token, Address[] _path);

    @External
    void setBalnRouteLimit(BigInteger limit);

    @External(readonly = true)
    BigInteger getBalnRouteLimit();

    @External
    void addDefaultRoute(Address token);

    @External
    void calculateRouteLimit(Address token);

    @External(readonly = true)
    BigInteger getRouteLimit(Address _fromToken);

    @External
    void deleteRoute(Address token);

    @External
    void routeFees();

    @External
    void routeToken(Address token, Address[] path);

    @External(readonly = true)
    List<String> getRoute(Address _fromToken);

    @External(readonly = true)
    List<Address> getRoutedTokens();

    @External(readonly = true)
    int getNextAllowedAddressIndex();
}
