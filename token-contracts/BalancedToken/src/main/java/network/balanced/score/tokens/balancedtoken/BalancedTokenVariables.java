package network.balanced.score.tokens.balancedtoken;

import java.math.BigInteger;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.BranchDB;

import static network.balanced.score.tokens.balancedtoken.Constants.*;

public class BalancedTokenVariables {
    static final VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);
    static final VarDB<Address> bnusdScore = Context.newVarDB(BNUSD_SCORE, Address.class);
    static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    static final VarDB<Address> oracle = Context.newVarDB(ORACLE, Address.class);

    static final VarDB<String> oracleName = Context.newVarDB(ORACLE_NAME, String.class);
    static final VarDB<BigInteger> priceUpdateTime = Context.newVarDB(PRICE_UPDATE_TIME, BigInteger.class);
    static final VarDB<BigInteger> lastPrice = Context.newVarDB(LAST_PRICE, BigInteger.class);
    static final VarDB<BigInteger> minInterval = Context.newVarDB(MIN_INTERVAL, BigInteger.class);

    static final VarDB<Boolean> stakingEnabled = Context.newVarDB(STAKING_ENABLED, Boolean.class);
    static final BranchDB<Address, DictDB<Integer, BigInteger>> stakedBalances = Context.newBranchDB(STAKED_BALANCES,
            BigInteger.class);
    static final VarDB<BigInteger> minimumStake = Context.newVarDB(MINIMUM_STAKE, BigInteger.class);
    static final VarDB<BigInteger> unstakingPeriod = Context.newVarDB(UNSTAKING_PERIOD, BigInteger.class);
    static final VarDB<BigInteger> totalStakedBalance = Context.newVarDB(TOTAL_STAKED_BALANCE, BigInteger.class);

    static final VarDB<Address> dividendsScore = Context.newVarDB(DIVIDENDS_SCORE, Address.class);
    static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);

    // [address][snapshot_id]["ids" || "amount"]
    static final BranchDB<Address, BranchDB<Integer, DictDB<String, BigInteger>>> stakeSnapshots = Context
            .newBranchDB(STAKE_SNAPSHOTS, BigInteger.class);
    // [address] = total_number_of_snapshots_taken
    static final DictDB<Address, Integer> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS, Integer.class);

    // [snapshot_id]["ids" || "amount"]
    static final BranchDB<Integer, DictDB<String, BigInteger>> totalStakedSnapshot = Context
            .newBranchDB(TOTAL_STAKED_SNAPSHOT, BigInteger.class);
    static final VarDB<Integer> totalStakedSnapshotCount = Context.newVarDB(TOTAL_STAKED_SNAPSHOT_COUNT,
            Integer.class);

    static final VarDB<Boolean> enableSnapshots = Context.newVarDB(ENABLE_SNAPSHOTS, Boolean.class);
    static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

    static  final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

}
