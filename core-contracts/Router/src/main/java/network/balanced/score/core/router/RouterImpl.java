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

package network.balanced.score.core.router;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import network.balanced.score.lib.interfaces.Router;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.XCallUtils;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.UserRevertException;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.StringUtils.convertStringToBigInteger;
import static network.balanced.score.lib.utils.BalancedAddressManager.getSicx;
import static network.balanced.score.lib.utils.BalancedAddressManager.getStaking;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDex;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDaofund;
import static network.balanced.score.lib.utils.BalancedAddressManager.getAssetManager;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;

public class RouterImpl implements Router {
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String VERSION = "version";

    public static final int MAX_NUMBER_OF_ITERATIONS = 4;
    private static final Address MINT_ADDRESS = new Address(new byte[Address.LENGTH]);
    public static final String TAG = "Balanced Router";

    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public RouterImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
        }
        BalancedAddressManager.setGovernance(governance.get());
        if (currentVersion.getOrDefault("").equals(Versions.ROUTER)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.ROUTER);
    }

    @External(readonly = true)
    public String name() {
        return Names.ROUTER;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        BalancedAddressManager.resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return BalancedAddressManager.getAddressByName(name);
    }

    private void swap(Address fromToken, Address toToken) {
        if (fromToken == null) {
            Context.require(toToken.equals(getSicx()), TAG + ": ICX can only be traded for sICX");
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.require(balance.compareTo(BigInteger.ZERO) > 0, "Invalid Trade path");
            Context.transfer(getStaking(), balance);
        } else if (toToken == null) {
            if (!fromToken.equals(getSicx())) {
                return;
            }
            JsonObject data = new JsonObject();
            data.add("method", "_swap_icx");
            BigInteger balance = (BigInteger) Context.call(fromToken, "balanceOf", Context.getAddress());
            Context.call(fromToken, "transfer", getDex(), balance, data.toString().getBytes());
        } else {
            JsonObject params = new JsonObject();
            params.add("toToken", toToken.toString());
            JsonObject data = new JsonObject();
            data.add("method", "_swap");
            data.add("params", params);
            BigInteger balance = (BigInteger) Context.call(fromToken, "balanceOf", Context.getAddress());
            Context.call(fromToken, "transfer", getDex(), balance, data.toString().getBytes());
        }
    }

    private void route(String from, Address startToken, Address[] _path, BigInteger _minReceive) {
        Address prevToken = null;
        Address currentToken = startToken;
        BigInteger fromAmount;
        Address fromAddress;
        if (currentToken == null) {
            fromAmount = Context.getBalance(Context.getAddress());
            fromAddress = EOA_ZERO;
        } else {
            fromAmount = (BigInteger) Context.call(currentToken, "balanceOf", Context.getAddress());
            fromAddress = startToken;
        }

        for (Address token : _path) {
            swap(currentToken, token);
            prevToken = currentToken;
            currentToken = token;
        }

        String nativeNid = XCallUtils.getNativeNid();
        NetworkAddress networkAddress = NetworkAddress.valueOf(from, nativeNid);
        if (currentToken == null && prevToken.equals(getSicx())) {
            currentToken = EOA_ZERO;
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.require(balance.compareTo(_minReceive) >= 0,
                    TAG + ": Below minimum receive amount of " + _minReceive);
            Context.require(networkAddress.net().equals(nativeNid), "Receiver must be a ICON address");
            Context.transfer(Address.fromString(networkAddress.account()), balance);
            Route(fromAddress, fromAmount, EOA_ZERO, balance);
            return;
        }

        boolean toNative = currentToken == null;
        if (toNative) {
            currentToken = prevToken;
        }

        BigInteger balance = (BigInteger) Context.call(currentToken, "balanceOf", Context.getAddress());
        Context.require(balance.compareTo(_minReceive) >= 0, TAG + ": Below minimum receive amount of " + _minReceive);

        if (networkAddress.net().equals(nativeNid)) {
            Context.require(!toNative, TAG + ": Native swaps not available to icon from " + currentToken);
            Context.call(currentToken, "transfer", Address.fromString(networkAddress.account()), balance, new byte[0]);
        } else {
            transferCrossChainResult(currentToken, networkAddress, balance, toNative);
        }


        Route(fromAddress, fromAmount, currentToken, balance);
    }


    private void transferCrossChainResult(Address token, NetworkAddress to, BigInteger amount, boolean toNative) {
        String toNet = to.net();
        if (canWithdraw(toNet)) {
            if (token.equals(getBnusd())) {
                transferBnUSD(token, to, amount);
            } else {
                transferHubToken(token, to, amount, toNative);
            }
        } else {
            Context.require(!toNative, TAG + ": Native swaps are not supported for this network");
            Context.call(token, "hubTransfer", to.toString(), amount, new byte[0]);
        }
    }

    private void transferBnUSD(Address bnusd, NetworkAddress to, BigInteger amount) {
        String toNet = to.net();
        BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", toNet, true);
        Context.call(xCallFee, bnusd, "crossTransfer", to.toString(), amount, new byte[0]);
    }

    private void transferHubToken(Address token, NetworkAddress to, BigInteger amount, boolean toNative) {
        String toNet = to.net();
        Address assetManager = getAssetManager();
        String nativeAddress = Context.call(String.class, assetManager, "getNativeAssetAddress", token);
        if (nativeAddress != null && NetworkAddress.valueOf(nativeAddress).net().equals(toNet)) {
            BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", toNet, true);
            String method = "withdrawTo";
            if (toNative) {
                method = "withdrawNativeTo";
            }
            Context.call(xCallFee, assetManager, method, token, to.toString(), amount);
        } else {
            Context.require(!toNative, TAG + ": Native swaps are not supported to other networks");
            Context.call(token, "hubTransfer", to.toString(), amount, new byte[0]);
        }
    }

    private boolean canWithdraw(String net) {
        return Context.call(Boolean.class, getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }

    private void transferResult(Address token, String to, BigInteger amount) {
        String nativeNid = XCallUtils.getNativeNid();
        NetworkAddress networkAddress = NetworkAddress.valueOf(to, nativeNid);
        if (networkAddress.net().equals(nativeNid)) {
            Context.call(token, "transfer", Address.fromString(networkAddress.account()), amount, new byte[0]);
            return;
        }

        String toNet = NetworkAddress.valueOf(to).net();
        if (canWithdraw(toNet)) {
            if (token.equals(getBnusd())) {
                transferBnUSD(token, to, amount);
            } else {
                transferHubToken(token, to, amount);
            }
        } else {
            Context.call(token, "hubTransfer", to, amount, new byte[0]);
        }
    }

    private void transferBnUSD(Address bnusd, String to, BigInteger amount) {
        String toNet = NetworkAddress.valueOf(to).net();
        BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", toNet, true);
        Context.call(xCallFee, bnusd, "crossTransfer", to, amount, new byte[0]);
    }

    private void transferHubToken(Address token, String to, BigInteger amount) {
        String toNet = NetworkAddress.valueOf(to).net();
        Address assetManager = getAssetManager();
        String nativeAddress = Context.call(String.class, assetManager, "getNativeAssetAddress", token);
        if (nativeAddress != null && NetworkAddress.valueOf(nativeAddress).net().equals(toNet)) {
            BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", toNet, true);
            Context.call(xCallFee, assetManager, "withdrawTo", token, to, amount);
        } else {
            Context.call(token, "hubTransfer", to, amount, new byte[0]);
        }
    }

    private boolean canWithdraw(String net) {
        return Context.call(Boolean.class, getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }

    @Payable
    @External
    public void route(Address[] _path, @Optional BigInteger _minReceive, @Optional String _receiver) {
        if (_minReceive == null) {
            _minReceive = BigInteger.ZERO;
        }
        if (_receiver == null || _receiver.equals("")) {
            _receiver = Context.getCaller().toString();
        }

        Context.require(_minReceive.signum() >= 0, TAG + ": Must specify a positive number for minimum to receive");
        Context.require(_path.length <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS);

        route(_receiver, null, _path, _minReceive);
    }

    /**
     * This is invoked when a token is transferred to this score. It expects a JSON
     * object with the following format:
     * <blockquote>
     * {"method": "METHOD_NAME", "params":{...}}
     * </blockquote>
     *
     * @param _from  The address calling `transfer` on the other contract
     * @param _value Amount of token transferred
     * @param _data  Data called by the transfer, json object expected.
     */
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        xTokenFallback(_from.toString(), _value, _data);
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        // Receive token transfers from Balanced DEX and staking while in mid-route
        if (_from.equals(getDex().toString()) || _from.equals(MINT_ADDRESS.toString())) {
            return;
        }

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");
        JsonObject json = Json.parse(unpackedData).asObject();
        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();
        Context.require(method.contains("_swap"), TAG + ": Fallback directly not allowed.");

        BigInteger minimumReceive = BigInteger.ZERO;
        if (params.contains("minimumReceive")) {
            JsonValue minimumReceiveValue = params.get("minimumReceive");
            if (minimumReceiveValue.isString()) {
                minimumReceive = convertStringToBigInteger(minimumReceiveValue.asString());
            } else if (minimumReceiveValue.isNumber()) {
                minimumReceive = new BigInteger(minimumReceiveValue.toString());
            } else {
                throw new UserRevertException(TAG + ": Invalid value format for minimum receive amount");
            }
            Context.require(minimumReceive.signum() >= 0, TAG + ": Must specify a positive number for minimum to " +
                    "receive");
        }
        String receiver;
        if (params.contains("receiver")) {
            receiver = params.get("receiver").asString();
        } else {
            receiver = _from;
        }

        JsonArray pathArray = params.get("path").asArray();
        Context.require(pathArray.size() <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS);

        Address[] path = new Address[pathArray.size()];

        for (int i = 0; i < pathArray.size(); i++) {
            JsonValue addressJsonValue = pathArray.get(i);
            if (addressJsonValue == null || addressJsonValue.toString().equals("null")) {
                path[i] = null;
            } else {
                path[i] = Address.fromString(addressJsonValue.asString());
            }
        }

        Address fromToken = Context.getCaller();
        route(receiver, fromToken, path, minimumReceive);
    }

    @Payable
    public void fallback() {
    }

    @EventLog(indexed = 1)
    public void Route(Address from, BigInteger fromAmount, Address to, BigInteger toAmount) {
    }
}
