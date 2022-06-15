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

import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.BalnAddress;
import network.balanced.score.lib.interfaces.addresses.GovernanceAddress;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface WorkerToken extends GovernanceAddress, AdminAddress, BalnAddress, TokenFallback, IRC2 {

    @External
    void adminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data);

    @External
    void distribute();

}
