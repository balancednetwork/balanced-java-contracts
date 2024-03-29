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

package network.balanced.score.lib.interfaces.tokens;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Version;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface AssetToken extends IRC2, AddressManager, Version {
    @External
    void govHubTransfer(String _from, String _to, BigInteger _value, @Optional byte[] _data);

    @External
    void burnFrom(String _account, BigInteger _amount);

    @External
    void mintAndTransfer(String _from, String _to, BigInteger _amount, @Optional byte[] _data);
}
