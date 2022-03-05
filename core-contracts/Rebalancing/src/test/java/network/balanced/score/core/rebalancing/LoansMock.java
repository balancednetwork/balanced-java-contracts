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

import java.math.BigInteger;
import java.util.Map;

public class LoansMock {

    public static BigInteger price = BigInteger.valueOf(1000000);

    @External
    public void raisePrice(BigInteger _totalTokensRequired) {
        price = price.add(_totalTokensRequired);
    }

    @External
    public void lowerPrice(BigInteger _totalTokensRequired) {
        price = price.subtract(_totalTokensRequired);
    }

    @External(readonly=true)
    public BigInteger getPrice() {
        return price;
    }
}

