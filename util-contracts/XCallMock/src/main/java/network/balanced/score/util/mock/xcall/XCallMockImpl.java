/*
 * Copyright (c) 2023 Balanced.network.
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

package network.balanced.score.util.mock.xcall;

import network.balanced.score.lib.interfaces.XCallMock;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;

public class XCallMockImpl implements XCallMock {
    public static String nid;

    private final VarDB<BigInteger> sn = Context.newVarDB("sn", BigInteger.class);
    private final DictDB<BigInteger, byte[]> rollbacks = Context.newDictDB("rollback", byte[].class);
    private final DictDB<BigInteger, Address> rollbackCaller = Context.newDictDB("rollback_caller", Address.class);

    public XCallMockImpl(String networkId) {
        nid = networkId;
    }

    /* Implementation-specific external */
    @External(readonly = true)
    public String getNetworkId() {
        return nid;
    }


    private BigInteger getNextSn() {
        BigInteger _sn = this.sn.getOrDefault(BigInteger.ZERO);
        _sn = _sn.add(BigInteger.ONE);
        this.sn.set(_sn);
        return _sn;
    }

    @Payable
    @External
    public BigInteger sendCallMessage(String _to,
                                    byte[] _data,
                                    @Optional byte[] _rollback,
                                    @Optional String[] _sources,
                                    @Optional String[] _destinations) {
        BigInteger sn = getNextSn();
        if (_rollback != null) {
            rollbacks.set(sn, _rollback);
            rollbackCaller.set(sn, Context.getCaller());
        }
        String net = NetworkAddress.valueOf(_to).net();
        Context.require(Context.getValue().equals(getFee(net, _rollback != null, _sources)), "Not enough fee");
        CallMessage(sn, _to, _data);
        return sn;
    }

    @External
    public void sendCall(Address to, String from, byte[] message) {
        Context.call(to, "handleCallMessage", from, message);
    }

    @External
    public void rollback(BigInteger _sn) {
        Context.call(rollbackCaller.get(_sn), "handleCallMessage", new NetworkAddress(nid, Context.getAddress()), rollbacks.get(_sn));
        rollbacks.set(_sn, null);
        rollbackCaller.set(_sn, null);
    }

    @External(readonly = true)
    public BigInteger getFee(String net, boolean response, @Optional String[] _sourceProtocols) {
        return BigInteger.ONE;
    }

    @EventLog(indexed = 1)
    public void CallMessage(BigInteger _sn, String to, byte[] data) {
    }
}
