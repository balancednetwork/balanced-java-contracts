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
import static network.balanced.score.lib.utils.Check.checkStatus;

public class SicxImpl extends IRC2Burnable implements Sicx {
    private static final String TAG = "sICX";
    private static final String TOKEN_NAME = Names.SICX;
    private static final String SYMBOL_NAME = "sICX";
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String STAKING = "staking";
    public static final String STATUS_MANAGER = "status_manager";
    private static final String VERSION = "version";

    private static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);
    private final VarDB<Address> statusManager = Context.newVarDB(STATUS_MANAGER, Address.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public SicxImpl(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (stakingAddress.get() == null) {
            stakingAddress.set(_admin);
        }

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

    @External
    public void setEmergencyManager(Address _address) {
        onlyOwner();
        statusManager.set(_address);
    }

    @External(readonly = true)
    public Address getEmergencyManager() {
        return statusManager.get();
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
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwner();
        if (!_to.equals(stakingAddress.get())) {
            Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        transfer(_from, _to, _value, _data);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus(statusManager);
        if (!_to.equals(stakingAddress.get())) {
            Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        transfer(Context.getCaller(), _to, _value, _data);
    }

}
