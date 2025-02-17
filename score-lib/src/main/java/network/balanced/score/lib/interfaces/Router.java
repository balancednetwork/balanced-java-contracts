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
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface Router extends Name, AddressManager,
        TokenFallback, Fallback, Version {

    @Payable
    @External
    void route(Address[] path, @Optional BigInteger _minReceive, @Optional String _receiver, @Optional byte[] _data);

    @Payable
    @External
    void routeV2(byte[] _path, @Optional BigInteger _minReceive, @Optional String _receiver, @Optional byte[] _data);
}
