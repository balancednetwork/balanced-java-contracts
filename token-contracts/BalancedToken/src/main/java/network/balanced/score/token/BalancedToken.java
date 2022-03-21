package network.balanced.score.token;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TWO;
import static java.math.BigInteger.TEN;
import static java.math.BigInteger.ZERO;
import static network.balanced.score.token.util.Mathematics.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import score.Address;
import score.ArrayDB;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.HashMap;

public class BalancedToken extends IRC2 {

	public static final String TAG = "BALN";
	public static final String TOKEN_NAME = "Balance Token";
	public static final String SYMBOL_NAME = "BALN";
	public static final String DEFAULT_ORACLE_NAME = "Balanced DEX";

    private static final String PRICE_UPDATE_TIME = "price_update_time";
    private static final String LAST_PRICE = "last_price";
    private static final String MIN_INTERVAL = "min_interval";

    private static final String EVEN_DAY_STAKE_CHANGES = "even_day_stake_changes";
    private static final String ODD_DAY_STAKE_CHANGES = "odd_day_stake_changes";

    private static final String INDEX_STAKE_ADDRESS_CHANGES = "index_stake_address_changes";
    private static final String INDEX_UPDATE_STAKE = "index_update_stake";
    private static final String STAKE_UPDATE_DB = "stake_update_db";
    private static final String STAKE_ADDRESS_UPDATE_DB = "stake_address_update_db";

    private static final String STAKING_ENABLED = "staking_enabled";

    private static final String STAKED_BALANCES = "staked_balances";
    private static final String MINIMUM_STAKE = "minimum_stake";
    private static final String UNSTAKING_PERIOD = "unstaking_period";
    private static final String TOTAL_STAKED_BALANCE = "total_staked_balance";

    private static final String DIVIDENDS_SCORE = "dividends_score";
    private static final String GOVERNANCE = "governance";
    private static final String ADMIN = "admin";

    private static final String DEX_SCORE = "dex_score";
    private static final String BNUSD_SCORE = "bnUSD_score";
    private static final String ORACLE = "oracle";
    private static final String ORACLE_NAME = "oracle_name";

    private static final String TIME_OFFSET = "time_offset";
    private static final String STAKE_SNAPSHOTS = "stake_snapshots";
    private static final String TOTAL_SNAPSHOTS = "total_snapshots";
    private static final String TOTAL_STAKED_SNAPSHOT = "total_staked_snapshot";
    private static final String TOTAL_STAKED_SNAPSHOT_COUNT = "total_staked_snapshot_count";

    private static final String ENABLE_SNAPSHOTS = "enable_snapshots";

