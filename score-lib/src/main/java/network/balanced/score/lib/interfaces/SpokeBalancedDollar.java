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

import network.balanced.score.lib.annotations.XCall;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.Version;
import score.annotation.External;
import score.annotation.Payable;
import score.Address;

import java.math.BigInteger;

public interface SpokeBalancedDollar extends Name, Version {
    @External
    @Payable
    void crossTransfer(String _to, BigInteger _value, byte[] _data);

    @XCall
    void xCrossTransfer(String from, String _from, String _to, BigInteger _value, byte[] _data);

    @XCall
    void xCrossTransferRevert(String from, Address _to, BigInteger _value);

    @External
    void setXCallManager(Address address);

    @External(readonly = true)
    Address getXCallManager();

    @External
    void setICONBnUSD(String address);

    @External(readonly = true)
    String getICONBnUSD();

    @External
    void setXCall(Address address);

    @External(readonly = true)
    Address getXCall();
}

