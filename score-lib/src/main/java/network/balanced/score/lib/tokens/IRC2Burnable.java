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

import network.balanced.score.lib.interfaces.base.IRC2BurnableInterface;
import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.onlyEither;

public class IRC2Burnable extends IRC2Mintable implements IRC2BurnableInterface {

    public IRC2Burnable(String _name, String _symbol, @Optional BigInteger _decimals) {
        super(_name, _symbol, _decimals);
    }

    @External
    public void burn(BigInteger _amount) {
        burnFrom(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        onlyEither(minter, minter2);
        super.burn(_account, _amount);
    }
}
