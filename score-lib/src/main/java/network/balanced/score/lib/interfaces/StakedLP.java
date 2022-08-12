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
import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import score.*;
import score.annotation.External;

import java.math.BigInteger;

@ScoreInterface
@ScoreClient
public interface StakedLP extends AddressManager, AdminAddress {
      @External(readonly = true)
      BigInteger balanceOf(Address _owner, BigInteger _id);

      @External(readonly = true)
      BigInteger totalStaked(BigInteger _id);

      @External
      void addPool(BigInteger id);

      @External
      void removePool(BigInteger id);

      @External(readonly = true)
      boolean isSupportedPool(BigInteger id);
  
      @External
      void unstake(BigInteger id, BigInteger value);
  
      @External
      void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data);
}
