package network.balanced.score.core.dividends;

import java.math.BigInteger;

import static network.balanced.score.core.dividends.Helpers.pow10;

public class Consts {

    public static BigInteger DEFAULT_CAP_VALUE = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
    public static BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18);
    public static BigInteger DAYS_IN_A_WEEK = BigInteger.valueOf(7);
    public static BigInteger MAX_LOOP = BigInteger.valueOf(50);
    public static BigInteger MINIMUM_ELIGIBLE_DEBT = BigInteger.valueOf(50).multiply(pow10(18));
    public static String BALNBNUSD = "BALN/bnUSD";
    public static String BALN_HOLDERS = "baln_holders";
    public static String DAO_FUND = "daofund";

    public static BigInteger BALNBNUSD_ID = BigInteger.valueOf(3);
    public static BigInteger BALNSICX_ID = BigInteger.valueOf(4);
    public static BigInteger U_SECONDS_DAY = BigInteger.valueOf(86400).multiply(pow10(6));
}
