package network.balanced.score.tokens;

import com.iconloop.score.util.EnumerableSet;
import network.balanced.score.lib.interfaces.BoostedBaln;
import network.balanced.score.tokens.db.LockedBalance;
import network.balanced.score.tokens.db.Point;
import network.balanced.score.tokens.utils.MathUtils;
import network.balanced.score.tokens.utils.UnsignedBigInteger;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.tokens.Constants.YEAR_IN_MICRO_SECONDS;
import static network.balanced.score.tokens.utils.UnsignedBigInteger.pow10;
import static score.Context.require;

public abstract class AbstractBoostedBaln implements BoostedBaln {

    public static final BigInteger MAX_TIME = BigInteger.valueOf(4L).multiply(YEAR_IN_MICRO_SECONDS);
    public static BigInteger ICX = MathUtils.pow(BigInteger.TEN,18);
    protected static final UnsignedBigInteger MULTIPLIER = pow10(18);

    protected static final int DEPOSIT_FOR_TYPE = 0;
    protected static final int CREATE_LOCK_TYPE = 1;
    protected static final int INCREASE_LOCK_AMOUNT = 2;
    protected static final int INCREASE_UNLOCK_TIME = 3;

    protected final String name;
    protected final String symbol;
    protected final int decimals;
    protected final Address tokenAddress;
    protected final Address rewardAddress;

    protected final NonReentrant nonReentrant = new NonReentrant("Boosted_Baln_Reentrancy");
    protected final VarDB<BigInteger> supply = Context.newVarDB("Boosted_Baln_Supply", BigInteger.class);
    protected final DictDB<Address, LockedBalance> locked = Context.newDictDB("Boosted_Baln_locked", LockedBalance.class);
    protected final VarDB<BigInteger> epoch = Context.newVarDB("Boosted_Baln_epoch", BigInteger.class);
    protected final DictDB<BigInteger, Point> pointHistory = Context.newDictDB("Boosted_baln_point_history", Point.class);
    protected final BranchDB<Address, DictDB<BigInteger, Point>> userPointHistory = Context.newBranchDB("Boosted_baln_user_point_history",Point.class);
    protected final DictDB<Address, BigInteger> userPointEpoch = Context.newDictDB("Boosted_Baln_user_point_epoch", BigInteger.class);
    protected final DictDB<BigInteger, BigInteger> slopeChanges = Context.newDictDB("Boosted_Baln_slope_changes", BigInteger.class);
    protected final VarDB<Address> admin = Context.newVarDB("Boosted_Baln_admin", Address.class);
    protected final VarDB<Address> futureAdmin = Context.newVarDB("Boosted_baln_future_admin", Address.class);
    protected final VarDB<Address> penaltyAddress = Context.newVarDB("Boosted_baln_penalty_address", Address.class);

    protected final EnumerableSet<Address> users = new EnumerableSet<>("users_list", Address.class);

    protected final VarDB<BigInteger> minimumLockingAmount = Context.newVarDB("Boosted_baln_minimum_locking_amount",
            BigInteger.class);


    public AbstractBoostedBaln(Address tokenAddress, Address rewardAddress, String name, String symbol) {
        this.admin.set(Context.getCaller());
        this.tokenAddress = tokenAddress;
        this.rewardAddress = rewardAddress;

        Point point = new Point();
        point.block = UnsignedBigInteger.valueOf(Context.getBlockHeight());
        point.timestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());
        this.pointHistory.set(BigInteger.ZERO, point);

        this.decimals = ((BigInteger) Context.call(tokenAddress, "decimals")).intValue();
        this.name = name;
        this.symbol = symbol;
        require(this.decimals <= 72, "Decimals should be less than 72");

        if (this.supply.get() == null) {
            this.supply.set(BigInteger.ZERO);
            this.epoch.set(BigInteger.ZERO);
            this.minimumLockingAmount.set(ICX);
        }
    }

    @EventLog
    public void CommitOwnership(Address admin) {
    }

    @EventLog
    public void ApplyOwnership(Address admin) {
    }

    @EventLog(indexed = 2)
    public void Deposit(Address provider, BigInteger locktime, BigInteger value, int type, BigInteger timestamp) {
    }

    @EventLog(indexed = 1)
    public void Withdraw(Address provider, BigInteger value, BigInteger timestamp) {
    }

    @EventLog
    public void Supply(BigInteger prevSupply, BigInteger supply) {
    }

    @External(readonly = true)
    public String name() {
        return this.name;
    }

    @External(readonly = true)
    public String symbol() {
        return this.symbol;
    }

    @External(readonly = true)
    public int decimals() {
        return this.decimals;
    }

    @External(readonly = true)
    public Address admin() {
        return this.admin.get();
    }

    @External(readonly = true)
    public Address futureAdmin() {
        return this.futureAdmin.get();
    }

    protected void updateRewardData(Map<String, Object> userDetails){
        Context.call(this.rewardAddress, "updateRewardsData", this.name, userDetails.get("totalSupply"),  userDetails.get("user"), userDetails.get("userBalance"));
    }

}
