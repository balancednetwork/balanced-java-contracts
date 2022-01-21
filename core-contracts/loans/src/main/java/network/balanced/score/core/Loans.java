package network.balanced.score.core;

import score.VarDB;
import score.Address;
import score.Context;
import score.annotation.Optional;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import scorex.util.ArrayList;
import java.util.Map;
import java.util.List;

import static java.util.Map.entry;
import scorex.util.HashMap;

import static network.balanced.score.core.Checks.*;
import static network.balanced.score.core.Constants.*;
import network.balanced.score.core.AssetDB;
import network.balanced.score.core.SnapshotDB;
import network.balanced.score.core.PositionsDB;

public class Loans {
    public static final String _LOANS_ON = "loans_on";
    public static final String _GOVERNANCE = "governance";
    public static final String _REBALANCE = "rebalance";
    public static final String _DEX = "dex";
    public static final String _DIVIDENDS = "dividends";
    public static final String _RESERVE = "reserve";
    public static final String _REWARDS = "rewards";
    public static final String _STAKING = "staking";
    public static final String _ADMIN = "admin";
    public static final String _SNAP_BATCH_SIZE = "snap_batch_size";
    public static final String _GLOBAL_INDEX = "global_index";
    public static final String _GLOBAL_BATCH_INDEX = "global_batch_index";

    public static final String _REWARDS_DONE = "rewards_done";
    public static final String _DIVIDENDS_DONE = "dividends_done";
    public static final String _CURRENT_DAY = "current_day";
    public static final String _TIME_OFFSET = "time_offset";

    public static final String _MINING_RATIO = "mining_ratio";
    public static final String _LOCKING_RATIO = "locking_ratio";
    public static final String _LIQUIDATION_RATIO = "liquidation_ratio";
    public static final String _ORIGINATION_FEE = "origination_fee";
    public static final String _REDEMPTION_FEE = "redemption_fee";
    public static final String _RETIREMENT_BONUS = "retirement_bonus";
    public static final String _LIQUIDATION_REWARD = "liquidation_reward";
    public static final String _NEW_LOAN_MINIMUM = "new_loan_minimum";
    public static final String _MIN_MINING_DEBT = "min_mining_debt";
    public static final String _MAX_DEBTS_LIST_LENGTH = "max_debts_list_length";

    public static final String _REDEEM_BATCH_SIZE = "redeem_batch_size";
    public static final String _MAX_RETIRE_PERCENT = "max_retire_percent";
    public static final String _CONTINUOUS_REWARD_DAY = "continuous_reward_day";

    public static final VarDB<Boolean> loansOn = Context.newVarDB(_LOANS_ON, Boolean.class);

    public static final VarDB<Address> admin = Context.newVarDB(_ADMIN, Address.class);
    public static final VarDB<Address> governance = Context.newVarDB(_GOVERNANCE, Address.class);
    public static final VarDB<Address> dex = Context.newVarDB(_DEX, Address.class);
    public static final VarDB<Address> rebalancing = Context.newVarDB(_REBALANCE, Address.class);
    public static final VarDB<Address> dividends = Context.newVarDB(_DIVIDENDS, Address.class);
    public static final VarDB<Address> reserve = Context.newVarDB(_RESERVE, Address.class);
    public static final VarDB<Address> rewards = Context.newVarDB(_REWARDS, Address.class);
    public static final VarDB<Address> staking = Context.newVarDB(_STAKING, Address.class);

    public static final VarDB<Integer> snapBatchSize = Context.newVarDB(_SNAP_BATCH_SIZE, Integer.class);
    public static final VarDB<BigInteger> globalIndex = Context.newVarDB(_GLOBAL_INDEX, BigInteger.class);
    public static final VarDB<BigInteger> globalBatchIndex = Context.newVarDB(_GLOBAL_BATCH_INDEX, BigInteger.class);

