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

import java.math.BigInteger;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.DexAddress;
import network.balanced.score.lib.interfaces.addresses.GovernanceAddress;
import network.balanced.score.lib.interfaces.addresses.LoansAddress;
import network.balanced.score.lib.interfaces.addresses.StabilityFundAddress;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import score.Address;
import score.annotation.External;

import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface FeeHandler extends Name, GovernanceAddress, AdminAddress, TokenFallback, LoansAddress, DexAddress, StabilityFundAddress {

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
    void setRoute(Address _fromToken, Address _toToken, Address[] _path);

    @External
    void deleteRoute(Address _fromToken, Address _toToken);

    @External(readonly = true)
    Map<String, Object> getRoute(Address _fromToken, Address _toToken);

    @External
    void setFeeProcessingInterval(BigInteger _interval);

    @External(readonly = true)
    BigInteger getFeeProcessingInterval();

    @External
    void addAllowedAddress(Address address);

    @External(readonly = true)
    List<Address> get_allowed_address(int offset);

    @External(readonly = true)
    int getNextAllowedAddressIndex();

    @External
    void route_contract_balances();

    @External(readonly = true)
    BigInteger getLoanFeesAccrued();

    @External(readonly = true)
    BigInteger getStabilityFundFeesAccrued();

    @External(readonly = true)
    BigInteger getSwapFeesAccruedByToken(Address token);
}
