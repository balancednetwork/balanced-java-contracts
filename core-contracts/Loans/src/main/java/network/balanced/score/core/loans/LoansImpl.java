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
import network.balanced.score.core.loans.collateral.CollateralManager;
import network.balanced.score.core.loans.debt.DebtManager;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.utils.PositionBatch;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.core.loans.utils.TokenUtils;

import score.Address;
import score.Context;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.LoansVariables.*;
import static network.balanced.score.core.loans.utils.Checks.loansOn;
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.ArrayDBUtils.removeFromArraydb;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static network.balanced.score.lib.utils.Math.pow;

public class LoansImpl {

    public static final String TAG = "BalancedLoans";

    public LoansImpl(Address _governance) {

        if (governance.get() == null) {
            governance.set(_governance);
            loansOn.set(true);
            lockingRatio.set(SICX_SYMBOL, LOCKING_RATIO);
            liquidationRatio.set(SICX_SYMBOL, LIQUIDATION_RATIO);
            originationFee.set(ORIGINATION_FEE);
            redemptionFee.set(REDEMPTION_FEE);
            liquidationReward.set(LIQUIDATION_REWARD);
            retirementBonus.set(BAD_DEBT_RETIREMENT_BONUS);
            newLoanMinimum.set(NEW_BNUSD_LOAN_MINIMUM);
            redeemBatch.set(REDEEM_BATCH_SIZE);
            maxRetirePercent.set(MAX_RETIRE_PERCENT);
            maxDebtsListLength.set(MAX_DEBTS_LIST_LENGTH);
        } else if (bnUSD.get() == null) {
            bnUSD.set(Address.fromString(CollateralManager.symbolMap.get(BNUSD_SYMBOL)));
        }

        if (arrayDbContains(CollateralManager.collateralList, "BALN")) {
            removeBALN();
        }
    }

    private void removeBALN() {
        String symbol = "BALN";
        Address address = CollateralManager.getAddress(symbol);
        
        CollateralManager.symbolMap.set(symbol, null);
        removeFromArraydb(address, CollateralManager.collateralAddresses);
        removeFromArraydb(symbol, CollateralManager.collateralList);

        Context.require(CollateralManager.symbolMap.get(symbol) == null);
        Context.require(!arrayDbContains(CollateralManager.collateralAddresses, address));
        Context.require(!arrayDbContains(CollateralManager.collateralList, symbol));
    }

