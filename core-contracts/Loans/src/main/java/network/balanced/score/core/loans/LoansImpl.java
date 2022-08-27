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
import com.eclipsesource.json.JsonValue;
import network.balanced.score.core.loans.asset.Asset;
import network.balanced.score.core.loans.asset.AssetDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.snapshot.Snapshot;
import network.balanced.score.core.loans.snapshot.SnapshotDB;
import network.balanced.score.core.loans.utils.Token;
import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.Context;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.loans.LoansVariables.loansOn;
import static network.balanced.score.core.loans.LoansVariables.*;
import static network.balanced.score.core.loans.utils.Checks.loansOn;
import static network.balanced.score.core.loans.utils.Checks.*;
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.convertToNumber;

public class LoansImpl implements Loans {

    public static final String TAG = "BalancedLoans";

    public LoansImpl(Address _governance) {

        if (governance.get() == null) {
            governance.set(_governance);
            loansOn.set(false);
            snapBatchSize.set(SNAP_BATCH_SIZE);
            rewardsDone.set(true);
            dividendsDone.set(true);
            miningRatio.set(MINING_RATIO);
            lockingRatio.set(LOCKING_RATIO);
            liquidationRatio.set(LIQUIDATION_RATIO);
            originationFee.set(ORIGINATION_FEE);
            redemptionFee.set(REDEMPTION_FEE);
            liquidationReward.set(LIQUIDATION_REWARD);
            retirementBonus.set(BAD_DEBT_RETIREMENT_BONUS);
            newLoanMinimum.set(NEW_BNUSD_LOAN_MINIMUM);
            minMiningDebt.set(MIN_BNUSD_MINING_DEBT);
            redeemBatch.set(REDEEM_BATCH_SIZE);
            maxRetirePercent.set(MAX_RETIRE_PERCENT);
            maxDebtsListLength.set(MAX_DEBTS_LIST_LENGTH);
        } else {
            Asset asset = AssetDB.getAsset(BNUSD_SYMBOL);
            if (asset.getBadDebt().equals(BigInteger.ZERO)) {
                asset.setLiquidationPool(null);
            }
        }

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


    @External
    public void removeZeroPosition(int id) {
        onlyOwner();
        if (AssetDB.getAsset(BNUSD_SYMBOL).getBorrowers().nodeValue(id).equals(BigInteger.ZERO)){
            AssetDB.getAsset(BNUSD_SYMBOL).getBorrowers().remove(id);
        }
    }

    @External
    public void migrateUserData(Address address) {
        Position position = PositionsDB.getPosition(address);
        int id = position.getSnapshotId(-1);

        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            if (position.getDataMigrationStatus(symbol)) {
                continue;
            }

            if (AssetDB.getAsset(symbol).isCollateral()) {
                position.setCollateralPosition(symbol, position.getAssets(id, symbol));
            } else {
                BigInteger debt = position.getAssets(id, symbol);
                BigInteger previousTotalDebt = LoansVariables.totalDebts.getOrDefault(symbol, BigInteger.ZERO);
                BigInteger newTotalDebt = previousTotalDebt.add(debt);

                LoansVariables.totalDebts.set(symbol, newTotalDebt);
                position.setLoansPosition(SICX_SYMBOL, symbol, debt);
            }
            position.setDataMigrationStatus(symbol, true);
        }
    }

    @External(readonly = true)
    public Map<String, Object> userMigrationDetails(Address _address) {
        Map<String, Object> migrationDetails = new HashMap<>();
        Position p = PositionsDB.getPosition(_address);

        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            migrationDetails.put("flag", Map.of(symbol, p.getDataMigrationStatus(symbol)));
            migrationDetails.put("old", Map.of(symbol, p.getAssets(1, symbol)));

            BigInteger amount;
            if (AssetDB.getAsset(symbol).isCollateral()) {
                amount = p.getCollateralPosition(symbol);
                migrationDetails.put(symbol, amount);
            } else {
                int activeCollateralCount = AssetDB.activeCollateral.size();
                for (int j = 0; j < activeCollateralCount; j++) {
                    String collateral = AssetDB.activeCollateral.get(j);
                    amount = p.getLoansPosition(collateral, symbol);
                    migrationDetails.put(symbol, Map.of(collateral, amount));
                }
            }
        }
        return migrationDetails;
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
        return timeDelta.divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External
    public void delegate(PrepDelegations[] prepDelegations) {
        only(governance);
        Context.call(staking.get(), "delegate", (Object) prepDelegations);
    }

