package network.balanced.score.core.dex;

import network.balanced.score.core.dex.LPMetadataDB;
import score.Address;
import score.Context;
import score.VarDB;
import score.DictDB;
import score.BranchDB;
import score.annotation.External;
import score.annotation.EventLog;
import score.annotation.Optional;
import score.annotation.Payable;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import static network.balanced.score.lib.utils.Check.*;

import java.math.BigInteger;
import java.util.Objects;

public class DexImpl {
    // Static DB Names
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
    private static final String SICXICX_MARKET_NAME = "sICX/ICX";
    private static final String CURRENT_DAY = "current_day";
    private static final String TIME_OFFSET = "time_offset";
    private static final String REWARDS_DONE = "rewards_done";
    private static final String DIVIDENDS_DONE = "dividends_done";

    private static BigInteger SICXICX_POOL_ID = BigInteger.valueOf(1L);
    private static BigInteger U_SECONDS_DAY = BigInteger.valueOf(86400 * 1000000);
    private static BigInteger MIN_LIQUIDITY = BigInteger.valueOf(1000);
    private static BigInteger FEE_SCALE = BigInteger.valueOf(10000);
    private static BigInteger EXA = BigInteger.valueOf(1000000000000000000L);
    private static BigInteger FIRST_NON_BALANCED_POOL = BigInteger.valueOf(6L);
    private static BigInteger USDS_BNUSD_ID = BigInteger.valueOf(10L);
    private static BigInteger IUSDT_BNUSD_ID = BigInteger.valueOf(15L);
    private static BigInteger WITHDRAW_LOCK_TIMEOUT = BigInteger.valueOf(86400000000L);
    private static Address MINT_ADDRESS = Address.fromString("hx0000000000000000000000000000000000000000");
    private final String TAG = "Balanced DEX";

    // Initialization

    // Initialize Addresses
    VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    VarDB<Address> dividends = Context.newVarDB(DIVIDENDS_ADDRESS, Address.class);
    VarDB<Address> rewards = Context.newVarDB(REWARDS_ADDRESS, Address.class);
    VarDB<Address> bnUSD = Context.newVarDB(bnUSD_ADDRESS, Address.class);
    VarDB<Address> baln = Context.newVarDB(BALN_ADDRESS, Address.class);
    VarDB<Address> feehandler = Context.newVarDB(FEEHANDLER_ADDRESS, Address.class);
    VarDB<Address> stakedlp = Context.newVarDB(STAKEDLP_ADDRESS, Address.class);
    VarDB<Boolean> dexOn = Context.newVarDB(DEX_ON, Boolean.class);

    // Deposits - Map: token_address -> user_address -> value
    BranchDB<Address, DictDB<Address, BigInteger>> deposit = Context.newBranchDB("deposit", BigInteger.class);
    BranchDB<BigInteger, BranchDB<String, DictDB<BigInteger, BigInteger>>> balnSnapshot = Context.newBranchDB("balnSnapshot", BigInteger.class);
    BranchDB<BigInteger, BranchDB<String, DictDB<BigInteger, BigInteger>>> totalSupplySnapshot = Context.newBranchDB(TOTAL_SUPPLY_SNAPSHOT, BigInteger.class);
    BranchDB<BigInteger, BranchDB<Address, BranchDB<String, DictDB<BigInteger, BigInteger>>>> accountBalanceSnapshot = Context.newBranchDB(ACCOUNT_BALANCE_SNAPSHOT, BigInteger.class);

    // Pool IDs - Map: token address -> opposite token_address -> id
    // New pools should write both addresses, and value is a nonce monotonically increasing from 1
    BranchDB<Address, DictDB<Address, BigInteger>> poolId = Context.newBranchDB("poolId", BigInteger.class);

    // Nonce for poolId
    VarDB<BigInteger> nonce = Context.newVarDB("nonce", BigInteger.class);

    // Total amount of each type of token in a pool
    // Map: pool_id -> token_address -> value
    BranchDB<BigInteger, DictDB<Address, BigInteger>> poolTotal = Context.newBranchDB("poolTotal", BigInteger.class);

    // Total LP Tokens in pool
    // Map: pool_id -> total LP tokens
    DictDB<BigInteger, BigInteger> total = Context.newDictDB("poolLPTotal", BigInteger.class);

    // User Balances
    // Map: pool_id -> user address -> lp token balance
    BranchDB<BigInteger, DictDB<Address, BigInteger>> balance = Context.newBranchDB("balances", BigInteger.class);
    BranchDB<BigInteger, DictDB<Address, BigInteger>> withdrawLock = Context.newBranchDB("withdrawLock", BigInteger.class);

    // Rewards/timekeeping logic
    VarDB<BigInteger> currentDay = Context.newVarDB(CURRENT_DAY, BigInteger.class);
    VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    VarDB<Boolean> rewardsDone = Context.newVarDB(REWARDS_DONE, Boolean.class);
    VarDB<Boolean> dividendsDone = Context.newVarDB(DIVIDENDS_DONE, Boolean.class);

    LPMetadataDB activeAddresses = new LPMetadataDB();

    // TODO(galen): Implement SetDB and quote coins
    //     # Pools must use one of these as a quote currency
    //     self._quote_coins = SetDB(self._QUOTE_COINS, db, value_type=Address)

    //     # All fees are divided by `FEE_SCALE` in consts
    VarDB<BigInteger> poolLpFee = Context.newVarDB("pool_lp_fee", BigInteger.class);
    VarDB<BigInteger> poolBalnFee = Context.newVarDB("pool_baln_fee", BigInteger.class);
    VarDB<BigInteger> icxConversionFee = Context.newVarDB("icx_conversion_fee", BigInteger.class);
    VarDB<BigInteger> icxBalnFee = Context.newVarDB("icx_baln_fee", BigInteger.class);


    // Map: pool_id -> base token address
    DictDB<BigInteger, Address> poolBase = Context.newDictDB("baseToken", Address.class);
    // Map: pool_id -> quote token address
    DictDB<BigInteger, Address> poolQuote = Context.newDictDB("quoteToken", Address.class);
    DictDB<BigInteger, Boolean> active = Context.newDictDB("activePool", Boolean.class);

    // TODO(galen): Implement Linked List, swap queue
    //     # Swap queue for sicxicx
    LinkedListDB icxQueue = new LinkedListDB("icxQueue");

    // Map: user_address -> order id
    DictDB<Address, BigInteger> icxQueueOrderId = Context.newDictDB("icxQueueOrderId", BigInteger.class);

    // Map: user_address -> integer of unclaimed earnings
    DictDB<Address, BigInteger> sicxEarnings = Context.newDictDB("sicxEarnings", BigInteger.class);
    VarDB<BigInteger> icxQueueTotal = Context.newVarDB(ICX_QUEUE_TOTAL, BigInteger.class);

    // Approvals Map: grantor address -> grantee address -> approval status
    BranchDB<Address, DictDB<Address, Boolean>> approvals = Context.newBranchDB("approvals", Boolean.class);

