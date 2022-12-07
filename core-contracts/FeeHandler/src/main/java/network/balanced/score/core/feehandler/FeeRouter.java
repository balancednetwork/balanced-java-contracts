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

package network.balanced.score.core.feehandler;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import network.balanced.score.lib.utils.IterableDictDB;
import score.*;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.core.feehandler.FeeHandlerImpl.acceptedDividendsTokens;

public class FeeRouter {
    private static final String ROUTES = "routes_iterable_dictionary";
    private static final String ROUTE_INDEX = "routes_index";
    public static final VarDB<Integer> routeIndex = Context.newVarDB(ROUTE_INDEX, Integer.class);
    private static final IterableDictDB<Address, String> routes = new IterableDictDB<Address, String>(ROUTES, String.class, Address.class, false);

    public static void addDefaultRoute(Address token) {
        Context.require(!arrayDbContains(acceptedDividendsTokens, token), "Token is accepted, should not be routed");

        Address dex = getDex();
        boolean isQuoteCoin = Context.call(Boolean.class, dex, "isQuoteCoinAllowed", token);

        Context.require(isQuoteCoin, "Only quote coins should be routed");
        Context.require(routes.get(token) == null, "Route is already defined");

        Address baln = getBaln();
        Address stakedLP = getStakedLp();
        BigInteger directPid = Context.call(BigInteger.class, dex, "getPoolId", token, baln);
        if (directPid == null) {
            directPid = Context.call(BigInteger.class, dex, "getPoolId", baln, token);
        }

        if (directPid != null && Context.call(Boolean.class, stakedLP, "isSupportedPool", directPid)) {
            routes.set(token, new JsonArray().toString());
            return;
        }

        Address bnusd = getBnusd();
        BigInteger tokenBnusdPid = Context.call(BigInteger.class, dex, "getPoolId", token, bnusd);
        Context.require(tokenBnusdPid != null, "no default path exists for " + token);


        boolean isSupportedPool = Context.call(Boolean.class, stakedLP, "isSupportedPool", tokenBnusdPid);
        Context.require(isSupportedPool, "no default path exists for " + token);
        JsonArray path = new JsonArray()
            .add(bnusd.toString())
            .add(baln.toString());

        routes.set(token, path.toString());
    }

    public static void setRoute(Address token, Address[] _path) {
        Context.require(!arrayDbContains(acceptedDividendsTokens, token), "Token is accepted, should not be routed");
        JsonArray path = new JsonArray();
        for (Address address : _path) {
            isContract(address);
            path.add(address.toString());
        }

        routes.set(token, path.toString());
    }

    public static void deleteRoute(Address _fromToken) {
        routes.remove(_fromToken);
    }

    public static List<String> getRoute(Address _fromToken) {
        String path = routes.get(_fromToken);
        if (path == null) {
            return null;
        }

        JsonArray pathJson = Json.parse(path).asArray();
        List<String> routePathArray = new ArrayList<>();

        for (JsonValue address : pathJson) {
            routePathArray.add(address.asString());
        }

        return routePathArray;
    }

    public static List<Address> getRoutedTokens() {
        return routes.keys();
    }

    public static void routeToken(Address token, Address[] _path) {
        Context.require(!arrayDbContains(acceptedDividendsTokens, token), "Token is accepted, can not be routed");
        boolean isQuoteCoin = Context.call(Boolean.class, getDex(), "isQuoteCoinAllowed", token);
        Context.require(!isQuoteCoin, "Only non quote coins can manually be routed");
        Context.require(routes.get(token) == null, "Automatically routed tokens can't be manually routed");

        BigInteger balance = Context.call(BigInteger.class, token, "balanceOf", Context.getAddress());
        Context.require(balance.compareTo(BigInteger.ZERO) > 0 , token + " balance is 0");
        JsonArray path = new JsonArray();
        for (Address address : _path) {
            isContract(address);
            path.add(address.toString());
        }

        if (path.size() > 1) {
            transferToken(token, getRouter(), balance, createDataFieldRouter(path));
        } else {
            transferToken(token, getDex(), balance, createDataFieldDex(getBaln()));
        }
    }


    public static void routeFees() {
        int size = routes.size();
        if (size == 0) {
            return;
        }

        int index = routeIndex.getOrDefault(0);
        index = (index + 1) % size;
        routeIndex.set(index);

        Address tokenToRoute = routes.getKey(index);
        BigInteger balance = Context.call(BigInteger.class, tokenToRoute, "balanceOf", Context.getAddress());
        if (balance.compareTo(BigInteger.ZERO) <= 0) {
           return;
        }

        JsonArray path;
        String route = routes.get(tokenToRoute);
        path = Json.parse(route).asArray();

        if (path.size() > 1) {
            transferToken(tokenToRoute, getRouter(), balance, createDataFieldRouter( path));
        } else {
            transferToken(tokenToRoute, getDex(), balance, createDataFieldDex(getBaln()));
        }
    }

    static void transferToken(Address _token, Address _to, BigInteger _amount, byte[] _data) {
        Context.call(_token, "transfer", _to, _amount, _data);
    }

    static byte[] createDataFieldRouter(JsonArray _path) {
        JsonObject params = Json.object().add("path", _path);
        JsonObject data = new JsonObject();
        data.add("method", "_swap");
        data.add("params", params);
        return data.toString().getBytes();
    }

    static byte[] createDataFieldDex(Address _toToken) {
        JsonObject params = Json.object().add("toToken", _toToken.toString());
        JsonObject data = new JsonObject();
        data.add("method", "_swap");
        data.add("params", params);
        return data.toString().getBytes();
    }
}
