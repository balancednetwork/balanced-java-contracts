package network.balanced.score.core.router;

import com.eclipsesource.json.JsonValue;

import network.balanced.score.core.router.LPMetadataDB;
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

import java.math.BigInteger;
import java.util.ArrayList;

import network.balanced.score.core.dex.LinkedListDB;
import network.balanced.score.core.dex.NodeDB;

import javax.management.modelmbean.RequiredModelMBean;

public class Dex {
    // Static DB Names
    private static String ACCOUNT_BALANCE_SNAPSHOT = "account_balance_snapshot";
    private static String TOTAL_SUPPLY_SNAPSHOT = "total_supply_snapshot";
    private static String QUOTE_COINS = "quote_coins";
    private static String ICX_QUEUE_TOTAL = "icx_queue_total";
    private static String SICX_ADDRESS = "sicx_address";
    private static String bnUSD_ADDRESS = "bnUSD_address";
    private static String BALN_ADDRESS = "baln_address";
    private static String STAKING_ADDRESS = "staking_address";
    private static String DIVIDENDS_ADDRESS = "dividends_address";
    private static String REWARDS_ADDRESS = "rewards_address";
    private static String GOVERNANCE_ADDRESS = "governance_address";
    private static String FEEHANDLER_ADDRESS = "feehandler_address";
    private static String NAMED_MARKETS = "named_markets";
    private static String ADMIN = "admin";
    private static String DEX_ON = "dex_on";
    private static String SICXICX_MARKET_NAME = "sICX/ICX";
    private static String CURRENT_DAY = "current_day";
    private static String TIME_OFFSET = "time_offset";
    private static String REWARDS_DONE = "rewards_done";
    private static String DIVIDENDS_DONE = "dividends_done";
    private static BigInteger SICXICX_POOL_ID = BigInteger.valueOf(1L);
    private static BigInteger U_SECONDS_DAY = BigInteger.valueOf(86400 * 1000000);
    private static BigInteger MIN_LIQUIDITY = BigInteger.valueOf(1000);
    private static BigInteger FEE_SCALE = BigInteger.valueOf(10000);
    private static BigInteger EXA = BigInteger.valueOf(1000000000000000000L);
    private static Address MINT_ADDRESS = Address.fromString("hx0000000000000000000000000000000000000000");
    private final String TAG = "BalancedDEX";

    // Initialization

    // Initialize Addresses
    VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    VarDB<Address> dividends = Context.newVarDB(DIVIDENDS_ADDRESS, Address.class);
    VarDB<Address> rewards = Context.newVarDB( REWARDS_ADDRESS, Address.class);
    VarDB<Address> bnUSD = Context.newVarDB(bnUSD_ADDRESS, Address.class);
    VarDB<Address> baln = Context.newVarDB(BALN_ADDRESS, Address.class);
    VarDB<Address> feehandler = Context.newVarDB(FEEHANDLER_ADDRESS, Address.class);
    VarDB<Boolean> dexOn = Context.newVarDB(DEX_ON, Boolean.class);

    // Deposits - Map: token_address -> user_address -> value
    BranchDB<Address, DictDB<Address, BigInteger>> deposit = Context.newBranchDB("deposit", BigInteger.class);

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

    // Events
    @EventLog(indexed=2)
    public void Swap(BigInteger _id, Address _baseToken, Address _fromToken, Address _toToken,
             Address _sender, Address _receiver, BigInteger _fromValue, BigInteger _toValue,
             BigInteger _timestamp, BigInteger _lpFees, int _balnFees, BigInteger _poolBase,
             BigInteger _poolQuote, BigInteger _endingPrice, BigInteger _effectiveFillPrice) {}

    @EventLog(indexed=3)
    public void MarketAdded(BigInteger _id, Address _baseToken,
                    Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue) {}

    @EventLog(indexed=3)
    public void Add(BigInteger _id, Address _owner, BigInteger _value, BigInteger _base, BigInteger _quote) {}

    @EventLog(indexed=3)
    public void Remove(BigInteger _id, Address _owner, BigInteger _value, BigInteger _base , BigInteger _quote) {}

    @EventLog(indexed=2)
    public void Deposit(Address _token, Address _owner, BigInteger _value) {}

    @EventLog(indexed=2)
    public void Withdraw(Address _token, Address _owner, BigInteger _value) {}

    @EventLog(indexed=2)
    public void ClaimSicxEarnings(Address _owner, BigInteger _value) {}

    @EventLog(indexed=3)
    public void TransferSingle(Address _operator, Address _from, Address _to, BigInteger _id, BigInteger _value) {}

    @EventLog(indexed=1)
    public void URI(BigInteger _id, String _value) {}

    @EventLog(indexed=1)
    public void Snapshot(BigInteger _id) {}