    // TODO(galen): write a python migration of this to EnumerableSetDB
    //     self._named_markets = IterableDictDB(
    //         self._NAMED_MARKETS, db, value_type=int, key_type=str, order=True)

    DictDB<BigInteger, String> marketsToNames = Context.newDictDB("marketsToNames", String.class);

    DictDB<Address, BigInteger> tokenPrecisions = Context.newDictDB("token_precisions", BigInteger.class);

    // VarDB used to track the current sent transaction. This helps bound iterations.
    VarDB<byte[]> currentTx = Context.newVarDB("current_tx", byte[].class);

    // Activation of continuous rewards day
    VarDB<BigInteger> continuousRewardsDay = Context.newVarDB("continuous_rewards_day", BigInteger.class);

    // Events
    @EventLog(indexed = 2)
    public void Swap(BigInteger _id, Address _baseToken, Address _fromToken, Address _toToken,
                     Address _sender, Address _receiver, BigInteger _fromValue, BigInteger _toValue,
                     BigInteger _timestamp, BigInteger _lpFees, int _balnFees, BigInteger _poolBase,
                     BigInteger _poolQuote, BigInteger _endingPrice, BigInteger _effectiveFillPrice) {
    }

    @EventLog(indexed = 3)
    public void MarketAdded(BigInteger _id, Address _baseToken,
                            Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue) {
    }

    @EventLog(indexed = 3)
    public void Add(BigInteger _id, Address _owner, BigInteger _value, BigInteger _base, BigInteger _quote) {
    }

    @EventLog(indexed = 3)
    public void Remove(BigInteger _id, Address _owner, BigInteger _value, BigInteger _base, BigInteger _quote) {
    }

