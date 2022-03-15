package network.balanced.score.core;

import java.math.BigInteger;
import java.util.Map;
import score.Address;

public class Constants   {
    private Constants() {
    }
    public static final BigInteger EXA =  BigInteger.valueOf(1000000).multiply(BigInteger.valueOf(1000000)).multiply(BigInteger.valueOf(1000000));

    public static final long SECOND = 1000000L;
    public static final long U_SECONDS_DAY = 86400L * SECOND;
    
    //All percentages expressed in terms of points.
    public static final BigInteger POINTS = BigInteger.valueOf(10000);
    public static final BigInteger MINING_RATIO = BigInteger.valueOf(50000);
    public static final BigInteger LOCKING_RATIO = BigInteger.valueOf(40000);
    public static final BigInteger LIQUIDATION_RATIO = BigInteger.valueOf(15000);
    public static final BigInteger LIQUIDATION_REWARD = BigInteger.valueOf(67);
    public static final BigInteger ORIGINATION_FEE = BigInteger.valueOf(100);
    public static final BigInteger REDEMPTION_FEE = BigInteger.valueOf(50);
    public static final BigInteger BAD_DEBT_RETIREMENT_BONUS = BigInteger.valueOf(1000);
    public static final BigInteger MAX_RETIRE_PERCENT = BigInteger.valueOf(100);
    
     //In USD
    public static final BigInteger NEW_BNUSD_LOAN_MINIMUM = BigInteger.TEN.multiply(EXA);
    public static final BigInteger MIN_BNUSD_MINING_DEBT = BigInteger.valueOf(50).multiply(EXA); 
    
    public static final int MAX_DEBTS_LIST_LENGTH = 400;
    public static final int SNAP_BATCH_SIZE = 50;
    public static final int REDEEM_BATCH_SIZE = 50;

    enum Standings {
        INDETERMINATE,
        ZERO,
        LIQUIDATE,
        LOCKED,
        NOT_MINING,
        MINING,
        NO_DEBT
    }

    public static Map<Standings, String> StandingsMap = Map.of(
        Standings.INDETERMINATE, "Indeterminate",
        Standings.ZERO, "Zero",
        Standings.LIQUIDATE, "Liquidate",
        Standings.LOCKED, "Locked",
        Standings.NOT_MINING, "Not Mining",
        Standings.MINING, "Mining",
        Standings.NO_DEBT, "No Debt"
    );
}

