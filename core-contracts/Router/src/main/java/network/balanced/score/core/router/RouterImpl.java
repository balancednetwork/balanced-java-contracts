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
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.Router;
import network.balanced.score.lib.structs.Route;
import network.balanced.score.lib.structs.RouteAction;
import network.balanced.score.lib.structs.RouteData;
import network.balanced.score.lib.utils.*;
import score.Address;
import score.Context;
import score.UserRevertException;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.StringUtils.convertStringToBigInteger;

public class RouterImpl implements Router {
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String VERSION = "version";

    public static final int MAX_NUMBER_OF_ITERATIONS = 4;
    private static final Address MINT_ADDRESS = new Address(new byte[Address.LENGTH]);
    public static final String TAG = "Balanced Router";

    public static final byte[] EMPTY_DATA = "None".getBytes();
    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    // ENUM of actions
    static final int SWAP = 1;
    static final int STABILITY_SWAP = 2;
    public boolean inRoute = false;

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

    private void swap(Address fromToken, Address toToken, int action) {
        if (action == SWAP) {
            swapDefault(fromToken, toToken);
        } else if (action == STABILITY_SWAP) {
            swapStable(fromToken, toToken);
        }
    }

    private void swapStable(Address fromToken, Address toToken) {
        BigInteger balance = (BigInteger) Context.call(fromToken, "balanceOf", Context.getAddress());
        Context.call(fromToken, "transfer", getStabilityFund(), balance, toToken.toString().getBytes());
    }

    private void swapDefault(Address fromToken, Address toToken) {
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

    private void route(String from, Address startToken, List<RouteAction> _path, BigInteger _minReceive) {
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

        inRoute = true;
        for (RouteAction action : _path) {
            swap(currentToken, action.toAddress, action.action);
            prevToken = currentToken;
            currentToken = action.toAddress;
        }

        inRoute = false;
        String nativeNid = XCallUtils.getNativeNid();
        NetworkAddress networkAddress = NetworkAddress.valueOf(from, nativeNid);
        if (currentToken == null && prevToken.equals(getSicx())) {
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.require(balance.compareTo(_minReceive) >= 0,
                    TAG + ": Below minimum receive amount of " + _minReceive);
            Context.require(networkAddress.net().equals(nativeNid), "Receiver must be a ICON address");
            Context.transfer(Address.fromString(networkAddress.account()), balance);
            Route(fromAddress, fromAmount, EOA_ZERO, balance);
            return;
        }

        BigInteger balance = (BigInteger) Context.call(currentToken, "balanceOf", Context.getAddress());
        Context.require(balance.compareTo(_minReceive) >= 0, TAG + ": Below minimum receive amount of " + _minReceive);

        byte[] data = new byte[0];
        if(networkAddress.net().equals(nativeNid)){
            data = EMPTY_DATA;
        }

        TokenTransfer.transfer(currentToken, networkAddress.toString(), balance, data);
        Route(fromAddress, fromAmount, currentToken, balance);
    }


    @Payable
    @External
    public void route(Address[] _path, @Optional BigInteger _minReceive, @Optional String _receiver) {
        Context.require(!inRoute);
        validateRoutePayload(_path.length, _minReceive);

        if (_receiver == null || _receiver.equals("")) {
            _receiver = Context.getCaller().toString();
        }
        List<RouteAction> routeActions = new ArrayList<>();
        for (Address path : _path) {
            routeActions.add(new RouteAction(1, path));
        }
        route(_receiver, null, routeActions, _minReceive);
    }

    @Payable
    @External
    public void routeV2(byte[] _path, @Optional BigInteger _minReceive, @Optional String _receiver) {
        Context.require(!inRoute);
        List<RouteAction> actions = Route.fromBytes(_path).actions;
        validateRoutePayload(actions.size(), _minReceive);
        if (_receiver == null || _receiver.equals("")) {
            _receiver = Context.getCaller().toString();
        }

        route(_receiver, null, actions, _minReceive);
    }

    private void validateRoutePayload(int _pathLength, BigInteger _minReceive) {
        if (_minReceive == null) {
            _minReceive = BigInteger.ZERO;
        }

        Context.require(_minReceive.signum() >= 0, TAG + ": Must specify a positive number for minimum to receive");

        Context.require(_pathLength <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS);
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
        if (inRoute) {
            return;
        }
        Context.require(_data.length > 0, "Token Fallback: Data can't be empty");

        // "{" is 123 as byte
        if (_data[0] == 123) {
            jsonRoute(_from, _data);
            return;
        }
        executeRoute(_from, _data);
    }

    private void executeRoute(String _from, byte[] data) {
        RouteData routeData = RouteData.fromBytes(data);
        Context.require(routeData.method.contains("_swap"), TAG + ": Fallback directly not allowed.");

        Address fromToken = Context.getCaller();
        BigInteger minimumReceive = BigInteger.ZERO;
        if (routeData.minimumReceive != null) {
            minimumReceive = routeData.minimumReceive;
        }

        String receiver;
        if (routeData.receiver != null) {
            receiver = routeData.receiver;
        } else {
            receiver = _from;
        }

        route(receiver, fromToken, routeData.actions, minimumReceive);
    }

    private void jsonRoute(String _from, byte[] data) {
        String unpackedData = new String(data);
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

        List<RouteAction> actions = new ArrayList<>();
        JsonArray pathArray = params.get("path").asArray();
        Context.require(pathArray.size() <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS);

        for (int i = 0; i < pathArray.size(); i++) {
            JsonValue addressJsonValue = pathArray.get(i);
            if (addressJsonValue == null || addressJsonValue.toString().equals("null")) {
                actions.add(new RouteAction(1, null));
            } else {
                actions.add(new RouteAction(1, Address.fromString(addressJsonValue.asString())));
            }
        }

        Address fromToken = Context.getCaller();
        route(receiver, fromToken, actions, minimumReceive);
    }

    @Payable
    public void fallback() {
    }

    @EventLog(indexed = 1)
    public void Route(Address from, BigInteger fromAmount, Address to, BigInteger toAmount) {
    }
}
