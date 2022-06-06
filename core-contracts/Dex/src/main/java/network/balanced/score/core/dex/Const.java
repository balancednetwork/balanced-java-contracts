package network.balanced.score.core.dex;

import score.Address;

import java.math.BigInteger;

public class Const {
    protected static final String ACCOUNT_BALANCE_SNAPSHOT = "account_balance_snapshot";
    protected static final String TOTAL_SUPPLY_SNAPSHOT = "total_supply_snapshot";
    protected static final String QUOTE_COINS = "quote_coins";
    protected static final String ICX_QUEUE_TOTAL = "icx_queue_total";
    protected static final String SICX_ADDRESS = "sicx_address";
    protected static final String bnUSD_ADDRESS = "bnUSD_address";
    protected static final String BALN_ADDRESS = "baln_address";
    protected static final String STAKING_ADDRESS = "staking_address";
    protected static final String DIVIDENDS_ADDRESS = "dividends_address";
    protected static final String REWARDS_ADDRESS = "rewards_address";
    protected static final String GOVERNANCE_ADDRESS = "governance_address";
    protected static final String FEEHANDLER_ADDRESS = "feehandler_address";
    protected static final String STAKEDLP_ADDRESS = "stakedLp_address";
    protected static final String NAMED_MARKETS = "named_markets";
    protected static final String ADMIN = "admin";
    protected static final String DEX_ON = "dex_on";
    protected static final String SICXICX_MARKET_NAME = "sICX/ICX";
    protected static final String CURRENT_DAY = "current_day";
    protected static final String TIME_OFFSET = "time_offset";
    protected static final String REWARDS_DONE = "rewards_done";
    protected static final String DIVIDENDS_DONE = "dividends_done";
    protected static final String DEPOSIT = "deposit";
    protected static final String POOL_ID = "poolId";
    protected static final String NONCE = "nonce";
    protected static final String POOL_TOTAL = "poolTotal";
    protected static final String TOTAL = "poolLPTotal";
    protected static final String BALANCE = "balances";
    protected static final String WITHDRAW_LOCK = "withdrawLock";
    protected static final String BALN_SNAPSHOT = "balnSnapshot";
    protected static final String POOL_LP_FEE = "pool_lp_fee";
    protected static final String POOL_BALN_FEE = "pool_baln_fee";
    protected static final String ICX_CONVERSION_FEE = "icx_conversion_fee";
    protected static final String ICX_BALN_FEE = "icx_baln_fee";
    protected static final String BASE_TOKEN = "baseToken";
    protected static final String QUOTE_TOKEN = "quoteToken";
    protected static final String ACTIVE_POOL = "activePool";
    protected static final String ICX_QUEUE = "icxQueue";
    protected static final String ICX_QUEUE_ORDER_ID = "icxQueueOrderId";
    protected static final String SICX_EARNINGS = "sicxEarnings";
    protected static final String MARKETS_NAMES = "marketsToNames";
    protected static final String TOKEN_PRECISIONS = "token_precisions";
    protected static final String CURRENT_TX = "current_tx";
    protected static final String CONTINUOUS_REWARDS_DAY = "continuous_rewards_day";

    protected static final BigInteger SICXICX_POOL_ID = BigInteger.valueOf(1L);
    protected static final BigInteger MIN_LIQUIDITY = BigInteger.valueOf(1000);
    protected static final BigInteger FEE_SCALE = BigInteger.valueOf(10000);
    protected static final BigInteger EXA = BigInteger.valueOf(1000000000000000000L);
    protected static final BigInteger FIRST_NON_BALANCED_POOL = BigInteger.valueOf(6L);
    protected static final Integer ICX_QUEUE_FILL_DEPTH = 50;
    protected static final BigInteger USDS_BNUSD_ID = BigInteger.valueOf(10L);
    protected static final BigInteger IUSDT_BNUSD_ID = BigInteger.valueOf(15L);
    protected static final BigInteger WITHDRAW_LOCK_TIMEOUT = BigInteger.valueOf(86400000000L);
    protected static final Address MINT_ADDRESS = Address.fromString("hx0000000000000000000000000000000000000000");
    protected static final Address DEX_ZERO_SCORE_ADDRESS = Address.fromString("cxf000000000000000000000000000000000000000");
    protected static final String TAG = "Balanced DEX";
}
