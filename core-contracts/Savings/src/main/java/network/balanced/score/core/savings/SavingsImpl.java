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

package network.balanced.score.core.savings;

import static network.balanced.score.lib.utils.BalancedAddressManager.getAddressByName;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBSR;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;
import static network.balanced.score.lib.utils.BalancedAddressManager.resetAddress;
import static network.balanced.score.lib.utils.Check.checkStatus;
import static network.balanced.score.lib.utils.Check.onlyGovernance;
import static network.balanced.score.lib.utils.Constants.EXA;

import java.math.BigInteger;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

public class SavingsImpl  {
    public static final String LOCKED_SAVINGS = "Locked savings";
    public static final String VERSION = "version";

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    public static final String TAG = Names.SAVINGS;

    public SavingsImpl(Address _governance) {
        BalancedAddressManager.setGovernance(_governance);

        if (this.currentVersion.getOrDefault("").equals(Versions.DAOFUND)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.DAOFUND);
    }

    @External(readonly = true)
    public String name() {
        return Names.SAVINGS;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External(readonly = true)
    public BigInteger getRate() {
        BigInteger bnUSDDeposits = getBnUSDBalance();
        if (bnUSDDeposits.equals(BigInteger.ZERO)) {
            return EXA;
        }

        BigInteger totalSupply = getBSRSupply();
        return bnUSDDeposits.multiply(EXA).divide(totalSupply);
    }

    private BigInteger getBSRSupply() {
        return Context.call(BigInteger.class, getBSR(), "xTotalSupply");
    }

    private BigInteger getBnUSDBalance() {
        return Context.call(BigInteger.class, getBnusd(), "balanceOf", Context.getAddress());
    }

    @External 
    public void unlock(BigInteger amount) {
        Context.require(amount.compareTo(BigInteger.ZERO) > 0, "Cannot unlock a negative or zero amount");
        RewardsManager.changeLock(Context.getCaller().toString(), amount.negate());
    }

    @External 
    public void claimRewards() {
        RewardsManager.claimRewards(Context.getCaller());
    }

    @External(readonly = true) 
    public Map<String, BigInteger> getUnclaimedRewards(String user) {
        return RewardsManager.getUnclaimedRewards(user);
    }

    @External(readonly = true)
    public BigInteger getLockedAmount(String user) {
        return RewardsManager.getLockedAmount(user);
    }

    @External
    public void addAcceptedToken(Address token) {
        onlyGovernance();
        RewardsManager.addToken(token);
    }

    @External
    public void removeAcceptedToken(Address token) {
        onlyGovernance();
        RewardsManager.removeToken(token);
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        handleTokenDeposit(_from, _value, _data);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        handleTokenDeposit(_from.toString(), _value, _data);
    }

    private void handleTokenDeposit(String _from, BigInteger _value, byte[] _data) {
        checkStatus();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, "Zero transfers not allowed");

        Address token = Context.getCaller();
        if (_data == null || _data.length == 0) {
            handleRewards(token, _value);
            return;
        }

        String unpackedData  = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");
        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        switch (method) {
            case "_deposit": {
                Context.require(token.equals(getBnusd()));
                BigInteger bnUSDBalance = getBnUSDBalance().subtract(_value);
                BigInteger bsrToMint;
                if (bnUSDBalance.equals(BigInteger.ZERO)) {
                    bsrToMint =_value;
                } else {
                    bsrToMint = (_value.multiply(getBSRSupply())).divide(getBnUSDBalance().subtract(_value));
                }

                Context.call(getBSR(), "mintTo", _from, bsrToMint, new byte[0]);
                break;
            }
            case "_lock": {
                Context.require(token.equals(getBSR()), "Only BSR can be locked");
                RewardsManager.changeLock(_from, _value);
                break;
            }
            case "_withdraw": {
                Context.require(token.equals(getBSR()), "Only BSR can be withdrawn");

                BigInteger rate = getRate();
                BigInteger amountToWithdraw = _value.multiply(rate).divide(EXA);
                Context.call(getBSR(), "burn", _value);
                Context.call(getBnusd(), "hubTransfer", _from, amountToWithdraw, new byte[0]);
                break;
            }
            default:
                Context.revert(100, "Unsupported method supplied");
                break;
        }
    }

    private void handleRewards(Address token, BigInteger value) {
        if (token.equals(getBnusd())) {
            return;
        };

        Context.require(!token.equals(getBSR()), "BSR can't be a rewards tokens");
        RewardsManager.addWeight(token, value);
    }
}
