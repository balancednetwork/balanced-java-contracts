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
import network.balanced.score.lib.tokens.HubTokenImpl;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import foundation.icon.xcall.NetworkAddress;

import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.checkStatus;
import static network.balanced.score.lib.utils.Check.only;

public class SicxImpl extends HubTokenImpl implements Sicx {
    private static final String TAG = "sICX";
    private static final String TOKEN_NAME = Names.SICX;
    private static final String SYMBOL_NAME = "sICX";
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String STAKING = "staking";
    public static final String STATUS_MANAGER = "status_manager";
    private static final String VERSION = "version";
    private final String MINTER = "admin";

    private static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);
    private final VarDB<Address> statusManager = Context.newVarDB(STATUS_MANAGER, Address.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    protected final VarDB<Address> minter = Context.newVarDB(MINTER, Address.class);

    public SicxImpl(Address _admin, Address _governance) {
        super("",TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (stakingAddress.get() == null) {
            stakingAddress.set(_admin);
        }
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }
        NATIVE_NID = (String)Context.call(BalancedAddressManager.getXCall(), "getNetworkId");
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
        super.transfer(_to, _value, _data);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus(statusManager);
        if (!_to.equals(stakingAddress.get())) {
            Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        super.transfer(_to, _value, _data);
    }

    @External
    public void burn(BigInteger _amount) {
        burnFrom(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        only(minter);
        super.burn(new NetworkAddress(NATIVE_NID, _account), _amount);
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

    protected void mintWithTokenFallback(Address _to, BigInteger _amount, byte[] _data) {
        mint(new NetworkAddress(NATIVE_NID, _to), _amount);
        byte[] data = (_data == null) ? new byte[0] : _data;
        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", new Address(new byte[Address.LENGTH]), _amount, data);
        }
    }

}