    @External(readonly = true)
    public String name() {
        return Names.LOANS;
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
    public Address getPositionAddress(int _index) {
        return PositionsDB.get(_index).getAddress();
    }

    @External(readonly = true)
    public Map<String, String> getAssetTokens() {
        Map<String, String> assetAndCollateral = new HashMap<>();
        assetAndCollateral.put(BNUSD_SYMBOL, bnUSD.get().toString());
        assetAndCollateral.putAll(CollateralManager.getCollateral());

        return assetAndCollateral;
    }

    @External(readonly = true)
    public Map<String, String> getCollateralTokens() {
        return CollateralManager.getCollateral();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateral() {
        return CollateralManager.getTotalCollateral();
    }

    @External(readonly = true)
    public Map<String, Object> getAccountPositions(Address _owner) {
        Context.require(PositionsDB.hasPosition(_owner), _owner + " does not have a position in Balanced");
        return PositionsDB.listPosition(_owner);
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getAvailableAssets() {
        return Map.of(BNUSD_SYMBOL, DebtManager.debtData());
    }

    @External(readonly = true)
    public int assetCount() {
        return 1;
    }

    @External(readonly = true)
    public int borrowerCount() {
        return PositionsDB.size();
    }

    @External(readonly = true)
    public boolean hasDebt(Address _owner) {
        return PositionsDB.getPosition(_owner, true).hasDebt();
    }

    // only adds collateral, name is kept for backwards compatibility
    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        only(admin);
        if (_collateral) {
            CollateralManager.addCollateral(_token_address);
            AssetAdded(_token_address, TokenUtils.symbol(_token_address), _collateral);
        }
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        Context.require(_name.equals("Loans"), TAG + ": Unsupported data source name");

        BigInteger totalSupply = DebtManager.getTotalDebt();

        int id = PositionsDB.getAddressIds(_owner);
        if (id < 1) {
            return Map.of(
                    "_balance", BigInteger.ZERO,
                    "_totalSupply", totalSupply
            );
        }

        Position position = PositionsDB.get(id);
        BigInteger balance = position.getTotalDebt();
        return Map.of(
                "_balance", balance,
                "_totalSupply", totalSupply
        );
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        return DebtManager.getTotalDebt();
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

        if (_from.equals(reserve.get())) {
            return;
        }

        String collateralSymbol = TokenUtils.symbol(token);
        Context.require(CollateralManager.getAddress(collateralSymbol).equals(token),
                TAG + ": The Balanced Loans contract does not accept that token type.");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        JsonValue amount = json.get("_amount");
        BigInteger requestedAmount = amount == null ? null : convertToNumber(amount);

        depositCollateral(collateralSymbol, _value, _from);
        if (BigInteger.ZERO.compareTo(requestedAmount) < 0) {
            originateLoan(collateralSymbol, requestedAmount, _from);
        }
    }

    @External
    public void borrow(String _collateralToBorrowAgainst, String _assetToBorrow, BigInteger _amountToBorrow) {
        loansOn();
        Context.require(_amountToBorrow.compareTo(BigInteger.ZERO) > 0, TAG + ": _amountToBorrow needs to be larger " +
                "than 0");
        Context.require(_assetToBorrow.equals(BNUSD_SYMBOL));
        originateLoan(_collateralToBorrowAgainst, _amountToBorrow, Context.getCaller());
    }

    @External
    @Payable
    public void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from,
                                 @Optional BigInteger _value) {
        loansOn();
        BigInteger deposit = Context.getValue();
        Address depositor = Context.getCaller();

        if (!deposit.equals(BigInteger.ZERO)) {
            Position position = PositionsDB.getPosition(depositor);
            BigInteger sicxDeposited = stakeICX(deposit);
            position.setCollateral(SICX_SYMBOL, position.getCollateral(SICX_SYMBOL).add(sicxDeposited));
            CollateralReceived(depositor, SICX_SYMBOL, sicxDeposited);
        }

        if (!BNUSD_SYMBOL.equals(_asset) || _amount == null || _amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        originateLoan(SICX_SYMBOL, _amount, depositor);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Address bnUSDAddress = bnUSD.get();

        Context.require(TokenUtils.balanceOf(bnUSDAddress, from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");

        BigInteger totalBadDebt = BigInteger.ZERO;
        BigInteger remainingValue = _value;
        for (String collateralSymbol : CollateralManager.getCollateral().keySet()) {
            BigInteger badDebt = DebtManager.getBadDebt(collateralSymbol);
            if (badDebt.equals(BigInteger.ZERO)) {
                continue;
            }

            BigInteger badDebtAmount = badDebt.min(remainingValue);
            TokenUtils.burnFrom(bnUSDAddress, from, badDebtAmount);
            BigInteger collateralToRedeem = badDebtRedeem(from, collateralSymbol, badDebtAmount);
            transferCollateral(collateralSymbol, from, collateralToRedeem, "Bad Debt redeemed.", new byte[0]);

            remainingValue = remainingValue.subtract(badDebtAmount);
            totalBadDebt = totalBadDebt.add(badDebtAmount);
            if (remainingValue.equals(BigInteger.ZERO)) {
                break;
            }
        }

        Context.require(totalBadDebt.compareTo(BigInteger.ZERO) > 0, TAG + ": No bad debt for " + BNUSD_SYMBOL);
        Context.require(_value.compareTo(totalBadDebt) >= 0, TAG + "Cannot retire more debt than value");

        BadDebtRetired(from, BNUSD_SYMBOL, totalBadDebt);
    }

    @External
    public void retireBadDebtForCollateral(String _symbol, BigInteger _value, String _collateralSymbol) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Address bnUSDAddress = bnUSD.get();

        Context.require(TokenUtils.balanceOf(bnUSDAddress, from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");

        BigInteger badDebt = DebtManager.getBadDebt(_collateralSymbol);
        Context.require(badDebt.compareTo(BigInteger.ZERO) > 0, TAG + ": No bad debt for " + BNUSD_SYMBOL);

        BigInteger badDebtRedeemed = badDebt.min(_value);
        Context.require(badDebtRedeemed.compareTo(BigInteger.ZERO) >= 0, TAG + ": Amount retired must be greater than" +
                " zero.");
        TokenUtils.burnFrom(bnUSDAddress, from, badDebtRedeemed);

        BigInteger collateralToRedeem = badDebtRedeem(from, _collateralSymbol, badDebtRedeemed);

        transferCollateral(_collateralSymbol, from, collateralToRedeem, "Bad Debt redeemed.", new byte[0]);
        BadDebtRetired(from, _symbol, badDebtRedeemed);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, @Optional String _collateralSymbol) {
        loansOn();
        Context.require(_symbol.equals(BNUSD_SYMBOL));
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Address bnUSDAddress = bnUSD.get();

        Context.require(TokenUtils.balanceOf(bnUSDAddress, from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": No debt repaid because, " + from + " does not have a " +
                "position in Balanced");

        BigInteger oldSupply = DebtManager.getTotalDebt();
        Position position = PositionsDB.getPosition(from);
        BigInteger oldUserDebt = position.getTotalDebt();
        BigInteger borrowed = position.getDebt(collateralSymbol);

        Context.require(_value.compareTo(borrowed) <= 0, TAG + ": Repaid amount is greater than the amount in the " +
                "position of " + from);

        BigInteger remaining = borrowed.subtract(_value);
        BigInteger repaid;
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            position.setDebt(collateralSymbol, remaining);
            repaid = _value;
        } else {
            position.setDebt(collateralSymbol, null);
            repaid = borrowed;
        }

        TokenUtils.burnFrom(bnUSDAddress, from, repaid);

        Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, oldUserDebt);

        String logMessage = "Loan of " + repaid + " " + BNUSD_SYMBOL + " repaid to Balanced.";
        LoanRepaid(from, BNUSD_SYMBOL, repaid, logMessage);
    }

    @External
    public void raisePrice(Address _collateralAddress, BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);
        String collateralSymbol = TokenUtils.symbol(_collateralAddress);
        Context.require(CollateralManager.getAddress(collateralSymbol).equals(_collateralAddress),
                collateralSymbol + " is not a supported collateral type.");

        Address bnUSDAddress = bnUSD.get();

        BigInteger oldTotalDebt = DebtManager.getTotalDebt();
        int batchSize = redeemBatch.get();

        BigInteger poolID = Context.call(BigInteger.class, dex.get(), "getPoolId", _collateralAddress, bnUSDAddress);
        BigInteger rate = Context.call(BigInteger.class, dex.get(), "getBasePriceInQuote", poolID);

        PositionBatch batch = DebtManager.getBorrowers(collateralSymbol).readDataBatch(batchSize);
        Map<Integer, BigInteger> positionsMap = batch.positions;

        BigInteger collateralToSell =
                maxRetirePercent.get().multiply(batch.totalDebt).multiply(EXA).divide(POINTS.multiply(rate));
        collateralToSell = collateralToSell.min(_total_tokens_required);

        expectedToken.set(bnUSDAddress);
        byte[] data = createSwapData(bnUSDAddress);
        transferCollateral(collateralSymbol, dex.get(), collateralToSell,
                collateralSymbol + " swapped for " + BNUSD_SYMBOL, data);
        BigInteger bnUSDReceived = amountReceived.get();
        amountReceived.set(null);

        TokenUtils.burnFrom(bnUSDAddress, Context.getAddress(), bnUSDReceived);

        BigInteger remainingSupply = batch.totalDebt;
        BigInteger remainingBnUSD = bnUSDReceived;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[batch.size];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.uncheckedGet(id);

            BigInteger loanShare = remainingBnUSD.multiply(userDebt).divide(remainingSupply);
            remainingBnUSD = remainingBnUSD.subtract(loanShare);

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = position.getTotalDebt();
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger collateralShare = collateralToSell.multiply(userDebt).divide(remainingSupply);
            collateralToSell = collateralToSell.subtract(collateralShare);

            position.setDebt(collateralSymbol, userDebt.subtract(loanShare));
            position.setCollateral(collateralSymbol,
                    position.getCollateral(collateralSymbol).subtract(collateralShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
                    "'d': " + loanShare.negate() + ", " +
                    "'c': " + collateralShare.negate() + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length() - 2, changeLog.length()).append("}");
        Rebalance(Context.getCaller(), BNUSD_SYMBOL, changeLog.toString(), batch.totalDebt);
    }

    @External
    public void lowerPrice(Address _collateralAddress, BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);
        Address bnUSDAddress = bnUSD.get();

        String collateralSymbol = TokenUtils.symbol(_collateralAddress);

        Context.require(CollateralManager.getAddress(collateralSymbol).equals(_collateralAddress),
                collateralSymbol + " is not a supported collateral type.");

        BigInteger oldTotalDebt = DebtManager.getTotalDebt();
        int batchSize = redeemBatch.get();

        PositionBatch batch = DebtManager.getBorrowers(collateralSymbol).readDataBatch(batchSize);
        Map<Integer, BigInteger> positionsMap = batch.positions;

        BigInteger bnUSDToSell = maxRetirePercent.get().multiply(batch.totalDebt).divide(POINTS);
        bnUSDToSell = bnUSDToSell.min(_total_tokens_required);

        expectedToken.set(bnUSDAddress);
        TokenUtils.mintTo(bnUSDAddress, Context.getAddress(), bnUSDToSell);
        amountReceived.set(null);

        expectedToken.set(_collateralAddress);
        byte[] data = createSwapData(_collateralAddress);
        transferAsset(bnUSDAddress, dex.get(), bnUSDToSell, BNUSD_SYMBOL + " swapped for " + collateralSymbol, data);
        BigInteger receivedCollateral = amountReceived.get();
        amountReceived.set(null);

        BigInteger remainingCollateral = receivedCollateral;
        BigInteger remainingSupply = batch.totalDebt;
        BigInteger remainingBnUSD = bnUSDToSell;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[batch.size];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.uncheckedGet(id);

            BigInteger loanShare = remainingBnUSD.multiply(userDebt).divide(remainingSupply);
            remainingBnUSD = remainingBnUSD.subtract(loanShare);

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = position.getTotalDebt();
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger collateralShare = remainingCollateral.multiply(userDebt).divide(remainingSupply);
            remainingCollateral = remainingCollateral.subtract(collateralShare);

            position.setDebt(collateralSymbol, userDebt.add(loanShare));
            position.setCollateral(collateralSymbol, position.getCollateral(collateralSymbol).add(collateralShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
                    "'d': " + loanShare + ", " +
                    "'c': " + collateralShare + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length() - 2, changeLog.length()).append("}");
        Rebalance(Context.getCaller(), BNUSD_SYMBOL, changeLog.toString(), batch.totalDebt);
    }

    @External
    public void withdrawAndUnstake(BigInteger _value) {
        loansOn();
        Address from = Context.getCaller();
        removeCollateral(from, _value, SICX_SYMBOL);

        JsonObject data = new JsonObject();
        data.set("method", "unstake");
        data.set("user", from.toString());
        transferCollateral(SICX_SYMBOL, staking.get(), _value, "SICX Collateral withdrawn and unstaked",
                data.toString().getBytes());
    }

    @External
    public void withdrawCollateral(BigInteger _value, @Optional String _collateralSymbol) {
        loansOn();
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        Address from = Context.getCaller();
        removeCollateral(from, _value, collateralSymbol);
        transferCollateral(collateralSymbol, from, _value, "Collateral withdrawn.", new byte[0]);
    }

    @External
    public void sellCollateral(BigInteger collateralAmountToSell, String collateralSymbol,
                               BigInteger minimumDebtRepaid) {
        loansOn();
        Address from = Context.getCaller();
        sellUserCollateral(from, collateralAmountToSell, collateralSymbol, minimumDebtRepaid);
    }

    @External
    public void liquidate(Address _owner, @Optional String _collateralSymbol) {
        loansOn();
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        Context.require(PositionsDB.hasPosition(_owner), TAG + ": This address does not have a position on Balanced.");
        Position position = PositionsDB.getPosition(_owner);
        Standings standing = position.getStanding(collateralSymbol, false).standing;

        if (standing != Standings.LIQUIDATE) {
            return;
        }

        BigInteger collateral = position.getCollateral(collateralSymbol);
        BigInteger reward = collateral.multiply(liquidationReward.get()).divide(POINTS);
        BigInteger forPool = collateral.subtract(reward);
        BigInteger totalDebt = position.totalDebtInLoop(collateralSymbol, false);
        BigInteger oldTotalDebt = DebtManager.getTotalDebt();
        BigInteger debt = position.getDebt(collateralSymbol);
        BigInteger oldUserDebt = position.getTotalDebt();

        if (debt.compareTo(BigInteger.ZERO) > 0) {
            BigInteger badDebt = DebtManager.getBadDebt(collateralSymbol);
            DebtManager.setBadDebt(collateralSymbol, badDebt.add(debt));
            BigInteger symbolDebt = debt.multiply(TokenUtils.getBnUSDPriceInLoop()).divide(EXA);
            BigInteger share = forPool.multiply(symbolDebt).divide(totalDebt);
            totalDebt = totalDebt.subtract(symbolDebt);
            forPool = forPool.subtract(share);
            DebtManager.setLiquidationPool(collateralSymbol, DebtManager.getLiquidationPool(collateralSymbol).add(share));
            position.setDebt(collateralSymbol, null);
            Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, _owner, oldUserDebt);
        }

        position.setCollateral(collateralSymbol, null);
        transferCollateral(collateralSymbol, Context.getCaller(), reward, "Liquidation reward of", new byte[0]);

        String logMessage = collateral + " liquidated from " + _owner;
        Liquidate(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, String collateralSymbol, BigInteger badDebtAmount) {

        Address collateralAddress = CollateralManager.getAddress(collateralSymbol);

        BigInteger collateralDecimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());
        BigInteger bnUSDPriceInLoop = TokenUtils.getBnUSDPriceInLoop();
        BigInteger collateralPriceInLoop = TokenUtils.getPriceInLoop(collateralSymbol, false);
        BigInteger inPool = DebtManager.getLiquidationPool(collateralSymbol);
        BigInteger badDebt = DebtManager.getBadDebt(collateralSymbol).subtract(badDebtAmount);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtCollateral =
                bonus.multiply(badDebtAmount).multiply(bnUSDPriceInLoop).multiply(collateralDecimals).
                        divide(collateralPriceInLoop.multiply(POINTS).multiply(EXA));

        DebtManager.setBadDebt(collateralSymbol, badDebt);
        if (inPool.compareTo(badDebtCollateral) >= 0) {
            DebtManager.setLiquidationPool(collateralSymbol, inPool.subtract(badDebtCollateral));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferCollateral(collateralSymbol, reserve.get(), inPool.subtract(badDebtCollateral), "Sweep to " +
                        "ReserveFund:", new byte[0]);
                DebtManager.setLiquidationPool(collateralSymbol, null);
            }

            return badDebtCollateral;
        }

        DebtManager.setLiquidationPool(collateralSymbol, null);
        BigInteger remainingCollateral = badDebtCollateral.subtract(inPool);
        BigInteger remainingValue = remainingCollateral.multiply(collateralPriceInLoop).divide(collateralDecimals);
        Context.call(reserve.get(), "redeem", from, remainingValue, collateralSymbol);

        return inPool;
    }

    private void depositCollateral(String _symbol, BigInteger _amount, Address _from) {
        Position position = PositionsDB.getPosition(_from);

        position.setCollateral(_symbol, position.getCollateral(_symbol).add(_amount));
        CollateralReceived(_from, _symbol, _amount);
    }

    private void sellUserCollateral(Address from, BigInteger collateralToSell, String collateralSymbol,
                                    BigInteger minimumDebtToRepay) {
        Context.require(collateralToSell.compareTo(BigInteger.ZERO) > 0, TAG + ": Sell amount must be more than zero.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        Position position = PositionsDB.getPosition(from);
        BigInteger userCollateral = position.getCollateral(collateralSymbol);

        Context.require(userCollateral.compareTo(collateralToSell) >= 0, TAG + ": Position holds less " +
                "collateral than the requested sell.");

        Address bnUSDAddress = bnUSD.get();
        BigInteger oldSupply = DebtManager.getTotalDebt();
        BigInteger oldUserDebt = position.getTotalDebt();
        BigInteger userDebt = position.getDebt(collateralSymbol);

        Address collateralAddress = CollateralManager.getAddress(collateralSymbol);
        Address dexAddress = dex.get();

        amountReceived.set(null);
        BigInteger poolID = Context.call(BigInteger.class, dexAddress, "getPoolId", collateralAddress, bnUSDAddress);
        Context.require(poolID != null, TAG + ": There doesn't exist a bnUSD pool for " + collateralSymbol + ".");

        Context.require(userDebt.compareTo(minimumDebtToRepay) >= 0, TAG + ": Minimum receive cannot be greater than " +
                "your debt.");
        expectedToken.set(bnUSDAddress);

        JsonObject swapParams = Json.object()
                .add("toToken", bnUSDAddress.toString())
                .add("minimumReceive", minimumDebtToRepay.toString());
        JsonObject swapData = Json.object()
                .add("method", "_swap")
                .add("params", swapParams);
        byte[] data = swapData.toString().getBytes();

        transferCollateral(collateralSymbol, dexAddress, collateralToSell,
                collateralSymbol + " swapped for " + BNUSD_SYMBOL, data);
        BigInteger bnUSDReceived = amountReceived.get();
        amountReceived.set(null);

        Context.require(userDebt.compareTo(bnUSDReceived) >= 0, TAG + ": Cannot sell collateral worth more than your " +
                "debt.");

        BigInteger remainingDebt = userDebt.subtract(bnUSDReceived);
        BigInteger remainingCollateral = userCollateral.subtract(collateralToSell);

        position.setCollateral(collateralSymbol, remainingCollateral);

        if (remainingDebt.compareTo(BigInteger.ZERO) > 0) {
            position.setDebt(collateralSymbol, remainingDebt);
        } else {
            position.setDebt(collateralSymbol, null);
        }

        TokenUtils.burnFrom(bnUSD.get(), Context.getAddress(), bnUSDReceived);

        Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, oldUserDebt);

        String logMessage = "Loan of " + bnUSDReceived + " " + BNUSD_SYMBOL + " sold for" + collateralToSell + " " +
                collateralSymbol + " to Balanced.";
        CollateralSold(from, BNUSD_SYMBOL, collateralSymbol, bnUSDReceived, logMessage);
    }

    private void removeCollateral(Address from, BigInteger value, String collateralSymbol) {
        Context.require(value.compareTo(BigInteger.ZERO) > 0, TAG + ": Withdraw amount must be more than zero.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        Position position = PositionsDB.getPosition(from);

        Context.require(position.getCollateral(collateralSymbol).compareTo(value) >= 0, TAG + ": Position holds less " +
                "collateral than the requested withdrawal.");
        BigInteger debtValue = position.totalDebtInLoop(collateralSymbol, false);
        BigInteger remainingCollateral = position.getCollateral(collateralSymbol).subtract(value);

        Address collateralAddress = CollateralManager.getAddress(collateralSymbol);
        BigInteger collateralDecimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());

        BigInteger remainingCollateralInLoop =
                remainingCollateral.multiply(TokenUtils.getPriceInLoop(collateralSymbol, false)).divide(collateralDecimals);

        BigInteger lockingValue = getLockingRatio(collateralSymbol).multiply(debtValue).divide(POINTS);
        Context.require(remainingCollateralInLoop.compareTo(lockingValue) >= 0,
                TAG + ": Requested withdrawal is more than available collateral. " +
                        "total debt value: " + debtValue + " ICX " +
                        "remaining collateral value: " + remainingCollateralInLoop + " ICX " +
                        "locking value (max debt): " + lockingValue + " ICX"
        );

        position.setCollateral(collateralSymbol, remainingCollateral);
    }

    private void originateLoan(String collateralSymbol, BigInteger amount, Address from) {

        Position position = PositionsDB.getPosition(from, true);
        BigInteger oldTotalDebt = DebtManager.getTotalDebt();

        BigInteger collateral = position.totalCollateralInLoop(collateralSymbol, false);
        BigInteger lockingRatio = getLockingRatio(collateralSymbol);
        Context.require(lockingRatio != null && lockingRatio.compareTo(BigInteger.ZERO) > 0,
                "Locking ratio for " + collateralSymbol + " is not set");
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(lockingRatio);
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);

        Address bnUSDAddress = bnUSD.get();
        BigInteger bnUSDPriceInLoop = TokenUtils.getBnUSDPriceInLoop();

        BigInteger newDebt = amount.add(fee);
        BigInteger newDebtValue = bnUSDPriceInLoop.multiply(newDebt).divide(EXA);
        BigInteger holdings = position.getDebt(collateralSymbol);
        if (holdings.equals(BigInteger.ZERO)) {
            BigInteger dollarValue = newDebtValue.multiply(EXA).divide(bnUSDPriceInLoop);
            Context.require(dollarValue.compareTo(newLoanMinimum.get()) >= 0, TAG + ": The initial loan of any " +
                    "asset must have a minimum value of " + newLoanMinimum.get().divide(EXA) + " dollars.");
            if (!DebtManager.getBorrowers(collateralSymbol).contains(position.getId())) {
                DebtManager.getBorrowers(collateralSymbol).append(newDebt, position.getId());
            }
        }

        BigInteger totalDebt = position.totalDebtInLoop(collateralSymbol, false);
        Context.require(totalDebt.add(newDebtValue).compareTo(maxDebtValue) <= 0,
                TAG + ": " + collateral + " collateral is insufficient" +
                        " to originate a loan of " + amount + " " + BNUSD_SYMBOL +
                        " when max_debt_value = " + maxDebtValue + "," +
                        " new_debt_value = " + newDebtValue + "," +
                        " which includes a fee of " + fee + " " + BNUSD_SYMBOL + "," +
                        " given an existing loan value of " + totalDebt + ".");

        BigInteger oldUserDebt = position.getTotalDebt();
        position.setDebt(collateralSymbol, holdings.add(newDebt));
        Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, from, oldUserDebt);

        TokenUtils.mintTo(bnUSDAddress, from, amount);
        String logMessage = "Loan of " + amount + " " + BNUSD_SYMBOL + " from Balanced.";
        OriginateLoan(from, BNUSD_SYMBOL, amount, logMessage);
        TokenUtils.mintTo(bnUSDAddress, Context.call(Address.class, governance.get(), "getContractAddress", "feehandler"), fee);
        FeePaid(BNUSD_SYMBOL, fee, "origination");
    }

