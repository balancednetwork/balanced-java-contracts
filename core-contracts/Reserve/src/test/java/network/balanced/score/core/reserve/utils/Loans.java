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

package network.balanced.score.core.reserve.utils;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class Loans {
    public static final VarDB<Address> reserve = Context.newVarDB("RESERVE", Address.class);

    public Loans(Address address) {
        reserve.set(address);

    }

    @External
    public void redeem(Address _to, BigInteger _amount, BigInteger _sicx_rate) {
        Context.call(reserve.get(), "redeem", _to, _amount, _sicx_rate);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {

    }
}
