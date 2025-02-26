/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.tokens.wicx;

import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.BalancedAddressManager;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;

public class WICX extends PayableIRC2Base {

    private static final String TOKEN_NAME = Names.WICX;
    private static final String SYMBOL_NAME = "wICX";
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String VERSION = "version";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public WICX(Address _governance) {
        super(TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }
        if (currentVersion.getOrDefault("").equals(Versions.WICX)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.WICX);
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @Override
    @Payable
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        Address from = Context.getCaller();
        BigInteger deposit = getICXDeposit();
        if (deposit.compareTo(BigInteger.ZERO) > 0) {
            mint(from, deposit);
        }
        super.transfer(_to, _value, _data);

        if (!_to.isContract()) {
            burn(_to, _value);
            Context.transfer(_to, _value);
        }
    }

    @External
    public void unwrap(BigInteger amount) {
        Address from = Context.getCaller();
        burn(from, amount);
        Context.transfer(from, amount);
    }

    @Payable
    public void fallback() {
        Address from = Context.getCaller();
        BigInteger deposit = getICXDeposit();
        if (deposit.compareTo(BigInteger.ZERO) > 0) {
            mint(from, deposit);
        }
    }

    BigInteger getICXDeposit() {
        return Context.getValue();
    }

}
