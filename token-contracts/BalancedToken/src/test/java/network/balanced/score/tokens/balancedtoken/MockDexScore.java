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

package network.balanced.score.tokens.balancedtoken;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;

public class MockDexScore {

    private final VarDB<Boolean> firstCall = Context.newVarDB("first_call_dex", Boolean.class);

    @External(readonly = true)
    private String name() {
        return "DEX Token";
    }

    @External
    public void transfer(Address _to, BigInteger _value) {
        Context.println(name() + "| transferred: " + _value + " to: " + _to);
    }

    @External
    public BigInteger getTimeOffset() {
        if (firstCall.getOrDefault(true)) {
            firstCall.set(false);
            return BigInteger.valueOf(30 * 60 * 1000);
        } else {
            return BigInteger.valueOf(60 * 60 * 1000);
        }

    }
}