    public static final VarDB<Boolean> rewardsDone = Context.newVarDB(_REWARDS_DONE, Boolean.class);
    public static final VarDB<Boolean> dividendsDone = Context.newVarDB(_DIVIDENDS_DONE, Boolean.class);
    public static final VarDB<Integer> continuousRewardDay = Context.newVarDB(_CONTINUOUS_REWARD_DAY, Integer.class);
    public static final VarDB<Integer> currentDay = Context.newVarDB(_CURRENT_DAY, Integer.class);
    public static final VarDB<Long> timeOffset = Context.newVarDB(_TIME_OFFSET, Long.class);
    public static final VarDB<BigInteger> miningRatio = Context.newVarDB(_MINING_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> lockingRatio  = Context.newVarDB(_LOCKING_RATIO, BigInteger.class);

    public static final VarDB<BigInteger> liquidationRatio = Context.newVarDB(_LIQUIDATION_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> originationFee = Context.newVarDB(_ORIGINATION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> redemptionFee = Context.newVarDB(_REDEMPTION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> retirementBonus = Context.newVarDB(_RETIREMENT_BONUS, BigInteger.class);
    public static final VarDB<BigInteger> liquidationReward = Context.newVarDB(_LIQUIDATION_REWARD, BigInteger.class);
    public static final VarDB<BigInteger> newLoanMinimum = Context.newVarDB(_NEW_LOAN_MINIMUM, BigInteger.class);
    public static final VarDB<BigInteger> minMiningDebt = Context.newVarDB(_MIN_MINING_DEBT, BigInteger.class);
    public static final VarDB<Integer> maxDebtsListLength = Context.newVarDB(_MAX_DEBTS_LIST_LENGTH, Integer.class);
    public static final VarDB<Integer> redeemBatch = Context.newVarDB(_REDEEM_BATCH_SIZE, Integer.class);
    public static final VarDB<BigInteger> maxRetirePercent = Context.newVarDB(_MAX_RETIRE_PERCENT, BigInteger.class);

    public static final VarDB<Address> expectedToken = Context.newVarDB("expectedToken", Address.class);
    public static final VarDB<BigInteger> amountReceived = Context.newVarDB("amountReceived", BigInteger.class);

    public Loans(Address _governance) {
        //TODO: tmp should be set by governance?
        if (continuousRewardDay.getOrDefault(0) == 0) {
            continuousRewardDay.set(_getDay() + (int)U_SECONDS_DAY*10);
        }

        if (governance.getOrDefault(null) != null) {
            return;
        }

        governance.set(_governance);
        admin.set(Context.getCaller());
        loansOn.set(false);
        rewardsDone.set(true);
        dividendsDone.set(true);
        snapBatchSize.set(SNAP_BATCH_SIZE);
        miningRatio.set(MINING_RATIO);
        lockingRatio.set(LOCKING_RATIO);
        liquidationRatio.set(LIQUIDATION_RATIO);
        originationFee.set(ORIGINATION_FEE);
        redemptionFee.set(REDEMPTION_FEE);
        retirementBonus.set(BAD_DEBT_RETIREMENT_BONUS);
        liquidationReward.set(LIQUIDATION_REWARD);
        newLoanMinimum.set(NEW_BNUSD_LOAN_MINIMUM);
        minMiningDebt.set(MIN_BNUSD_MINING_DEBT);
        maxDebtsListLength.set(MAX_DEBTS_LIST_LENGTH);
        redeemBatch.set(REDEEM_BATCH_SIZE);
        maxRetirePercent.set(MAX_RETIRE_PERCENT);
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Loans";
    }

    @External
    public void turnLoansOn() {
        onlyGovernance();
        loansOn.set(true);
        ContractActive("Loans", "Active");
        currentDay.set(_getDay());
        SnapshotDB.startNewSnapshot();
    }

    @External
    public void toggleLoansOn() {
        onlyGovernance();
        loansOn.set(!loansOn.get());
        ContractActive("Loans", loansOn.get() ? "Active" : "Inactive");
    }

    @External(readonly = true)
    public boolean getLoansOn() {
        return loansOn.get();
    }

    @External
    public void setContinuousRewardsDay(int _day) {
        onlyGovernance();
        continuousRewardDay.set(_day);
    }

    @External(readonly = true)
    public int getContinuousRewardsDay() {
        return continuousRewardDay.get();
    }

    @External(readonly = true)
    public int getDay() {
        return _getDay();
    }


    public static int _getDay() {
        long blockTime = (Context.getBlockTimestamp() - timeOffset.getOrDefault(0l));
        return (int) (blockTime / (U_SECONDS_DAY));
    }

    @External
    public void delegate(byte[] prepDelegations) {
        onlyGovernance();
        Context.call(staking.get(), "delegate", prepDelegations);
    }

//     RpcObject delegation = new RpcObject.Builder()
//     .put("_address", new RpcValue(fromWallet.getAddress()))
//     .put("_votes_in_per", new RpcValue(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18))))
//     .build();
// RpcArray delegations = new RpcArray.Builder()
//     .add(delegation)
//     .build();
// RpcObject params = new RpcObject.Builder()
//     .put("_delegations", delegations)
//     .build();

    @External(readonly = true)
    public Map<String, Boolean> getDistributionsDone() {
        return Map.of (
            "Rewards", rewardsDone.get(),
            "Dividends",  dividendsDone.get()
        );
    }

    @External(readonly = true)
    public List<String> checkDeadMarkets() {
        return AssetDB.getDeadMarkets();
    }

    @External(readonly = true)
    public int getNonzeroPositionCount() {
        Snapshot snap = SnapshotDB.get(-1);
        int count = PositionsDB.getNonZero().size() + snap.getAddNonzero().size() - snap.getRemoveNonzero().size();

        if (snap.day.get() > 1) {
            Snapshot lastSnap = SnapshotDB.get(-2);
            count = count + lastSnap.getAddNonzero().size() - lastSnap.getRemoveNonzero().size();
        }
        
        return count;
    }

    @External(readonly = true)
    public Map<String, Object> getPositionStanding(Address _address, @Optional int snapshot) {
        if (snapshot == 0) {
            //TOOD no longer possible to fetch snap 0
            snapshot = -1;
        }

        Context.require(snapshot < continuousRewardDay.get(), "The continuous rewards is already active.");
        return PositionsDB.getPosition(_address).getStanding(snapshot, true).toMap();
    }

    @External(readonly = true)
    public Address getPositionAddress(int _index) {
        return PositionsDB.get(_index).address.get();
    }

    @External(readonly = true)
    public Map<String, Address> getAssetTokens() {
        return AssetDB.getAssetTokens();
    }

    @External(readonly = true)
    public Map<String, Address> getCollateralTokens() {
        return AssetDB.getCollateral();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateral() {
        return AssetDB.getTotalCollateral().divide(EXA);
    }

    @External(readonly = true)
    public Map<String, Object> getAccountPositions(Address _owner) {
        Context.require(PositionsDB.hasPosition(_owner), _owner + " does not have a position in Balanced");
        return PositionsDB.listPosition(_owner);
    }

    @External(readonly = true)
    public Map<String, Object> getPositionByIndex(int _index, int _day) {
        Context.require(_day < continuousRewardDay.get(), "The continuous rewards is already active.");
        return PositionsDB.get(_index).toMap(_day);
    }

     @External(readonly = true)
    public Map<String, Map<String, Object>> getAvailableAssets() {
        return AssetDB.getAvailableAssets();
    }

    @External(readonly = true)
    public int assetCount() {
        return AssetDB.assetAddresses.size();
    }

    @External(readonly = true)
    public int borrowerCount() {
        return PositionsDB.size();
    }

    @External(readonly = true)
    public boolean hasDebt(Address _owner) {
        return PositionsDB.getPosition(_owner).hasDebt(-1);
    }

    @External(readonly = true)
    public Map<String, Object> getSnapshot(@Optional int _snap_id) {
        if (_snap_id == 0) {
            _snap_id = -1;
        }
        if (_snap_id > SnapshotDB.getLastSnapshotIndex() || _snap_id + SnapshotDB.size() < 0) {
            Map.of();
        }
        
        return SnapshotDB.get(_snap_id).toMap();
    }

    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        onlyAdmin();
        AssetDB.addAsset(_token_address, _active, _collateral);
        Asset asset = AssetDB.getAsset(_token_address.toString());
        AssetAdded(_token_address, asset.symbol(), _collateral);
    }


    @External
    public void toggleAssetActive(String _symbol) {
        onlyAdmin();
        Asset asset = AssetDB.get(_symbol);
        Boolean active = asset.isActive();
        asset.active.set(!active);
        AssetActive(_symbol, active ? "Active" : "Inactive");
    }

    @External
    public boolean precompute(int _snapshot_id, int batch_size) {
        onlyRewards();
        checkForNewDay();
        return PositionsDB.calculateSnapshot(_snapshot_id, batch_size);
    }


    @External(readonly = true)
    public BigInteger getTotalValue(String _name, int _snapshot_id) {
        Context.require(_snapshot_id < continuousRewardDay.get(),  "The continuous rewards is already active.");

        return SnapshotDB.get(_snapshot_id).totalMiningDebt.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address __owner) {
        Context.require(_name == "Loans",  "Unsupported data source name");
        int id = PositionsDB.addressIds.get(__owner);
        if (id < 1) {
            return Map.of(
                "_balance", BigInteger.ZERO,
                "_totalSupply", BigInteger.ZERO
            );
        }

        Position position = PositionsDB.get(id);

        BigInteger balance = position.get("bnUSD");
        BigInteger totalSupply = AssetDB.get("bnUSD").totalSupply();

        return Map.of(
            "_balance", balance,
            "_totalSupply", totalSupply
        );
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        Asset asset = AssetDB.get("bnUSD");
        BigInteger totalSupply = asset.totalSupply();

        return totalSupply.subtract(asset.badDebt.get());
    }

    @External(readonly = true)
    public Map<Address, BigInteger> getDataBatch(String _name, int _snapshot_id, int _limit, @Optional int _offset) {
        Context.require(_snapshot_id <= continuousRewardDay.get(),  "The continuous rewards is already active.");

        Snapshot snapshot = SnapshotDB.get(_snapshot_id);
        int totalMiners = snapshot.mining.size();
        int start = Math.max(0, Math.min(_offset, totalMiners));
        int end = Math.min(_offset + _limit, totalMiners);

        HashMap<Address, BigInteger> batch = new HashMap<Address, BigInteger>(end-start);
        for (int i = start; i < end; i++) {
            int id = snapshot.mining.get(i);
            Position position = PositionsDB.get(id);
            batch.put(position.address.get(), snapshot.positionStates.at(id).get("total_debt"));
        }

        return batch;
    }

    @External
    public boolean checkForNewDay() {
        loansOn();
        int day = _getDay();

        if (currentDay.get() < day && day <= continuousRewardDay.get()) {
            currentDay.set(day);
            PositionsDB.takeSnapshot();
            Snapshot(_getDay());
            checkDeadMarkets();
            return true;
        }
    
        checkDeadMarkets();
        return false;
    }

    @External
    public void checkDistributions(int _day, boolean _new_day) {
        loansOn();
        Boolean _rewardsDone = rewardsDone.get();
        Boolean _dividendsDone = dividendsDone.get();

        if (_new_day && _rewardsDone && _dividendsDone)  {
            rewardsDone.set(false);
            dividendsDone.set(false);
        } else if (!_dividendsDone) {
            dividendsDone.set((Boolean )Context.call(dividends.get(), "distribute"));
        } else if (!_rewardsDone) {
            rewardsDone.set((Boolean) Context.call(rewards.get(), "distribute"));
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");
        if (token.equals(expectedToken.get())) {
            amountReceived.set(_value);
            expectedToken.set(null);
            return;
        }
        
        Context.require(token.equals(AssetDB.get("sICX").getAddress()), "The Balanced Loans contract does not accept that token type.");
        
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();
     
        String _symbol = json.get("_asset").asString();
        BigInteger amount = BigInteger.valueOf(json.get("_amount").asLong()).multiply(EXA);
        depositAndBorrow(_symbol, amount, _from, _value);
    }

    @External
    @Payable
    public void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value) {
        loansOn();
        BigInteger deposit = Context.getValue();
        Address sender = Context.getCaller();
        if (!sender.equals(AssetDB.get("sICX").getAddress())) {
            _from = sender;
            if (deposit.compareTo(BigInteger.ZERO) == 1) {
                expectedToken.set(AssetDB.get("sICX").getAddress());
                Context.call(deposit, staking.get(), "stakeICX", Context.getAddress());
                
                BigInteger received = amountReceived.get();
                Context.require(!received.equals(BigInteger.ZERO), "Expected sICX not received.");
                amountReceived.set(BigInteger.ZERO);
                _value = received;
            }
        }

        Boolean newDay = checkForNewDay();
        int day = _getDay();
        checkDistributions(day, newDay);
        Position position = PositionsDB.getPosition(_from);
        if (_value.compareTo(BigInteger.ZERO) == 1) {
            position.set("sICX",   position.get("sICX").add(_value));
            CollateralReceived(_from, "sICX", _value);
        }
        if (_asset == "" || _amount.compareTo(BigInteger.ZERO) != 1 ) {
            return;
        }

        originateLoan(_asset, _amount, _from);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) == 1, "Amount retired must be greater than zero.");
        Address from = Context.getCaller();
        Asset asset = AssetDB.get(_symbol);
        BigInteger badDebt = asset.badDebt.get();

        Context.require(!asset.isCollateral.getOrDefault(false), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.active.getOrDefault(false), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.balanceOf(from).compareTo(_value) != -1, "Insufficient balance.");
        Context.require(badDebt.compareTo(_value) != -1, "No bad debt for " + _symbol);

        Boolean newDay = checkForNewDay();
        int day = _getDay();
        checkDistributions(day, newDay);

        BigInteger badDebtValue = badDebt.min(_value);
        asset.burnFrom(from, badDebtValue);
        BigInteger sICX = badDebtRedeem(from, asset, badDebtValue);
        transferToken("sICX", from, sICX, "Bad Debt redeemed.", new byte[0]);
        asset.isDead();
        BadDebtRetired(from, _symbol, badDebtValue, sICX);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, boolean _repay) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) == 1, "Amount retired must be greater than zero.");
        Address from = Context.getCaller();
        Asset asset = AssetDB.get(_symbol);

