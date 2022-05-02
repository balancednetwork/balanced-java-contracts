package network.balanced.score.core.loans.asset;

import score.Context;
import score.ArrayDB;
import score.DictDB;
import score.Address;

import java.math.BigInteger;
import scorex.util.HashMap;
import scorex.util.ArrayList;
import java.util.Map;

import static network.balanced.score.lib.utils.ArrayDBUtils.*;

public class AssetDB {
    private static final String ASSET_DB_PREFIX = "asset";

    public static ArrayDB<Address> assetAddresses = Context.newArrayDB("address_list", Address.class);
    public static ArrayDB<String> assetSymbols = Context.newArrayDB("symbol_list", String.class);
    public static ArrayDB<String> activeAssets = Context.newArrayDB("active_assets_list", String.class);
    public static ArrayDB<String> activeCollateral = Context.newArrayDB("active_collateral_list", String.class);
    public static ArrayDB<String> collateralList = Context.newArrayDB("collateral", String.class);
    public static DictDB<String, String> symbolMap = Context.newDictDB("symbol|address", String.class);

    // private static HashMap<String, Asset> items = new HashMap<String, Asset>(10);

    public static Asset get(String symbol) {
        Context.require(arrayDbContains(assetSymbols, symbol), symbol + "is not a supported asset.");
        // if (!items.containsKey(symbol)) {
        //     items.put(symbol, getAsset(symbolMap.get(symbol)));
        // }

        // return items.get(symbol);
        return getAsset(symbolMap.get(symbol));
    }


    public static int size() {
        return assetAddresses.size();
    }

    public static Asset getAsset(String address) {
        return new Asset(ASSET_DB_PREFIX + "|" + address);
    }

    public static  ArrayList<String> getDeadMarkets() {
        ArrayList<String> deadAssets = new ArrayList<String>(activeAssets.size());

        for (int i = 0; i < activeAssets.size(); i++) {
            String symbol = activeAssets.get(i);
            Asset asset = get(symbol);
            if (asset.dead.getOrDefault(false)) {
                deadAssets.add(symbol);
            }
        }

        return deadAssets;
    }

    public static Map<String, String> getAssetTokens() {
        HashMap<String, String> assets = new HashMap<String, String>(assetSymbols.size());
        for (int i = 0; i < assetSymbols.size(); i++) {
            String symbol = assetSymbols.get(i);
            assets.put(symbol, symbolMap.get(symbol));
        }

        return assets;
    }

    public static Map<String, Map<String, Object>> getAvailableAssets() {
        HashMap<String, Map<String, Object>> assets = new HashMap<String, Map<String, Object>>(activeAssets.size());
        for (int i = 0; i < activeAssets.size(); i++) {
            String symbol = activeAssets.get(i);
            assets.put(symbol, get(symbol).toMap());

        }

        return assets;
    }

    public static Map<String, String> getCollateral() {
        HashMap<String, String> collateral = new HashMap<String, String>(collateralList.size());
        for (int i = 0; i < collateralList.size(); i++) {
            String symbol = collateralList.get(i);
            collateral.put(symbol, symbolMap.get(symbol));
        }

        return collateral;
    }

    public static BigInteger getTotalCollateral() {
        BigInteger collateral = BigInteger.ZERO;
        for (int i = 0; i < activeCollateral.size(); i++) {
            String symbol = activeCollateral.get(i);
            Asset asset = get(symbol);
            BigInteger value = asset.balanceOf(Context.getAddress()).multiply(asset.lastPriceInLoop());
            collateral = collateral.add(value);
        }

        return collateral;
    }

    public static void addAsset(Address address, Boolean active, Boolean collateral) {
        String stringAddress = address.toString();
        Context.require(!arrayDbContains(assetAddresses, address), stringAddress + " already exists in the database.");
        
        Asset asset = getAsset(stringAddress);

        asset.added.set(BigInteger.valueOf(Context.getBlockTimestamp()));
        asset.address.set(address);
        asset.dead.set(false);
        asset.active.set(active);
        asset.isCollateral.set(collateral);
        asset.badDebt.set(BigInteger.ZERO);
        asset.liquidationPool.set(BigInteger.ZERO);

        String symbol = asset.symbol();

        symbolMap.set(symbol, stringAddress);
        assetAddresses.add(address);
        assetSymbols.add(symbol);

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

        // items.put(symbol, assset);
    }


}