    public Dex(@Optional Address _governance) {
        if(_governance != null) {
            governance.set(_governance);
            admin.set(_governance);
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
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    /**
     *  Gets the current admin address. This user can call using the
     *         `@only_admin` decorator.
     * @return Returns the address of the current admin
     */
    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    /**
     *
     * @param _admin  The new admin address to set.
     *         Can make calls with the `@only_admin` decorator.
     *         Should be called before DEX use.
     */
    @External
    public void setAdmin(Address _admin) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        admin.set(_admin);
    }


    @External(readonly = true)
    public Address getSicx() {
        return sicx.get();
    }

    @External
    public void setSicx(Address _address) {
        Context.require(
                Context.getCaller() == admin.get(),
                "Only governance address can call this method"
        );
        Context.require(_address.isContract(), TAG +
                "Address provided is an EOA address. A contract address is required.");
        sicx.set(_address);
    }

    /**
     * Sets new Governance contract address. Should be called before dex use.
     * @param _address New contract address to set.
     */
    @External
    public void setGovernance(Address _address) {
        Context.require(
                Context.getCaller() == Context.getOwner(),
                "Only g governance call this method"
        );
        Context.require(_address.isContract(), TAG +
                "Address provided is an EOA address. A contract address is required.");
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    /**
     *
     * @return Gets the address of the Staking contract.
     */
    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger timeDelta = blockTime.subtract(timeOffset.get());
        return timeDelta;
    }

    @External
    public void setTimeOffset(BigInteger _delta_time) {
        timeOffset.set(_delta_time);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.get();
    }

    @External(readonly = true)
    public BigInteger getQuotePriceInBase(BigInteger _id) {
        Context.require((_id.compareTo(nonce.get()) < 0) && (_id.compareTo(BigInteger.ZERO) == 1), TAG + ": Invalid pool ID");

        // TODO(galen): handle SICX_ICX case
        Address poolQuoteAddress = poolQuote.get(_id);
        Address poolBaseAddress = poolBase.get(_id);

        BigInteger poolQuote = poolTotal.at(_id).get(poolQuoteAddress);
        BigInteger poolBase = poolTotal.at(_id).get(poolBaseAddress);

        return poolBase.multiply(EXA).divide(poolQuote);
    }


    @External(readonly = true)
    public BigInteger getBasePriceInQuote(BigInteger _id) {
        Context.require((_id.compareTo(nonce.get()) < 0) && (_id.compareTo(BigInteger.ZERO) == 1), TAG + ": Invalid pool ID");

        // TODO(galen): handle SICX_ICX case
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

    private Boolean isReentrantTx() {
        Boolean reentrancyStatus = false;

        if (Context.getTransactionHash() == currentTx.getOrDefault(new byte[0])) {
            reentrancyStatus = true;
        }
        currentTx.set(Context.getTransactionHash());

        return reentrancyStatus;
    }

    // Day Change Functions
    private void takeNewDaySnapshot() {
        BigInteger day = this.getDay();
        if (day.compareTo(currentDay.get()) == 1) {
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
            // TODO(galen): Create interface score and call rewards.
            // Set rewardsDone to the result of the distribute() call
        }

        if (dividendsDone.getOrDefault(false) != true) {
            // TODO(galen): Create interface score and call dividends
            // set dividendsDone to the result of the distribute() call
        }
    }

    private void revertOnIncompleteRewards() {
        Context.require(rewardsDone.getOrDefault(false), TAG + " Rewards distribution in progress, please try again shortly");
    }

    @Payable
    public void fallback() {
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        BigInteger orderValue = Context.getValue();
        Address user = Context.getCaller();

        Context.require(orderValue.compareTo(EXA.multiply(BigInteger.TEN)) >= 0, TAG + ": Minimum pool contribution is 10 ICX");

        BigInteger orderId = icxQueueOrderId.getOrDefault(user, BigInteger.ZERO);

        // Update total ICX queue size
        BigInteger currentIcxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO).add(orderValue);
        icxQueueTotal.set(currentIcxTotal);

        // Upsert Order so we can bump to the back of the queue
        if (orderId.compareTo(BigInteger.ZERO) > 0) {
            NodeDB node = icxQueue.getNode(orderId);
            orderValue = orderValue.add(node.getSize());
            icxQueue.remove(orderId);
        }

        // Insert order to the back of the queue
        BigInteger nextTailId = icxQueue.getTailId().add(BigInteger.ONE);
        orderId = icxQueue.append(orderValue, user, nextTailId);

        // Update user's linked order
        icxQueueOrderId.set(user, orderId);

        activeAddresses.get(SICXICX_POOL_ID).add(user);
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
            userBalance.add(_value);
            deposit.at(fromToken).set(_from, userBalance);
            Deposit(fromToken, _from, _value);

        } else if (method == "_swap_icx") {

                Context.require(
                        fromToken == sicx.get(),
                        TAG + "Swap ICX can called with sICX"
                );
            //TODO(galen): Implement swap icx

        } else if (method == "_swap"){

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
            if(params.contains("receiver")) {
                receiver = Address.fromString(params.get("receiver").asString());
            }
            else  {
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

    private void exchange(Address _fromToken, Address _toToken, Address _sender, Address  _receiver, BigInteger _value, BigInteger _minimum_receive) {

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

        if(!isSell) {
            effectiveFillPrice = EXA.multiply(sendAmount).divide(value);
        }

        //Swap(id, poolBaseToken, _fromToken, _toToken, _sender, _receiver, originalValue, sendAmount, Context.getBlockTimestamp(), lpFees, balnFees, totalBase, totalQuote, endingPrice, effectiveFillPrice);
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

        if (_withdraw_unused) {

            if (userDepositedBase.compareTo(BigInteger.ZERO) == 1) {
                withdraw(_baseToken, userDepositedBase);
            }

            if (userDepositedQuote.compareTo(BigInteger.ZERO) == 1) {
                withdraw(_quoteToken, userDepositedQuote);
            }
                    
        }
    }


}
