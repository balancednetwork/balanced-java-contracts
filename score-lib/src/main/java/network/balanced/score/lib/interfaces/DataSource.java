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

import foundation.icon.score.client.ScoreInterface;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

@ScoreInterface
public interface DataSource {
    @External
    Object precompute(BigInteger _snapshot_id, BigInteger batch_size);

    @External(readonly = true)
    BigInteger getTotalValue(String _name, BigInteger _snapshot_id);

    @External
    BigInteger getBnusdValue(String _name);

    @External
    Map<String, BigInteger> getDataBatch(String _name, int _snapshot_id, int _limit, int _offset);

    @External
    BigInteger getBalnPrice();

    @External
    Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner);
}