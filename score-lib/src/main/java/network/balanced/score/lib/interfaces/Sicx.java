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

import network.balanced.score.lib.interfaces.addresses.OracleAddress;
import network.balanced.score.lib.interfaces.addresses.StakingAddress;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;

@ScoreClient
@ScoreInterface
public interface Sicx extends StakingAddress, IRC2, OracleAddress {

    @External(readonly = true)
    String getPeg();

    @External(readonly = true)
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();

    @External(readonly = true)
    Address getAdmin();
    
    @External
    void setOracleName(String _name);
      
    @External
    void setMinInterval(BigInteger _interval);

}
