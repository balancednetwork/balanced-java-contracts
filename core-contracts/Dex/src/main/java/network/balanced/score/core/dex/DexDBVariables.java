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

package network.balanced.score.core.dex;

import network.balanced.score.core.dex.db.LinkedListDB;
import network.balanced.score.core.dex.utils.LPMetadataDB;
import network.balanced.score.lib.utils.IterableDictDB;
import network.balanced.score.lib.utils.SetDB;
import score.*;

import java.math.BigInteger;

public class DexDBVariables {

    private static final String ACCOUNT_BALANCE_SNAPSHOT = "account_balance_snapshot";
    private static final String TOTAL_SUPPLY_SNAPSHOT = "total_supply_snapshot";
    private static final String QUOTE_COINS = "quote_coins";
    private static final String ICX_QUEUE_TOTAL = "icx_queue_total";
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String NAMED_MARKETS = "named_markets";
    private static final String DEX_ON = "dex_on";

    private static final String CURRENT_DAY = "current_day";
    private static final String TIME_OFFSET = "time_offset";
    private static final String REWARDS_DONE = "rewards_done";
    private static final String DIVIDENDS_DONE = "dividends_done";
    private static final String DEPOSIT = "deposit";
    private static final String POOL_ID = "poolId";
    private static final String NONCE = "nonce";
    private static final String POOL_TOTAL = "poolTotal";
    private static final String POOL_LP_TOTAL = "poolLPTotal";
    private static final String BALANCE = "balances";
    private static final String BALN_SNAPSHOT = "balnSnapshot";
    private static final String POOL_LP_FEE = "pool_lp_fee";
    private static final String POOL_BALN_FEE = "pool_baln_fee";
    private static final String ICX_CONVERSION_FEE = "icx_conversion_fee";
    private static final String ICX_BALN_FEE = "icx_baln_fee";
    private static final String BASE_TOKEN = "baseToken";
    private static final String QUOTE_TOKEN = "quoteToken";
    private static final String ACTIVE_POOL = "activePool";
    private static final String ICX_QUEUE = "icxQueue";
    private static final String ICX_QUEUE_ORDER_ID = "icxQueueOrderId";
    private static final String SICX_EARNINGS = "sicxEarnings";
    private static final String MARKETS_NAMES = "marketsToNames";
    private static final String TOKEN_PRECISIONS = "token_precisions";
    private static final String CURRENT_TX = "current_tx";
    private static final String CONTINUOUS_REWARDS_DAY = "continuous_rewards_day";
    public static final String VERSION = "version";


    final static VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public final static VarDB<Boolean> dexOn = Context.newVarDB(DEX_ON, Boolean.class);

    // Deposits - Map: token_address -> user_address -> value
    final static BranchDB<Address, DictDB<Address, BigInteger>> deposit = Context.newBranchDB(DEPOSIT,
            BigInteger.class);
    // Pool IDs - Map: token address -> opposite token_address -> id
    final static BranchDB<Address, DictDB<Address, Integer>> poolId = Context.newBranchDB(POOL_ID, Integer.class);

    public final static VarDB<Integer> nonce = Context.newVarDB(NONCE, Integer.class);

    // Total amount of each type of token in a pool
    // Map: pool_id -> token_address -> value
    final static BranchDB<Integer, DictDB<Address, BigInteger>> poolTotal = Context.newBranchDB(POOL_TOTAL,
            BigInteger.class);

    // Total LP Tokens in pool
    // Map: pool_id -> total LP tokens
    final static DictDB<Integer, BigInteger> poolLpTotal = Context.newDictDB(POOL_LP_TOTAL, BigInteger.class);

    // User Balances
    // Map: pool_id -> user address -> lp token balance
    final static BranchDB<Integer, DictDB<Address, BigInteger>> balance = Context.newBranchDB(BALANCE,
            BigInteger.class);

