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

package network.balanced.score.core.rebalancing;

import score.Address;
import score.annotation.External;

import foundation.icon.score.client.ScoreInterface;

import java.math.BigInteger;
import java.util.List;

//TODO should be moved to a balanced interface folder where all interfaces are kept
@ScoreInterface
public interface Rebalancing {
    @External
    void setBnusd(Address _address);
    
    @External
    void setLoans(Address _address);

    @External
    void setSicx(Address _address);
    
    @External
    void setGovernance(Address _address);

    @External
    void setDex(Address _address);

    @External
    void setAdmin(Address _address);

    @External(readonly = true)
    Address getGovernance();

    @External(readonly = true)
    Address getAdmin();

    @External(readonly = true)
    Address getLoans();

    @External(readonly = true)
    Address getBnusd();

    @External(readonly = true)
    Address getSicx();

    @External(readonly = true)
    Address getDex();

    @External
    void setPriceDiffThreshold(BigInteger _value);

    @External(readonly = true)
    BigInteger getPriceChangeThreshold();

    @External(readonly = true)
    List<Object> getRebalancingStatus();

    @External
    void rebalance();

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);
}