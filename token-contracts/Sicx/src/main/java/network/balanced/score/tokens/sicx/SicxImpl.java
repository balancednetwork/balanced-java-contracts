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

package network.balanced.score.tokens.sicx;

import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.tokens.IRC2Burnable;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.DictDB;
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
    private static final String VERSION = "version";

    private static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);

    private static final String BLACKLIST = "blacklist";
    private static final DictDB<Address, Boolean> blackListed = Context.newDictDB(BLACKLIST, Boolean.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public SicxImpl(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (stakingAddress.get() == null) {
            stakingAddress.set(_admin);
        }

//        blackListed.set(Address.fromString("hxc35cffe7c582cb313820fa6838dd357027ad3d07"), true);
//        blackListed.set(Address.fromString("hx51f13e696c1b0d17f57696bdc22c6cd697706086"), true);
//        blackListed.set(Address.fromString("hxc308be82c57c7190ce623a3f39e0db39c7aa93ab"), true);
//        blackListed.set(Address.fromString("hx2cb7cfad74447a5f47f109690599a1916f349a52"), true);
//        blackListed.set(Address.fromString("hxd5271567e1121bdba855cbedd12163cb38e48e65"), true);
//        blackListed.set(Address.fromString("cxfb312bbd0a244b9e7bb5794c91f4e4acc41dea94"), true);
        if (currentVersion.getOrDefault("").equals(Versions.SICX)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.SICX);
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
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

    @External
    public void blackList(Address address, boolean blacklist) {
        onlyOwner();
        blackListed.set(address, blacklist);
    }

    @External
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwner();
        transfer(_from, _to, _value, _data);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        Context.require(!blackListed.getOrDefault(Context.getCaller(), false), "Blacklisted");
        if (!_to.equals(stakingAddress.get())) {
            Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        transfer(Context.getCaller(), _to, _value, _data);
    }

}
