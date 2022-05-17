/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.loans;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.core.loans.asset.Asset;
import network.balanced.score.core.loans.asset.AssetDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.snapshot.Snapshot;
import network.balanced.score.core.loans.snapshot.SnapshotDB;
import network.balanced.score.core.loans.utils.Token;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.Check.*;

public class LoansImpl {
    private static final String _LOANS_ON = "loans_on";
    private static final String _GOVERNANCE = "governance";
    private static final String _REBALANCE = "rebalance";
    private static final String _DEX = "dex";
    private static final String _DIVIDENDS = "dividends";
    private static final String _RESERVE = "reserve";
    private static final String _REWARDS = "rewards";
    private static final String _STAKING = "staking";
    private static final String _ADMIN = "admin";
    private static final String _SNAP_BATCH_SIZE = "snap_batch_size";
    private static final String _GLOBAL_INDEX = "global_index";
    private static final String _GLOBAL_BATCH_INDEX = "global_batch_index";

    private static final String _REWARDS_DONE = "rewards_done";
    private static final String _DIVIDENDS_DONE = "dividends_done";
    private static final String _CURRENT_DAY = "current_day";
    private static final String _TIME_OFFSET = "time_offset";

    private static final String _MINING_RATIO = "mining_ratio";
    private static final String _LOCKING_RATIO = "locking_ratio";
    private static final String _LIQUIDATION_RATIO = "liquidation_ratio";
    private static final String _ORIGINATION_FEE = "origination_fee";
    private static final String _REDEMPTION_FEE = "redemption_fee";
    private static final String _RETIREMENT_BONUS = "retirement_bonus";
    private static final String _LIQUIDATION_REWARD = "liquidation_reward";
    private static final String _NEW_LOAN_MINIMUM = "new_loan_minimum";
    private static final String _MIN_MINING_DEBT = "min_mining_debt";
    private static final String _MAX_DEBTS_LIST_LENGTH = "max_debts_list_length";

    private static final String _REDEEM_BATCH_SIZE = "redeem_batch_size";
    private static final String _MAX_RETIRE_PERCENT = "max_retire_percent";
    private static final String _CONTINUOUS_REWARD_DAY = "continuous_reward_day";

    private static final VarDB<Boolean> loansOn = Context.newVarDB(_LOANS_ON, Boolean.class);

    private static final VarDB<Address> admin = Context.newVarDB(_ADMIN, Address.class);
    private static final VarDB<Address> governance = Context.newVarDB(_GOVERNANCE, Address.class);
    private static final VarDB<Address> dex = Context.newVarDB(_DEX, Address.class);
    private static final VarDB<Address> rebalancing = Context.newVarDB(_REBALANCE, Address.class);
    private static final VarDB<Address> dividends = Context.newVarDB(_DIVIDENDS, Address.class);
    private static final VarDB<Address> reserve = Context.newVarDB(_RESERVE, Address.class);
    private static final VarDB<Address> rewards = Context.newVarDB(_REWARDS, Address.class);
    private static final VarDB<Address> staking = Context.newVarDB(_STAKING, Address.class);

    public static final VarDB<Integer> snapBatchSize = Context.newVarDB(_SNAP_BATCH_SIZE, Integer.class);
    public static final VarDB<BigInteger> globalIndex = Context.newVarDB(_GLOBAL_INDEX, BigInteger.class);
    public static final VarDB<BigInteger> globalBatchIndex = Context.newVarDB(_GLOBAL_BATCH_INDEX, BigInteger.class);

