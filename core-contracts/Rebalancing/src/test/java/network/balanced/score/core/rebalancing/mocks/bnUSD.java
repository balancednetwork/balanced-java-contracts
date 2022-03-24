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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.token.irc2.IRC2Mintable;
import score.Context;
import score.VarDB;
import score.annotation.External;
import java.math.BigInteger;

public class bnUSD extends IRC2Mintable {
    public static final VarDB<BigInteger> lastPriceInLoop = Context.newVarDB("last_price", BigInteger.class);

    public bnUSD (String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
        super(_name, _symbol, _decimals);
    }

    @External
    public void setLastPriceInLoop(BigInteger _lastPriceInLoop) {
        lastPriceInLoop.set(_lastPriceInLoop);
    }

    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        return lastPriceInLoop.get();
    }
}