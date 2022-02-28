package network.balanced.score.core;

import java.math.BigInteger;
import java.lang.Math;


public class Constant {
    public static final String TAG = "Staked ICX Manager";
    public static final String SYSTEM_SCORE_ADDRESS = "cx0000000000000000000000000000000000000000";
    public static final BigInteger DENOMINATOR =  BigInteger.valueOf(1000000000000000000L);
    public static final BigInteger HUNDRED = BigInteger.valueOf(100L);
    public static final BigInteger TOP_PREP_COUNT = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_UNSTAKE_BATCH_LIMIT = BigInteger.valueOf(200L);
    public static final BigInteger MAX_ITERATION_LOOP = BigInteger.valueOf(100L);
    public static final BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18L);
    long calc = (long) Math.pow(2,256);
    long newCalc = calc - 1L;
    public final BigInteger DEFAULT_CAP_VALUE = BigInteger.valueOf(newCalc);

//    DEFAULT_CAP_VALUE = 2 ** 256 -1
}
