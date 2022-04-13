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

package network.balanced.score.tokens;

import network.balanced.score.tokens.tokens.IRC2Mintable;
import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;

public class DummyContract extends IRC2Mintable {

    public DummyContract() {
        super("Dummy", "Dummy", BigInteger.valueOf(30), BigInteger.valueOf(18));
    }

    @External
    public BigInteger testPriceInLoop(Address address) {
        return (BigInteger) Context.call(address, "priceInLoop");
    }
}
