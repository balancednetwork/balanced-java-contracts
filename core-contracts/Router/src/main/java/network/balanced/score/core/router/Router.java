/*
 * Copyright (c) 2022 Balanced.network.
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
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static network.balanced.score.lib.utils.Check.*;

public class Router {
    private static final String DEX_ADDRESS = "dex_address";
    private static final String SICX_ADDRESS = "sicx_address";
    private static final String STAKING_ADDRESS = "staking_address";
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String ADMIN = "admin";

    private static final int MAX_NUMBER_OF_ITERATIONS = 4;
    private static final Address MINT_ADDRESS = new Address(new byte[Address.LENGTH]);
    private final String TAG = "Balanced Router";

    VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    VarDB<Address> dex = Context.newVarDB(DEX_ADDRESS, Address.class);

    public Router(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
        }
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setAdmin(Address _admin) {
        only(governance);
        admin.set(_admin);
    }

    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    @External
    public void setDex(Address _dex) {
        only(admin);
        isContract(_dex);
        dex.set(_dex);
    }

    @External(readonly = true)
    public Address getSicx() {
        return sicx.get();
    }

    @External
    public void setSicx(Address _address) {
        only(admin);
        isContract(_address);
        sicx.set(_address);
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    @External
    public void setStaking(Address _address) {
        only(admin);
        isContract(_address);
        staking.set(_address);
    }


    void swap(Address _fromToken, Address _toToken) {
        if(_fromToken == null) {
            Context.require(_toToken == sicx.get(), TAG + ": ICX can only be traded for sICX");
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.transfer(staking.get(), balance);
        }
        else if(_fromToken == sicx.get() && _toToken == null) {
            BigInteger balance = (BigInteger) Context.call(_fromToken, "balanceOf");
            String data = "{\"method\":\"_swap_icx\"}";
            Context.call(_fromToken, "transfer", balance, data.getBytes());
        }
        else {
            BigInteger balance = (BigInteger) Context.call(_fromToken, "balanceOf");
            String data = "{\"method\":\"_swap\",\"params\":{\"toToken\":\"" + _toToken + "\"}}";
            Context.call(_fromToken, "transfer", balance, data.getBytes());
        }
    }

    void route(Address _from, Address _startToken, Address[] _path, BigInteger _minReceive) {
        Address currentToken = _startToken;

        for(var token: _path) {
            swap(currentToken, token);
            currentToken = token;
        }

        if(currentToken == null) {
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.require(balance.compareTo(_minReceive) >= 0,
                    TAG + ": Below minimum receive amount of" + _minReceive);
            Context.transfer(_from, balance);
        }
        else {
            BigInteger balance = (BigInteger) Context.call(currentToken, "balanceOf", Context.getAddress());
            Context.require(balance.compareTo(_minReceive) >= 0,
                    TAG + ":Below minimum receive amount of" + _minReceive);
            Context.call(currentToken, "transfer", _from, balance, new byte[0]);
        }
    }

    /**
     * Uses DEX for exchange in the path where the highest return can be obtained
     */
    @Payable
    @External
    public void route(Address[] _path, @Optional BigInteger _minReceive) {
        if (_minReceive == null) {
            _minReceive = BigInteger.ZERO;
        }

        Context.require(_path.length <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of" + MAX_NUMBER_OF_ITERATIONS);

        route(Context.getCaller(), null, _path, _minReceive);
    }

    /**
     *  This is invoked when a token is transferred to this score.
     *  It expects a JSON object with the following format:
     *      ```
     *      {"method": "METHOD_NAME", "params":{...}}
     *      ```
     *      Token transfers to this contract are rejected unless any of the
     *      following methods are passed in the object:
     *      1) `_deposit` - Calls the `_deposit()` function
     *      2) `_swap_icx` - Calls the `_swap_icx()` function
     *      3) `_swap` - Calls the `_swap()` function
     *      All calls to this function update snapshots and process dividends.
     *
     * @param _from The address calling `transfer` on the other contract
     * @param _value Amount of token transferred
     * @param _data Data called by the transfer, json object expected.
     */
    @External
    public void tokenFallBack(Address _from, BigInteger _value, byte[] _data) {
        if (_from == dex.get() || _from == MINT_ADDRESS) {
            return ;
        }

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();

        Context.require(method.contains("_swap"), TAG + ": Fallback directly not allowed.");
        BigInteger minimumReceive = BigInteger.ZERO;
        if (params.contains("minimumReceive")) {
            minimumReceive = BigInteger.valueOf(params.get("minimumReceive").asInt());
            Context.require(minimumReceive.signum() >= 0, TAG + ": Must specify a positive number for minimum to " +
                    "receive");
        }
        Address receiver;
        if (params.contains("receiver")) {
            receiver = Address.fromString(params.get("receiver").asString());
        } else {
            receiver = _from;
        }

        Context.require(params.get("path").asArray().size() <= MAX_NUMBER_OF_ITERATIONS,
                TAG + ": Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS);

        List<Address> path = new ArrayList<>();

        for (JsonValue addressJsonValue : params.get("path").asArray()) {
            if (addressJsonValue != null) {
                path.add(Address.fromString(addressJsonValue.asString()));
            }
        }

        Address fromToken = Context.getCaller();
        route(receiver, fromToken, (Address[]) path.toArray(), minimumReceive);
    }
}
