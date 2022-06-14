
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
import score.annotation.External;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.BalnAddress;
import network.balanced.score.lib.interfaces.addresses.LoansAddress;
import network.balanced.score.lib.interfaces.addresses.SicxAddress;
import network.balanced.score.lib.interfaces.base.TokenFallback;

@ScoreClient
@ScoreInterface
public interface Reserve extends TokenFallback, AdminAddress, BalnAddress, SicxAddress, LoansAddress {

    @External
    public void redeem(Address to, BigInteger amount, BigInteger icxRate);

    @External(readonly = true)
    public Map<String, BigInteger> getBalances();
    
}

