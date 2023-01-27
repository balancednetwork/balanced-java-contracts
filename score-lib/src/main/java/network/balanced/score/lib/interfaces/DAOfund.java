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
import network.balanced.score.lib.interfaces.base.Emergency;
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface DAOfund extends Name, AddressManager, TokenFallback, Fallback, Emergency {

    @External
    void delegate(PrepDelegations[] prepDelegations);

    @External(readonly = true)
    Map<String, BigInteger> getBalances();

    @External(readonly = true)
    Map<String, Object> getDisbursementDetail(Address _user);

    @External(readonly = true)
    void disburse(Address token, Address recipient, BigInteger amount, @Optional byte[] data);

    void claim();

    @External
    void claimRewards();

    @External
    void claimNetworkFees();

    @External
    void supplyLiquidity(Address baseAddress, BigInteger baseAmount, Address quoteAddress, BigInteger quoteAmount);

    @External
    void withdrawLiquidity(BigInteger pid, BigInteger amount);

    @External
    void setPOLSupplySlippage(BigInteger points);

    @External(readonly = true)
    BigInteger getPOLSupplySlippage();

    @External
    void stakeLPTokens(BigInteger pid);

    @External(readonly = true)
    BigInteger getBalnEarnings();

    @External(readonly = true)
    Map<String, BigInteger> getFeeEarnings();
}
