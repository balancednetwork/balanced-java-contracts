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
import network.balanced.score.lib.interfaces.addresses.StakingAddress;
import network.balanced.score.lib.interfaces.base.Version;
import network.balanced.score.lib.interfaces.tokens.HubToken;
import network.balanced.score.lib.interfaces.tokens.IRC2BurnableInterface;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface Sicx extends StakingAddress, HubToken, IRC2BurnableInterface, Version {
    @External
    void setEmergencyManager(Address _address);

    @External(readonly = true)
    Address getEmergencyManager();

    @External(readonly = true)
    String getPeg();

    @External(readonly = true)
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();

    @External
    void setMinter(Address _address);

    @External(readonly = true)
    Address getMinter();

    @External
    void mint(BigInteger _amount, @Optional byte[] _data);

    @External
    void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data);
}
