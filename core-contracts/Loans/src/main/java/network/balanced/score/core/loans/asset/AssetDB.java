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

package network.balanced.score.core.loans.asset;

import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.Constants.EXA;

public class AssetDB {
    private static final String TAG = "BalancedLoansAssets";
    private static final String ASSET_DB_PREFIX = "asset";

    public static ArrayDB<Address> assetAddresses = Context.newArrayDB("address_list", Address.class);
    public static ArrayDB<String> assetSymbols = Context.newArrayDB("symbol_list", String.class);
    public static ArrayDB<String> activeAssets = Context.newArrayDB("active_assets_list", String.class);
    public static ArrayDB<String> activeCollateral = Context.newArrayDB("active_collateral_list", String.class);
    private static final ArrayDB<String> collateralList = Context.newArrayDB("collateral", String.class);
    public static final DictDB<String, String> symbolMap = Context.newDictDB("symbol|address", String.class);

    public static int size() {
        return assetAddresses.size();
    }

    public static Asset getAsset(String symbol) {
        Context.require(arrayDbContains(assetSymbols, symbol), symbol + "is not a supported asset.");
        String assetAddress = symbolMap.get(symbol);
        return new Asset(ASSET_DB_PREFIX + "|" + assetAddress);
    }

    public static Map<String, String> getAssetSymbolsAndAddress() {
        int totalSymbolsCount = assetSymbols.size();
        Map<String, String> assets = new HashMap<>();
        for (int i = 0; i < totalSymbolsCount; i++) {
            String symbol = assetSymbols.get(i);
            assets.put(symbol, symbolMap.get(symbol));
        }
        return assets;
    }

    public static Map<String, Map<String, Object>> getActiveAssets() {
        int totalActiveAssetsCount = activeAssets.size();
        Map<String, Map<String, Object>> assets = new HashMap<>();
        for (int i = 0; i < totalActiveAssetsCount; i++) {
            String symbol = activeAssets.get(i);
            assets.put(symbol, getAsset(symbol).toMap());
        }
        return assets;
    }

    public static Map<String, BigInteger> getAssetPrices() {
        Map<String, BigInteger> assets = new HashMap<>();
        int totalActiveAssetsCount = activeAssets.size();
        for (int i = 0; i < totalActiveAssetsCount; i++) {
            String symbol = activeAssets.get(i);
            Address assetAddress = getAsset(symbol).getAssetAddress();
            Token assetContract = new Token(assetAddress);
            BigInteger lastPrice = assetContract.lastPriceInLoop();
            assets.put(symbol, lastPrice);
        }
        return assets;
    }

    public static void addAsset(Address address, Boolean active, Boolean collateral) {
        String assetToAdd = address.toString();
        Context.require(!arrayDbContains(assetAddresses, address), TAG + ": " + assetToAdd + " already exists in " +
                "the database.");

        assetAddresses.add(address);

        Asset asset = new Asset(assetToAdd);
        asset.setAsset(address, BigInteger.valueOf(Context.getBlockTimestamp()), active, collateral);

        Token assetContract = new Token(address);
        String symbol = assetContract.symbol();

        assetSymbols.add(symbol);
        symbolMap.set(symbol, assetToAdd);

        if (active) {
            if (collateral) {
                activeCollateral.add(symbol);
            } else {
                activeAssets.add(symbol);
            }
        }

        if (collateral) {
            collateralList.add(symbol);
        }
    }

    public static List<String> getDeadMarkets() {
        List<String> deadAssets = new ArrayList<>();

        int activeAssetsCount = activeAssets.size();
        for (int i = 0; i < activeAssetsCount; i++) {
            String symbol = activeAssets.get(i);
            Asset asset = getAsset(symbol);
            if (asset.isDeadMarket()) {
                deadAssets.add(symbol);
            }
        }
        return deadAssets;
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
        int activeCollateralCount = activeCollateral.size();
        for (int i = 0; i < activeCollateralCount; i++) {
            String symbol = activeCollateral.get(i);
            Asset asset = getAsset(symbol);
            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);
            BigInteger value = assetContract.balanceOf(Context.getAddress()).multiply(assetContract.lastPriceInLoop());
            totalCollateral = totalCollateral.add(value);
        }

        return totalCollateral.divide(EXA);
    }


}