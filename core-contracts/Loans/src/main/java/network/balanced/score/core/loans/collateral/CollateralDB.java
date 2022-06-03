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

import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.Constants.EXA;


public class CollateralDB {
    private static final String TAG = "BalancedLoansAssets";
    private static final String COLLATERAL_DB_PREFIX = "asset";

    public static ArrayDB<Address> collateralAddresses = Context.newArrayDB("collateral_only_address_list", Address.class); // new
    public static ArrayDB<String> collateralList = Context.newArrayDB("collateral", String.class);
    public static final DictDB<String, String> symbolMap = Context.newDictDB("symbol|address", String.class);

    public static void migrateToNewDBs() {
        int totalCollateralCount = collateralList.size();
        if (collateralAddresses.size() > 0) {
            return;
        }

        for (int i = 0; i < totalCollateralCount; i++) {
            String symbol = collateralList.get(i);
            Collateral collateral = getCollateral(symbol);
            
            collateralAddresses.add(collateral.getAssetAddress());
        }
    }

    public static int size() {
        return collateralAddresses.size();
    }

    public static Collateral getCollateral(String symbol) {
        Context.require(arrayDbContains(collateralList, symbol), symbol + " is not a supported collateral type.");
        String collateralAddress = symbolMap.get(symbol);
        return new Collateral(COLLATERAL_DB_PREFIX + "|" + collateralAddress);
    }

    public static void addCollateral(Address address, Boolean active) {
        String collateralToAdd = address.toString();
        Context.require(!arrayDbContains(collateralAddresses, address), TAG + ": " + collateralToAdd + " already exists in " +
                "the database.");

        collateralAddresses.add(address);
        
        Collateral collateral = new Collateral(COLLATERAL_DB_PREFIX + "|" + collateralToAdd);
        collateral.setCollateral(address, active);

        Token collateralContract = new Token(address);
        String symbol = collateralContract.symbol();
        symbolMap.set(symbol, collateralToAdd);
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
            Collateral collateral = getCollateral(symbol);
            if (!collateral.isActive()) {
                continue;
            }

            Address collateralAddress = collateral.getAssetAddress();
            Token collateralContract = new Token(collateralAddress);
            BigInteger value = collateralContract.balanceOf(Context.getAddress()).multiply(collateralContract.lastPriceInLoop());
            totalCollateral = totalCollateral.add(value);
        }

        return totalCollateral.divide(EXA);
    }
}