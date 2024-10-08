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

package network.balanced.score.lib.interfaces.base;

import score.Address;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;

public interface IRC31Base {

    @External(readonly = true)
    BigInteger balanceOf(Address _owner, BigInteger _id);

//    @EventLog(indexed = 3)
//    void TransferSingle(Address _operator, Address _from, Address _to, BigInteger _id, BigInteger _value);
}
