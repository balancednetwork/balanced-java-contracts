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
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.IRC31Base;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Dex extends AdminAddress, AddressManager, Name, Fallback, TokenFallback,
        IRC31Base {

    @External
    void setPoolLpFee(BigInteger _value);

    @External
    void setPoolBalnFee(BigInteger _value);

    @External
    void setIcxConversionFee(BigInteger _value);

    @External
    void setIcxBalnFee(BigInteger _value);

    @External
    void setMarketName(BigInteger _id, String _name);

    @External
    void turnDexOn();

    @External(readonly = true)
    boolean getDexOn();

    @External
    void addQuoteCoin(Address _address);

    @External(readonly = true)
    boolean isQuoteCoinAllowed(Address _address);

    @External(readonly = true)
    BigInteger getDay();

    @External
    void setTimeOffset(BigInteger _delta_time);

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    void cancelSicxicxOrder();

    @External
    void transfer(Address _to, BigInteger _value, BigInteger _id, @Optional byte[] _data);

    @External
    void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data);

    @External
    boolean precompute(BigInteger snap, BigInteger batch_size);

    @External(readonly = true)
    BigInteger getDeposit(Address _tokenAddress, Address _user);

    @External(readonly = true)
    BigInteger getSicxEarnings(Address _user);

    @External(readonly = true)
    BigInteger getPoolId(Address _token1Address, Address _token2Address);

    @External(readonly = true)
    BigInteger getNonce();

    @External(readonly = true)
    List<String> getNamedPools();

    @External(readonly = true)
    BigInteger lookupPid(String _name);

    @External(readonly = true)
    BigInteger getPoolTotal(BigInteger _id, Address _token);

    @External(readonly = true)
    BigInteger totalSupply(BigInteger _id);

    @External
    void delegate(PrepDelegations[] prepDelegations);

    @External(readonly = true)
    Map<String, BigInteger> getFees();

    @External(readonly = true)
    Address getPoolBase(BigInteger _id);

    @External(readonly = true)
    Address getPoolQuote(BigInteger _id);

    @External
    BigInteger getQuotePriceInBase(BigInteger _id);

    @External(readonly = true)
    BigInteger getBasePriceInQuote(BigInteger _id);

    @External(readonly = true)
    BigInteger getPrice(BigInteger _id);

    @External(readonly = true)
    BigInteger getBalnPrice();

    @External(readonly = true)
    BigInteger getSicxBnusdPrice();

    @External(readonly = true)
    BigInteger getBnusdValue(String _name);

    @External(readonly = true)
    BigInteger getPriceByName(String _name);

    @External(readonly = true)
    BigInteger getICXBalance(Address _address);

    @External(readonly = true)
    String getPoolName(BigInteger _id);

    @External(readonly = true)
    Map<String, Object> getPoolStats(BigInteger _id);

    @External(readonly = true)
    BigInteger totalDexAddresses(BigInteger _id);

    @External(readonly = true)
    Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner);

    @External(readonly = true)
    BigInteger balanceOfAt(Address _account, BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa);

    @External(readonly = true)
    BigInteger totalSupplyAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa);

    @External(readonly = true)
    BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa);

    @External(readonly = true)
    BigInteger getTotalValue(String _name, BigInteger _snapshot_id);

    @External
    void permit(BigInteger _id, boolean _permission);

    @External
    void withdraw(Address _token, BigInteger _value);

    @External
    void remove(BigInteger _id, BigInteger _value, @Optional boolean _withdraw);

    @External
    void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
             @Optional boolean _withdraw_unused);

    @External
    void withdrawSicxEarnings(@Optional BigInteger _value);

    @External
    void addLpAddresses(BigInteger _poolId, Address[] _addresses);

    @External(readonly = true)
    BigInteger depositOfUser(Address _owner, Address _token);
}

