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

package network.balanced.score.tokens.sicx;

import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.tokens.IRC2Burnable;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.onlyOwner;

public class SicxImpl extends IRC2Burnable implements Sicx {
    private static final String TAG = "sICX";
    private static final String TOKEN_NAME = Names.SICX;
    private static final String SYMBOL_NAME = "sICX";
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String STAKING = "staking";

    private static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);

    public SicxImpl(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (stakingAddress.get() == null) {
            stakingAddress.set(_admin);
        }
    }

    @External(readonly = true)
    public String getPeg() {
        return TAG;
    }

    @External
    public void setStaking(Address _address) {
        onlyOwner();
        stakingAddress.set(_address);
    }

    @External(readonly = true)
    public Address getStaking() {
        return stakingAddress.get();
    }

    @External(readonly = true)
    public BigInteger priceInLoop() {
        return (BigInteger) Context.call(stakingAddress.get(), "getTodayRate");
    }

    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        return priceInLoop();
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        return;
        // if (!_to.equals(stakingAddress.get())) {
        //     Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        // }
        // transfer(Context.getCaller(), _to, _value, _data);
    }

}
