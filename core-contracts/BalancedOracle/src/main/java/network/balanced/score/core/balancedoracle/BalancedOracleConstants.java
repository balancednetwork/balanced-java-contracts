
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

package network.balanced.score.core.balancedoracle;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;

public class BalancedOracleConstants {
    private static final String GOVERNANCE = "governance";
    private static final String ADMIN = "admin";
    private static final String DEX = "dex";
    private static final String ORACLE = "oracle";
    private static final String STAKING = "staking";

    private static final String DEX_PRICED_ASSETS = "dexPriceAssets";
    private static final String ASSET_SYMBOL_PEG = "assetPegMap";

    private static final String ORACLE_PRICE_DECAY = "oraclePriceDecay";
    private static final String DEX_PRICE_DECAY = "dexPriceDecay";
    private static final String PRICE_UPDATE_THRESHOLD = "priceUpdateThreshold";

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    public static final VarDB<Address> dex = Context.newVarDB(DEX, Address.class);
    public static final VarDB<Address> oracle = Context.newVarDB(ORACLE, Address.class);
    public static final VarDB<Address> staking = Context.newVarDB(STAKING, Address.class);

    public static final DictDB<String, BigInteger> dexPricedAssets = Context.newDictDB(DEX_PRICED_ASSETS,
            BigInteger.class);
    public static final DictDB<String, String> assetPeg = Context.newDictDB(ASSET_SYMBOL_PEG, String.class);

    public static final VarDB<BigInteger> oraclePriceEMADecay = Context.newVarDB(ORACLE_PRICE_DECAY, BigInteger.class);
    public static final VarDB<BigInteger> dexPriceEMADecay = Context.newVarDB(DEX_PRICE_DECAY, BigInteger.class);
    public static final VarDB<BigInteger> lastUpdateThreshold = Context.newVarDB(PRICE_UPDATE_THRESHOLD,
            BigInteger.class);
}