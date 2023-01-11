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

package network.balanced.score.core.loans.collateral;

import network.balanced.score.core.loans.utils.TokenUtils;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.Math.pow;


public class CollateralDB {
    private static final String TAG = "BalancedLoansAssets";
    public static ArrayDB<Address> collateralAddresses = Context.newArrayDB("collateral_only_address_list",
            Address.class);
    public static ArrayDB<String> collateralList = Context.newArrayDB("collateral", String.class);
    public static final DictDB<String, String> symbolMap = Context.newDictDB("symbol|", String.class);
    public static final DictDB<String, String> addressMap = Context.newDictDB("address|symbol", String.class);

    public static void migrateAddressMap() {
        int collateralCount = collateralList.size();
        for (int i = 0; i < collateralCount; i++) {
            String symbol = collateralList.get(i);
            String collateralAddress = symbolMap.get(symbol);
            addressMap.set(collateralAddress, symbol);
        }
    }

    public static int size() {
        return collateralAddresses.size();
    }

    public static Address getAddress(String symbol) {
        Context.require(arrayDbContains(collateralList, symbol), symbol + " is not a supported collateral type.");
        return Address.fromString(symbolMap.get(symbol));
    }

    public static String getSymbol(Address address) {
        String symbol = addressMap.get(address.toString());
        Context.require(symbol != null, address + " is not a supported collateral type.");
        return symbol;
    }

    public static void addCollateral(Address address, String symbol) {
        String collateralToAdd = address.toString();
        Context.require(!arrayDbContains(collateralAddresses, address), TAG + ": " + collateralToAdd + " already " +
                "exists in the database.");

        collateralAddresses.add(address);

        symbolMap.set(symbol, collateralToAdd);
        addressMap.set(collateralToAdd, symbol);

        collateralList.add(symbol);
    }

    public static Map<String, String> getCollateral() {
        Map<String, String> collateral = new HashMap<>();
        int collateralListCount = collateralList.size();
        for (int i = 0; i < collateralListCount; i++) {
            String symbol = collateralList.get(i);
            collateral.put(symbol, symbolMap.get(symbol));
        }

        return collateral;
    }

    public static BigInteger getTotalCollateral() {
        BigInteger totalCollateral = BigInteger.ZERO;
        int collateralCount = collateralList.size();
        for (int i = 0; i < collateralCount; i++) {
            String symbol = collateralList.get(i);
            Address collateralAddress = getAddress(symbol);
            BigInteger collateralDecimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());

            BigInteger value =
                    TokenUtils.balanceOf(collateralAddress, Context.getAddress()).multiply(TokenUtils.getPriceInLoop(symbol)).divide(collateralDecimals);
            totalCollateral = totalCollateral.add(value);
        }

        return totalCollateral;
    }
}