    private static final VarDB<Boolean> rewardsDone = Context.newVarDB(_REWARDS_DONE, Boolean.class);
    private static final VarDB<Boolean> dividendsDone = Context.newVarDB(_DIVIDENDS_DONE, Boolean.class);
    public static final VarDB<BigInteger> continuousRewardDay = Context.newVarDB(_CONTINUOUS_REWARD_DAY, BigInteger.class);
    private static final VarDB<BigInteger> currentDay = Context.newVarDB(_CURRENT_DAY, BigInteger.class);
    private static final VarDB<BigInteger> timeOffset = Context.newVarDB(_TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> miningRatio = Context.newVarDB(_MINING_RATIO, BigInteger.class);
    public static final VarDB<BigInteger> lockingRatio  = Context.newVarDB(_LOCKING_RATIO, BigInteger.class);

    public static final VarDB<BigInteger> liquidationRatio = Context.newVarDB(_LIQUIDATION_RATIO, BigInteger.class);
    private static final VarDB<BigInteger> originationFee = Context.newVarDB(_ORIGINATION_FEE, BigInteger.class);
    private static final VarDB<BigInteger> redemptionFee = Context.newVarDB(_REDEMPTION_FEE, BigInteger.class);
    private static final VarDB<BigInteger> retirementBonus = Context.newVarDB(_RETIREMENT_BONUS, BigInteger.class);
    private static final VarDB<BigInteger> liquidationReward = Context.newVarDB(_LIQUIDATION_REWARD, BigInteger.class);
    private static final VarDB<BigInteger> newLoanMinimum = Context.newVarDB(_NEW_LOAN_MINIMUM, BigInteger.class);
    public static final VarDB<BigInteger> minMiningDebt = Context.newVarDB(_MIN_MINING_DEBT, BigInteger.class);
    private static final VarDB<Integer> maxDebtsListLength = Context.newVarDB(_MAX_DEBTS_LIST_LENGTH, Integer.class);
    private static final VarDB<Integer> redeemBatch = Context.newVarDB(_REDEEM_BATCH_SIZE, Integer.class);
    private static final VarDB<BigInteger> maxRetirePercent = Context.newVarDB(_MAX_RETIRE_PERCENT, BigInteger.class);

    private static final VarDB<Address> expectedToken = Context.newVarDB("expectedToken", Address.class);
    private static final VarDB<BigInteger> amountReceived = Context.newVarDB("amountReceived", BigInteger.class);

    public LoansImpl(Address _governance) {
        //TODO: tmp should be set by governance?
        if (continuousRewardDay.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            continuousRewardDay.set(_getDay().add(U_SECONDS_DAY.multiply(BigInteger.TEN)));
        }

        if (governance.getOrDefault(null) != null) {
            return;
        }

        governance.set(_governance);
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
        only(governance);
        loansOn.set(true);
        ContractActive("Loans", "Active");
        currentDay.set(_getDay());
        SnapshotDB.startNewSnapshot();
    }

    @External
    public void toggleLoansOn() {
        only(governance);
        loansOn.set(!loansOn.get());
        ContractActive("Loans", loansOn.get() ? "Active" : "Inactive");
    }

    @External(readonly = true)
    public boolean getLoansOn() {
        return loansOn.get();
    }

    @External(readonly = true)
    public void migrateUserData(Address address) {
        Context.require(getDay().compareTo(
            continuousRewardDay.get()) >= 0,
            "This method can be called only after continuous rewards day is active.");
        Position position = PositionsDB.getPosition(address);
        BigInteger id = position.getSnapshotId(BigInteger.valueOf(-1));
        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (position.dataMigrationStatus.getOrDefault(symbol, false)) {
                continue;
            }

            position.dataMigrationStatus.set(symbol, true);
            if (AssetDB.getAsset(symbol).isCollateral()) {
                position.collateral.set(symbol, position.assets.at(id).getOrDefault(symbol, BigInteger.ZERO));
            } else {
                position.loans.at("sICX").set(symbol, position.assets.at(id).getOrDefault(symbol, BigInteger.ZERO));
            }
        }
    }

    @External
    public void setContinuousRewardsDay(BigInteger _day) {
        only(governance);
        continuousRewardDay.set(_day);
    }

    @External(readonly = true)
    public BigInteger getContinuousRewardsDay() {
        return continuousRewardDay.get();
    }

    @External(readonly = true)
    public BigInteger getDay() {
        return _getDay();
    }