    private void transferCollateral(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(CollateralManager.getAddress(tokenSymbol), "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString() + " " + tokenSymbol + " sent to " + to;
        TokenTransfer(to, amount, logMessage);
    }

    private void transferAsset(Address address, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(address, "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString() + " " + TokenUtils.symbol(address) + " sent to " + to;
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

    private BigInteger stakeICX(BigInteger amount) {
        if (amount.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        expectedToken.set(CollateralManager.getAddress(SICX_SYMBOL));
        Context.call(amount, staking.get(), "stakeICX", Context.getAddress(), new byte[0]);

        BigInteger received = amountReceived.getOrDefault(BigInteger.ZERO);
        Context.require(!received.equals(BigInteger.ZERO), TAG + ": Expected sICX not received.");
        amountReceived.set(null);

        return received;
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
    public void setOracle(Address _address) {
        only(admin);
        isContract(_address);
        oracle.set(_address);
    }

    @External(readonly = true)
    public Address getOracle() {
        return oracle.get();
    }

    @External
    public void setBnusd(Address _address) {
        bnUSD.set(_address);
    }

    @External(readonly = true)
    public Address getBnusd() {
        return bnUSD.get();
    }

    @External
    public void setLockingRatio(String _symbol, BigInteger _ratio) {
        only(admin);
        Context.require(_ratio.compareTo(BigInteger.ZERO) > 0, "Locking Ratio has to be greater than 0");
        lockingRatio.set(_symbol, _ratio);
    }

    @External(readonly = true)
    public BigInteger getLockingRatio(String _symbol) {
        return lockingRatio.get(_symbol);
    }

    @External
    public void setLiquidationRatio(String _symbol, BigInteger _ratio) {
        only(admin);
        Context.require(_ratio.compareTo(BigInteger.ZERO) > 0, "Liquidation Ratio has to be greater than 0");
        liquidationRatio.set(_symbol, _ratio);
    }

    @External(readonly = true)
    public BigInteger getLiquidationRatio(String _symbol) {
        return liquidationRatio.get(_symbol);
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
    public void setDebtCeiling(String symbol, BigInteger ceiling) {
        only(admin);
        DebtManager.setDebtCeiling(symbol, ceiling);
    }

    @External(readonly = true)
    public BigInteger getDebtCeiling(String symbol) {
        return DebtManager.getDebtCeiling(symbol);
    }

    @External(readonly = true)
    public BigInteger getTotalDebt(@Optional String assetSymbol) {
        return DebtManager.getTotalDebt();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateralDebt(String collateral, @Optional String assetSymbol) {
        return DebtManager.getCollateralDebt(collateral);
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
        parameters.put("locking ratio", lockingRatio.get(SICX_SYMBOL));
        parameters.put("liquidation ratio", liquidationRatio.get(SICX_SYMBOL));
        parameters.put("origination fee", originationFee.get());
        parameters.put("redemption fee", redemptionFee.get());
        parameters.put("liquidation reward", liquidationReward.get());
        parameters.put("new loan minimum", newLoanMinimum.get());
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
    public void CollateralSold(Address account, String assetSymbol, String collateralSymbol, BigInteger amount,
                               String note) {
    }

    @EventLog(indexed = 3)
    public void BadDebtRetired(Address account, String symbol, BigInteger amount) {
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
}