    // Map: pool_id -> user address -> ids/values/length -> length/0 -> value
    final static BranchDB<Integer, BranchDB<Address, BranchDB<String, DictDB<BigInteger, BigInteger>>>> accountBalanceSnapshot =
            Context.newBranchDB(ACCOUNT_BALANCE_SNAPSHOT, BigInteger.class);
    // Map: pool_id -> ids/values/length -> length/0 -> value
    final static BranchDB<Integer, BranchDB<String, DictDB<BigInteger, BigInteger>>> totalSupplySnapshot =
            Context.newBranchDB(TOTAL_SUPPLY_SNAPSHOT, BigInteger.class);

    // Map: pool_id -> ids/values/length -> length/0 -> value
    final static BranchDB<Integer, BranchDB<String, DictDB<BigInteger, BigInteger>>> balnSnapshot =
            Context.newBranchDB(BALN_SNAPSHOT, BigInteger.class);

    // Rewards/timekeeping logic
    final static VarDB<BigInteger> currentDay = Context.newVarDB(CURRENT_DAY, BigInteger.class);
    final static VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    final static VarDB<Boolean> rewardsDone = Context.newVarDB(REWARDS_DONE, Boolean.class);
    final static VarDB<Boolean> dividendsDone = Context.newVarDB(DIVIDENDS_DONE, Boolean.class);

    final static LPMetadataDB activeAddresses = new LPMetadataDB();
    // Pools must use one of these as quote currency
    final static SetDB<Address> quoteCoins = new SetDB<>(QUOTE_COINS, Address.class, null);

    // All fees are divided by `FEE_SCALE` in const
    final static VarDB<BigInteger> poolLpFee = Context.newVarDB(POOL_LP_FEE, BigInteger.class);
    final static VarDB<BigInteger> poolBalnFee = Context.newVarDB(POOL_BALN_FEE, BigInteger.class);
    final static VarDB<BigInteger> icxConversionFee = Context.newVarDB(ICX_CONVERSION_FEE, BigInteger.class);
    final static VarDB<BigInteger> icxBalnFee = Context.newVarDB(ICX_BALN_FEE, BigInteger.class);

    // Map: pool_id -> base token address
    final static DictDB<Integer, Address> poolBase = Context.newDictDB(BASE_TOKEN, Address.class);
    // Map: pool_id -> quote token address
    final static DictDB<Integer, Address> poolQuote = Context.newDictDB(QUOTE_TOKEN, Address.class);
    final static DictDB<Integer, Boolean> active = Context.newDictDB(ACTIVE_POOL, Boolean.class);

    final static LinkedListDB icxQueue = new LinkedListDB(ICX_QUEUE);

    // Map: user_address -> order id
    final static DictDB<Address, BigInteger> icxQueueOrderId = Context.newDictDB(ICX_QUEUE_ORDER_ID, BigInteger.class);

    // Map: user_address -> integer of unclaimed earnings
    final static DictDB<Address, BigInteger> sicxEarnings = Context.newDictDB(SICX_EARNINGS, BigInteger.class);
    final static VarDB<BigInteger> icxQueueTotal = Context.newVarDB(ICX_QUEUE_TOTAL, BigInteger.class);


    final static IterableDictDB<String, Integer> namedMarkets = new IterableDictDB<>(NAMED_MARKETS, Integer.class,
            String.class, true);

    final static DictDB<Integer, String> marketsToNames = Context.newDictDB(MARKETS_NAMES, String.class);

    final static DictDB<Address, BigInteger> tokenPrecisions = Context.newDictDB(TOKEN_PRECISIONS, BigInteger.class);

    // VarDB used to track the current sent transaction. This helps bound iterations.
    final static VarDB<byte[]> currentTx = Context.newVarDB(CURRENT_TX, byte[].class);

    // Activation of continuous rewards day
    final static VarDB<BigInteger> continuousRewardsDay = Context.newVarDB(CONTINUOUS_REWARDS_DAY, BigInteger.class);

    public static final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
}