    @External(readonly = true)
    public Map<String, Boolean> getDistributionsDone() {
        return Map.of(
                "Rewards", rewardsDone.get(),
                "Dividends", dividendsDone.get()
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

        if (snap.getDay() > 1) {
            Snapshot lastSnap = SnapshotDB.get(-2);
            count = count + lastSnap.getAddNonzero().size() - lastSnap.getRemoveNonzero().size();
        }

        return count;
    }

    @External(readonly = true)
    public Map<String, Object> getPositionStanding(Address _address, @Optional BigInteger snapshot) {
        if (snapshot == null || snapshot.equals(BigInteger.ZERO)) {
            snapshot = BigInteger.valueOf(-1);
        }

        Context.require(isBeforeContinuousRewardDay(SnapshotDB.getSnapshotId(snapshot.intValue())),
                continuousRewardsErrorMessage);
        return PositionsDB.getPosition(_address).getStanding(snapshot.intValue(), true).toMap();
    }

    @External(readonly = true)
    public Address getPositionAddress(int _index) {
        return PositionsDB.get(_index).getAddress();
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
        Context.require(isBeforeContinuousRewardDay(_day), continuousRewardsErrorMessage);
        return PositionsDB.get(_index).toMap(_day.intValue());
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
        return PositionsDB.getPosition(_owner).hasDebt(-1);
    }

    @External(readonly = true)
    public Map<String, Object> getSnapshot(@Optional BigInteger _snap_id) {
        if (_snap_id == null || _snap_id.equals(BigInteger.ZERO)) {
            _snap_id = BigInteger.valueOf(-1);
        }

        if (_snap_id.intValue() > SnapshotDB.getLastSnapshotIndex() || (_snap_id.intValue() + SnapshotDB.size()) < 0) {
            return Map.of();
        }

        return SnapshotDB.get(_snap_id.intValue()).toMap();
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
    public boolean precompute(BigInteger _snapshot_id, BigInteger batch_size) {
        only(rewards);
        checkForNewDay();
        return PositionsDB.calculateSnapshot(_snapshot_id, batch_size.intValue());
    }


    @External(readonly = true)
    public BigInteger getTotalValue(String _name, BigInteger _snapshot_id) {
        Context.require(isBeforeContinuousRewardDay(SnapshotDB.getSnapshotId(_snapshot_id.intValue())),
                continuousRewardsErrorMessage);

        return SnapshotDB.get(_snapshot_id.intValue()).getTotalMiningDebt();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        Context.require(_name.equals("Loans"), TAG + ": Unsupported data source name");

        BigInteger totalSupply = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);

        int id = PositionsDB.getAddressIds(_owner);
        if (id < 1) {
            return Map.of(
                    "_balance", BigInteger.ZERO,
                    "_totalSupply", totalSupply
            );
        }

        Position position = PositionsDB.get(id);
        BigInteger balance = position.getAssetPosition(BNUSD_SYMBOL);

        return Map.of(
                "_balance", balance,
                "_totalSupply", totalSupply
        );
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        Asset asset = AssetDB.getAsset(BNUSD_SYMBOL);
        Token assetContract = new Token(asset.getAssetAddress());
        BigInteger totalSupply = assetContract.totalSupply();

        return totalSupply.subtract(asset.getBadDebt());
    }

    @External(readonly = true)
    public BigInteger getDataCount(BigInteger _snapshot_id) {
        Context.require(isBeforeContinuousRewardDay(_snapshot_id), continuousRewardsErrorMessage);
        return BigInteger.valueOf(SnapshotDB.get(_snapshot_id.intValue()).getMiningSize());
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDataBatch(String _name, BigInteger _snapshot_id, int _limit, @Optional int _offset) {
        Context.require(isBeforeContinuousRewardDay(_snapshot_id), continuousRewardsErrorMessage);

        Snapshot snapshot = SnapshotDB.get(_snapshot_id.intValue());
        int totalMiners = snapshot.getMiningSize();
        int start = Math.max(0, Math.min(_offset, totalMiners));
        int end = Math.min(_offset + _limit, totalMiners);

        Map<String, BigInteger> batch = new HashMap<>();
        for (int i = start; i < end; i++) {
            int id = snapshot.getMining(i);
            Position position = PositionsDB.get(id);
            batch.put(position.getAddress().toString(), snapshot.getPositionStates(id, "total_debt"));
        }

        return batch;
    }

    @External
    public boolean checkForNewDay() {
        loansOn();
        BigInteger day = _getDay();

        if (currentDay.get().compareTo(day) < 0 && isBeforeContinuousRewardDay(day.subtract(BigInteger.ONE))) {
            currentDay.set(day);
            PositionsDB.takeSnapshot();
            Snapshot(_getDay());
            AssetDB.updateDeadMarkets();
            return true;
        }

        // TODO See the update frequency of checking dead markets
        AssetDB.updateDeadMarkets();
        return false;
    }

    @External
    public void checkDistributions(BigInteger _day, boolean _new_day) {
        loansOn();
        Boolean rewardsDone = LoansVariables.rewardsDone.get();
        Boolean dividendsDone = LoansVariables.dividendsDone.get();

        if (_new_day && rewardsDone && dividendsDone) {
            LoansVariables.rewardsDone.set(false);
            LoansVariables.dividendsDone.set(false);
        } else if (!dividendsDone) {
            LoansVariables.dividendsDone.set(Context.call(Boolean.class, dividends.get(), "distribute"));
        } else if (!rewardsDone) {
            LoansVariables.rewardsDone.set(Context.call(Boolean.class, rewards.get(), "distribute"));
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        loansOn();

        Context.require(_value.signum() > 0, TAG + ": Token value should be a positive number");

        Address token = Context.getCaller();
        if (token.equals(expectedToken.get())) {
            amountReceived.set(_value);
            expectedToken.set(null);
            return;
        }

        Context.require(token.equals(AssetDB.getAsset(SICX_SYMBOL).getAssetAddress()), TAG + ": The Balanced Loans " +
                "contract does not accept that token type.");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String requestedAsset = json.get("_asset").asString();
        JsonValue amount = json.get("_amount");
        BigInteger requestedAmount = amount == null ? null : convertToNumber(amount);
        depositAndBorrow(requestedAsset, requestedAmount, _from, _value);
    }

    @External
    @Payable
    public void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value) {
        loansOn();
        BigInteger deposit = Context.getValue();
        Address sender = Context.getCaller();
        Address sicxAddress = AssetDB.getAsset(SICX_SYMBOL).getAssetAddress();

        Address depositor = _from;
        BigInteger sicxDeposited = _value;

        if (!sender.equals(sicxAddress)) {
            depositor = sender;
            if (deposit.compareTo(BigInteger.ZERO) > 0) {
                expectedToken.set(sicxAddress);
                Context.call(deposit, staking.get(), "stakeICX", Context.getAddress(), new byte[0]);

                BigInteger received = amountReceived.getOrDefault(BigInteger.ZERO);
                Context.require(!received.equals(BigInteger.ZERO), TAG + ": Expected sICX not received.");
                amountReceived.set(null);
                sicxDeposited = received;
            }
        }

        boolean isNewDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, isNewDay);
        Position position = PositionsDB.getPosition(depositor);
        if (sicxDeposited.compareTo(BigInteger.ZERO) > 0) {
            position.setAssetPosition(SICX_SYMBOL, position.getAssetPosition(SICX_SYMBOL).add(sicxDeposited));
            CollateralReceived(depositor, SICX_SYMBOL, sicxDeposited);
        }

        if (_asset == null || _asset.equals("") || _amount == null || _amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        originateLoan(_asset, _amount, depositor);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Asset asset = AssetDB.getAsset(_symbol);
        BigInteger badDebt = asset.getBadDebt();

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(!asset.isCollateral(), TAG + ": " + _symbol + " is not an active, borrowable asset on " +
                "Balanced.");
        Context.require(asset.isActive(), TAG + ": " + _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
        Context.require(badDebt.compareTo(BigInteger.ZERO) > 0, TAG + ": No bad debt for " + _symbol);

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);

        BigInteger badDebtValue = badDebt.min(_value);
        asset.burnFrom(from, badDebtValue);

        BigInteger sicxCollateralToRedeem = badDebtRedeem(from, asset, badDebtValue);
        transferToken(SICX_SYMBOL, from, sicxCollateralToRedeem, "Bad Debt redeemed.", new byte[0]);
        asset.checkForDeadMarket();
        BadDebtRetired(from, _symbol, badDebtValue, sicxCollateralToRedeem);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, @Optional boolean _repay) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Asset asset = AssetDB.getAsset(_symbol);

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(!asset.isCollateral(), TAG + ": " + _symbol + " is not an active, borrowable asset on " +
                "Balanced.");
        Context.require(asset.isActive(), TAG + ": " + _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": No debt repaid because, " + from + " does not have a " +
                "position in Balanced");

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);

        BigInteger oldSupply = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
        Position position = PositionsDB.getPosition(from);
        BigInteger borrowed = position.getAssetPosition(_symbol);

        Context.require(_value.compareTo(borrowed) <= 0, TAG + ": Repaid amount is greater than the amount in the " +
                "position of " + from);
        if (_value.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BigInteger remaining = borrowed.subtract(_value);
        BigInteger repaid;
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            position.setAssetPosition(_symbol, position.getAssetPosition(_symbol).subtract(_value));
            repaid = _value;
        } else {
            position.setAssetPosition(_symbol, null);
            repaid = borrowed;
        }

        asset.burnFrom(from, repaid);
        if (isBeforeContinuousRewardDay()) {
            if (!position.hasDebt(-1)) {
                PositionsDB.removeNonZero(position.getId());
            }
        }

        Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, borrowed);

