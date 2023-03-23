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

package network.balanced.score.util.otc;

import network.balanced.score.lib.utils.IterableDictDB;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.External;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;

public class BalancedOTC {
    public static final String ORDERS = "orders";
    public static final String DISCOUNTS = "discounts";
    public static final String VERSION = "version";

    private static final IterableDictDB<Address, BigInteger> orders = new IterableDictDB<>(ORDERS, BigInteger.class,
            Address.class, false);
    private static final DictDB<Address, BigInteger> discounts = Context.newDictDB(DISCOUNTS, BigInteger.class);
    private static final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    public static BigInteger BASE_DISCOUNT = BigInteger.valueOf(100);
    public static BigInteger MAX_DISCOUNT = BigInteger.valueOf(750);

    public BalancedOTC(Address _governance) {
        if (currentVersion.get() == null) {
            setGovernance(_governance);
        }

        if (currentVersion.getOrDefault("").equals(Versions.BALANCED_OTC)) {
            Context.revert("Can't Update same version of code");
        }

        currentVersion.set(Versions.BALANCED_OTC);
    }

    @External(readonly = true)
    public String name() {
        return Names.BALANCED_OTC;
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        checkStatus();
        if (dataIsNull(_data)) {
            swap(_from, Context.getCaller(), _value);
            return;
        }

        only(getBnusd());
        Context.require(_from.equals(getDaofund()), "Only daofund is allowed to create orders");

        String address = new String(_data);
        Address wantedToken = Address.fromString(address);

        BigInteger decimals = Context.call(BigInteger.class, wantedToken, "decimals");
        Context.require(decimals.equals(BigInteger.valueOf(18)),
                "Currently only supports purchase of tokens with 18 decimal precision");
        String symbol = Context.call(String.class, wantedToken, "symbol");
        BigInteger price = Context.call(BigInteger.class, getBalancedOracle(), "getPriceInUSD", symbol);
        Context.require(price != null && !price.equals(BigInteger.ZERO),
                "Token must be supported by the balanced Oracle");

        addOTC(wantedToken, _value);
    }

    @External
    public void setDiscount(Address token, BigInteger discount) {
        onlyGovernance();
        Context.require(discount.compareTo(MAX_DISCOUNT) <= 0, "Discount can't be higher than 7.5%");
        discounts.set(token, discount);
    }

    @External(readonly = true)
    public BigInteger getDiscount(Address token) {
        return discounts.getOrDefault(token, BASE_DISCOUNT);
    }

    @External
    public void cancelOrder(Address token) {
        onlyGovernance();
        BigInteger orderSize = orders.getOrDefault(token, BigInteger.ZERO);
        Context.require(orderSize.compareTo(BigInteger.ZERO) > 0);
        orders.remove(token);
        transferToken(getDaofund(), getBnusd(), orderSize);
    }

    @External(readonly = true)
    public List<Map<String, Object>> getOrders() {
        List<Map<String, Object>> ordersList = new ArrayList<>();
        for (Address token : orders.keys()) {
            Map<String, Object> order = Map.of(
                    "token", token.toString(),
                    "orderSize", orders.getOrDefault(token, BigInteger.ZERO),
                    "discount", discounts.getOrDefault(token, BASE_DISCOUNT));
            ordersList.add(order);
        }

        return ordersList;
    }

    @External(readonly = true)
    public BigInteger getExpectedBnUSDAmount(Address token, BigInteger amount) {
        return calculateSwap(token, amount, true);
    }

    private void swap(Address from, Address token, BigInteger amount) {
        BigInteger bnUSDAmount = calculateSwap(token, amount, false);

        transferToken(getDaofund(), token, amount);
        transferToken(from, getBnusd(), bnUSDAmount);
    }

    private BigInteger calculateSwap(Address token, BigInteger amount, boolean readonly) {
        BigInteger orderSize = orders.getOrDefault(token, BigInteger.ZERO);
        Context.require(orderSize.compareTo(BigInteger.ZERO) > 0, "Order does not exist");

        BigInteger discount = discounts.getOrDefault(token, BASE_DISCOUNT);
        BigInteger price = getPrice(token, readonly);
        BigInteger discountedPrice = price.add(price.multiply(discount).divide(POINTS));

        BigInteger bnUSDAmount = amount.multiply(discountedPrice).divide(EXA);
        Context.require(orderSize.compareTo(bnUSDAmount) >= 0,
                "Only " + orderSize + " left in the order for the token: " + token);
        if (readonly) {
            return bnUSDAmount;
        }

        orderSize = orderSize.subtract(bnUSDAmount);
        Context.require(orderSize.compareTo(BigInteger.ZERO) >= 0);

        if (orderSize.equals(BigInteger.ZERO)) {
            orders.remove(token);
        } else {
            orders.set(token, orderSize);
        }

        return bnUSDAmount;
    }

    private BigInteger getPrice(Address token, boolean readonly) {
        String symbol = Context.call(String.class, token, "symbol");
        if (readonly) {
            return Context.call(BigInteger.class, getBalancedOracle(), "getLastPriceInUSD", symbol);
        }

        return Context.call(BigInteger.class, getBalancedOracle(), "getPriceInUSD", symbol);

    }

    private void addOTC(Address wantedToken, BigInteger amount) {
        BigInteger currentOrderSize = orders.getOrDefault(wantedToken, BigInteger.ZERO);
        BigInteger orderSize = currentOrderSize.add(amount);
        orders.set(wantedToken, orderSize);
    }

    private void transferToken(Address to, Address token, BigInteger amount) {
        Context.call(token, "transfer", to, amount, new byte[0]);
    }

    private boolean dataIsNull(byte[] data) {
        if (data == null) {
            return true;
        }
        String unpackedData = new String(data);
        if (unpackedData.equals("")) {
            return true;
        } else if (unpackedData.equals("None")) {
            return true;
        }

        return false;
    }
}