    @EventLog(indexed = 2)
    public void Deposit(Address _token, Address _owner, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void Withdraw(Address _token, Address _owner, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void ClaimSicxEarnings(Address _owner, BigInteger _value) {
    }

    @EventLog(indexed = 3)
    public void TransferSingle(Address _operator, Address _from, Address _to, BigInteger _id, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void ApprovalForAll(Address _owner, Address _operator, Boolean _approved) {
    }

    @EventLog(indexed = 1)
    public void URI(BigInteger _id, String _value) {
    }

    @EventLog(indexed = 1)
    public void Snapshot(BigInteger _id) {
    }

    public DexImpl(@Optional Address _governance) {
        if (_governance != null) {
            governance.set(_governance);

            // Set Default Fee Rates
            poolLpFee.set(BigInteger.valueOf(15L));
            poolBalnFee.set(BigInteger.valueOf(15L));
            icxConversionFee.set(BigInteger.valueOf(70L));
            icxBalnFee.set(BigInteger.valueOf(30L));

            // Set Starting Pool nonce (sICX/ICX reserves 1)
            nonce.set(BigInteger.valueOf(2L));
            currentDay.set(BigInteger.valueOf(1L));
            //TODO(galen): Implement named markets
            //self._named_markets[self._SICXICX_MARKET_NAME] = self._SICXICX_POOL_ID
            marketsToNames.set(SICXICX_POOL_ID, SICXICX_MARKET_NAME);
        }
        continuousRewardsDay.set(BigInteger.ZERO);
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setAdmin(Address _admin) {
        only(governance);
        admin.set(_admin);
    }


    @External(readonly = true)
    public Address getSicx() {
        return sicx.get();
    }

    @External
    public void setSicx(Address _address) {
        only(admin);
        isContract(_address);
        sicx.set(_address);
        // TODO : self._quote_coins.add(_address)
    }

    @External
    public void setDividends(Address _address) {
        only(admin);
        isContract(_address);
        dividends.set(_address);
    }

    @External(readonly = true)
    public Address getDividends() {
        return dividends.get();
    }

    @External
    public void setStaking(Address _address) {
        only(admin);
        isContract(_address);
        staking.set(_address);
    }

    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setRewards(Address _address) {
        only(admin);
        isContract(_address);
        rewards.set(_address);
    }

    @External(readonly = true)
    public Address getRewards() {
        return rewards.get();
    }

    @External
    public void setbnUSD(Address _address) {
        only(admin);
        isContract(_address);
        bnUSD.set(_address);
        // TODO : self._quote_coins.add(_address)
    }

    @External(readonly = true)
    public Address getbnUSD() {
        return bnUSD.get();
    }

    @External
    public void setBaln(Address _address) {
        only(admin);
        isContract(_address);
        baln.set(_address);
    }

    @External(readonly = true)
    public Address getBaln() {
        return baln.get();
    }

    @External
    public void setFeehandler(Address _address) {
        only(admin);
        isContract(_address);
        feehandler.set(_address);
    }

    @External(readonly = true)
    public Address getFeehandler() {
        return feehandler.get();
    }

    @External
    public void setStakedLp(Address _address) {
        only(admin);
        isContract(_address);
        stakedlp.set(_address);
    }

    @External(readonly = true)
    public Address getStakedLp() {
        return stakedlp.get();
    }

    @External
    public void setPoolLpFee(BigInteger _value) {
        only(governance);
        poolLpFee.set(_value);
    }

    @External
    public void setPoolBalnFee(BigInteger _value) {
        only(governance);
        poolBalnFee.set(_value);
    }

    @External
    public void setIcxConversionFee(BigInteger _value) {
        only(governance);
        icxConversionFee.set(_value);
    }

    @External
    public void setIcxBalnFee(BigInteger _value) {
        only(governance);
        icxBalnFee.set(_value);
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        only(governance);
        // TODO  self._named_markets[_name] = _id
        marketsToNames.set(_id, _name);
    }

    @External
    public void turnDexOn() {
        only(governance);
        dexOn.set(true);
    }

    @External(readonly = true)
    public Boolean getDexOn() {
        return dexOn.get();
    }

    @External
    public void addQuoteCoin(Address _address) {
        only(governance);
        // TODO  self._quote_coins.add(_address)
    }

    @External(readonly = true)
    public Boolean isQuoteCoinAllowed(Address _address) {
        // TODO : return _address in self._quote_coins
        return true;
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger timeDelta = blockTime.subtract(timeOffset.get());
        return timeDelta.divide(U_SECONDS_DAY);
    }

    @External
    public void setTimeOffset(BigInteger _delta_time) {
        only(governance);
        timeOffset.set(_delta_time);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.get();
    }

    public boolean isLockingPool(BigInteger _id) {
        boolean continuousRewardsLaunched = continuousRewardsDay.get().compareTo(currentDay.get()) <= 0;
        boolean restrictedPoolId = (_id.compareTo(FIRST_NON_BALANCED_POOL) < 0 || _id.equals(USDS_BNUSD_ID) || _id.equals(IUSDT_BNUSD_ID));

        return restrictedPoolId && continuousRewardsLaunched;
    }

    @External
    public void setContinuousRewardsDay(BigInteger _continuous_rewards_day) {
        only(governance);
        continuousRewardsDay.set(_continuous_rewards_day);
    }

    @External(readonly = true)
    public BigInteger getContinuousRewardsDay() {
        return continuousRewardsDay.get();
    }

    @Payable
    public void fallback() {
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        BigInteger orderValue = Context.getValue();
        Address user = Context.getCaller();
        BigInteger oldOrderValue = BigInteger.ZERO;
        BigInteger orderId = icxQueueOrderId.getOrDefault(user, BigInteger.ZERO);

        Context.require(orderValue.compareTo(EXA.multiply(BigInteger.TEN)) >= 0, TAG + ": Minimum pool contribution is 10 ICX");

        // TODO:         self._withdraw_lock[self._SICXICX_POOL_ID][self.msg.sender] = self.now()

        // Upsert Order so we can bump to the back of the queue
        if (orderId.compareTo(BigInteger.ZERO) > 0) {
            NodeDB node = icxQueue.getNode(orderId);
            oldOrderValue = node.getSize();
            orderValue = orderValue.add(oldOrderValue);
            icxQueue.remove(orderId);
        }

        // Insert order to the back of the queue
        BigInteger nextTailId = icxQueue.getTailId().add(BigInteger.ONE);
        orderId = icxQueue.append(orderValue, user, nextTailId);
        icxQueueOrderId.set(user, orderId);

        BigInteger oldIcxTotal = icxQueueTotal.get();

        // Update total ICX queue size
        BigInteger currentIcxTotal = oldIcxTotal.add(orderValue);

        icxQueueTotal.set(currentIcxTotal);

        //TODO :self._revert_below_minimum(order_value, None)
        activeAddresses.get(SICXICX_POOL_ID).add(user);


        updateAccountSnapshot(user, SICXICX_POOL_ID);
        updateTotalSupplySnapshot(SICXICX_POOL_ID);


        currentIcxTotal = oldIcxTotal.add(orderValue);
        icxQueueTotal.set(currentIcxTotal);


        HashMap<String, Object> rewardsEntry = new HashMap<>();
        rewardsEntry.put("_user", user);
        rewardsEntry.put("_balance", oldOrderValue);
        ArrayList<HashMap<String, Object>> rewardsList = new ArrayList<>();
        rewardsList.add(rewardsEntry);
        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, rewardsList);
    }

    public void cancelSicxicxOrder() {
        isOn(dexOn);
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();
        Address user = Context.getCaller();
        Context.require(icxQueueOrderId.getOrDefault(user, BigInteger.ZERO).compareTo(BigInteger.ZERO) > 0, TAG + ": No open order in sICX/ICX queue.");
        // TODO: self._revert_on_withdrawal_lock(self.msg.sender, self._SICXICX_POOL_ID)
        BigInteger orderId = icxQueueOrderId.get(user);
        NodeDB order = icxQueue.getNode(orderId);
        BigInteger withdrawAmount = order.getSize();
        BigInteger oldIcxTotal = icxQueueTotal.get();
        BigInteger currentIcxTotal = oldIcxTotal.subtract(withdrawAmount);
        icxQueueTotal.set(currentIcxTotal);
        icxQueue.remove(orderId);
        icxQueueOrderId.set(user, null);
        Context.transfer(user, withdrawAmount);
        activeAddresses.get(SICXICX_POOL_ID).remove(user);
        updateAccountSnapshot(user, SICXICX_POOL_ID);
        updateTotalSupplySnapshot(SICXICX_POOL_ID);
        HashMap<String, Object> rewardsEntry = new HashMap<>();
        rewardsEntry.put("_user", user);
        rewardsEntry.put("_balance", withdrawAmount);
        ArrayList<HashMap<String, Object>> rewardsList = new ArrayList<>();
        rewardsList.add(rewardsEntry);
        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, rewardsList);
    }

    private Boolean isReentrantTx() {
        boolean reentrancyStatus = false;
        byte[] txHash = Context.getTransactionHash();
        if (txHash == currentTx.getOrDefault(new byte[0])) {
            reentrancyStatus = true;
        } else {
            currentTx.set(txHash);
        }

        return reentrancyStatus;
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {

        // Take new day snapshot and check distributions as necessary
        this.takeNewDaySnapshot();
        this.checkDistributions();

        // Parse the transaction data submitted by the user
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();
        Address fromToken = Context.getCaller();

        Context.require(_value.compareTo(BigInteger.valueOf(0)) > -1, "Invalid token transfer value");

        // Call an internal method based on the "method" param sent in tokenFallBack
        if (method.equals("_deposit")) {

            BigInteger userBalance = deposit.at(fromToken).getOrDefault(_from, BigInteger.valueOf(0));
            userBalance = userBalance.add(_value);
            deposit.at(fromToken).set(_from, userBalance);
            Deposit(fromToken, _from, _value);

            if (tokenPrecisions.get(fromToken) == null) {
                BigInteger decimalValue = (BigInteger) Context.call(fromToken, "decimals");
                tokenPrecisions.set(fromToken, decimalValue);
            }
        } else if (method.equals("_swap_icx")) {
            if (fromToken.equals(sicx.get())) {
                // TODO:  swapIcx(_from, _value);
            } else {
                Context.revert(
                        TAG + ": InvalidAsset: _swap_icx can only be called with sICX"
                );
            }

        } else if (method.equals("_swap")) {

            // Parse the slippage sent by the user in minimumReceive.
            // If none is sent, use the maximum.
            BigInteger minimumReceive = BigInteger.ZERO;
            if (params.contains("minimumReceive")) {
                minimumReceive = BigInteger.valueOf(params.get("minimumReceive").asInt());
                Context.require(
                        minimumReceive.signum() >= 0,
                        TAG + "Must specify a positive number for minimum to receive"
                );
            }

            // Check if an alternative recipient of the swap is set.
            Address receiver;
            if (params.contains("receiver")) {
                receiver = Address.fromString(params.get("receiver").asString());
            } else {
                receiver = _from;
            }

            // Get destination coin from the swap
            Context.require(params.contains("toToken"), TAG + ": No toToken specified in swap");
            Address toToken = Address.fromString(params.get("toToken").asString());

            // Perform the swap
            exchange(fromToken, toToken, _from, receiver, _value, minimumReceive);

        } else {
            // If no supported method was sent, revert the transaction
            Context.revert(100, TAG + "Unsupported method supplied");
        }
    }

    public void transfer(Address _to, BigInteger _value, BigInteger _id, @Optional byte[] _data) {
        isOn(dexOn);
        if (_data == null) {
            _data = new byte[0];
        }
        _transfer(Context.getCaller(), _to, _value, _id, _data);

    }

    protected void _transfer(Address _from, Address _to, BigInteger _value, BigInteger _id, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0, TAG + ": Transferring value cannot be less than 0.");
        Context.require(balance.at(_id).getOrDefault(_from, BigInteger.ZERO).compareTo(_value) >= 0, TAG + ": Out of balance");
        if (isLockingPool(_id)) {
            Context.revert(TAG + ": untransferrable token id");
        }
        BigInteger fromBalance = balance.at(_id).getOrDefault(_from, BigInteger.ZERO);
        BigInteger toBalance = balance.at(_id).getOrDefault(_to, BigInteger.ZERO);
        balance.at(_id).set(_from, fromBalance.subtract(_value));
        balance.at(_id).set(_to, fromBalance.add(_value));
        if (_value.compareTo(BigInteger.ZERO) > 0) {
            activeAddresses.get(_id).add(_to);
        }
        if (balance.at(_id).getOrDefault(_from, BigInteger.ZERO).compareTo(BigInteger.ZERO) == 0) {
            activeAddresses.get(_id).remove(_from);
        }
        TransferSingle(_from, _from, _to, _id, _value);
        updateAccountSnapshot(_from, _id);
        updateAccountSnapshot(_to, _id);
        if (_to.isContract()) {
            Context.call(_to, "onIRC31Received", _from, _from, _id, _value, _data);
        }
    }

    @External
    public void onIRC31Received(Address _operator, Address _from,
                                BigInteger _id, BigInteger _value,
                                byte[] _data) {
        Context.revert(TAG + ": IRC31 Tokens not accepted");

    }

    @External
    public boolean precompute(BigInteger snap, BigInteger batch_size) {
        return true;
    }

    @External(readonly = true)
    public BigInteger getDeposit(Address _tokenAddress, Address _user) {
        return deposit.at(_tokenAddress).get(_user);
    }

    @External(readonly = true)
    public BigInteger getSicxEarnings(Address _user) {
        return sicxEarnings.get(_user);
    }

    @External(readonly = true)
    public BigInteger getWithdrawLock(BigInteger _id, Address _owner) {
        return withdrawLock.at(_id).get(_owner);
    }

    @External(readonly = true)
    public BigInteger getPoolId(Address _token1Address, Address _token2Address) {
        return poolId.at(_token1Address).get(_token2Address);
    }

    @External(readonly = true)
    public BigInteger getNonce() {
        return nonce.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public ArrayList<String> getNamedPools() {
        // TODO :         for pool in self._named_markets.keys():
        //            rv.append(pool)
        return null;
    }

    @External(readonly = true)
    public BigInteger lookupPid(String _name) {
        // TODO         return self._named_markets[_name]
        return null;
    }

    @External(readonly = true)
    public BigInteger getPoolTotal(BigInteger _id, Address _token) {
        return poolTotal.at(_id).get(_token);
    }

    @External(readonly = true)
    public BigInteger totalSupply(BigInteger _id) {
        if (_id == SICXICX_POOL_ID) {
            return icxQueueTotal.get();
        }
        return total.get(_id);
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, BigInteger _id) {
        if (_id == SICXICX_POOL_ID) {
            BigInteger orderId = icxQueueOrderId.get(_owner);
            if (orderId == null) {
                return BigInteger.ZERO;
            }
            return icxQueue.getNode(orderId).getSize();
        } else {
            return balance.at(_id).get(_owner);
        }
    }

    @External(readonly = true)
    public HashMap<String, BigInteger> getFees() {
        BigInteger icxBalnFees = icxBalnFee.get();
        BigInteger icxConversionFees = icxConversionFee.get();
        BigInteger poolBalnFees = poolBalnFee.get();
        BigInteger poolLpFees = poolLpFee.get();
        HashMap<String, BigInteger> feesMapping = new HashMap<>();
        feesMapping.put("icx_total", icxBalnFees.add(icxConversionFees));
        feesMapping.put("pool_total", poolBalnFees.add(poolLpFees));
        feesMapping.put("pool_lp_fee", poolLpFees);
        feesMapping.put("pool_baln_fee", poolBalnFees);
        feesMapping.put("icx_conversion_fees", icxConversionFees);
        feesMapping.put("icx_baln_fee", icxBalnFees);
        return feesMapping;
    }

    @External(readonly = true)
    public Address getPoolBase(BigInteger _id) {
        return poolBase.get(_id);
    }

    @External(readonly = true)
    public Address getPoolQuote(BigInteger _id) {
        return poolQuote.get(_id);
    }

    @External(readonly = true)
    public BigInteger getQuotePriceInBase(BigInteger _id) {
        Context.require((_id.compareTo(nonce.get()) < 0) && (_id.compareTo(BigInteger.ZERO) == 1), TAG + ": Invalid pool ID");

        if (_id.equals(SICXICX_POOL_ID)) {
            return ((EXA.multiply(EXA)).divide(getSicxRate()));
        }
        Address poolQuoteAddress = poolQuote.get(_id);
        Address poolBaseAddress = poolBase.get(_id);

        BigInteger poolQuote = poolTotal.at(_id).get(poolQuoteAddress);
        BigInteger poolBase = poolTotal.at(_id).get(poolBaseAddress);

        return poolBase.multiply(EXA).divide(poolQuote);
    }

    @External(readonly = true)
    public BigInteger getBasePriceInQuote(BigInteger _id) {
        Context.require((_id.compareTo(nonce.get()) < 0) && (_id.compareTo(BigInteger.ZERO) == 1), TAG + ": Invalid pool ID");

        if (_id.equals(SICXICX_POOL_ID)) {
            return getSicxRate();
        }
        Address poolQuoteAddress = poolQuote.get(_id);
        Address poolBaseAddress = poolBase.get(_id);

        BigInteger poolQuote = poolTotal.at(_id).get(poolQuoteAddress);
        BigInteger poolBase = poolTotal.at(_id).get(poolBaseAddress);

        return poolQuote.multiply(EXA).divide(poolBase);
    }

    @External(readonly = true)
    public BigInteger getPrice(BigInteger _id) {
        return this.getBasePriceInQuote(_id);
    }

    @External(readonly = true)
    public BigInteger getBalnPrice() {
        return getBasePriceInQuote(poolId.at(baln.get()).get(bnUSD.get()));
    }

    @External(readonly = true)
    public BigInteger getSicxBnusdPrice() {
        return getBasePriceInQuote(poolId.at(sicx.get()).get(bnUSD.get()));
    }

    public BigInteger getBnusdValue(String _name) {
        //TODO : _id = self._named_markets[_name]
        BigInteger _id = BigInteger.ZERO;
        if (_id == SICXICX_POOL_ID) {
            BigInteger icxTotal = icxQueueTotal.get();
            return (icxTotal.multiply(getSicxBnusdPrice())).divide(getSicxRate());
        } else if (poolQuote.get(_id) == sicx.get()) {
            BigInteger sicxTotal = (poolTotal.at(_id).get(sicx.get())).multiply(BigInteger.TWO);
            return (getSicxBnusdPrice().multiply(sicxTotal)).divide(EXA);
        } else if (poolQuote.get(_id) == bnUSD.get()) {
            return (poolTotal.at(_id).get(bnUSD.get())).multiply(BigInteger.TWO);
        } else {
            return BigInteger.ZERO;
        }
    }

    @External(readonly = true)
    public BigInteger getPriceByName(String _name) {
        // TODO : self.getPrice(self._named_markets[_name])
        return null;
    }

    @External(readonly = true)
    public BigInteger getICXBalance(Address _address) {
        BigInteger orderId = icxQueueOrderId.get(_address);
        if (orderId == null) {
            return BigInteger.ZERO;
        }
        return icxQueue.getNode(orderId).getSize();
    }

    @External(readonly = true)
    public String getPoolName(BigInteger _id) {
        // TODO :         return self._markets_to_names[_id] if _id in self._markets_to_names else None
        return null;
    }

    @External(readonly = true)
    public HashMap<String, Object> getPoolStats(BigInteger _id) {
        Context.require((_id.compareTo(nonce.get()) < 0) && (_id.compareTo(BigInteger.ZERO) == 1), TAG + ": Invalid pool ID");
        HashMap<String, Object> poolStats = new HashMap<>();
        if (_id == SICXICX_POOL_ID) {
            poolStats.put("base_token", sicx.get());
            poolStats.put("quote_token", null);
            poolStats.put("base", BigInteger.ZERO);
            poolStats.put("quote", icxQueueTotal.get());
            poolStats.put("total_supply", icxQueueTotal.get());
            poolStats.put("price", getPrice(_id));
            poolStats.put("name", SICXICX_MARKET_NAME);
            poolStats.put("base_decimals", 18);
            poolStats.put("quote_decimals", 18);
            // TODO: poolStats.put("min_quote", getRewardableAmount(None));
            return poolStats;
        } else {
            Address baseToken = poolBase.get(_id);
            Address quoteToken = poolQuote.get(_id);
            String name = marketsToNames.get(_id);
            poolStats.put("base", poolTotal.at(_id).get(baseToken));
            poolStats.put("quote", poolTotal.at(_id).get(quoteToken));
            poolStats.put("base_token", baseToken);
            poolStats.put("quote_token", quoteToken);
            poolStats.put("total_supply", total.get(_id));
            poolStats.put("price", getPrice(_id));
            poolStats.put("name", name);
            poolStats.put("base_decimals", tokenPrecisions.get(baseToken));
            poolStats.put("quote_decimals", tokenPrecisions.get(quoteToken));
            // TODO: poolStats.put("min_quote", getRewardableAmount(None));
            return poolStats;
        }
    }

    @External(readonly = true)
    public int totalDexAddresses(BigInteger _id) {
        return activeAddresses.get(_id).length();
    }

    private BigInteger getExchangeRate() {
        return (BigInteger) Context.call(staking.get(), "getTodayRate");
    }

    private void revertOnIncompleteRewards() {
        Context.require(rewardsDone.getOrDefault(false), TAG + " Rewards distribution in progress, please try again shortly");
    }

    private void revertOnWithdrawalLock(Address user, BigInteger id) {
        BigInteger depositTime = withdrawLock.at(id).get(user);
        if (depositTime.add(WITHDRAW_LOCK_TIMEOUT).compareTo(BigInteger.valueOf(Context.getTransactionTimestamp())) > 0) {
            Context.revert(TAG + ":  Assets must remain in the pool for 24 hours, please try again later.");
        }
    }

    @External(readonly = true)
    public HashMap<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        if (_name == SICXICX_MARKET_NAME) {
            HashMap<String, BigInteger> rewardsData = new HashMap<>();
            rewardsData.put("_balance", balanceOf(_owner, SICXICX_POOL_ID));
            rewardsData.put("_totalSupply", totalSupply(SICXICX_POOL_ID));
            return rewardsData;
        }
        BigInteger poolId = lookupPid(_name);
        if (poolId != null) {
            BigInteger totalSupply = (BigInteger) Context.call(stakedlp.get(), "totalSupply", poolId);
            BigInteger balance = (BigInteger) Context.call(stakedlp.get(), "balanceOf", _owner, poolId);
            HashMap<String, BigInteger> rewardsData = new HashMap<>();
            rewardsData.put("_balance", balance);
            rewardsData.put("_totalSupply", totalSupply);
            return rewardsData;
        } else {
            Context.revert(TAG + ": Unsupported data source name");
        }
        return null;
    }

    private void exchange(Address _fromToken, Address _toToken, Address _sender, Address _receiver, BigInteger _value, BigInteger _minimum_receive) {

        BigInteger lpFees = _value.multiply(poolLpFee.get()).divide(FEE_SCALE);
        BigInteger balnFees = _value.multiply(poolBalnFee.get()).divide(FEE_SCALE);
        BigInteger fees = lpFees.add(balnFees);

        BigInteger originalValue = _value;
        BigInteger value = _value.subtract(fees);

        BigInteger id = poolId.at(_fromToken).getOrDefault(_toToken, BigInteger.ZERO);

        Context.require(id.compareTo(BigInteger.ONE) == 1, TAG + ": Invalid Pool ID");


        Address poolBaseToken = poolBase.get(id);
        Boolean isSell = _fromToken == poolBaseToken;

        //TODO(galen): Check with scott if we should even still implement active pools.

        // We consider the trade in terms of toToken (token we are trading to), and
        // fromToken (token we are trading away) in the pool. It must obey the xy=k
        // constant product formula.

        BigInteger oldFromToken = poolTotal.at(id).get(_fromToken);
        BigInteger oldToToken = poolTotal.at(id).get(_toToken);

        // We perturb the pool by the asset we are trading in, less fees.
        // Fees are credited to LPs at the end of the process.
        BigInteger newFromToken = oldFromToken.add(value);

        // Compute the new fromToken according to the constant product formula
        BigInteger newToToken = oldFromToken.multiply(oldToToken).divide(newFromToken);

        // Send the trader the amount of toToken removed from the pool by the constant
        // product formula
        BigInteger sendAmount = oldToToken.subtract(newToToken);

        // Revert the transaction if the below slippage, as specified in _minimum_receive
        Context.require(sendAmount.compareTo(_minimum_receive) >= 0, TAG + ": MinimumReceiveError: Receive amount " + sendAmount.toString() + " below supplied minimum");

        // Apply fees to fromToken after computing constant product. lpFees
        // are credited to the LPs, the rest are sent to BALN holders.
        newFromToken = newFromToken.add(lpFees);

        // Save updated pool totals
        poolTotal.at(id).set(_fromToken, newFromToken);
        poolTotal.at(id).set(_toToken, newToToken);

        // Capture details for eventlogs
        BigInteger totalBase = isSell ? newFromToken : newToToken;
        BigInteger totalQuote = isSell ? newToToken : newFromToken;

        BigInteger sendPrice = (EXA.multiply(_value)).divide(sendAmount);

        // Send the trader their funds
        Context.call(_toToken, "transfer", Context.getAddress(), sendAmount, null);

        // Send the platform fees to the feehandler SCORE
        Context.call(_fromToken, "transfer", feehandler.get(), balnFees, null);

        // Broadcast pool ending price
        BigInteger endingPrice = this.getPrice(id);
        BigInteger effectiveFillPrice = sendPrice;

        if (!isSell) {
            effectiveFillPrice = EXA.multiply(sendAmount).divide(value);
        }

        //Swap(id, poolBaseToken, _fromToken, _toToken, _sender, _receiver, originalValue, sendAmount, Context.getBlockTimestamp(), lpFees, balnFees, totalBase, totalQuote, endingPrice, effectiveFillPrice);
    }

    protected BigInteger getSicxRate() {
        return (BigInteger) Context.call(staking.get(), "getTodayRate()");
    }

    protected BigInteger getRewardableAmount(Address tokenAddress) {
        if (tokenAddress == null) {
            return BigInteger.TEN.multiply(EXA);
        } else if (sicx.get() == tokenAddress) {
            return (BigInteger.TEN.multiply(EXA.multiply(EXA))).divide(getSicxRate());
        } else if (bnUSD.get() == tokenAddress) {
            return BigInteger.TEN.multiply(EXA);
        } else {
            return BigInteger.ZERO;
        }
    }

    // TODO _swap_ICX


    private BigInteger getUnitValue(Address tokenAddress) {
        if (tokenAddress == null) {
            return EXA;
        } else {
            return BigInteger.TEN.pow(tokenPrecisions.get(tokenAddress).intValue());
        }
    }

    private void revertBelowMinimum(BigInteger value, Address quoteToken) {
        BigInteger minAmount = getRewardableAmount(quoteToken);
        if (value.compareTo(minAmount) < 0) {
            BigInteger readableMin = minAmount.divide(getUnitValue(quoteToken));
            Context.revert(TAG + ": Total liquidity provided must be above " + readableMin + " quote currency");
        }
    }


    // Day Change Functions
    private void takeNewDaySnapshot() {
        BigInteger day = this.getDay();
        if (day.compareTo(currentDay.get()) > 0) {
            currentDay.set(day);
            Snapshot(day);
            rewardsDone.set(false);
            if (day.mod(BigInteger.valueOf(7)).compareTo(BigInteger.valueOf(0)) == 0) {
                dividendsDone.set(false);
            }
        }
    }

    private void checkDistributions() {
        if (this.isReentrantTx()) {
            return;
        }

        if (rewardsDone.getOrDefault(false) != true) {
            Context.call(rewards.get(), "distribute");
        } else if (dividendsDone.getOrDefault(false) != true) {
            Context.call(dividends.get(), "distribute");

        }
    }

    private void updateAccountSnapshot(Address account, BigInteger id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentTime = BigInteger.valueOf(Context.getTransactionTimestamp());
        BigInteger currentValue = balanceOf(account, id);
        BigInteger length = accountBalanceSnapshot.at(id).at(account).at("length").get(BigInteger.ZERO);
        BigInteger lastSnapshotId = BigInteger.ZERO;
        BigInteger dayStartUs = timeOffset.get().add(U_SECONDS_DAY.multiply(currentId));
        BigInteger dayElapsedUs = currentTime.subtract(dayStartUs);
        BigInteger dayRemainingUs = U_SECONDS_DAY.subtract(dayElapsedUs);
        if (length == BigInteger.ZERO) {
            accountBalanceSnapshot.at(id).at(account).at("ids").set(length, currentId);
            accountBalanceSnapshot.at(id).at(account).at("values").set(length, currentValue);
            accountBalanceSnapshot.at(id).at(account).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        } else {
            lastSnapshotId = accountBalanceSnapshot.at(id).at(account).at("length").get(length);

        }
        if (lastSnapshotId.compareTo(currentId) < 0) {
            accountBalanceSnapshot.at(id).at(account).at("ids").set(length, currentId);
            accountBalanceSnapshot.at(id).at(account).at("values").set(length, currentValue);
            accountBalanceSnapshot.at(id).at(account).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
        } else {
            accountBalanceSnapshot.at(id).at(account).at("values").set(length.subtract(BigInteger.ONE), currentValue);

        }
    }

    private void updateBalnSnapshot(BigInteger _id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentTime = BigInteger.valueOf(Context.getTransactionTimestamp());
        BigInteger currentValue = poolTotal.at(_id).get(baln.get());
        BigInteger length = balnSnapshot.at(_id).at("length").get(BigInteger.ZERO);
        BigInteger lastSnapshotId = BigInteger.ZERO;

        BigInteger dayStartUs = timeOffset.get().add(U_SECONDS_DAY.multiply(currentId));
        BigInteger dayElapsedUs = currentTime.subtract(dayStartUs);
        BigInteger dayRemainingUs = U_SECONDS_DAY.subtract(dayElapsedUs);
        if (length == BigInteger.ZERO) {
            balnSnapshot.at(_id).at("ids").set(length, currentId);
            balnSnapshot.at(_id).at("values").set(length, currentValue);
            balnSnapshot.at(_id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        } else {

            lastSnapshotId = balnSnapshot.at(_id).at("ids").get(length.subtract(BigInteger.ONE));

        }
        if (lastSnapshotId.compareTo(currentId) < 0) {
            balnSnapshot.at(_id).at("ids").set(length, currentId);
            balnSnapshot.at(_id).at("values").set(length, currentValue);
            balnSnapshot.at(_id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));

        } else {
            balnSnapshot.at(_id).at("values").set(length.subtract(BigInteger.ONE), currentValue);
        }
    }

    private void updateTotalSupplySnapshot(BigInteger id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentTime = BigInteger.valueOf(Context.getTransactionTimestamp());
        BigInteger currentValue = totalSupply(id);
        BigInteger length = balnSnapshot.at(id).at("length").get(BigInteger.ZERO);
        BigInteger lastSnapshotId = BigInteger.ZERO;

        BigInteger dayStartUs = timeOffset.get().add(U_SECONDS_DAY.multiply(currentId));
        BigInteger dayElapsedUs = currentTime.subtract(dayStartUs);
        BigInteger dayRemainingUs = U_SECONDS_DAY.subtract(dayElapsedUs);
        if (length == BigInteger.ZERO) {
            totalSupplySnapshot.at(id).at("ids").set(length, currentId);
            totalSupplySnapshot.at(id).at("values").set(length, currentValue);
            totalSupplySnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        } else {
            lastSnapshotId = totalSupplySnapshot.at(id).at("ids").get(length.subtract(BigInteger.ONE));

        }
        if (lastSnapshotId.compareTo(currentId) < 0) {
            totalSupplySnapshot.at(id).at("ids").set(length, currentId);
            totalSupplySnapshot.at(id).at("values").set(length, currentValue);
            totalSupplySnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));

        } else {
            totalSupplySnapshot.at(id).at("values").set(length.subtract(BigInteger.ONE), currentValue);
        }

    }

