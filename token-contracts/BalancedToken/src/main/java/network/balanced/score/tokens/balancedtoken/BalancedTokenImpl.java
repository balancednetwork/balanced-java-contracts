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

package network.balanced.score.tokens.balancedtoken;

import network.balanced.score.lib.interfaces.BalancedToken;
import network.balanced.score.lib.tokens.HubTokenImpl;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.Names;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.xcall.NetworkAddress;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Math.pow;
import static network.balanced.score.tokens.balancedtoken.Constants.*;
import static network.balanced.score.tokens.balancedtoken.BalancedTokenVariables.*;

public class BalancedTokenImpl extends HubTokenImpl implements BalancedToken {

    protected final VarDB<Address> minter = Context.newVarDB(MINTER, Address.class);

    public BalancedTokenImpl(Address _governance) {
        super("", TOKEN_NAME, SYMBOL_NAME, null);

        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }

        NATIVE_NID = (String)Context.call(BalancedAddressManager.getXCall(), "getNetworkId");

        if (currentVersion.getOrDefault("").equals(Versions.BALN)) {
            Context.revert("Can't Update same version of code");
        }

        currentVersion.set(Versions.BALN);
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
    public void setMinter(Address _address) {
        onlyOwner();
        minter.set(_address);
    }

    @External(readonly = true)
    public Address getMinter() {
        return minter.get();
    }

    @External(readonly = true)
    public BigInteger unstakedBalanceOf(Address _owner) {
        return this.balanceOf(_owner);
    }

    @External(readonly = true)
    public BigInteger stakedBalanceOf(Address _owner) {
        return BigInteger.ZERO;
    }

    @External(readonly = true)
    public BigInteger availableBalanceOf(Address _owner) {
        return this.balanceOf(_owner);
    }

    @External(readonly = true)
    public BigInteger totalStakedBalance() {
        return BigInteger.ZERO;
    }

    @External
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyGovernance();
        super._transfer(new NetworkAddress(NATIVE_NID, _from), new NetworkAddress(NATIVE_NID, _to), _value, _data);
    }



    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus();
        super.transfer(_to, _value, _data);
    }

    @External
    public void hubTransfer(String _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus();
        super.hubTransfer(_to, _value, _data);
    }
    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        Address to = Context.getCaller();
        this.mintTo(to, _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        checkStatus();
        only(minter);

        mintWithTokenFallback(_account, _amount, _data);
    }

    @External
    public void burn(BigInteger _amount) {
        Address from = Context.getCaller();
        this.burnFrom(from, _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        only(minter);
        super.burn(new NetworkAddress(NATIVE_NID, _account), _amount);
    }

    protected void mintWithTokenFallback(Address _to, BigInteger _amount, byte[] _data) {
        mint(new NetworkAddress(NATIVE_NID, _to), _amount);
        byte[] data = (_data == null) ? new byte[0] : _data;
        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", new Address(new byte[Address.LENGTH]), _amount, data);
        }
    }

    @Override
    public BigInteger getHopFee(String net) {
        if (!canWithdraw(net)) {
            return BigInteger.ONE.negate();
        }
        return Context.call(BigInteger.class, BalancedAddressManager.getDaofund(), "claimXCallFee", net, false);
    }

    private boolean canWithdraw(String net) {
        return Context.call(Boolean.class, BalancedAddressManager.getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }
}