        Context.require(!asset.isCollateral.getOrDefault(false), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.active.getOrDefault(false), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.balanceOf(from).compareTo(_value) != -1, "Insufficient balance.");
        Context.require(PositionsDB.hasPosition(from), "No debt repaid because, " + from + " does not have a position in Balanced");
        Context.require(_repay, "No debt repaid because, repay=false");

        Boolean newDay = checkForNewDay();
        int day = _getDay();
        checkDistributions(day, newDay);

        BigInteger oldSupply = asset.totalSupply();
        Position position = PositionsDB.getPosition(from);
        BigInteger borrowed = position.get(_symbol);

        Context.require(_value.compareTo(borrowed) != 1, "Repaid amount is greater than the amount in the position of " + from);
        if (_value.compareTo(BigInteger.ZERO) != 1) {
            return;
        }

        BigInteger remaning = borrowed.subtract(_value);
        BigInteger repaid;
        if (remaning.compareTo(BigInteger.ZERO) == 1) {
            position.set(_symbol,  position.get(_symbol).subtract(_value));
            repaid = _value;
        } else {
            position.set(_symbol, BigInteger.ZERO);
            repaid = borrowed;
        }

        asset.burnFrom(from, repaid);
        if (day < continuousRewardDay.get()) {
            if (!position.hasDebt(-1)) {
                PositionsDB.removeNonZero(position.id.get());
            }
        } else {
            Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, borrowed);
        }

        asset.isDead();
        String logMessage = "Loan of " + repaid.toString()  + " " + _symbol +" repaid to Balanced.";
        LoanRepaid(from, _symbol, repaid, logMessage);
    }

    @External
    public void raisePrice(BigInteger _total_tokens_required) {
        loansOn();
        onlyRebalance();
        String symbol = "bnUSD";
        Asset asset = AssetDB.get(symbol);

        BigInteger rate = (BigInteger) Context.call(dex.get(), "getSicxBnusdPrice");
        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = asset.getBorrowers();

        int nodeId = borrowers.getHeadId();
        BigInteger totalBatchDebt = BigInteger.ZERO;
        HashMap<Integer, BigInteger> postionsMap = new HashMap<Integer, BigInteger>(batchSize);
        
        int iterations = Math.min(batchSize, borrowers.size());
        for (int i = 0; i < iterations; i++) {
            BigInteger debt = borrowers.nodeValue(nodeId);
            postionsMap.put(nodeId, debt);
            totalBatchDebt = totalBatchDebt.add(debt);
            borrowers.headToTail();
            nodeId = borrowers.getHeadId();
        }

        borrowers.serialize();

        BigInteger sicxToSell = maxRetirePercent.get().multiply(totalBatchDebt).multiply(EXA).divide(POINTS.multiply(rate));
        sicxToSell = sicxToSell.min(_total_tokens_required);
        
        expectedToken.set(asset.getAddress());
        byte[] data = createSwapData(asset.getAddress());
        transferToken("sICX", dex.get(), sicxToSell, "sICX swapped for bnUSD", data);

        BigInteger bnUSDReceived = amountReceived.get();

        amountReceived.set(BigInteger.ZERO);
        asset.burnFrom(Context.getAddress(), bnUSDReceived);

        BigInteger remaningSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnUSDReceived;

        Map<Integer, Map<String, BigInteger>> changeLog = new HashMap<Integer, Map<String, BigInteger>>(iterations);
        ArrayList<Map<String, Object>> rewardsBatchList = new ArrayList<Map<String, Object>>(iterations);
        for (Map.Entry<Integer, BigInteger> entry : postionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remaningSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.set(symbol, position.get(symbol).subtract(loanShare));
            
            Map<String, Object> userMap = Map.of(
                "_user", position.address,
                "_balance", userDebt
            );
            rewardsBatchList.add(userMap);

            BigInteger sicxShare = sicxToSell.multiply(userDebt).divide(remaningSupply);
            sicxToSell = sicxToSell.subtract(sicxShare);
            position.set("sICX", position.get("sICX").subtract(sicxShare));

            remaningSupply = remaningSupply.subtract(userDebt);
            changeLog.put(id, Map.of(
                "d", loanShare.negate(),
                "c", sicxShare.negate()
            ));
        }

        if (_getDay() >= continuousRewardDay.get()) {
            Context.call(rewards.get(), "updateBatchRewardsData", "Loans", asset.totalSupply(), rewardsBatchList);
        }
        Rebalance(Context.getCaller(), symbol, changeLog.toString(), totalBatchDebt);
    }

    @External
    public void lowerPrice(BigInteger _total_tokens_required) {
        loansOn();
        onlyRebalance();
        String symbol = "sICX";
        Asset asset = AssetDB.get(symbol);

        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = AssetDB.get("bnUSD").getBorrowers();
        int nodeId = borrowers.getHeadId();

        BigInteger totalBatchDebt = BigInteger.ZERO;
        HashMap<Integer, BigInteger> postionsMap = new HashMap<Integer, BigInteger>(batchSize);
        
        int iterations = Math.min(batchSize, borrowers.size());
        for (int i = 0; i < iterations; i++) {
            BigInteger debt = borrowers.nodeValue(nodeId);
            postionsMap.put(nodeId, debt);
            totalBatchDebt = totalBatchDebt.add(debt);
            borrowers.headToTail();
            nodeId = borrowers.getHeadId();
        }

        BigInteger bnusdToSell = maxRetirePercent.get().multiply(totalBatchDebt).divide(POINTS);
        bnusdToSell = bnusdToSell.min(_total_tokens_required);
        expectedToken.set(AssetDB.get("bnUSD").getAddress());
        AssetDB.get("bnUSD").mint(Context.getAddress(), bnusdToSell);
        amountReceived.set(BigInteger.ZERO);

        expectedToken.set(asset.getAddress());
        byte[] data = createSwapData(asset.getAddress());
        transferToken("bnUSD", dex.get(), bnusdToSell, "bnUSD swapped for sICX", data);
        BigInteger receivedSicx = amountReceived.get();
        amountReceived.set(BigInteger.ZERO);

        BigInteger remaningSicx = receivedSicx;
        BigInteger remaningSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnusdToSell;
        
        Map<Integer, Map<String, BigInteger>> changeLog = new HashMap<Integer, Map<String, BigInteger>>(iterations);
        ArrayList<Map<String, Object>> rewardsBatchList = new ArrayList<Map<String, Object>>(iterations);
       
        for (Map.Entry<Integer, BigInteger> entry : postionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remaningSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.set("bnUSD", position.get("bnUSD").add(loanShare));
            
            Map<String, Object> userMap = Map.of(
                "_user", position.address,
                "_balance", userDebt
            );
            rewardsBatchList.add(userMap);

            BigInteger sicxShare = remaningSicx.multiply(userDebt).divide(remaningSupply);
            remaningSicx = remaningSicx.subtract(sicxShare);
            position.set(symbol, position.get(symbol).add(sicxShare));

            remaningSupply = remaningSupply.subtract(userDebt);
            changeLog.put(id, Map.of(
                "d", loanShare,
                "c", sicxShare
            ));
        }

        if (_getDay() >= continuousRewardDay.get()) {
            Context.call(rewards.get(), "updateBatchRewardsData", "Loans", AssetDB.get("bnUSD").totalSupply(), rewardsBatchList);
        }
        Rebalance(Context.getCaller(), "bnUSD", changeLog.toString(), totalBatchDebt);
    }

    @External
    public void withdrawCollateral(BigInteger _value) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) == 1, "Withdraw amount must be more than zero.");
        Address from = Context.getCaller();
        Context.require(PositionsDB.hasPosition(from), "This address does not have a position on Balanced.");

        Boolean newDay = checkForNewDay();
        int day = _getDay();
        checkDistributions(day, newDay);
        Position position = PositionsDB.getPosition(from);

        Context.require(position.get("sICX").compareTo(_value) != -1,  "Position holds less collateral than the requested withdrawal.");
        BigInteger assetValue = position.totalDebt(-1, false);
        BigInteger remaningSicx = position.get("sICX").subtract(_value);
        BigInteger remaningCollateral = remaningSicx.multiply(AssetDB.get("sICX").priceInLoop()).divide(EXA);

        BigInteger lockingValue = lockingRatio.get().multiply(assetValue).divide(POINTS);
        Context.require(remaningCollateral.compareTo(lockingValue) != -1, 
            "Requested withdrawal is more than available collateral. " +
            "total debt value: " + assetValue + " ICX " +
            "remaining collateral value: " + remaningCollateral + " ICX " +
            "locking value (max debt): " + lockingValue + " ICX"
        );
        
        position.set("sICX", remaningSicx);
        transferToken("sICX", from, _value, "Collateral withdrawn.", new byte[0]);
    }

    @External
    public void liquidate(Address _owner) {
        loansOn();
        Context.require(PositionsDB.hasPosition(_owner), "This address does not have a position on Balanced.");
        Position position = PositionsDB.getPosition(_owner);
        Standings standing = position.updateStanding(-1);
        if (standing  != Standings.LIQUIDATE) {
            return;
        }

        BigInteger collateral = position.get("sICX");
        BigInteger reward = collateral.multiply(liquidationReward.get()).divide(POINTS);
        BigInteger forPool = collateral.subtract(reward);
        BigInteger totalDebt = position.totalDebt(-1, false);

        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.get(symbol);
            BigInteger debt = position.get(symbol);
            if (!asset.isCollateral.getOrDefault(false) && asset.active.getOrDefault(false) && debt.compareTo(BigInteger.ZERO) == 1) {
                if (_getDay() >= continuousRewardDay.get()) {
                    Context.call(rewards.get(), "updateRewardsData", "Loans", asset.totalSupply(), _owner, debt);
                }

                BigInteger badDebt = asset.badDebt.get();
                asset.badDebt.set(badDebt.add(debt));
                BigInteger symbolDebt = debt.multiply(asset.priceInLoop()).divide(EXA);
                BigInteger share = forPool.multiply(symbolDebt.divide(totalDebt));
                totalDebt = totalDebt.subtract(symbolDebt);
                forPool = forPool.subtract(share);
                asset.liquidationPool.set(asset.liquidationPool.get().add(share));
                position.set(symbol, BigInteger.ZERO);
            }
        }  

        position.set("sICX", BigInteger.ZERO);
        transferToken("sICX", Context.getCaller(), reward, "Liquidation reward of", new byte[0]);
        if (_getDay() < continuousRewardDay.get()) {
            PositionsDB.removeNonZero(position.id.get());
        }

        String logMessage = collateral.toString() +" liquidated from " + _owner;
        Liquidate(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, Asset asset, BigInteger badDebtValue) {
        BigInteger price = asset.priceInLoop();
        Asset sicx = AssetDB.get("sICX");
        BigInteger sicxRate = sicx.priceInLoop();
        BigInteger inPool = asset.liquidationPool.get();
        BigInteger badDebt = asset.badDebt.get().subtract(badDebtValue);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtSicx = bonus.multiply(badDebtValue).multiply(price).divide(sicxRate.multiply(POINTS));
        asset.badDebt.set(badDebt);
        if (inPool.compareTo(badDebtSicx) != -1) {
            asset.liquidationPool.set(inPool.subtract(badDebtSicx));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferToken("sICX", reserve.get(), inPool.subtract(badDebtSicx), "Sweep to ReserveFund:", new byte[0]);
            }
            return badDebtSicx;
        }
        
        asset.liquidationPool.set(BigInteger.ZERO);
        expectedToken.set(sicx.getAddress());
        Context.call(reserve.get(), "redeem", from, badDebtSicx.subtract(inPool), sicxRate);
        BigInteger received = amountReceived.get();
        Context.require(received.equals(badDebtSicx.subtract(inPool)), "Got unexpected sICX from reserve.");
        amountReceived.set(BigInteger.ZERO);
        return inPool.add(received);
    }

    private void originateLoan(String _symbol, BigInteger amount, Address from) {
        Asset asset = AssetDB.get(_symbol);
        Context.require(!asset.dead.get(), "No new loans of " + _symbol + " can be originated since it is in a dead market state.");
        Context.require(!asset.isCollateral(), "Loans of collateral assets are not allowed.");
        Context.require(asset.active.getOrDefault(false), "Loans of inactive assets are not allowed.");

        Position position = PositionsDB.getPosition(from);

        BigInteger collateral = position.totalCollateral(-1);
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(lockingRatio.get());
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);
        BigInteger newDebtValue = asset.priceInLoop().multiply(amount.add(fee)).divide(EXA);
        BigInteger holdings = position.get(_symbol);
        if (holdings.equals(BigInteger.ZERO)) {
            BigInteger dollarValue = newDebtValue.multiply(EXA).divide(AssetDB.get("bnUSD").priceInLoop());
            Context.require(dollarValue.compareTo(newLoanMinimum.get()) != -1, "The initial loan of any" +
                                                                                "asset must have a minimum value" +
                                                                                "of "+ newLoanMinimum.get().divide(EXA) +" dollars.");
        }

        BigInteger totalDebt = position.totalDebt(-1, false);
        Context.require(totalDebt.add(newDebtValue).compareTo(maxDebtValue) != 1, 
            collateral + " collateral is insufficient" +
            " to originate a loan of " + amount.divide(EXA) + " " +_symbol +
            " when max_debt_value = " + maxDebtValue + "," +
            " new_debt_value = " + newDebtValue + "," +
            " which includes a fee of " + fee.divide(EXA) + " " + _symbol + "," +
            " given an existing loan value of " + totalDebt+ ".");

        if (_getDay() < continuousRewardDay.get()) {
            if (totalDebt.equals(BigInteger.ZERO)) {
                Snapshot snap = SnapshotDB.get(-1);
                PositionsDB.addNonZero(position.id.get());
            }
        } else {
            Context.call(rewards.get(), "updateRewardsData", "Loans", asset.totalSupply(), from, holdings);
        }

        BigInteger newDebt = amount.add(fee);
        position.set(_symbol,  holdings.add(newDebt));
        asset.mint(from, amount);

        String logMessage = "Loan of " + amount + " " + _symbol + " from Balanced.";
        OriginateLoan(from, _symbol, amount, logMessage);

        Address feeHandler = (Address)Context.call(governance.get(), "getContractAddress", "feehandler");
        asset.mint(feeHandler, fee);
        FeePaid(_symbol, fee, "origination");
    }

    
    private void checkForDeadMarkets() {
        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol = AssetDB.activeAssets.get(i);
            Asset asset = AssetDB.get(symbol);
            asset.isDead();
        }
    }

    private void transferToken(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(AssetDB.get(tokenSymbol).getAddress(), "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString()  + " " + tokenSymbol + " sent to " + to;
        TokenTransfer(to, amount, logMessage);
    }

    public static Object call(Address targetAddress, String method, Object... params) {
        return Context.call(targetAddress, method, params);
    }

    private byte[] createSwapData(Address toToken) {
        JsonObject data = Json.object();
        data.add("method", "_swap");
        data.add("params", Json.object().add("toToken", toToken.toString()));
        return data.toString().getBytes();
    }

    @External
    public void setAdmin(Address _address) {
        onlyGovernance();
        admin.set(_address);
    }

    @External
    public void setGovernance(Address _address) {
        onlyAdmin();
        Context.require(_address.isContract(), "Loans: Governance address should be a contract");
        governance.set(_address);
    }

    @External
    public void setDex(Address _address) {
        onlyAdmin();
        dex.set(_address);
    }
    
    @External
    public void setRebalance(Address _address) {
        onlyAdmin();
        rebalancing.set(_address);
    }

    @External
    public void setDividends(Address _address) {
        onlyAdmin();
        dividends.set(_address);
    }

    @External
    public void setReserve(Address _address) {
        onlyAdmin();
        reserve.set(_address);
    }

    @External
    public void setRewards(Address _address) {
        onlyAdmin();
        rewards.set(_address);
    }

    @External
    public void setStaking(Address _address) {
        onlyAdmin();
        staking.set(_address);
    }

    @External
    public void setMiningRatio(BigInteger _ratio) {
        onlyAdmin();
        miningRatio.set(_ratio);
    }

    @External
    public void setLockingRatio(BigInteger _ratio) {
        onlyAdmin();
        lockingRatio.set(_ratio);
    }

    @External
    public void setLiquidationRatio(BigInteger _ratio) {
        onlyAdmin();
        liquidationRatio.set(_ratio); 
    }

    @External
    public void setOriginationFee(BigInteger _fee) {
        onlyAdmin();
        originationFee.set(_fee);
    } 

    @External
    public void setRedemptionFee(BigInteger _fee) {
        onlyAdmin();
        redemptionFee.set(_fee);
    }

    @External
    public void setRetirementBonus(BigInteger _points) {
        onlyAdmin();
        retirementBonus.set(_points);
    }

    @External
    public void setLiquidationReward(BigInteger _points) {
        onlyAdmin();
        liquidationReward.set(_points);
    }

    @External
    public void setNewLoanMinimum(BigInteger _minimum) {
        onlyAdmin();
        newLoanMinimum.set(_minimum);
    }

    @External
    public void setMinMiningDebt(BigInteger _minimum) {
        onlyAdmin();
        minMiningDebt.set(_minimum);
    }

    @External
    public void setTimeOffset(long deltaTime) {
        onlyGovernance();
        timeOffset.set(deltaTime);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        onlyAdmin();
        Context.require(_value.compareTo(BigInteger.ZERO) != -1 && 
                    _value.compareTo(BigInteger.valueOf(10000)) != 1, 
                    "Input parameter must be in the range 0 to 10000 points.");
        maxRetirePercent.set(_value); 
    }

    @External
    public void setRedeemBatchSize(int _value) {
        onlyAdmin();
        redeemBatch.set(_value);
    }

    @External(readonly= true)
    public Map<String, Object> getParameters() {
        return Map.ofEntries(
            entry("admin", admin.get()),
            entry("governance", governance.get()),
            entry("dividends", dividends.get()),
            entry("reserve_fund", reserve.get()),
            entry("rewards", rewards.get()),
            entry("staking", staking.get()),
            entry("mining ratio", miningRatio.get()),
            entry("locking ratio", lockingRatio.get()),
            entry("liquidation ratio", liquidationRatio.get()),
            entry("origination fee", originationFee.get()),
            entry("redemption fee", redemptionFee.get()),
            entry("liquidation reward", liquidationReward.get()),
            entry("new loan minimum", newLoanMinimum.get()),
            entry("min mining debt", minMiningDebt.get()),
            entry("max div debt length", maxDebtsListLength.get()),
            entry("time offset", timeOffset.getOrDefault(0l)),
            entry("redeem batch size", redeemBatch.get()),
            entry("retire percent max", maxRetirePercent.get())
        );
    }


    @EventLog(indexed = 1)
    public void ContractActive(String contractName, String contractState) {}

    @EventLog(indexed = 1)
    public void AssetActive(String _asset, String _state) {}

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {}

    @EventLog(indexed = 3)
    public void AssetAdded(Address account, String symbol, boolean is_collateral) {}

    @EventLog(indexed = 2)
    public void CollateralReceived(Address account, String symbol, BigInteger value) {}

    @EventLog(indexed = 3)
    public void OriginateLoan(Address recipient, String symbol, BigInteger amount, String note) {}

    @EventLog(indexed = 3)
    public void LoanRepaid(Address account, String symbol, BigInteger amount, String note) {}

    @EventLog(indexed = 3)
    public void BadDebtRetired(Address account, String symbol, BigInteger amount, BigInteger sicx_received) {}

    @EventLog(indexed = 2)
    public void Liquidate(Address account, BigInteger amount, String note) {}

    @EventLog(indexed = 3)
    public void FeePaid(String symbol, BigInteger amount, String type) {}

    @EventLog(indexed = 2)
    public void Rebalance(Address account, String symbol, String change_in_pos, BigInteger total_batch_debt) {}

    @EventLog(indexed = 2)
    public void PositionStanding(Address address, String standing, BigInteger total_collateral, BigInteger total_debt) {}

    @EventLog(indexed = 1)
    public void Snapshot(int _id) {}
        
}
