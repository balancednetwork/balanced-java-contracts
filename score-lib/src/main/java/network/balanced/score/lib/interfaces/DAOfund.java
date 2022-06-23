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
import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.GovernanceAddress;
import network.balanced.score.lib.interfaces.addresses.LoansAddress;
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.Disbursement;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.score.client.ScoreInterface;

@ScoreClient
@ScoreInterface
public interface DAOfund extends Name, GovernanceAddress, AdminAddress, LoansAddress, TokenFallback, Fallback {

    @External
    void addAddressToSetdb();

    @External(readonly = true)
    Map<String, BigInteger> getBalances();

    @External(readonly = true)
    Map<String, Object> getDisbursementDetail(Address _user);

    @External(readonly = true)
    boolean disburse(Address _recipient, Disbursement[] _amounts);

    @External
    void claim();
}
