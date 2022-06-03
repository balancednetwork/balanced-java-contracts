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

package network.balanced.score.core.loans.asset;

import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;

public class AssetDB {
    private static final String TAG = "BalancedLoansAssets";
    private static final String ASSET_DB_PREFIX = "asset";

    private static ArrayDB<String> assetSymbols = Context.newArrayDB("symbol_list", String.class); // deprecated

    public static ArrayDB<Address> assetAddresses = Context.newArrayDB("assets_only_address_list", Address.class); // new
    public static ArrayDB<String> assetList = Context.newArrayDB("assets_only_list", String.class); // new 
    public static final DictDB<String, String> symbolMap = Context.newDictDB("symbol|address", String.class); // shared with collateral


    public static void migrateToNewDBs() {
        int totalSymbolsCount = assetSymbols.size();
        if (assetList.size() > 0) {
            return;
        }

        for (int i = 0; i < totalSymbolsCount; i++) {
            String symbol = assetSymbols.get(i);
            Asset asset = getAsset(symbol);
            if (asset.isCollateral()) {
                continue;
            }
            
            assetAddresses.add(asset.getAssetAddress());
            assetList.add(symbol);
        }
    }

    public static int size() {
        return assetAddresses.size();
    }

    public static Asset getAsset(String symbol) {
        Context.require(arrayDbContains(assetList, symbol), TAG + " " + symbol + " is not a supported asset.");
        String assetAddress = symbolMap.get(symbol);
        Asset asset = new Asset(ASSET_DB_PREFIX + "|" + assetAddress);
        return asset;
    }

    public static Map<String, String> getAssets() {
        int totalSymbolsCount = assetList.size();
        Map<String, String> assets = new HashMap<>();
        for (int i = 0; i < totalSymbolsCount; i++) {
            String symbol = assetList.get(i);
            assets.put(symbol, symbolMap.get(symbol));
        }

        return assets;
    }

    public static Map<String, Map<String, Object>> getActiveAssets() {
        int totalAssetsCount = assetList.size();
        Map<String, Map<String, Object>> assets = new HashMap<>();
        for (int i = 0; i < totalAssetsCount; i++) {
            String symbol = assetList.get(i);
            if (getAsset(symbol).isActive()){
                assets.put(symbol, getAsset(symbol).toMap());
            }
        }

        return assets;
    }

    public static void addAsset(Address address, Boolean active) {
        String assetToAdd = address.toString();
        Context.require(!arrayDbContains(assetAddresses, address), TAG + ": " + assetToAdd + " already exists in " +
                "the database.");

        assetAddresses.add(address);

        Asset asset = new Asset(ASSET_DB_PREFIX + "|" + assetToAdd);
        asset.setAsset(address, active);

        Token assetContract = new Token(address);
        String symbol = assetContract.symbol();

        assetList.add(symbol);
        symbolMap.set(symbol, assetToAdd);
    }

    public static List<String> getDeadMarkets() {
        List<String> deadAssets = new ArrayList<>();

        int assetsCount = assetList.size();
        for (int i = 0; i < assetsCount; i++) {
            String symbol = assetList.get(i);
            Asset asset = getAsset(symbol);
            if (asset.isActive() && asset.isDeadMarket()) {
                deadAssets.add(symbol);
            }
        }

        return deadAssets;
    }

    public static void updateDeadMarkets() {
        int assetsCount = assetList.size();
        for (int i = 0; i < assetsCount; i++) {
            String symbol = assetList.get(i);
            Asset asset = getAsset(symbol);
            if (asset.isActive()) {
                asset.checkForDeadMarket();
            }
        }
    }


}