    public static BigInteger _getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger timeDelta = blockTime.subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return timeDelta.divide(U_SECONDS_DAY);
    }

    @External
    public void delegate(PrepDelegations[] prepDelegations) {
        only(governance);
        Context.call(staking.get(), "delegate", (Object) prepDelegations);
    }

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
        Snapshot snap = SnapshotDB.get(BigInteger.valueOf(-1));
        int count = PositionsDB.getNonZero().size() + snap.getAddNonzero().size() - snap.getRemoveNonzero().size();

        if (snap.day.get().compareTo(BigInteger.ONE) > 0) {
            Snapshot lastSnap = SnapshotDB.get(BigInteger.valueOf(-2));
            count = count + lastSnap.getAddNonzero().size() - lastSnap.getRemoveNonzero().size();
        }
        
        return count;
    }

    @External(readonly = true)
    public Map<String, Object> getPositionStanding(Address _address, @Optional BigInteger snapshot) {
        if (snapshot == null || snapshot.equals(BigInteger.ZERO)) {
            snapshot = BigInteger.valueOf(-1);
        }

        Context.require(SnapshotDB.getSnapshotId(snapshot).compareTo(continuousRewardDay.get()) < 0, continuousRewardsErrorMessage);
        return PositionsDB.getPosition(_address).getStanding(snapshot, true).toMap();
    }

    @External(readonly = true)
    public Address getPositionAddress(int _index) {
        return PositionsDB.get(_index).address.get();
    }

    @External(readonly = true)
    public Map<String, String> getAssetTokens() {
        return AssetDB.getAssetSymbolsAndAddress();
    }

    @External(readonly = true)
    public Map<String, String> getCollateralTokens() {
        return AssetDB.getCollateral();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateral() {
        return AssetDB.getTotalCollateral();
    }

    @External(readonly = true)
    public Map<String, Object> getAccountPositions(Address _owner) {
        Context.require(PositionsDB.hasPosition(_owner), _owner + " does not have a position in Balanced");
        return PositionsDB.listPosition(_owner);
    }

    @External(readonly = true)
    public Map<String, Object> getPositionByIndex(int _index, BigInteger _day) {
        Context.require(SnapshotDB.getSnapshotId(_day).compareTo(continuousRewardDay.get()) < 0, continuousRewardsErrorMessage);
        return PositionsDB.get(_index).toMap(_day);
    }

     @External(readonly = true)
    public Map<String, Map<String, Object>> getAvailableAssets() {
         return AssetDB.getActiveAssets();
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
        return PositionsDB.getPosition(_owner).hasDebt(BigInteger.valueOf(-1));
    }

    @External(readonly = true)
    public Map<String, Object> getSnapshot(@Optional BigInteger _snap_id) {
        if (_snap_id == null || _snap_id.equals(BigInteger.ZERO)) {
            _snap_id = BigInteger.valueOf(-1);
        }

        if (_snap_id.compareTo(SnapshotDB.getLastSnapshotIndex()) > 0 || _snap_id.add(SnapshotDB.size()).compareTo(BigInteger.ZERO) < 0) {
            return Map.of();
        }
        
        return SnapshotDB.get(_snap_id).toMap();
    }

    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        only(admin);
        AssetDB.addAsset(_token_address, _active, _collateral);

        Token assetContract = new Token(_token_address);
        AssetAdded(_token_address, assetContract.symbol(), _collateral);
    }


    @External
    public void toggleAssetActive(String _symbol) {
        only(admin);
        Asset asset = AssetDB.getAsset(_symbol);
        boolean active = asset.isActive();
        asset.setActive(!active);
        AssetActive(_symbol, active ? "Active" : "Inactive");
    }

    @External
    public boolean precompute(BigInteger _snapshot_id, int batch_size) {
        only(rewards);
        checkForNewDay();
        return PositionsDB.calculateSnapshot(_snapshot_id, batch_size);
    }


    @External(readonly = true)
    public BigInteger getTotalValue(String _name, BigInteger _snapshot_id) {
        Context.require(SnapshotDB.getSnapshotId(_snapshot_id).compareTo(continuousRewardDay.get()) < 0,  continuousRewardsErrorMessage);

        return SnapshotDB.get(_snapshot_id).totalMiningDebt.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        Context.require(_name.equals("Loans"), "Unsupported data source name");

        Address bnUSDAddress = Address.fromString(AssetDB.symbolMap.get("bnUSD"));
        Token bnUSD = new Token(bnUSDAddress);
        BigInteger totalSupply = bnUSD.totalSupply();

        int id = PositionsDB.addressIds.getOrDefault(_owner, -1);
        if (id < 1) {
            return Map.of(
                    "_balance", BigInteger.ZERO,
                    "_totalSupply", totalSupply
            );
        }

        Position position = PositionsDB.get(id);
        BigInteger balance = position.get("bnUSD");

        return Map.of(
            "_balance", balance,
            "_totalSupply", totalSupply
        );
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        Asset asset = AssetDB.getAsset("bnUSD");
        Token assetContract = new Token(asset.getAssetAddress());
        BigInteger totalSupply = assetContract.totalSupply();

        return totalSupply.subtract(asset.getBadDebt());
    }

    @External(readonly = true)
    public BigInteger getDataCount(BigInteger _snapshot_id) {
        Context.require(_snapshot_id.compareTo(continuousRewardDay.get()) <= 0,  continuousRewardsErrorMessage);
        return BigInteger.valueOf(SnapshotDB.get(_snapshot_id).mining.size());
    }

    @External(readonly = true)
    public Map<Address, BigInteger> getDataBatch(String _name, BigInteger _snapshot_id, int _limit, @Optional int _offset) {
        Context.require(_snapshot_id.compareTo(continuousRewardDay.get()) <= 0,  continuousRewardsErrorMessage);

        Snapshot snapshot = SnapshotDB.get(_snapshot_id);
        int totalMiners = snapshot.mining.size();
        int start = Math.max(0, Math.min(_offset, totalMiners));
        int end = Math.min(_offset + _limit, totalMiners);

        Map<Address, BigInteger> batch = new HashMap<>();
        for (int i = start; i < end; i++) {
            int id = snapshot.mining.get(i);
            Position position = PositionsDB.get(id);
            batch.put(position.address.get(), snapshot.positionStates.at(id).get("total_debt"));
        }

        return batch;
    }

    @External
    public boolean checkForNewDay() {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        BigInteger day = _getDay();

        if (currentDay.get().compareTo(day) < 0 && day.compareTo(continuousRewardDay.get()) <= 0) {
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
    public List<String> checkForDeadMarkets() {
        List<String> deadAssets = new ArrayList<>();
        for (int i = 0; i < AssetDB.activeAssets.size(); i++) {
            String symbol = AssetDB.activeAssets.get(i);
            Asset asset = AssetDB.getAsset(symbol);
            if (asset.isDeadMarket()) {
                deadAssets.add(symbol);
            }
        }

        return deadAssets;
    }

    @External
    public void checkDistributions(BigInteger _day, boolean _new_day) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        Boolean _rewardsDone = rewardsDone.get();
        Boolean _dividendsDone = dividendsDone.get();

        if (_new_day && _rewardsDone && _dividendsDone)  {
            rewardsDone.set(false);
            dividendsDone.set(false);
        } else if (!_dividendsDone) {
            dividendsDone.set(Context.call(Boolean.class, dividends.get(), "distribute"));
        } else if (!_rewardsDone) {
            rewardsDone.set(Context.call(Boolean.class, rewards.get(), "distribute"));
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

        Context.require(token.equals(AssetDB.getAsset("sICX").getAssetAddress()), "The Balanced Loans contract does " +
                "not accept that token type.");

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
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        BigInteger deposit = Context.getValue();
        Address sender = Context.getCaller();
        if (!sender.equals(AssetDB.getAsset("sICX").getAssetAddress())) {
            _from = sender;
            if (deposit.compareTo(BigInteger.ZERO) > 0) {
                expectedToken.set(AssetDB.getAsset("sICX").getAssetAddress());
                Context.call(deposit, staking.get(), "stakeICX", Context.getAddress());

                BigInteger received = amountReceived.get();
                Context.require(!received.equals(BigInteger.ZERO), "Expected sICX not received.");
                amountReceived.set(BigInteger.ZERO);
                _value = received;
            }
        }

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);
        Position position = PositionsDB.getPosition(_from);
        if (_value.compareTo(BigInteger.ZERO) > 0) {
            position.set("sICX",   position.get("sICX").add(_value));
            CollateralReceived(_from, "sICX", _value);
        }

        if (_asset.equals("") || _amount == null || _amount.compareTo(BigInteger.ZERO) != 1) {
            return;
        }

        originateLoan(_asset, _amount, _from);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, "Amount retired must be greater than zero.");
        Address from = Context.getCaller();
        Asset asset = AssetDB.getAsset(_symbol);
        BigInteger badDebt = asset.getBadDebt();

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(!asset.isCollateral(), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.isActive(), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) != -1, "Insufficient balance.");
        Context.require(badDebt.compareTo(_value) != -1, "No bad debt for " + _symbol);

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);

        BigInteger badDebtValue = badDebt.min(_value);
        asset.burnFrom(from, badDebtValue);
        BigInteger sICX = badDebtRedeem(from, asset, badDebtValue);
        transferToken("sICX", from, sICX, "Bad Debt redeemed.", new byte[0]);
        asset.checkForDeadMarket();
        BadDebtRetired(from, _symbol, badDebtValue, sICX);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, boolean _repay) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, "Amount retired must be greater than zero.");
        Address from = Context.getCaller();
        Asset asset = AssetDB.getAsset(_symbol);

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(!asset.isCollateral(), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(asset.isActive(), _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) != -1, "Insufficient balance.");
        Context.require(PositionsDB.hasPosition(from), "No debt repaid because, " + from + " does not have a position" +
                " in Balanced");
        Context.require(_repay, "No debt repaid because, repay=false");

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);

        BigInteger oldSupply = assetContract.totalSupply();
        Position position = PositionsDB.getPosition(from);
        BigInteger borrowed = position.get(_symbol);

        Context.require(_value.compareTo(borrowed) != 1, "Repaid amount is greater than the amount in the position of " + from);
        if (_value.compareTo(BigInteger.ZERO) != 1) {
            return;
        }

        BigInteger remaning = borrowed.subtract(_value);
        BigInteger repaid;
        if (remaning.compareTo(BigInteger.ZERO) > 0) {
            position.set(_symbol, position.get(_symbol).subtract(_value));
            repaid = _value;
        } else {
            position.set(_symbol, BigInteger.ZERO);
            repaid = borrowed;
        }

        asset.burnFrom(from, repaid);
        if (day.compareTo(continuousRewardDay.get()) < 0) {
            if (!position.hasDebt(BigInteger.valueOf(-1))) {
                PositionsDB.removeNonZero(position.id.get());
            }
        } else {
            Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, borrowed);
        }

        asset.checkForDeadMarket();
        String logMessage = "Loan of " + repaid + " " + _symbol +" repaid to Balanced.";
        LoanRepaid(from, _symbol, repaid, logMessage);
    }

    @External
    public void raisePrice(BigInteger _total_tokens_required) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        only(rebalancing);
        String symbol = "bnUSD";
        Asset asset = AssetDB.getAsset(symbol);
        BigInteger rate = Context.call(BigInteger.class, dex.get(), "getSicxBnusdPrice");
        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = asset.getBorrowers();

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        int nodeId = borrowers.getHeadId();
        BigInteger totalBatchDebt = BigInteger.ZERO;
        Map<Integer, BigInteger> postionsMap = new HashMap<>();

        int iterations = Math.min(batchSize, borrowers.size());
        for (int i = 0; i < iterations; i++) {
            BigInteger debt = borrowers.nodeValue(nodeId);
            postionsMap.put(nodeId, debt);
            totalBatchDebt = totalBatchDebt.add(debt);
            borrowers.headToTail();
            nodeId = borrowers.getHeadId();
        }

        borrowers.serialize();

        BigInteger sicxToSell =
                maxRetirePercent.get().multiply(totalBatchDebt).multiply(EXA).divide(POINTS.multiply(rate));
        sicxToSell = sicxToSell.min(_total_tokens_required);

        expectedToken.set(asset.getAssetAddress());
        byte[] data = createSwapData(asset.getAssetAddress());
        transferToken("sICX", dex.get(), sicxToSell, "sICX swapped for bnUSD", data);

        BigInteger bnUSDReceived = amountReceived.get();

        amountReceived.set(BigInteger.ZERO);
        asset.burnFrom(Context.getAddress(), bnUSDReceived);

        BigInteger remaningSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnUSDReceived;

        Map<Integer, Map<String, BigInteger>> changeLog = new HashMap<>();
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[iterations];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : postionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remaningSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.set(symbol, position.get(symbol).subtract(loanShare));
            
            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.address.get();
            userEntry._balance = userDebt;
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger sicxShare = sicxToSell.multiply(userDebt).divide(remaningSupply);
            sicxToSell = sicxToSell.subtract(sicxShare);
            position.set("sICX", position.get("sICX").subtract(sicxShare));

            remaningSupply = remaningSupply.subtract(userDebt);
            changeLog.put(id, Map.of(
                "d", loanShare.negate(),
                "c", sicxShare.negate()
            ));
        }

        if (_getDay().compareTo(continuousRewardDay.get()) >= 0) {
            Context.call(rewards.get(), "updateBatchRewardsData", "Loans", assetContract.totalSupply(), rewardsBatchList);
        }

        Rebalance(Context.getCaller(), symbol, changeLog.toString(), totalBatchDebt);
    }

    @External
    public void lowerPrice(BigInteger _total_tokens_required) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        only(rebalancing);
        String symbol = "sICX";
        Asset asset = AssetDB.getAsset(symbol);

        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = AssetDB.getAsset("bnUSD").getBorrowers();
        int nodeId = borrowers.getHeadId();

        BigInteger totalBatchDebt = BigInteger.ZERO;
        Map<Integer, BigInteger> postionsMap = new HashMap<>();
        
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

        Address bnusdAddress = AssetDB.getAsset("bnUSD").getAssetAddress();
        Token bnusdContract = new Token(bnusdAddress);

        expectedToken.set(bnusdAddress);
        bnusdContract.mint(Context.getAddress(), bnusdToSell);
        amountReceived.set(BigInteger.ZERO);

        expectedToken.set(asset.getAssetAddress());
        byte[] data = createSwapData(asset.getAssetAddress());
        transferToken("bnUSD", dex.get(), bnusdToSell, "bnUSD swapped for sICX", data);
        BigInteger receivedSicx = amountReceived.get();
        amountReceived.set(BigInteger.ZERO);

        BigInteger remainingSicx = receivedSicx;
        BigInteger remainingSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnusdToSell;

        Map<Integer, Map<String, BigInteger>> changeLog = new HashMap<>();
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[iterations];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : postionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remainingSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.set("bnUSD", position.get("bnUSD").add(loanShare));

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.address.get();
            userEntry._balance = userDebt;
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger sicxShare = remainingSicx.multiply(userDebt).divide(remainingSupply);
            remainingSicx = remainingSicx.subtract(sicxShare);
            position.set(symbol, position.get(symbol).add(sicxShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.put(id, Map.of(
                    "d", loanShare,
                    "c", sicxShare
            ));
        }

        if (_getDay().compareTo(continuousRewardDay.get()) >= 0) {
            Context.call(rewards.get(), "updateBatchRewardsData", "Loans", bnusdContract.totalSupply(), rewardsBatchList);
        }
        Rebalance(Context.getCaller(), "bnUSD", changeLog.toString(), totalBatchDebt);
    }

    @External
    public void withdrawCollateral(BigInteger _value) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, "Withdraw amount must be more than zero.");
        Address from = Context.getCaller();
        Context.require(PositionsDB.hasPosition(from), "This address does not have a position on Balanced.");

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);
        Position position = PositionsDB.getPosition(from);

        Context.require(position.get("sICX").compareTo(_value) != -1, "Position holds less collateral than the " +
                "requested withdrawal.");
        BigInteger assetValue = position.totalDebt(BigInteger.valueOf(-1), false);
        BigInteger remainingSicx = position.get("sICX").subtract(_value);

        Address sicxAddress = AssetDB.getAsset("sICX").getAssetAddress();
        Token sicxContract = new Token(sicxAddress);

        BigInteger remainingCollateral = remainingSicx.multiply(sicxContract.priceInLoop()).divide(EXA);

        BigInteger lockingValue = lockingRatio.get().multiply(assetValue).divide(POINTS);
        Context.require(remainingCollateral.compareTo(lockingValue) != -1,
                "Requested withdrawal is more than available collateral. " +
                        "total debt value: " + assetValue + " ICX " +
                        "remaining collateral value: " + remainingCollateral + " ICX " +
                        "locking value (max debt): " + lockingValue + " ICX"
        );

        position.set("sICX", remainingSicx);
        transferToken("sICX", from, _value, "Collateral withdrawn.", new byte[0]);
    }

    @External
    public void liquidate(Address _owner) {
        Context.require(LoansImpl.loansOn.get(), "Balanced Loans SCORE is not active.");
        Context.require(PositionsDB.hasPosition(_owner), "This address does not have a position on Balanced.");
        Position position = PositionsDB.getPosition(_owner);
        boolean checkDay = getDay().compareTo(LoansImpl.continuousRewardDay.get()) < 0;
        Standings standing;
        if (checkDay) {
            standing = position.updateStanding(BigInteger.valueOf(-1));
        } else {
            standing = position.getStanding(BigInteger.valueOf(-1), false).standing;
        }
       
        if (standing  != Standings.LIQUIDATE) {
            return;
        }

        BigInteger collateral = position.get("sICX");
        BigInteger reward = collateral.multiply(liquidationReward.get()).divide(POINTS);
        BigInteger forPool = collateral.subtract(reward);
        BigInteger totalDebt = position.totalDebt(BigInteger.valueOf(-1), false);

        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.getAsset(symbol);
            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);
            BigInteger debt = position.get(symbol);
            if (!asset.isCollateral() && asset.isActive() && debt.compareTo(BigInteger.ZERO) > 0) {
                if (_getDay().compareTo(continuousRewardDay.get()) >= 0) {
                    Context.call(rewards.get(), "updateRewardsData", "Loans", assetContract.totalSupply(), _owner,
                            debt);
                }

                BigInteger badDebt = asset.getBadDebt();
                asset.setBadDebt(badDebt.add(debt));
                BigInteger symbolDebt = debt.multiply(assetContract.priceInLoop()).divide(EXA);
                BigInteger share = forPool.multiply(symbolDebt.divide(totalDebt));
                totalDebt = totalDebt.subtract(symbolDebt);
                forPool = forPool.subtract(share);
                asset.setLiquidationPool(asset.getLiquidationPool().add(share));
                position.set(symbol, BigInteger.ZERO);
            }
        }  

        position.set("sICX", BigInteger.ZERO);
        transferToken("sICX", Context.getCaller(), reward, "Liquidation reward of", new byte[0]);
        if (_getDay().compareTo(continuousRewardDay.get()) < 0) {
            PositionsDB.removeNonZero(position.id.get());
        }

        String logMessage = collateral +" liquidated from " + _owner;
        Liquidate(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, Asset asset, BigInteger badDebtValue) {
        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        BigInteger price = assetContract.priceInLoop();
        Asset sicx = AssetDB.getAsset("sICX");
        Address sicxAddress = sicx.getAssetAddress();
        Token sicxContract = new Token(sicxAddress);
        BigInteger sicxRate = sicxContract.priceInLoop();
        BigInteger inPool = asset.getLiquidationPool();
        BigInteger badDebt = asset.getBadDebt().subtract(badDebtValue);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtSicx = bonus.multiply(badDebtValue).multiply(price).divide(sicxRate.multiply(POINTS));
        asset.setBadDebt(badDebt);
        if (inPool.compareTo(badDebtSicx) != -1) {
            asset.setLiquidationPool(inPool.subtract(badDebtSicx));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferToken("sICX", reserve.get(), inPool.subtract(badDebtSicx), "Sweep to ReserveFund:",
                        new byte[0]);
            }
            return badDebtSicx;
        }

        asset.setLiquidationPool(BigInteger.ZERO);
        expectedToken.set(sicx.getAssetAddress());

        Context.call(reserve.get(), "redeem", from, badDebtSicx.subtract(inPool), sicxRate);

        BigInteger received = amountReceived.get();
        Context.require(received.equals(badDebtSicx.subtract(inPool)), "Got unexpected sICX from reserve.");
        amountReceived.set(BigInteger.ZERO);
        return inPool.add(received);
    }

    private void originateLoan(String _symbol, BigInteger amount, Address from) {
        Asset asset = AssetDB.getAsset(_symbol);
        Context.require(!asset.isDeadMarket(), "No new loans of " + _symbol + " can be originated since it is in a " +
                "dead market state.");
        Context.require(!asset.isCollateral(), "Loans of collateral assets are not allowed.");
        Context.require(asset.isActive(), "Loans of inactive assets are not allowed.");

        Position position = PositionsDB.getPosition(from);

        BigInteger collateral = position.totalCollateral(BigInteger.valueOf(-1));
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(lockingRatio.get());
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        BigInteger newDebtValue = assetContract.priceInLoop().multiply(amount.add(fee)).divide(EXA);
        BigInteger holdings = position.get(_symbol);
        if (holdings.equals(BigInteger.ZERO)) {
            Token bnusd = new Token(AssetDB.getAsset("bnUSD").getAssetAddress());
            BigInteger dollarValue = newDebtValue.multiply(EXA).divide(bnusd.priceInLoop());
            Context.require(dollarValue.compareTo(newLoanMinimum.get()) != -1, "The initial loan of any" +
                    "asset must have a minimum value" +
                    "of " + newLoanMinimum.get().divide(EXA) + " dollars.");
        }

        BigInteger totalDebt = position.totalDebt(BigInteger.valueOf(-1), false);
        Context.require(totalDebt.add(newDebtValue).compareTo(maxDebtValue) != 1, 
            collateral + " collateral is insufficient" +
            " to originate a loan of " + amount.divide(EXA) + " " +_symbol +
            " when max_debt_value = " + maxDebtValue + "," +
            " new_debt_value = " + newDebtValue + "," +
            " which includes a fee of " + fee.divide(EXA) + " " + _symbol + "," +
            " given an existing loan value of " + totalDebt+ ".");

        if (_getDay().compareTo(continuousRewardDay.get()) < 0) {
            if (totalDebt.equals(BigInteger.ZERO)) {
                Snapshot snap = SnapshotDB.get(BigInteger.valueOf(-1));
                PositionsDB.addNonZero(position.id.get());
            }
        } else {
            Context.call(rewards.get(), "updateRewardsData", "Loans", assetContract.totalSupply(), from, holdings);
        }

        BigInteger newDebt = amount.add(fee);
        position.set(_symbol, holdings.add(newDebt));
        assetContract.mint(from, amount);

        String logMessage = "Loan of " + amount + " " + _symbol + " from Balanced.";
        OriginateLoan(from, _symbol, amount, logMessage);

        Address feeHandler = Context.call(Address.class, governance.get(), "getContractAddress", "feehandler");
        assetContract.mint(feeHandler, fee);
        FeePaid(_symbol, fee, "origination");
    }

    private void transferToken(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(AssetDB.getAsset(tokenSymbol).getAssetAddress(), "transfer", to, amount, data);
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
        only(governance);
        admin.set(_address);
    }

    @External
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        Context.require(_address.isContract(), "Loans: Governance address should be a contract");
        governance.set(_address);
    }

    @External
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setDex(Address _address) {
        only(admin);
        isContract(_address);
        dex.set(_address);
    }

    @External
    public Address getDex() {
        return dex.get();
    }
    
    @External
    public void setRebalance(Address _address) {
        only(admin);
        isContract(_address);
        rebalancing.set(_address);
    }

    @External
    public Address getRebalance() {
        return rebalancing.get();
    }

    @External
    public void setDividends(Address _address) {
        only(admin);
        isContract(_address);
        dividends.set(_address);
    }

    @External
    public Address getDividends() {
        return dividends.get();
    }

    @External
    public void setReserve(Address _address) {
        only(admin);
        isContract(_address);
        reserve.set(_address);
    }

    @External
    public Address getReserve() {
        return reserve.get();
    }

    @External
    public void setRewards(Address _address) {
        only(admin);
        isContract(_address);
        rewards.set(_address);
    }

    @External
    public Address getRewards() {
        return rewards.get();
    }

    @External
    public void setStaking(Address _address) {
        only(admin);
        isContract(_address);
        staking.set(_address);
    }

    @External
    public Address getStaking() {
        return staking.get();
    }

    @External
    public void setMiningRatio(BigInteger _ratio) {
        only(admin);
        miningRatio.set(_ratio);
    }

    @External
    public void setLockingRatio(BigInteger _ratio) {
        only(admin);
        lockingRatio.set(_ratio);
    }

    @External
    public void setLiquidationRatio(BigInteger _ratio) {
        only(admin);
        liquidationRatio.set(_ratio); 
    }

    @External
    public void setOriginationFee(BigInteger _fee) {
        only(admin);
        originationFee.set(_fee);
    } 

    @External
    public void setRedemptionFee(BigInteger _fee) {
        only(admin);
        redemptionFee.set(_fee);
    }

    @External
    public void setRetirementBonus(BigInteger _points) {
        only(admin);
        retirementBonus.set(_points);
    }

    @External
    public void setLiquidationReward(BigInteger _points) {
        only(admin);
        liquidationReward.set(_points);
    }

    @External
    public void setNewLoanMinimum(BigInteger _minimum) {
        only(admin);
        newLoanMinimum.set(_minimum);
    }

    @External
    public void setMinMiningDebt(BigInteger _minimum) {
        only(admin);
        minMiningDebt.set(_minimum);
    }

    @External
    public void setTimeOffset(BigInteger deltaTime) {
        only(governance);
        timeOffset.set(deltaTime);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        only(admin);
        Context.require(_value.compareTo(BigInteger.ZERO) != -1 && 
                    _value.compareTo(BigInteger.valueOf(10000)) != 1, 
                    "Input parameter must be in the range 0 to 10000 points.");
        maxRetirePercent.set(_value); 
    }

    @External
    public void setRedeemBatchSize(int _value) {
        only(admin);
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
            entry("time offset", timeOffset.getOrDefault(BigInteger.ZERO)),
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
    public void Snapshot(BigInteger _id) {}
        
}
