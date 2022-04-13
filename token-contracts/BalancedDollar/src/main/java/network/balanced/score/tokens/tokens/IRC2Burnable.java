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

package network.balanced.score.tokens.tokens;

import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;

public class IRC2Burnable extends IRC2{
    /**
     * @param _tokenName     : The name of the token.
     * @param _symbolName    : The symbol of the token.
     * @param _initialSupply :The total number of tokens to initialize with.
     *                       It is set to total supply in the beginning, 0 by default.
     * @param _decimals      The number of decimals. Set to 18 by default.
     */
    public IRC2Burnable(String _tokenName, String _symbolName, BigInteger _initialSupply, BigInteger _decimals) {
        super(_tokenName, _symbolName, _initialSupply, _decimals);
    }

    /**
     * Destroys `_amount` number of tokens from the caller account.
     * 		Decreases the balance of that account and total supply.
     * @param _amount Number of tokens to be destroyed.
     */
    @External
    public void burn(BigInteger _amount) {
        burn(Context.getCaller(), _amount);
    }

    /**
     * Destroys `_amount` number of tokens from the specified `_account` account.
     * 		Decreases the balance of that account and total supply.
     * 		See {IRC2-_burn}
     * @param _account The account at which token is to be destroyed.
     * @param _amount Number of tokens to be destroyed at the `_account`.
     */
    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        burn(_account, _amount);
    }
}
