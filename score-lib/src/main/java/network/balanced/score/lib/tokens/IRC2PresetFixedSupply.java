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

package network.balanced.score.lib.tokens;

import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.pow;

public class IRC2PresetFixedSupply extends IRC2Base {

    public IRC2PresetFixedSupply(String _tokenName, String _symbolName, BigInteger _initialSupply,
                                 BigInteger _decimals) {
        super(_tokenName, _symbolName, _decimals);
        if (totalSupply().equals(BigInteger.ZERO)) {
            Context.require(_initialSupply.compareTo(BigInteger.ZERO) > 0, "Initial Supply cannot be less than or " +
                    "equal to than zero");

            BigInteger totalSupply = _initialSupply.multiply(pow(BigInteger.TEN, _decimals.intValue()));
            final Address caller = Context.getCaller();
            mint(caller, totalSupply);
        }
    }

}