    private VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);
    private VarDB<Address> bnusdScore = Context.newVarDB(BNUSD_SCORE, Address.class);
    private VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private VarDB<Address> oracle = Context.newVarDB(ORACLE, Address.class);
    private VarDB<String> oracleName = Context.newVarDB(ORACLE_NAME, String.class);
    private VarDB<BigInteger> priceUpdateTime = Context.newVarDB(PRICE_UPDATE_TIME, BigInteger.class);
    private VarDB<BigInteger> lastPrice = Context.newVarDB(LAST_PRICE, BigInteger.class);
    private VarDB<BigInteger> minInterval = Context.newVarDB(MIN_INTERVAL, BigInteger.class);

    private ArrayDB<Address> evenDayStakeChanges = Context.newArrayDB(EVEN_DAY_STAKE_CHANGES, Address.class);
    private ArrayDB<Address> oddDayStakeChanges = Context.newArrayDB(ODD_DAY_STAKE_CHANGES, Address.class);
    private List<ArrayDB<Address>> stakeChanges = List.of(evenDayStakeChanges, oddDayStakeChanges);

    private VarDB<BigInteger> indexUpdateStake = Context.newVarDB(INDEX_UPDATE_STAKE, BigInteger.class);
    private VarDB<BigInteger> indexStakeAddressChanges = Context.newVarDB(INDEX_STAKE_ADDRESS_CHANGES, BigInteger.class);

    private VarDB<BigInteger> stakeUpdateDb = Context.newVarDB(STAKE_UPDATE_DB, BigInteger.class);
    private VarDB<BigInteger> stakeAddressUpdateDb = Context.newVarDB(STAKE_ADDRESS_UPDATE_DB, BigInteger.class);

    private VarDB<Boolean> stakingEnabled = Context.newVarDB(STAKING_ENABLED, Boolean.class);

    //TODO: this is the scenario where java can not handle this dynamic structure, python uses depth=2
    private BranchDB<Address, DictDB<String, BigInteger>> stakedBalances = Context.newBranchDB(STAKED_BALANCES, BigInteger.class);
    private VarDB<BigInteger> minimumStake = Context.newVarDB(MINIMUM_STAKE, BigInteger.class);
    private VarDB<BigInteger> unstakingPeriod = Context.newVarDB(UNSTAKING_PERIOD, BigInteger.class);
    private VarDB<BigInteger> totalStakedBalance = Context.newVarDB(TOTAL_STAKED_BALANCE, BigInteger.class);

    private VarDB<Address> dividendsScore = Context.newVarDB(DIVIDENDS_SCORE, Address.class);

    private VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);

    //TODO: [address][snapshot_id]["ids" || "amount"]
    // a more problematic structure depth=3
    private BranchDB<Address, BranchDB<BigInteger,DictDB<String, BigInteger>>> stakeSnapshots = Context.newBranchDB(STAKE_SNAPSHOTS, BigInteger.class );
    //# [address] = total_number_of_snapshots_taken    
    private DictDB<Address, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS, BigInteger.class);

    //TODO: [snapshot_id]["ids" || "amount"]
    // another depth=2
    private BranchDB<BigInteger, DictDB<String, BigInteger>> totalStakedSnapshot = Context.newBranchDB(TOTAL_STAKED_SNAPSHOT, BigInteger.class);
    private VarDB<BigInteger> totalStakedSnapshotCount = Context.newVarDB(TOTAL_STAKED_SNAPSHOT_COUNT, BigInteger.class);

    private VarDB<Boolean> enableSnapshots = Context.newVarDB(ENABLE_SNAPSHOTS, Boolean.class);

    public BalancedToken( Address _governance, @Optional Boolean update) {
    	super(TOKEN_NAME, SYMBOL_NAME, null, null, update);

    	if(update) {
    		onUpdate();
    		return;
    	}

		this.governance.set(_governance);
		this.stakingEnabled.set(false);
		this.indexUpdateStake.set(BigInteger.ZERO);
		this.indexStakeAddressChanges.set(ZERO);
		this.stakeUpdateDb.set(ZERO);
		this.stakeAddressUpdateDb.set(ZERO);
		this.oracleName.set(DEFAULT_ORACLE_NAME);
		this.lastPrice.set(INITIAL_PRICE_ESTIMATE);
		this.minInterval.set(MIN_UPDATE_TIME);
		this.minimumStake.set(MINIMUM_STAKE_AMOUNT);
		this.unstakingPeriod.set(DEFAULT_UNSTAKING_PERIOD);

    }

    public void onUpdate() {
        //self.setTimeOffset()
        //self._enable_snapshots.set(False)    	
    }

	@Override
	public String getTag() {
		return TAG;
	}

    @External(readonly=true)
    public String getPeg() {
        return TAG;
    }

    @External
    public void setbnUSD(Address _address) {
    	onlyGovernance();
        this.bnusdScore.set(_address);
    }

    //TODO: py version is returning a dict , verify if this dict is a json (seems like it is)
    //if it is, then it is ok to return Address, it will be returned as json object
    @External(readonly=true)
    public Address getbnUSD() { 
        return this.bnusdScore.get();
    }

    @External
    public void setOracle(Address _address) {
    	onlyGovernance();
        this.oracle.set(_address);
    }

    //TODO: py version is returning a dict , verify if this dict is a json (seems like it is)
    //if it is, then it is ok to return Address, it will be returned as json object
    @External(readonly=true)
    public Address getOracle() {
        return this.oracle.get();
    }

    @External
    public void setDex(Address _address) {
    	onlyGovernance();
        this.dexScore.set(_address);
    }

    //TODO: py version is returning a dict , verify if this dict is a json (seems like it is)
    //if it is, then it is ok to return Address, it will be returned as json object
    @External(readonly=true)
    public Address getDex() {
        return this.dexScore.get();
    }

    @External
    public void setOracleName(String _name) {
    	onlyGovernance();
        this.oracleName.set(_name);
    }

    //TODO: py version is returning a dict , verify if this dict is a json (seems like it is)
    //but what will be the result of string to map?
    @External(readonly=true)
    public String getOracleName() {//-> dict:
        return this.oracleName.get();
    }

    @External
    public void setGovernance(Address _address) {
    	onlyOwner();
        this.governance.set(_address);
    }

    @External(readonly=true)
	@Override
	public Address getGovernance() {
		return this.governance.get();
	}

    /**
    Sets the authorized address.

    :param _admin: The authorized admin address.
    **/
    @External
    public void setAdmin(Address _admin) {
    	onlyGovernance();
        this.admin.set(_admin);
    }

    @External
    public void setMinInterval(BigInteger _interval) {
    	onlyGovernance();
        this.minInterval.set(_interval);
    }

    @External(readonly=true)
    public BigInteger getMinInterval() {
        return this.minInterval.get();
    }

    /**
    Returns the price of the asset in loop. Makes a call to the oracle if
    the last recorded price is not recent enough.
    **/
    @External
    public BigInteger priceInLoop() {
    	BigInteger now = BigInteger.valueOf(Context.getBlockTimestamp());
    	BigInteger price = this.priceUpdateTime.getOrDefault(ZERO);
    	BigInteger interval = this.minInterval.getOrDefault(ZERO);
    	if( now.subtract(price).compareTo(interval) > 0 ) {
    		this.updateAssetValue();
    	}
        return this.lastPrice.get();
    }

    /**
    Returns the latest price of the asset in loop.
    **/
    @External(readonly=true)
    public BigInteger lastPriceInLoop() {
        Address dexScore = this.dexScore.get();
        Address oracleAddress = this.oracle.get();

        BigInteger price = Context.call(BigInteger.class, dexScore, "getBalnPrice");
        Map<String, BigInteger> priceData = Context.call(Map.class, oracleAddress, "get_reference_data", "USD", "ICX");

        return priceData.getOrDefault("rate", ZERO).multiply(price).divide(EXA);
    }

    /**
    Calls the oracle method for the asset and updates the asset
    value in loop.
    **/
    public void updateAssetValue() {
        String base = "BALN";
        String quote = "bnUSD";
        Address dexScore = this.dexScore.get();
        Address oracleAddress = this.oracle.get();

        try {
            //dex = self.create_interface_score(dex_score, DexInterface)
            //oracle = self.create_interface_score(oracle_address, OracleInterface)
            BigInteger price = Context.call(BigInteger.class, dexScore, "getBalnPrice");
            Map<String, BigInteger> priceData = Context.call(Map.class, oracleAddress,"get_reference_data","USD", "ICX");
            this.lastPrice.set(priceData.getOrDefault("rate", ZERO).multiply(price).divide(EXA));
            this.priceUpdateTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));
            this.OraclePrice(base + quote, this.oracleName.get(), dexScore, price);
        }catch(Exception e) {
            Context.revert(base + quote + "," +this.oracleName.get() + ","+dexScore +".");
        }
    }


    @External(readonly=true)
    public Map<String, BigInteger> detailsBalanceOf(Address _owner) {

    	BigInteger currUnstaked;
        if ( this.stakedBalances.at(_owner).getOrDefault(Status.UNSTAKING_PERIOD.name(), ZERO)
        		.compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) < 0 ) {
            currUnstaked = this.stakedBalances.at(_owner).getOrDefault(Status.UNSTAKING.name(), ZERO);
        }else {
        	currUnstaked = ZERO;
        }

        BigInteger availableBalance;
        if (this.firstTime(_owner)) {
            availableBalance = this.balanceOf(_owner);
        }else {
            availableBalance = this.stakedBalances.at(_owner).getOrDefault(Status.AVAILABLE.name(), ZERO);
        }

        BigInteger unstakingAmount = this.stakedBalances.at(_owner).getOrDefault(Status.UNSTAKING.name(), ZERO).subtract(currUnstaked);
        
        BigInteger unstakingTime;
        if (unstakingAmount.equals(ZERO)) {
        	unstakingTime = ZERO;
        }else {
        	unstakingTime = this.stakedBalances.at(_owner).getOrDefault(Status.UNSTAKING_PERIOD.name(), ZERO);
        }

        return Map.of(
            "Total balance", this.balances.getOrDefault(_owner, ZERO),
            "Available balance", availableBalance.add(currUnstaked),
            "Staked balance", this.stakedBalances.at(_owner).getOrDefault(Status.STAKED.name(), ZERO),
            "Unstaking balance", unstakingAmount,
            "Unstaking time (in microseconds)", unstakingTime
        );
    }

    protected boolean firstTime(Address from) {
        return (
                this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).equals(ZERO)
                && this.stakedBalances.at(from).getOrDefault(Status.STAKED.name(), ZERO).equals(ZERO)
                && this.stakedBalances.at(from).getOrDefault(Status.UNSTAKING.name(), ZERO).equals(ZERO)
                && !this.balances.getOrDefault(from, ZERO).equals(ZERO)
        );
    }

    @External(readonly=true)
    public BigInteger unstakedBalanceOf(Address _owner) {
        Map<String, BigInteger> detailBalance = this.detailsBalanceOf(_owner);
        return detailBalance.get("Unstaking balance");
    }

    @External(readonly=true)
    public BigInteger stakedBalanceOf(Address _owner) {
        return this.stakedBalances.at(_owner).getOrDefault(Status.STAKED.name(), ZERO);
    }

    @External(readonly=true)
    public BigInteger availableBalanceOf(Address _owner) {
        Map<String, BigInteger> detailBalance = this.detailsBalanceOf(_owner);
        return detailBalance.get("Available balance");
    }

    @External(readonly=true)
    public Boolean getStakingEnabled() {
        return this.stakingEnabled.getOrDefault(false);
    }

    @External(readonly=true)
    public BigInteger totalStakedBalance() {
        return this.totalStakedBalance.getOrDefault(ZERO);
    }

    @External(readonly=true)
    public BigInteger getMinimumStake() {
        return this.minimumStake.getOrDefault(ZERO);
    }

    @External(readonly=true)
    public BigInteger getUnstakingPeriod() {
        BigInteger timeInMicroseconds = this.unstakingPeriod.getOrDefault(ZERO);
        BigInteger timeInDays = timeInMicroseconds.divide(DAY_TO_MICROSECOND);
        return timeInDays;
    }

    protected void checkFirstTime(Address from) {
        // If first time copy the balance to available staked balances
        if (this.firstTime(from)) {
            this.stakedBalances.at(from).set(Status.AVAILABLE.name(), this.balances.getOrDefault(from, ZERO));
        }
    }

    public void stakingEnabledOnly() {
        if (!this.stakingEnabled.getOrDefault(false)) {
            Context.revert(TAG + " : Staking must first be enabled.");
        }
    }

    @External
    public void toggleEnableSnapshot() {
    	onlyOwner();
        this.enableSnapshots.set( !this.enableSnapshots.getOrDefault(false) );
    }

    @External(readonly=true)
    public Boolean getSnapshotEnabled() {
        return this.enableSnapshots.getOrDefault(false);
    }

    @External
    public void toggleStakingEnabled() {
    	onlyGovernance();
        this.stakingEnabled.set( !this.stakingEnabled.getOrDefault(false) );
    }

    public void makeAvailable(Address from) {

        if ( this.stakedBalances.at(from).getOrDefault(Status.UNSTAKING_PERIOD.name(), ZERO)
        		.compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) <= 0) {
            BigInteger currUnstaked = this.stakedBalances.at(from).getOrDefault(Status.UNSTAKING.name(), ZERO);
            this.stakedBalances.at(from).set(Status.UNSTAKING.name(), ZERO);
            this.stakedBalances.at(from).set(Status.AVAILABLE.name(), 
                	this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).add(currUnstaked)); 
        }
    }

    @External
    public void stake(BigInteger _value) {
        this.stakingEnabledOnly();
        Address from = Context.getCaller();
        if (_value.compareTo(ZERO) < 0) {
            Context.revert(TAG + ": Staked BALN value can't be less than zero.");
        }
        if (_value.compareTo(this.balances.getOrDefault(from, ZERO)) > 0) {
            Context.revert(TAG + ": Out of BALN balance.");
        }
        if (_value.compareTo(this.minimumStake.getOrDefault(ZERO)) < 0
        		&& !_value.equals(ZERO) ) {
            Context.revert(TAG + ": Staked BALN must be greater than the minimum stake amount and non zero.");
        }

        this.checkFirstTime(from);
        this.makeAvailable(from);

        BigInteger oldStake = this.stakedBalances.at(from).getOrDefault(Status.STAKED.name(), ZERO)
        		.add(
        				this.stakedBalances.at(from).getOrDefault(Status.UNSTAKING.name(), ZERO));
        BigInteger newStake = _value;
        BigInteger stakeIncrement = _value.subtract(this.stakedBalances.at(from).getOrDefault(Status.STAKED.name(), ZERO));
        BigInteger unstakeAmount = ZERO;
        if (newStake.compareTo(oldStake) > 0) {
            BigInteger offset = newStake.subtract(oldStake);
            this.stakedBalances.at(from).set(Status.AVAILABLE.name(), 
            		this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).subtract(offset));
        }else {
            unstakeAmount = oldStake.subtract(newStake);
        }

        this.stakedBalances.at(from).set(Status.STAKED.name(), _value);
        this.stakedBalances.at(from).set(Status.UNSTAKING.name(), unstakeAmount);
        this.stakedBalances.at(from).set(Status.UNSTAKING_PERIOD.name(), 
        		BigInteger.valueOf(Context.getBlockTimestamp()).add( this.unstakingPeriod.getOrDefault(ZERO)) );
        this.totalStakedBalance.set(this.totalStakedBalance.getOrDefault(ZERO).add(stakeIncrement));

        if  (this.enableSnapshots.getOrDefault(false) ) {
        	this.updateSnapshotForAddress( Context.getCaller(), _value);
            this.updateTotalStakedSnapshot(this.totalStakedBalance.getOrDefault(ZERO) );
        }
    }

    @External
    public void setMinimumStake(BigInteger _amount) {
    	onlyGovernance();
        if (_amount.compareTo(ZERO) < 0) {
            Context.revert(TAG + ": Amount cannot be less than zero.");
        }

        BigInteger totalAmount = pow( _amount.multiply(TEN), this.decimals.getOrDefault(ZERO).intValue() );
        this.minimumStake.set(totalAmount);
    }

    @External
    public void setUnstakingPeriod(BigInteger _time) {
    	onlyGovernance();
        if (_time.compareTo(ZERO) < 0) {
            Context.revert(TAG +": Time cannot be negative.");
        }
        BigInteger totalTime = _time.multiply(DAY_TO_MICROSECOND);
        this.unstakingPeriod.set(totalTime);
    }

    @External
    public void setDividends(Address _score) {
    	onlyGovernance();
        this.dividendsScore.set(_score);
    }

    @External(readonly=true)
    public Address getDividends() {
        return this.dividendsScore.get();
    }

    protected void dividendsOnly() {
    	Address dividensScore = this.dividendsScore.get();
    	Address sender = Context.getCaller();
        if ( dividensScore == null || !sender.equals(dividensScore)) {
            Context.revert(TAG +": This method can only be called by the dividends distribution contract.");
        }
    }

    @External
    public Map<Address, BigInteger> getStakeUpdates(){
        this.dividendsOnly();
        this.stakingEnabledOnly();

        ArrayDB<Address> stakeChanges = this.stakeChanges.get(this.stakeUpdateDb.getOrDefault(ZERO).intValue() );
        int lengthList = stakeChanges.size();

        int start = this.indexUpdateStake.get().intValue();
        if (start == lengthList ){
            if (! this.stakeUpdateDb.getOrDefault(ZERO).equals( this.stakeAddressUpdateDb.getOrDefault(ZERO)) ) {
                this.stakeUpdateDb.set(this.stakeAddressUpdateDb.getOrDefault(ZERO) );
                this.indexUpdateStake.set(this.indexStakeAddressChanges.getOrDefault(ZERO));
            }
            return Map.of();
        }

        int end = Math.min(start + MAX_LOOP.intValue(), lengthList);

        HashMap<Address, BigInteger> detailedStakeBalances = new HashMap<>();
        for(int i = start; i< end; i++) {
        	detailedStakeBalances.put(stakeChanges.get(i) , this.stakedBalanceOf(stakeChanges.get(i)) );
        }

        this.indexUpdateStake.set(BigInteger.valueOf(end));
        return detailedStakeBalances;
    }

    @External
    public Boolean clearYesterdaysStakeChanges() {
        this.dividendsOnly();
        this.stakingEnabledOnly();

        int yesterday = (this.stakeAddressUpdateDb.getOrDefault(ZERO).add(ONE)).mod(TWO).intValue();
        ArrayDB<Address> yesterdaysChanges = this.stakeChanges.get(yesterday);
        int lengthList = yesterdaysChanges.size();

        if (lengthList == 0) {
            return true;
        }

        int loopCount = Math.min(lengthList, MAX_LOOP.intValue());
        for (int i = 0; i< loopCount; i++) {
            yesterdaysChanges.pop();
        }

        return !(yesterdaysChanges.size() > 0);
    }

    @External
    public void switchStakeUpdateDB() {
        this.dividendsOnly();
        this.stakingEnabledOnly();

        BigInteger newDay = this.stakeAddressUpdateDb.getOrDefault(ZERO).add(ONE).mod(TWO);
        this.stakeAddressUpdateDb.set(newDay);
        ArrayDB<Address> stakeChanges = this.stakeChanges.get(newDay.intValue());
        this.indexStakeAddressChanges.set( BigInteger.valueOf((long) stakeChanges.size()));
    }

    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {

        Address from = Context.getCaller();
        this.checkFirstTime(from);
        this.checkFirstTime(_to);
        this.makeAvailable(from);
        this.makeAvailable(_to);

        if ( this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).compareTo(_value) < 0) {
            Context.revert(TAG +": Out of available balance. Please check staked and total balance.");
        }

        this.stakedBalances.at(from).set(Status.AVAILABLE.name(), 
        		this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).subtract(_value));
        this.stakedBalances.at(_to).set(Status.AVAILABLE.name(),
        		this.stakedBalances.at(_to).getOrDefault(Status.AVAILABLE.name(), ZERO).add(_value));

        super.transfer(_to, _value, _data);
    }

    /**
    Creates `_amount` number of tokens, and assigns to caller account.
    Increases the balance of that account and total supply.
    See {IRC2-_mint}

    :param _amount: Number of tokens to be created at the account.
    :param _data: data to mint
    **/
    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        if (_data == null) {
            _data = "None".getBytes();
        }

        Address to = Context.getCaller();
        this.checkFirstTime(to);
        this.makeAvailable(to);
        this.stakedBalances.at(to).set(Status.AVAILABLE.name(),
        		this.stakedBalances.at(to).getOrDefault(Status.AVAILABLE.name(), ZERO).add(_amount));

        this._mint(to, _amount, _data);
    }

    /**
    Creates `_amount` number of tokens, assigns to self, then transfers to `_account`.
    Increases the balance of that account and total supply.
    See {IRC2-_mint}

    :param _account: The account at which token is to be created.
    :param _amount: Number of tokens to be created at the account.
    :param _data: data to mint
    **/
    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        if (_data == null) {
            _data = "None".getBytes();
        }

        this.checkFirstTime(_account);
        this.makeAvailable(_account);
        this.stakedBalances.at(_account).set(Status.AVAILABLE.name(), 
        		this.stakedBalances.at(_account).getOrDefault(Status.AVAILABLE.name(), ZERO).add(_amount));

        this._mint(_account, _amount, _data);
    }

    /**
    Destroys `_amount` number of tokens from the caller account.
    Decreases the balance of that account and total supply.
    See {IRC2-_burn}

    :param _amount: Number of tokens to be destroyed.
    **/
    @External
    public void burn(BigInteger _amount) {
        Address from = Context.getCaller();
        this.checkFirstTime(from);
        this.makeAvailable(from);

        if ( this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).compareTo(_amount) < 0 ) {
            Context.revert(TAG + ": Out of available balance. Please check staked and total balance.");
        }
        this.stakedBalances.at(from).set(Status.AVAILABLE.name(), 
        		this.stakedBalances.at(from).getOrDefault(Status.AVAILABLE.name(), ZERO).subtract(_amount));

        this._burn(from, _amount);
    }

    /**
    Destroys `_amount` number of tokens from the specified `_account` account.
    Decreases the balance of that account and total supply.
    See {IRC2-_burn}

    :param _account: The account at which token is to be destroyed.
    :param _amount: Number of tokens to be destroyed at the `_account`.
    **/
    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        this.checkFirstTime(_account);
        this.makeAvailable(_account);

        if ( this.stakedBalances.at(_account).getOrDefault(Status.AVAILABLE.name(), ZERO).compareTo(_amount) < 0) {
            Context.revert(TAG + ": Out of available balance. Please check staked and total balance.");
        }
        this.stakedBalances.at(_account).set(Status.AVAILABLE.name(),
        		this.stakedBalances.at(_account).getOrDefault(Status.AVAILABLE.name(), ZERO).subtract(_amount));

        this._burn(_account, _amount);
    }

    @External
    public void setTimeOffset() {
    	onlyOwner();

    	Address dexScore = this.dexScore.get();
    	if(dexScore == null) {
    		Context.revert("dex score address must be set first");
    	}
        BigInteger deltaTime = Context.call(BigInteger.class, dexScore, "getTimeOffset");
        this.timeOffset.set(deltaTime);
    }

    @External(readonly=true)
    public BigInteger getTimeOffset() {
        return this.timeOffset.getOrDefault(ZERO);
    }

    /**
    Returns the current day (floored). Used for snapshotting,
    paying rewards, and paying dividends.
    **/
    @External(readonly=true)
    public BigInteger getDay() {
        return BigInteger.valueOf(Context.getBlockTimestamp()).subtract(this.timeOffset.getOrDefault(ZERO)).divide(DAY_TO_MICROSECOND);
    }

    // ----------------------------------------------------------
    // Snapshots
    // ----------------------------------------------------------

    protected void updateSnapshotForAddress(Address account, BigInteger amount) {
        if (this.timeOffset.getOrDefault(ZERO).equals(ZERO)) {
            this.setTimeOffset();
        }
        BigInteger currentId = this.getDay();
        BigInteger totalSnapshotsTaken = this.totalSnapshots.getOrDefault(account, ZERO);

        if ( totalSnapshotsTaken.compareTo(ZERO) > 0 
        		&& this.stakeSnapshots
        		.at(account)
        		.at(totalSnapshotsTaken.subtract(ONE)).getOrDefault(IDS, ZERO)
        		.equals(currentId)) {
            this.stakeSnapshots.at(account).at(totalSnapshotsTaken.subtract(ONE)).set(AMOUNT, amount);
        }else {
            this.stakeSnapshots.at(account).at(totalSnapshotsTaken).set(IDS, currentId);
            this.stakeSnapshots.at(account).at(totalSnapshotsTaken).set(AMOUNT,amount);
            this.totalSnapshots.set(account, totalSnapshotsTaken.add(ONE));
        }
    }

    public void updateTotalStakedSnapshot(BigInteger amount) {

        if (this.timeOffset.getOrDefault(ZERO).equals(ZERO)) {
            this.setTimeOffset();
        }
        BigInteger currentId = this.getDay();
        BigInteger totalSnapshotsTaken = this.totalStakedSnapshotCount.getOrDefault(ZERO);

        if (totalSnapshotsTaken.compareTo(ZERO) > 0 
        		&& this.totalStakedSnapshot
        		.at(totalSnapshotsTaken.subtract(ONE))
        		.getOrDefault(IDS, ZERO)
        		.equals(currentId)) {
            this.totalStakedSnapshot.at(totalSnapshotsTaken.subtract(ONE)).set(AMOUNT, amount);
        }else {
            this.totalStakedSnapshot.at(totalSnapshotsTaken).set(IDS, currentId);
            this.totalStakedSnapshot.at(totalSnapshotsTaken).set(AMOUNT, amount);
            this.totalStakedSnapshotCount.set(totalSnapshotsTaken.add(ONE));
        }
    }

    @External(readonly=true)
    public BigInteger stakedBalanceOfAt(Address _account, BigInteger _day) {
        BigInteger currentDay = this.getDay();
        if ( _day.compareTo(currentDay) > 0) {
            Context.revert(TAG +": Asked _day is greater than current day");
        }

        BigInteger totalSnapshotsTaken = this.totalSnapshots.getOrDefault(_account, ZERO);
        if ( totalSnapshotsTaken.equals(ZERO)) {
            return ZERO;
        }

        if ( this.stakeSnapshots
        		.at(_account)
        		.at(totalSnapshotsTaken.subtract(ONE))
        		.getOrDefault(IDS, ZERO)
        		.compareTo(_day) <= 0) {
            return this.stakeSnapshots.at(_account).at(totalSnapshotsTaken.subtract(ONE)).getOrDefault(AMOUNT, ZERO);
        }

        if (this.stakeSnapshots
        		.at(_account)
        		.at(ZERO)
        		.getOrDefault(IDS, ZERO)
        		.compareTo(_day) > 0) {
            return ZERO;
        }

        BigInteger low = ZERO;
        BigInteger high = totalSnapshotsTaken.subtract(ONE);
        while (high.compareTo(low) > 0) {
            BigInteger mid = high.subtract(high.subtract(low).divide(TWO));
            DictDB<String, BigInteger> midValue = this.stakeSnapshots.at(_account).at(mid);
            if (midValue.getOrDefault(IDS, ZERO) == _day) {
                return midValue.getOrDefault(AMOUNT, ZERO);
            }else if (midValue.getOrDefault(IDS, ZERO).compareTo(_day) < 0) {
                low = mid;
            }else {
                high = mid.subtract(ONE);
            }
        }

        return this.stakeSnapshots.at(_account).at(low).getOrDefault(AMOUNT, ZERO);
    }

    
	@Override
	public Address getAdmin() {
		return this.admin.getOrDefault(null);
	}

    // --------------------------------------------------------------------------
    // EVENTS
    // --------------------------------------------------------------------------

    @EventLog(indexed=3)
    public void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price) {}

}