        asset.checkForDeadMarket();
        String logMessage = "Loan of " + repaid + " " + _symbol + " repaid to Balanced.";
        LoanRepaid(from, _symbol, repaid, logMessage);
    }

    @External
    public void raisePrice(BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);

        Asset asset = AssetDB.getAsset(BNUSD_SYMBOL);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
        BigInteger rate = Context.call(BigInteger.class, dex.get(), "getSicxBnusdPrice");
        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = asset.getBorrowers();

        int nodeId = borrowers.getHeadId();
        BigInteger totalBatchDebt = BigInteger.ZERO;
        Map<Integer, BigInteger> positionsMap = new HashMap<>();

        int iterations = Math.min(batchSize, borrowers.size());
        for (int i = 0; i < iterations; i++) {
            BigInteger debt = borrowers.nodeValue(nodeId);
            positionsMap.put(nodeId, debt);
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
        transferToken(SICX_SYMBOL, dex.get(), sicxToSell, "sICX swapped for bnUSD", data);

        BigInteger bnUSDReceived = amountReceived.get();

        amountReceived.set(null);
        asset.burnFrom(Context.getAddress(), bnUSDReceived);

        BigInteger remainingSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnUSDReceived;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[iterations];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remainingSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.setAssetPosition(BNUSD_SYMBOL, userDebt.subtract(loanShare));

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = userDebt;
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger sicxShare = sicxToSell.multiply(userDebt).divide(remainingSupply);
            sicxToSell = sicxToSell.subtract(sicxShare);
            position.setAssetPosition(SICX_SYMBOL, position.getAssetPosition(SICX_SYMBOL).subtract(sicxShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
            "'d': " + loanShare.negate() +", " + 
            "'c': " + sicxShare.negate() + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length()-2, changeLog.length()).append("}");

        Rebalance(Context.getCaller(), BNUSD_SYMBOL, changeLog.toString(), totalBatchDebt);
    }

    @External
    public void lowerPrice(BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);

        Asset asset = AssetDB.getAsset(SICX_SYMBOL);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
        int batchSize = redeemBatch.get();
        LinkedListDB borrowers = AssetDB.getAsset(BNUSD_SYMBOL).getBorrowers();

        int nodeId = borrowers.getHeadId();
        BigInteger totalBatchDebt = BigInteger.ZERO;
        Map<Integer, BigInteger> positionsMap = new HashMap<>();

        int iterations = Math.min(batchSize, borrowers.size());
        for (int i = 0; i < iterations; i++) {
            BigInteger debt = borrowers.nodeValue(nodeId);
            positionsMap.put(nodeId, debt);
            totalBatchDebt = totalBatchDebt.add(debt);
            borrowers.headToTail();
            nodeId = borrowers.getHeadId();
        }

        borrowers.serialize();

        BigInteger bnusdToSell = maxRetirePercent.get().multiply(totalBatchDebt).divide(POINTS);
        bnusdToSell = bnusdToSell.min(_total_tokens_required);

        Address bnusdAddress = AssetDB.getAsset(BNUSD_SYMBOL).getAssetAddress();
        Token bnusdContract = new Token(bnusdAddress);

        expectedToken.set(bnusdAddress);
        bnusdContract.mintTo(Context.getAddress(), bnusdToSell);
        amountReceived.set(null);

        expectedToken.set(asset.getAssetAddress());
        byte[] data = createSwapData(asset.getAssetAddress());
        transferToken(BNUSD_SYMBOL, dex.get(), bnusdToSell, "bnUSD swapped for sICX", data);
        BigInteger receivedSicx = amountReceived.get();
        amountReceived.set(null);

        BigInteger remainingSicx = receivedSicx;
        BigInteger remainingSupply = totalBatchDebt;
        BigInteger remainingBnusd = bnusdToSell;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[iterations];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.get(id);
            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remainingSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);
            position.setAssetPosition(BNUSD_SYMBOL, userDebt.add(loanShare));

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = userDebt;
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger sicxShare = remainingSicx.multiply(userDebt).divide(remainingSupply);
            remainingSicx = remainingSicx.subtract(sicxShare);
            position.setAssetPosition(SICX_SYMBOL, position.getAssetPosition(SICX_SYMBOL).add(sicxShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
                "'d': " + loanShare +", " + 
                "'c': " + sicxShare + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length()-2, changeLog.length()).append("}");
        Rebalance(Context.getCaller(), BNUSD_SYMBOL, changeLog.toString(), totalBatchDebt);
    }

    @External
    public void withdrawCollateral(BigInteger _value) {
        loansOn();

        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Withdraw amount must be more than zero.");
        Address from = Context.getCaller();
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        boolean newDay = checkForNewDay();
        BigInteger day = _getDay();
        checkDistributions(day, newDay);
        Position position = PositionsDB.getPosition(from);

        Context.require(position.getAssetPosition(SICX_SYMBOL).compareTo(_value) >= 0, TAG + ": Position holds less " +
                "collateral than the requested withdrawal.");
        BigInteger assetValue = position.totalDebt(-1, false);
        BigInteger remainingSicx = position.getAssetPosition(SICX_SYMBOL).subtract(_value);

        Address sicxAddress = AssetDB.getAsset(SICX_SYMBOL).getAssetAddress();
        Token sicxContract = new Token(sicxAddress);

        BigInteger remainingCollateral = remainingSicx.multiply(sicxContract.priceInLoop()).divide(EXA);

        BigInteger lockingValue = lockingRatio.get().multiply(assetValue).divide(POINTS);
        Context.require(remainingCollateral.compareTo(lockingValue) >= 0,
                TAG + ": Requested withdrawal is more than available collateral. " +
                        "total debt value: " + assetValue + " ICX " +
                        "remaining collateral value: " + remainingCollateral + " ICX " +
                        "locking value (max debt): " + lockingValue + " ICX"
        );

        position.setAssetPosition(SICX_SYMBOL, remainingSicx);
        transferToken(SICX_SYMBOL, from, _value, "Collateral withdrawn.", new byte[0]);
    }

    @External
    public void liquidate(Address _owner) {
        loansOn();

        Context.require(PositionsDB.hasPosition(_owner), TAG + ": This address does not have a position on Balanced.");
        Position position = PositionsDB.getPosition(_owner);
        Standings standing;
        boolean isBeforeContinuousRewardDay = isBeforeContinuousRewardDay();
        if (isBeforeContinuousRewardDay) {
            standing = position.updateStanding(-1);
        } else {
            standing = position.getStanding(-1, false).standing;
        }

        if (standing != Standings.LIQUIDATE) {
            return;
        }

        BigInteger collateral = position.getAssetPosition(SICX_SYMBOL);
        BigInteger reward = collateral.multiply(liquidationReward.get()).divide(POINTS);
        BigInteger forPool = collateral.subtract(reward);
        BigInteger totalDebt = position.totalDebt(-1, false);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);

        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.getAsset(symbol);
            if (!asset.isActive() || asset.isCollateral()) {
                continue;
            }
            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);
            BigInteger debt = position.getAssetPosition(symbol);
            if (debt.compareTo(BigInteger.ZERO) > 0) {
                Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, _owner, debt);

                BigInteger badDebt = asset.getBadDebt();
                asset.setBadDebt(badDebt.add(debt));
                BigInteger symbolDebt = debt.multiply(assetContract.priceInLoop()).divide(EXA);
                BigInteger share = forPool.multiply(symbolDebt.divide(totalDebt));
                totalDebt = totalDebt.subtract(symbolDebt);
                forPool = forPool.subtract(share);
                asset.setLiquidationPool(asset.getLiquidationPool().add(share));
                position.setAssetPosition(symbol, null);
            }
        }

        position.setAssetPosition(SICX_SYMBOL, null);
        transferToken(SICX_SYMBOL, Context.getCaller(), reward, "Liquidation reward of", new byte[0]);
        AssetDB.updateDeadMarkets();

        if (isBeforeContinuousRewardDay) {
            PositionsDB.removeNonZero(position.getId());
        }

        String logMessage = collateral + " liquidated from " + _owner;
        Liquidate(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, Asset asset, BigInteger badDebtValue) {
        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        BigInteger price = assetContract.priceInLoop();
        Asset sicx = AssetDB.getAsset(SICX_SYMBOL);
        Address sicxAddress = sicx.getAssetAddress();
        Token sicxContract = new Token(sicxAddress);

        BigInteger sicxRate = sicxContract.priceInLoop();
        BigInteger inPool = asset.getLiquidationPool();
        BigInteger badDebt = asset.getBadDebt().subtract(badDebtValue);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtSicx = bonus.multiply(badDebtValue).multiply(price).divide(sicxRate.multiply(POINTS));
        asset.setBadDebt(badDebt);
        if (inPool.compareTo(badDebtSicx) >= 0) {
            asset.setLiquidationPool(inPool.subtract(badDebtSicx));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferToken(SICX_SYMBOL, reserve.get(), inPool.subtract(badDebtSicx), "Sweep to ReserveFund:",
                        new byte[0]);
                asset.setLiquidationPool(null);
            }

            return badDebtSicx;
        }

        asset.setLiquidationPool(null);
        expectedToken.set(sicx.getAssetAddress());

        Context.call(reserve.get(), "redeem", from, badDebtSicx.subtract(inPool), sicxRate);

        BigInteger received = amountReceived.get();
        Context.require(received.equals(badDebtSicx.subtract(inPool)), TAG + ": Got unexpected sICX from reserve.");
        amountReceived.set(null);
        return inPool.add(received);
    }

    private void originateLoan(String assetToBorrow, BigInteger amount, Address from) {
        Asset asset = AssetDB.getAsset(assetToBorrow);
        Context.require(!asset.checkForDeadMarket(), TAG + ": No new loans of " + assetToBorrow + " can be originated" +
                " since it is in a dead market state.");
        Context.require(!asset.isCollateral(), TAG + ": Loans of collateral assets are not allowed.");
        Context.require(asset.isActive(), TAG + ": Loans of inactive assets are not allowed.");

        Position position = PositionsDB.getPosition(from);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);

        BigInteger collateral = position.totalCollateral(-1);
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(lockingRatio.get());
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);

        Address borrowAssetAddress = asset.getAssetAddress();
        Token borrowAsset = new Token(borrowAssetAddress);

        BigInteger newDebt = amount.add(fee);
        BigInteger newDebtValue = borrowAsset.priceInLoop().multiply(newDebt).divide(EXA);
        BigInteger holdings = position.getAssetPosition(assetToBorrow);
        if (holdings.equals(BigInteger.ZERO)) {
            Token bnusd = new Token(AssetDB.getAsset(BNUSD_SYMBOL).getAssetAddress());
            BigInteger dollarValue = newDebtValue.multiply(EXA).divide(bnusd.priceInLoop());
            Context.require(dollarValue.compareTo(newLoanMinimum.get()) >= 0, TAG + ": The initial loan of any " +
                    "asset must have a minimum value of " + newLoanMinimum.get().divide(EXA) + " dollars.");
            if (!AssetDB.getAsset(assetToBorrow).getBorrowers().contains(position.getId())) {
                AssetDB.getAsset(assetToBorrow).getBorrowers().append(newDebt, position.getId());
            }
        }

        BigInteger totalDebt = position.totalDebt(-1, false);
        Context.require(totalDebt.add(newDebtValue).compareTo(maxDebtValue) <= 0,
                TAG + ": " + collateral + " collateral is insufficient" +
                        " to originate a loan of " + amount + " " + assetToBorrow +
                        " when max_debt_value = " + maxDebtValue + "," +
                        " new_debt_value = " + newDebtValue + "," +
                        " which includes a fee of " + fee + " " + assetToBorrow + "," +
                        " given an existing loan value of " + totalDebt + ".");

        if (isBeforeContinuousRewardDay()) {
            if (totalDebt.equals(BigInteger.ZERO)) {
                PositionsDB.addNonZero(position.getId());
            }
        }

        Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, from, holdings);

        position.setAssetPosition(assetToBorrow, holdings.add(newDebt));
        borrowAsset.mintTo(from, amount);

        String logMessage = "Loan of " + amount + " " + assetToBorrow + " from Balanced.";
        OriginateLoan(from, assetToBorrow, amount, logMessage);

        Address feeHandler = Context.call(Address.class, governance.get(), "getContractAddress", "feehandler");
        borrowAsset.mintTo(feeHandler, fee);
        FeePaid(assetToBorrow, fee, "origination");
    }

    private void transferToken(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(AssetDB.getAsset(tokenSymbol).getAssetAddress(), "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString() + " " + tokenSymbol + " sent to " + to;
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

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
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
    public void setDex(Address _address) {
        only(admin);
        isContract(_address);
        dex.set(_address);
    }

    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    @External
    public void setRebalance(Address _address) {
        only(admin);
        isContract(_address);
        rebalancing.set(_address);
    }

    @External(readonly = true)
    public Address getRebalance() {
        return rebalancing.get();
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
    public void setReserve(Address _address) {
        only(admin);
        isContract(_address);
        reserve.set(_address);
    }

    @External(readonly = true)
    public Address getReserve() {
        return reserve.get();
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
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0 &&
                        _value.compareTo(BigInteger.valueOf(10_000)) <= 0,
                "Input parameter must be in the range 0 to 10000 points.");
        maxRetirePercent.set(_value);
    }

    @External
    public void setRedeemBatchSize(int _value) {
        only(admin);
        redeemBatch.set(_value);
    }

    @External(readonly = true)
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("admin", admin.get());
        parameters.put("governance", governance.get());
        parameters.put("dividends", dividends.get());
        parameters.put("reserve_fund", reserve.get());
        parameters.put("rewards", rewards.get());
        parameters.put("staking", staking.get());
        parameters.put("mining ratio", miningRatio.get());
        parameters.put("locking ratio", lockingRatio.get());
        parameters.put("liquidation ratio", liquidationRatio.get());
        parameters.put("origination fee", originationFee.get());
        parameters.put("redemption fee", redemptionFee.get());
        parameters.put("liquidation reward", liquidationReward.get());
        parameters.put("new loan minimum", newLoanMinimum.get());
        parameters.put("min mining debt", minMiningDebt.get());
        parameters.put("max div debt length", maxDebtsListLength.get());
        parameters.put("time offset", timeOffset.getOrDefault(BigInteger.ZERO));
        parameters.put("redeem batch size", redeemBatch.get());
        parameters.put("retire percent max", maxRetirePercent.get());

        return parameters;
    }


    @EventLog(indexed = 1)
    public void ContractActive(String _contract, String _state) {
    }

    @EventLog(indexed = 1)
    public void AssetActive(String _asset, String _state) {
    }

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void AssetAdded(Address account, String symbol, boolean is_collateral) {
    }

    @EventLog(indexed = 2)
    public void CollateralReceived(Address account, String symbol, BigInteger value) {
    }

    @EventLog(indexed = 3)
    public void OriginateLoan(Address recipient, String symbol, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void LoanRepaid(Address account, String symbol, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void BadDebtRetired(Address account, String symbol, BigInteger amount, BigInteger sicx_received) {
    }

    @EventLog(indexed = 2)
    public void Liquidate(Address account, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void FeePaid(String symbol, BigInteger amount, String type) {
    }

    @EventLog(indexed = 2)
    public void Rebalance(Address account, String symbol, String change_in_pos, BigInteger total_batch_debt) {
    }

    @EventLog(indexed = 2)
    public void PositionStanding(Address address, String standing, BigInteger total_collateral, BigInteger total_debt) {
    }

    @EventLog(indexed = 1)
    public void Snapshot(BigInteger _id) {
    }

}
