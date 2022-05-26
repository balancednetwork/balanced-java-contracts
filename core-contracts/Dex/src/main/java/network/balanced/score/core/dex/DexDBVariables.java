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

package network.balanced.score.core.dex;

import network.balanced.score.core.dex.db.LinkedListDB;
import network.balanced.score.lib.utils.IterableDictDB;
import network.balanced.score.lib.utils.SetDB;
import score.*;

import java.math.BigInteger;

public class DexDBVariables {

    private static final String ACCOUNT_BALANCE_SNAPSHOT = "account_balance_snapshot";
    private static final String TOTAL_SUPPLY_SNAPSHOT = "total_supply_snapshot";
    private static final String QUOTE_COINS = "quote_coins";
    private static final String ICX_QUEUE_TOTAL = "icx_queue_total";
    private static final String SICX_ADDRESS = "sicx_address";
    private static final String bnUSD_ADDRESS = "bnUSD_address";
    private static final String BALN_ADDRESS = "baln_address";
    private static final String STAKING_ADDRESS = "staking_address";
    private static final String DIVIDENDS_ADDRESS = "dividends_address";
    private static final String REWARDS_ADDRESS = "rewards_address";
    private static final String GOVERNANCE_ADDRESS = "governance_address";
    private static final String FEEHANDLER_ADDRESS = "feehandler_address";
    private static final String STAKEDLP_ADDRESS = "stakedLp_address";
    private static final String NAMED_MARKETS = "named_markets";
    private static final String ADMIN = "admin";
    private static final String DEX_ON = "dex_on";

    private static final String CURRENT_DAY = "current_day";
    private static final String TIME_OFFSET = "time_offset";
    private static final String REWARDS_DONE = "rewards_done";
    private static final String DIVIDENDS_DONE = "dividends_done";
    private static final String DEPOSIT = "deposit";
    private static final String POOL_ID = "poolId";
    private static final String NONCE = "nonce";
    private static final String POOL_TOTAL = "poolTotal";
    private static final String TOTAL = "poolLPTotal";
    private static final String BALANCE = "balances";
    private static final String WITHDRAW_LOCK = "withdrawLock";
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


    public final static VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    public final static VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    public final static VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    public final static VarDB<Address> dividends = Context.newVarDB(DIVIDENDS_ADDRESS, Address.class);
    public final static VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    public final static VarDB<Address> rewards = Context.newVarDB(REWARDS_ADDRESS, Address.class);
    public final static VarDB<Address> bnUSD = Context.newVarDB(bnUSD_ADDRESS, Address.class);
    public final static VarDB<Address> baln = Context.newVarDB(BALN_ADDRESS, Address.class);
    public final static VarDB<Address> feehandler = Context.newVarDB(FEEHANDLER_ADDRESS, Address.class);
    public final static VarDB<Address> stakedlp = Context.newVarDB(STAKEDLP_ADDRESS, Address.class);
    public final static VarDB<Boolean> dexOn = Context.newVarDB(DEX_ON, Boolean.class);

    // Deposits - Map: token_address -> user_address -> value
    public final static BranchDB<Address, DictDB<Address, BigInteger>> deposit = Context.newBranchDB(DEPOSIT,
            BigInteger.class);
    // Pool IDs - Map: token address -> opposite token_address -> id
    public final static BranchDB<Address, DictDB<Address, BigInteger>> poolId = Context.newBranchDB(POOL_ID,
            BigInteger.class);

    public final static VarDB<BigInteger> nonce = Context.newVarDB(NONCE, BigInteger.class);

    // Total amount of each type of token in a pool
    // Map: pool_id -> token_address -> value
    public final static BranchDB<BigInteger, DictDB<Address, BigInteger>> poolTotal = Context.newBranchDB(POOL_TOTAL,
            BigInteger.class);

    // Total LP Tokens in pool
    // Map: pool_id -> total LP tokens
    public final static DictDB<BigInteger, BigInteger> total = Context.newDictDB(TOTAL, BigInteger.class);