    @External(readonly = true)
    public BigInteger balanceOfAt(Address _account, BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {
        BigInteger matchedIndex = BigInteger.ZERO;
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert(TAG + ": Snapshot id is equal to or greater then Zero.");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = accountBalanceSnapshot.at(_id).at(_account).at("length").get(BigInteger.ZERO);

        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (accountBalanceSnapshot.at(_id).at(_account).at("ids").get(mid).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }

        }

        if (accountBalanceSnapshot.at(_id).at(_account).at("ids").get(BigInteger.ZERO) == _snapshot_id) {
            return accountBalanceSnapshot.at(_id).at(_account).at("values").get(BigInteger.ZERO);
        } else if (low == BigInteger.ZERO) {
            return BigInteger.ZERO;
        } else {
            matchedIndex = low.subtract(BigInteger.ONE);
        }
        return null;
    }

    @External(readonly = true)
    public BigInteger totalSupplyAt(BigInteger _id, BigInteger _snapshot_id, @Optional Boolean _twa) {
        BigInteger matchedIndex = BigInteger.ZERO;
        if (_twa == null) {
            _twa = false;
        }
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Snapshot id is equal to or greater then Zero");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = totalSupplySnapshot.at(_id).at("length").get(BigInteger.ZERO);
        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (totalSupplySnapshot.at(_id).at("ids").get(mid).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }
        }
        if (totalSupplySnapshot.at(_id).at("ids").get(BigInteger.ZERO) == _snapshot_id) {
            return totalSupplySnapshot.at(_id).at("values").get(BigInteger.ZERO);
        } else if (low == BigInteger.ZERO) {
            return BigInteger.ZERO;
        } else {
            matchedIndex = low.subtract(BigInteger.ONE);
        }
        return totalSupplySnapshot.at(_id).at("values").get(matchedIndex);
    }

    @External(readonly = true)
    public BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id, @Optional Boolean _twa) {
        BigInteger matchedIndex = BigInteger.ZERO;
        if (_twa == null) {
            _twa = false;
        }
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Snapshot id is equal to or greater then Zero");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = balnSnapshot.at(_id).at("length").get(BigInteger.ZERO);
        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (balnSnapshot.at(_id).at("ids").get(mid).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }
        }
        if (balnSnapshot.at(_id).at("ids").get(BigInteger.ZERO) == _snapshot_id) {
            return balnSnapshot.at(_id).at("values").get(BigInteger.ZERO);
        } else if (low == BigInteger.ZERO) {
            return BigInteger.ZERO;
        } else {
            matchedIndex = low.subtract(BigInteger.ONE);
        }
        return balnSnapshot.at(_id).at("values").get(matchedIndex);
    }

    @External(readonly = true)
    public BigInteger getTotalValue(String _name, BigInteger _snapshot_id) {
        // TODO: return totalSupply(na)
        return null;
    }

    @External(readonly = true)
    public BigInteger getBalnSnapshot(String _name, BigInteger _snapshot_id) {
        // TODO:
        return null;
    }

    public HashMap<String, Object> loadBalancesAtSnapshot(BigInteger _id, BigInteger _snapshot_id, BigInteger _limit, @Optional BigInteger _offset) {
        if (_offset == null) {
            _offset = BigInteger.ZERO;
        }
        Context.require(_snapshot_id.compareTo(BigInteger.ZERO) >= 0, TAG + ":  Snapshot id is equal to or greater then Zero.");
        Context.require(_id.compareTo(BigInteger.ZERO) >= 0, TAG + ":  Pool id is equal to or greater then Zero.");
        Context.require(_offset.compareTo(BigInteger.ZERO) >= 0, TAG + ":  Offset is equal to or greater then Zero.");
        HashMap<String, Object> rv = new HashMap<>();

        // TODO : for (Address addr : activeAddresses.get(_id).range())
        return rv;
    }

    @External(readonly = true)
    public HashMap<String, Object> getDataBatch(String _name, BigInteger _snapshot_id, BigInteger _limit, @Optional BigInteger _offset) {
        if (_offset == null) {
            _offset = BigInteger.ZERO;
        }
        // TODO: namedMarkets
        BigInteger pid = BigInteger.ZERO;
        BigInteger total = BigInteger.valueOf(totalDexAddresses(pid));
        BigInteger clampedOffset = _offset.max(total);
        BigInteger clamped_limit = _limit.min(total.subtract(clampedOffset));
        return loadBalancesAtSnapshot(pid, _snapshot_id, clamped_limit, clampedOffset);
    }

    @External
    public void permit(BigInteger _id, boolean _permission) {
        only(governance);
        active.set(_id, _permission);
    }

    @External
    public void withdraw(Address _token, BigInteger _value) {
        Address sender = Context.getCaller();
        BigInteger deposit_amount = deposit.at(_token).getOrDefault(sender, BigInteger.ZERO);

        Context.require(_value.compareTo(deposit_amount) >= 0, TAG + ": Insufficient Balance");
        Context.require(_value.compareTo(BigInteger.ZERO) > 1, TAG + ": Must specify a posititve amount");

        deposit.at(_token).set(sender, deposit_amount.subtract(_value));

        Context.call(_token, "transfer", Context.getAddress(), _value, null);
        Withdraw(_token, Context.getCaller(), _value);
    }

    @External
    public void remove(BigInteger _id, BigInteger _value, Boolean _withdraw) {
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        //TODO(galen): Withdrawal lock

        Address user = Context.getCaller();
        BigInteger userBalance = balance.at(_id).getOrDefault(user, BigInteger.ZERO);

        //TODO(galen): Should we even reimplement deactive pool by id?

        Context.require(_value.compareTo(BigInteger.ZERO) == 1, TAG + " Cannot withdraw a negative balance");
        Context.require(_value.compareTo(userBalance) >= 0, TAG + ": Insufficient balance");

        Address baseToken = poolBase.get(_id);
        Address quoteToken = poolQuote.get(_id);

        BigInteger totalBase = poolTotal.at(_id).get(baseToken);
        BigInteger totalQuote = poolTotal.at(_id).get(quoteToken);
        BigInteger totalLPToken = total.get(_id);

        activeAddresses.get(_id).remove(Context.getCaller());

        BigInteger baseToWithdraw = totalBase.multiply(_value).divide(totalLPToken);
        BigInteger quoteToWithdraw = totalQuote.multiply(_value).divide(totalLPToken);

        BigInteger newBase = totalBase.subtract(baseToWithdraw);
        BigInteger newQuote = totalQuote.subtract(quoteToWithdraw);
        BigInteger newUserBalance = userBalance.subtract(_value);
        BigInteger newTotal = totalLPToken.subtract(_value);

        Context.require(newTotal.compareTo(MIN_LIQUIDITY) >= 0, TAG + ": Cannot withdraw pool past minimum LP token amount");

        poolTotal.at(_id).set(baseToken, newBase);
        poolTotal.at(_id).set(quoteToken, newQuote);
        balance.at(_id).set(user, newUserBalance);
        total.set(_id, newTotal);

        Remove(_id, user, _value, baseToWithdraw, quoteToWithdraw);

        BigInteger depositedBase = deposit.at(baseToken).getOrDefault(user, BigInteger.ZERO);
        deposit.at(baseToken).set(user, depositedBase.add(baseToWithdraw));

        BigInteger depositedQuote = deposit.at(quoteToken).getOrDefault(user, BigInteger.ZERO);
        deposit.at(quoteToken).set(user, depositedQuote.add(quoteToWithdraw));
        updateAccountSnapshot(user, _id);
        updateTotalSupplySnapshot(_id);
        if (baseToken == baln.get()) {
            updateBalnSnapshot(_id);
        }

        if (_withdraw) {
            withdraw(baseToken, baseToWithdraw);
            withdraw(quoteToken, quoteToWithdraw);
        }

    }

    @External
    public void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
                    Boolean _withdraw_unused) {
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        Address user = Context.getCaller();

        // We check if there is a previously seen pool with this id.
        // If none is found (return 0), we create a new pool.
        BigInteger id = poolId.at(_baseToken).getOrDefault(_quoteToken, BigInteger.ZERO);

        Context.require(_baseToken != _quoteToken, TAG + ": Pool must contain two token contracts");

        // Check base/quote balances are valid
        Context.require(_baseValue.compareTo(BigInteger.ZERO) < 1, TAG + ": Cannot send 0 or negative base token");
        Context.require(_quoteValue.compareTo(BigInteger.ZERO) < 1, TAG + ": Cannot send 0 or negative quote token");

        BigInteger userDepositedBase = deposit.at(_baseToken).getOrDefault(user, BigInteger.ZERO);
        BigInteger userDepositedQuote = deposit.at(_quoteToken).getOrDefault(user, BigInteger.ZERO);

        // Check deposits are sufficient to cover balances
        Context.require(userDepositedBase.compareTo(_baseValue) >= 0, TAG + ": Insufficient base asset funds deposited");
        Context.require(userDepositedQuote.compareTo(_quoteValue) >= 0, TAG + ": Insufficient quote asset funds deposited");

        BigInteger baseToCommit = _baseValue;
        BigInteger quoteToCommit = _quoteValue;

        // Initialize pool total variables
        BigInteger liquidity = BigInteger.ZERO;
        BigInteger poolBaseAmount = BigInteger.ZERO;
        BigInteger poolQuoteAmount = BigInteger.ZERO;
        BigInteger poolLpAmount = BigInteger.ZERO;
        BigInteger userLpAmount = BigInteger.ZERO;

        // We need to only supply new base and quote in the pool ratio.
        // If there isn't a pool yet, we can form one with the supplied ratios.

        if (id.compareTo(BigInteger.ZERO) == 0) {
            // No pool exists for this pair, we should create a new one.

            // Issue the next pool id to this pool
            BigInteger nextPoolNonce = nonce.get();
            poolId.at(_baseToken).set(_quoteToken, nextPoolNonce);
            poolId.at(_quoteToken).set(_baseToken, nextPoolNonce);
            id = nextPoolNonce;

            nonce.set(nextPoolNonce.add(BigInteger.ONE));

            //TODO(galen): Discuss with scott whether we should even use active pools, and activate here if so.

            liquidity = (_baseValue.multiply(_quoteValue)).sqrt();

            Context.require(liquidity.compareTo(MIN_LIQUIDITY) == 1, TAG + ": Initial LP tokens must exceed " + MIN_LIQUIDITY.toString(10));

            MarketAdded(id, _baseToken, _quoteToken, _baseValue, _quoteValue);
        } else {
            // Pool already exists, supply in the permitted order.
            Address poolBaseAddress = poolBase.get(id);
            Address poolQuoteAddress = poolQuote.get(id);

            Context.require((poolBaseAddress == _baseToken) && (poolQuoteAddress == _quoteToken), TAG + ": Must supply " + _baseToken.toString() + " as base and " + _quoteToken.toString() + " as quote");

            // We can only commit in the ratio of the pool. We determine this as:
            // Min(ratio of quote from base, ratio of base from quote)
            // Any assets not used are refunded

            poolBaseAmount = poolTotal.at(id).get(_baseToken);
            poolQuoteAmount = poolTotal.at(id).get(_quoteToken);
            poolLpAmount = total.get(id);

            BigInteger baseFromQuote = _quoteValue.multiply(poolBaseAmount).divide(poolQuoteAmount);
            BigInteger quoteFromBase = _baseValue.multiply(poolQuoteAmount).divide(poolBaseAmount);

            if (quoteToCommit.compareTo(_quoteValue) < 1) {
                quoteToCommit = quoteFromBase;
            } else {
                baseToCommit = baseFromQuote;
            }

            BigInteger liquidityFromBase = poolLpAmount.multiply(baseToCommit).divide(poolBaseAmount);
            BigInteger liquidityFromQuote = poolLpAmount.multiply(quoteToCommit).divide(poolQuoteAmount);

            liquidity = liquidityFromBase.min(liquidityFromQuote);

            Context.require(liquidity.compareTo(BigInteger.ZERO) >= 0);

            // Set user previous LP Balance
            userLpAmount = balance.at(id).getOrDefault(user, BigInteger.ZERO);
        }

        // Apply the funds to the pool

        poolBaseAmount = poolBaseAmount.add(baseToCommit);
        poolQuoteAmount = poolQuoteAmount.add(quoteToCommit);

        poolTotal.at(id).set(_baseToken, baseToCommit);
        poolTotal.at(id).set(_quoteToken, quoteToCommit);

        // Deduct the user's deposit
        userDepositedBase = userDepositedBase.subtract(baseToCommit);
        deposit.at(_baseToken).set(user, userDepositedBase);
        userDepositedQuote = userDepositedQuote.subtract(quoteToCommit);
        deposit.at(_quoteToken).set(user, userDepositedQuote);

        // Credit the user LP Tokens
        userLpAmount = userLpAmount.add(liquidity);
        poolLpAmount = poolLpAmount.add(liquidity);

        balance.at(id).set(user, userLpAmount);
        total.set(id, poolLpAmount);

        Add(id, user, liquidity, baseToCommit, quoteToCommit);

        TransferSingle(user, MINT_ADDRESS, user, id, liquidity);

        activeAddresses.get(id).add(user);
        updateAccountSnapshot(user, id);
        updateTotalSupplySnapshot(id);
        if (_baseToken == baln.get()) {
            updateBalnSnapshot(id);
        }

        if (_withdraw_unused) {

            if (userDepositedBase.compareTo(BigInteger.ZERO) == 1) {
                withdraw(_baseToken, userDepositedBase);
            }

            if (userDepositedQuote.compareTo(BigInteger.ZERO) == 1) {
                withdraw(_quoteToken, userDepositedQuote);
            }

        }
    }
    // TODO : crosscheck galen's code exchange, swap_icx, remove, add, withdraw,

    public void withdrawSicxEarnings(@Optional BigInteger _value) {
        isOn(dexOn);
        if (_value == null) {
            _value = BigInteger.ZERO;
        }
        Address sender = Context.getCaller();
        if (Objects.equals(_value, BigInteger.ZERO)) {
            _value = sicxEarnings.get(sender);
        }

        if (_value.compareTo(sicxEarnings.get(sender)) > 0) {
            Context.revert(TAG + ": Insufficient balance.");
        }
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": InvalidAmountError: Please send a positive amount.");
        sicxEarnings.set(sender, sicxEarnings.getOrDefault(sender, BigInteger.ZERO).subtract(_value));
        Context.call(sicx.get(), "transfer", sender, _value);
        ClaimSicxEarnings(sender, _value);
    }

    public void addLpAddresses(BigInteger _poolId, ArrayList<Object> _addresses) {
        for (Object address : _addresses) {
            if (balanceOf((Address) address, _poolId).compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(_poolId).add((Address) address);
            }
        }
    }
}
