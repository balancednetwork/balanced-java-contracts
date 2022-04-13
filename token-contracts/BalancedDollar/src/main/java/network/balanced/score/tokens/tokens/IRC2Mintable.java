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
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public class IRC2Mintable extends IRC2Burnable{
    /**
     * @param _tokenName     : The name of the token.
     * @param _symbolName    : The symbol of the token.
     * @param _initialSupply :The total number of tokens to initialize with.
     *                       It is set to total supply in the beginning, 0 by default.
     * @param _decimals      The number of decimals. Set to 18 by default.
     */
    public IRC2Mintable(String _tokenName, String _symbolName, BigInteger _initialSupply, BigInteger _decimals) {
        super(_tokenName, _symbolName, _initialSupply, _decimals);
    }


    /**
     * Creates `_amount` number of tokens, and assigns to caller account.
     * Increases the balance of that account and total supply.
     * @param _amount  Number of tokens to be created at the account.
     */
    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        if (_data == null) {
            String data = "None";
            _data = data.getBytes();
        }
    }

    /**
     * Creates `_amount` number of tokens, and assigns to `_account`.
     * 		Increases the balance of that account and total supply.
     * @param _account The account at which token is to be created.
     * @param _amount Number of tokens to be created at the account.
     */
    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        mint(_account, _amount, _data);
    }
}