    // User Balances
    // Map: pool_id -> user address -> lp token balance
    public final static BranchDB<BigInteger, DictDB<Address, BigInteger>> balance = Context.newBranchDB(BALANCE,
            BigInteger.class);
    public final static BranchDB<BigInteger, DictDB<Address, BigInteger>> withdrawLock =
            Context.newBranchDB(WITHDRAW_LOCK, BigInteger.class);

    public final static BranchDB<BigInteger, BranchDB<Address, BranchDB<String, DictDB<BigInteger, BigInteger>>>> accountBalanceSnapshot = Context.newBranchDB(ACCOUNT_BALANCE_SNAPSHOT, BigInteger.class);

    public final static BranchDB<BigInteger, BranchDB<String, DictDB<BigInteger, BigInteger>>> totalSupplySnapshot =
            Context.newBranchDB(TOTAL_SUPPLY_SNAPSHOT, BigInteger.class);


    public final static BranchDB<BigInteger, BranchDB<String, DictDB<BigInteger, BigInteger>>> balnSnapshot =
            Context.newBranchDB(BALN_SNAPSHOT, BigInteger.class);

    // Rewards/timekeeping logic
    public final static VarDB<BigInteger> currentDay = Context.newVarDB(CURRENT_DAY, BigInteger.class);
    public final static VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public final static VarDB<Boolean> rewardsDone = Context.newVarDB(REWARDS_DONE, Boolean.class);
    public final static VarDB<Boolean> dividendsDone = Context.newVarDB(DIVIDENDS_DONE, Boolean.class);

    public final static LPMetadataDB activeAddresses = new LPMetadataDB();

    public final static SetDB<Address> quoteCoins = new SetDB<>(QUOTE_COINS, Address.class, null);


    //     # All fees are divided by `FEE_SCALE` in consts
    public final static VarDB<BigInteger> poolLpFee = Context.newVarDB(POOL_LP_FEE, BigInteger.class);
    public final static VarDB<BigInteger> poolBalnFee = Context.newVarDB(POOL_BALN_FEE, BigInteger.class);
    public final static VarDB<BigInteger> icxConversionFee = Context.newVarDB(ICX_CONVERSION_FEE, BigInteger.class);
    public final static VarDB<BigInteger> icxBalnFee = Context.newVarDB(ICX_BALN_FEE, BigInteger.class);


    // Map: pool_id -> base token address
    public final static DictDB<BigInteger, Address> poolBase = Context.newDictDB(BASE_TOKEN, Address.class);
    // Map: pool_id -> quote token address
    public final static DictDB<BigInteger, Address> poolQuote = Context.newDictDB(QUOTE_TOKEN, Address.class);
    public final static DictDB<BigInteger, Boolean> active = Context.newDictDB(ACTIVE_POOL, Boolean.class);

    public final static LinkedListDB icxQueue = new LinkedListDB(ICX_QUEUE);


    // Map: user_address -> order id
    public final static DictDB<Address, BigInteger> icxQueueOrderId = Context.newDictDB(ICX_QUEUE_ORDER_ID,
            BigInteger.class);

    // Map: user_address -> integer of unclaimed earnings
    public final static DictDB<Address, BigInteger> sicxEarnings = Context.newDictDB(SICX_EARNINGS, BigInteger.class);
    public final static VarDB<BigInteger> icxQueueTotal = Context.newVarDB(ICX_QUEUE_TOTAL, BigInteger.class);


    public final static IterableDictDB<String, BigInteger> namedMarkets = new IterableDictDB<>(NAMED_MARKETS,
            BigInteger.class, String.class, true);

    public final static DictDB<BigInteger, String> marketsToNames = Context.newDictDB(MARKETS_NAMES, String.class);

    public final static DictDB<Address, BigInteger> tokenPrecisions = Context.newDictDB(TOKEN_PRECISIONS,
            BigInteger.class);

    // VarDB used to track the current sent transaction. This helps bound iterations.
    public final static VarDB<byte[]> currentTx = Context.newVarDB(CURRENT_TX, byte[].class);

    // Activation of continuous rewards day
    public final static VarDB<BigInteger> continuousRewardsDay = Context.newVarDB(CONTINUOUS_REWARDS_DAY,
            BigInteger.class);
}
