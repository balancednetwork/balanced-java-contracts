
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
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.Disbursement;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Reserve extends Name, TokenFallback, AddressManager {
    @External
    void redeem(Address to, BigInteger _valueInLoop, String collateralSymbol);

    @External(readonly = true)
    Map<String, BigInteger> getBalances();

    @External
    boolean disburse(Address _recipient, Disbursement[] _amounts);

    @External
    void claim();

    @External
    void transfer(Address _tokenAddress, Address _targetAddress, BigInteger _amount);
}

