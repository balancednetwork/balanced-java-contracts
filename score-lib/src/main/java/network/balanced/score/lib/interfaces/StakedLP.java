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
import network.balanced.score.lib.annotations.XCall;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreInterface
@ScoreClient
public interface StakedLP extends Version {
    @External(readonly = true)
    Address getDex();

    @External
    void setDex(Address dex);

    @External(readonly = true)
    Address getGovernance();

    @External
    void setGovernance(Address governance);

    @External(readonly = true)
    Address getRewards();

    @External
    void setRewards(Address rewards);

    @External(readonly = true)
    BigInteger balanceOf(Address _owner, BigInteger _id);

    @External(readonly = true)
    BigInteger xBalanceOf(String _owner, BigInteger _id);

    @External(readonly = true)
    BigInteger totalStaked(BigInteger _id);

    @External
    void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols);

    @External
    void unstake(BigInteger id, BigInteger value);

    @XCall
    void xUnstake(String from, BigInteger id, BigInteger value);

    @External
    void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data);

    @External
    void onXIRC31Received(String _operator, String _from, BigInteger _id, BigInteger _value, byte[] _data);

    @External
    void addDataSource(BigInteger id, String name);

    @External(readonly = true)
    Map<String, BigInteger> getBalanceAndSupply(String _name, String _owner);

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

    @External(readonly = true)
    String getSourceName(BigInteger id);

    @External(readonly = true)
    BigInteger getSourceId(String name);

    @External(readonly = true)
    List<BigInteger> getAllowedPoolIds();

    @External(readonly = true)
    boolean isSupportedPool(BigInteger id);

    @External(readonly = true)
    List<String> getDataSources();
}
