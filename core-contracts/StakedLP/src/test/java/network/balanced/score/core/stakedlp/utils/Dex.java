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

package network.balanced.score.core.stakedlp.utils;

import com.iconloop.score.token.irc31.IRC31MintBurn;
import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

public class Dex extends IRC31MintBurn {

    public static final Map<BigInteger, String> poolNames = Map.of(BigInteger.ONE, "sICX/bnUSD", BigInteger.TWO,
            "BALN/bnUSD");

    public Dex() {

    }

    @External(readonly = true)
    public String getPoolName(BigInteger id) {
        return poolNames.getOrDefault(id, "");
    }

    @External
    public void transfer(Address _to,BigInteger _value,BigInteger _id,byte[] _data) {
        this.transferFrom(Context.getCaller(), _to, _id, _value, _data);
    }

}
