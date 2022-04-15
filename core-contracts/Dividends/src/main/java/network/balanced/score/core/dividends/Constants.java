package network.balanced.score.core.dividends;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Math.pow;

public class Constants {
    public static final String TAG = "Balanced Dividends";

    public  static final String GOVERNANCE = "governance";
    public  static final String ADMIN = "admin";
    public  static final String LOANS_SCORE = "loans_score";
    public  static final String DAOFUND = "daofund";
    public  static final String BALN_SCORE = "baln_score";
    public  static final String DEX_SCORE = "dex_score";

    public  static final String ACCEPTED_TOKENS = "accepted_tokens";
    public  static final String AMOUNT_TO_DISTRIBUTE = "amount_to_distribute";
    public  static final String AMOUNT_BEING_DISTRIBUTED = "amount_being_distributed";
    public  static final String BALN_DIST_INDEX = "baln_dist_index";

    public  static final String STAKED_BALN_HOLDERS = "staked_baln_holders";
    public  static final String STAKED_DIST_INDEX = "staked_dist_index";

    public  static final String BALN_IN_DEX = "baln_in_dex";
    public  static final String TOTAL_LP_TOKENS = "total_lp_tokens";
    public  static final String LP_HOLDERS_INDEX = "lp_holders_index";
    public  static final String USERS_BALANCE = "users_balance";

    public  static final String DIVIDENDS_DISTRIBUTION_STATUS = "dividends_distribution_status";
    public  static final String SNAPSHOT_ID = "snapshot_id";
    public  static final String AMOUNT_RECEIVED_STATUS = "amount_received_status";
    public  static final String DAILY_FEES = "daily_fees";

    public  static final String MAX_LOOP_COUNT = "max_loop_count";
    public  static final String MIN_ELIGIBLE_DEBT = "minimum_eligible_debt";
    public  static final String DIVIDENDS_CATEGORIES = "dividends_categories";
    public  static final String DIVIDENDS_PERCENTAGE = "dividends_percentage";

    public  static final String DISTRIBUTION_ACTIVATE = "distribution_activate";
    public  static final String DIVIDENDS_BATCH_SIZE = "dividends_batch_size";

    public  static final String CLAIMED_BIT_MAP = "claimed_bit_map_";
    public  static final String TIME_OFFSET = "time_offset";

    public  static final String DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY = "dividends_enabled_to_staked_baln_only_day";

    public  static final String SNAPSHOT_DIVIDENDS = "snapshot_dividends";
    public  static final String TOTAL_SNAPSHOT = "total_snapshots";
    public  static final String COMPLETE_DIVIDENDS_CATEGORIES = "complete_dividends_categories";

    public static BigInteger MAX_LOOP = BigInteger.valueOf(50);
    public static BigInteger MINIMUM_ELIGIBLE_DEBT = BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18));
    public static String BALNBNUSD = "BALN/bnUSD";
    public static String BALN_HOLDERS = "baln_holders";
    public static String DAO_FUND = "daofund";

    public static BigInteger BALNBNUSD_ID = BigInteger.valueOf(3);
    public static BigInteger BALNSICX_ID = BigInteger.valueOf(4);
    public static BigInteger U_SECONDS_DAY = BigInteger.valueOf(86400).multiply(pow(BigInteger.TEN,6));
}
