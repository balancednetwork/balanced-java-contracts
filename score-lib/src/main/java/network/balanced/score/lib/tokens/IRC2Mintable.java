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

import network.balanced.score.lib.interfaces.addresses.MinterAddress;
import network.balanced.score.lib.interfaces.base.IRC2MintableInterface;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;

public class IRC2Mintable extends IRC2Base implements IRC2MintableInterface, MinterAddress {

    private final String MINTER = "admin";

    protected final VarDB<Address> minter = Context.newVarDB(MINTER, Address.class);

    public IRC2Mintable(String _tokenName, String _symbolName, BigInteger _decimals) {
        super(_tokenName, _symbolName, _decimals);
    }

    @External
    public void setMinter(Address _address) {
        onlyOwner();
        minter.set(_address);
    }

    @External(readonly = true)
    public Address getMinter() {
        return minter.get();
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        mintTo(Context.getCaller(), _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        only(minter);
        mintWithTokenFallback(_account, _amount, _data);
    }

    private void mintWithTokenFallback(Address _to, BigInteger _amount, byte[] _data) {
        mint(_to, _amount);
        byte[] data = (_data == null) ? new byte[0] : _data;
        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", ZERO_ADDRESS, _amount, data);
        }
    }

}
