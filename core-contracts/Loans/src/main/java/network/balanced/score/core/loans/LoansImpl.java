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
import network.balanced.score.core.loans.collateral.Collateral;
import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.utils.PositionBatch;
import network.balanced.score.core.loans.utils.Token;
import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.Names;
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
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.convertToNumber;

public class LoansImpl implements Loans {

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
        }

        if (liquidationRatio.get(SICX_SYMBOL) == null) {
            lockingRatio.set(SICX_SYMBOL, lockingRatioSICX.get());
            liquidationRatio.set(SICX_SYMBOL, liquidationRatioSICX.get());
        }

        CollateralDB.migrateToNewDBs();
        AssetDB.migrateToNewDBs();
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
    public List<String> checkDeadMarkets() {
        return AssetDB.getDeadMarkets();
    }

    @External(readonly = true)
    public Address getPositionAddress(int _index) {
        return PositionsDB.get(_index).getAddress();
    }

    @External(readonly = true)
    public Map<String, String> getAssetTokens() {
        Map<String, String> assestAndCollateral = new HashMap<>();
        assestAndCollateral.putAll(AssetDB.getAssets());
        assestAndCollateral.putAll(CollateralDB.getCollateral());

        return assestAndCollateral;
    }

    @External(readonly = true)
    public Map<String, String> getCollateralTokens() {
        return CollateralDB.getCollateral();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateral() {
        return CollateralDB.getTotalCollateral();
    }

    @External(readonly = true)
    public Map<String, Object> getAccountPositions(Address _owner) {
        Context.require(PositionsDB.hasPosition(_owner), _owner + " does not have a position in Balanced");
        return PositionsDB.listPosition(_owner);
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
        return PositionsDB.getPosition(_owner, true).hasDebt();
    }

    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        only(admin);
        Token assetContract = new Token(_token_address);
        String symbol = assetContract.symbol();

        if (_collateral) {
            CollateralDB.addCollateral(_token_address, _active);
        } else {
            AssetDB.addAsset(_token_address, _active);
        }

        lockingRatio.set(symbol, LOCKING_RATIO);
        liquidationRatio.set(symbol, LIQUIDATION_RATIO);
        AssetAdded(_token_address, symbol, _collateral);
    }

    @External
    public void toggleAssetActive(String _symbol) {
        only(admin);
        boolean active;
        if (arrayDbContains(AssetDB.assetList, _symbol)) {
            Asset asset = AssetDB.getAsset(_symbol);
            active = asset.isActive();
            asset.setActive(!active);
        } else {
            Collateral collateral = CollateralDB.getCollateral(_symbol);
            active = collateral.isActive();
            collateral.setActive(!active);
        }
       
        AssetActive(_symbol, active ? "Active" : "Inactive");
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
        BigInteger balance = position.getTotalDebt(BNUSD_SYMBOL);
        return Map.of(
                "_balance", balance,
                "_totalSupply", totalSupply
        );
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        // rewrite to work correctly? or remove and rewrite get APY in Rewards?
        Asset asset = AssetDB.getAsset(BNUSD_SYMBOL);
        Token assetContract = new Token(asset.getAssetAddress());
        BigInteger totalSupply = assetContract.totalSupply();

        return totalSupply.subtract(asset.getBadDebt(SICX_SYMBOL));
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

        Token collateralToken = new Token(token);
        String collateralSymbol = collateralToken.symbol();
        Context.require(CollateralDB.getCollateral(collateralSymbol).isActive(), TAG + ": The Balanced Loans " +
            "contract does not accept that token type.");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String assetToBorrow = json.get("_asset").asString();
        JsonValue amount = json.get("_amount");
        BigInteger requestedAmount = amount == null ? null : convertToNumber(amount);

        depositCollateral(collateralSymbol, _value, _from);
        if (requestedAmount.compareTo(BigInteger.ZERO) > 0) {
            originateLoan(collateralSymbol, assetToBorrow, requestedAmount, _from);
        }
    }

    @External
    public void borrow(String _collateralToBorrowAgainst, String _assetToBorrow, BigInteger _amountToBorrow) {
        loansOn();
        originateLoan(_collateralToBorrowAgainst, _assetToBorrow, _amountToBorrow,  Context.getCaller());
    }

    @External
    @Payable
    public void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from, @Optional BigInteger _value) {
        loansOn();
        BigInteger deposit = Context.getValue();
        Address depositor = Context.getCaller();

        if (!deposit.equals(BigInteger.ZERO)) {
            Position position = PositionsDB.getPosition(depositor);
            BigInteger sicxDeposited = stakeICX(deposit);
            position.setCollateral(SICX_SYMBOL, position.getCollateral(SICX_SYMBOL).add(sicxDeposited));
            CollateralReceived(depositor, SICX_SYMBOL, sicxDeposited);
        }

        if (_asset == null || _asset.equals("") || _amount == null || _amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        originateLoan(SICX_SYMBOL, _asset, _amount, depositor);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();
        Asset asset = AssetDB.getAsset(_symbol);

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(asset.isActive(), TAG + ": " + _symbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
   
        BigInteger totalBadDebt = BigInteger.ZERO;
        BigInteger remaningValue = _value;
        for (String collateralSymbol :  CollateralDB.getCollateral().keySet()) {
            BigInteger badDebt = asset.getBadDebt(collateralSymbol);
            BigInteger badDebtAmount = badDebt.min(remaningValue);

            BigInteger collateralToRedeem = badDebtRedeem(from, collateralSymbol, asset, badDebtAmount);
            transferCollateral(collateralSymbol, from, collateralToRedeem, "Bad Debt redeemed.", new byte[0]);

            remaningValue = remaningValue.subtract(badDebtAmount);
            totalBadDebt = totalBadDebt.add(badDebtAmount);
            if (remaningValue.equals(BigInteger.ZERO)) {
                break;
            }
        }

        Context.require(totalBadDebt.compareTo(BigInteger.ZERO) > 0, TAG + ": No bad debt for " + _symbol);
        Context.require(remaningValue.compareTo(BigInteger.ZERO) >= 0, TAG + ": Amount retired must be greater than zero.");
        //unreachable safeguard
        Context.require(_value.compareTo(totalBadDebt) >= 0, TAG + "Cannot retire more debt that value");

        asset.burnFrom(from, totalBadDebt);
        asset.checkForDeadMarket();
        BadDebtRetired(from, _symbol, totalBadDebt);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, @Optional String _collateralSymbol) {
        loansOn();
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        String assetSymbol = _symbol;
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();

        Asset asset = AssetDB.getAsset(assetSymbol);

        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Context.require(asset.isActive(), TAG + ": " + assetSymbol + " is not an active, borrowable asset on Balanced.");
        Context.require(assetContract.balanceOf(from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": No debt repaid because, " + from + " does not have a " +
                "position in Balanced");

        BigInteger oldSupply = totalDebts.getOrDefault(assetSymbol, BigInteger.ZERO);
        Position position = PositionsDB.getPosition(from);
        BigInteger oldUserDebt = position.getTotalDebt(assetSymbol);
        BigInteger borrowed = position.getDebt(collateralSymbol, assetSymbol);

        Context.require(_value.compareTo(borrowed) <= 0, TAG + ": Repaid amount is greater than the amount in the " +
                "position of " + from);
        if (_value.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BigInteger remaining = borrowed.subtract(_value);
        BigInteger repaid;
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            position.setDebt(collateralSymbol, assetSymbol, remaining);
            repaid = _value;
        } else {
            position.setDebt(collateralSymbol, assetSymbol, null);
            repaid = borrowed;
        }

        asset.burnFrom(from, repaid);

        Context.call(rewards.get(), "updateRewardsData", "Loans", oldSupply, from, oldUserDebt);

        asset.checkForDeadMarket();
        String logMessage = "Loan of " + repaid + " " + assetSymbol + " repaid to Balanced.";
        LoanRepaid(from, assetSymbol, repaid, logMessage);
    }

    @External
    public void raisePrice(String _collateralSymbol, BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);
        String collateralSymbol = _collateralSymbol;
        Address collateralAddress = Address.fromString(CollateralDB.symbolMap.get(collateralSymbol));

        String assetSymbol = BNUSD_SYMBOL;
        Asset asset = AssetDB.getAsset(assetSymbol);
        Address assetAddress = asset.getAssetAddress();

        BigInteger oldTotalDebt = totalDebts.getOrDefault(assetSymbol, BigInteger.ZERO);
        BigInteger poolID = Context.call(BigInteger.class, dex.get(), "getPoolId", collateralAddress, assetAddress);
        BigInteger rate = Context.call(BigInteger.class, dex.get(), "getBasePriceInQuote", poolID);
        int batchSize = redeemBatch.get();
        
        PositionBatch batch = asset.getBorrowers(collateralSymbol).readDataBatch(batchSize);
        Map<Integer, BigInteger> positionsMap = batch.positions;

        BigInteger collateralToSell =
                maxRetirePercent.get().multiply(batch.totalDebt).multiply(EXA).divide(POINTS.multiply(rate));
        collateralToSell = collateralToSell.min(_total_tokens_required);

        expectedToken.set(assetAddress);
        byte[] data = createSwapData(assetAddress);
        transferCollateral(collateralSymbol, dex.get(), collateralToSell, "sICX swapped for bnUSD", data);

        BigInteger bnUSDReceived = amountReceived.get();
        amountReceived.set(null);

        asset.burnFrom(Context.getAddress(), bnUSDReceived);

        BigInteger remainingSupply = batch.totalDebt;
        BigInteger remainingBnusd = bnUSDReceived;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[batch.size];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.uncheckedGet(id);

            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remainingSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = position.getTotalDebt(assetSymbol);
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger collateralShare = collateralToSell.multiply(userDebt).divide(remainingSupply);
            collateralToSell = collateralToSell.subtract(collateralShare);
            
            position.setDebt(collateralSymbol, assetSymbol, userDebt.subtract(loanShare));
            position.setCollateral(collateralSymbol, position.getCollateral(collateralSymbol).subtract(collateralShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
            "'d': " + loanShare.negate() +", " + 
            "'c': " + collateralShare.negate() + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length()-2, changeLog.length()).append("}");

        Rebalance(Context.getCaller(), assetSymbol, changeLog.toString(), batch.totalDebt);
    }

    @External
    public void lowerPrice(String _collateralSymbol, BigInteger _total_tokens_required) {
        loansOn();
        only(rebalancing);
        String collateralSymbol = _collateralSymbol;
        String assetSymbol = BNUSD_SYMBOL;
        Asset asset = AssetDB.getAsset(assetSymbol);

        Collateral collateral = CollateralDB.getCollateral(collateralSymbol);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(assetSymbol, BigInteger.ZERO);
        int batchSize = redeemBatch.get();
        
        PositionBatch batch = asset.getBorrowers(collateralSymbol).readDataBatch(batchSize);
        Map<Integer, BigInteger> positionsMap = batch.positions;

        BigInteger bnusdToSell = maxRetirePercent.get().multiply(batch.totalDebt).divide(POINTS);
        bnusdToSell = bnusdToSell.min(_total_tokens_required);

        Address bnusdAddress = asset.getAssetAddress();
        Token bnusdContract = new Token(bnusdAddress);

        expectedToken.set(bnusdAddress);
        bnusdContract.mintTo(Context.getAddress(), bnusdToSell);
        amountReceived.set(null);

        expectedToken.set(collateral.getAssetAddress());
        byte[] data = createSwapData(collateral.getAssetAddress());
        transferAsset(assetSymbol, dex.get(), bnusdToSell, "bnUSD swapped for sICX", data);
        BigInteger receivedSicx = amountReceived.get();
        amountReceived.set(null);

        BigInteger remainingSicx = receivedSicx;
        BigInteger remainingSupply = batch.totalDebt;
        BigInteger remainingBnusd = bnusdToSell;

        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[batch.size];
        int dataEntryIndex = 0;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            BigInteger userDebt = entry.getValue();
            Position position = PositionsDB.uncheckedGet(id);
            BigInteger loanShare = remainingBnusd.multiply(userDebt).divide(remainingSupply);
            remainingBnusd = remainingBnusd.subtract(loanShare);

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress();
            userEntry._balance = position.getTotalDebt(assetSymbol);
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            BigInteger collateralShare = remainingSicx.multiply(userDebt).divide(remainingSupply);
            remainingSicx = remainingSicx.subtract(collateralShare);
            
            position.setDebt(collateralSymbol, assetSymbol, userDebt.add(loanShare));
            position.setCollateral(collateralSymbol, position.getCollateral(collateralSymbol).add(collateralShare));

            remainingSupply = remainingSupply.subtract(userDebt);
            changeLog.append("'" + id + "': {" +
                "'d': " + loanShare +", " + 
                "'c': " + collateralShare + "}, ");
        }

        Context.call(rewards.get(), "updateBatchRewardsData", "Loans", oldTotalDebt, rewardsBatchList);

        changeLog.delete(changeLog.length()-2, changeLog.length()).append("}");
        Rebalance(Context.getCaller(), assetSymbol, changeLog.toString(), batch.totalDebt);
    }

    @External
    public void withdrawAndUnstake(BigInteger _value) {
        loansOn();
        Address from = Context.getCaller();
        removeCollateral(from, _value, SICX_SYMBOL);

        JsonObject data = new JsonObject();
        data.set("method", "unstake");
        data.set("user", from.toString());
        transferCollateral(SICX_SYMBOL, staking.get(), _value, "SICX Collateral withdrawn and unstaked", data.toString().getBytes());
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
    public void liquidate(Address _owner,  @Optional String _collateralSymbol) {
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
        BigInteger oldTotalDebt = totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);

        int assetSymbolsCount = AssetDB.assetList.size();

        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetList.get(i);
            Asset asset  = AssetDB.getAsset(symbol);
                
            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);
            BigInteger debt = position.getDebt(collateralSymbol, symbol);
            BigInteger oldUserDebt = position.getTotalDebt(symbol);
            if (asset.isActive() && debt.compareTo(BigInteger.ZERO) > 0) {
                Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, _owner, oldUserDebt);

                BigInteger badDebt = asset.getBadDebt(collateralSymbol);
                asset.setBadDebt(collateralSymbol, badDebt.add(debt));
                BigInteger symbolDebt = debt.multiply(assetContract.priceInLoop()).divide(EXA);
                BigInteger share = forPool.multiply(symbolDebt.divide(totalDebt));
                totalDebt = totalDebt.subtract(symbolDebt);
                forPool = forPool.subtract(share);
                asset.setLiquidationPool(collateralSymbol, asset.getLiquidationPool(collateralSymbol).add(share));
                position.setDebt(collateralSymbol, symbol, null);
            }
        }

        position.setCollateral(collateralSymbol, null);
        transferCollateral(collateralSymbol, Context.getCaller(), reward, "Liquidation reward of", new byte[0]);
        AssetDB.updateDeadMarkets();

        String logMessage = collateral + " liquidated from " + _owner;
        Liquidated(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, String collateralSymbol, Asset asset, BigInteger badDebtAmount) {
        Address assetAddress = asset.getAssetAddress();
        Token assetContract = new Token(assetAddress);

        Collateral collateral = CollateralDB.getCollateral(collateralSymbol);
        Address collateralAddress = collateral.getAssetAddress();
        Token collateralContract = new Token(collateralAddress);

        BigInteger assetPriceInLoop = assetContract.priceInLoop();
        BigInteger collateralPriceInLoop = collateralContract.priceInLoop();
        BigInteger inPool = asset.getLiquidationPool(collateralSymbol);
        BigInteger badDebt = asset.getBadDebt(collateralSymbol).subtract(badDebtAmount);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtCollateral = bonus.multiply(badDebtAmount).multiply(assetPriceInLoop).divide(collateralPriceInLoop.multiply(POINTS));

        asset.setBadDebt(collateralSymbol, badDebt);
        if (inPool.compareTo(badDebtCollateral) >= 0) {
            asset.setLiquidationPool(collateralSymbol, inPool.subtract(badDebtCollateral));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferCollateral(collateralSymbol, reserve.get(), inPool.subtract(badDebtCollateral), "Sweep to ReserveFund:",
                        new byte[0]);
            }

            return badDebtCollateral;
        }

        asset.setLiquidationPool(collateralSymbol, null);
        BigInteger remaningCollateral = badDebtCollateral.subtract(inPool);
        BigInteger remaningValue =  remaningCollateral.multiply(collateralPriceInLoop).divide(EXA);
        Context.call(reserve.get(), "redeem", from, remaningValue);
        return inPool;

    }

    private void depositCollateral(String _symbol, BigInteger _amount, Address _from) {
        Position position = PositionsDB.getPosition(_from);

        Token collateralContract = new Token(CollateralDB.getCollateral(_symbol).getAssetAddress());
        BigInteger collateralLimit = collateralLimits.get(_symbol);
        Context.require(collateralLimit == null || collateralContract.balanceOf(Context.getAddress()).compareTo(collateralLimit) <= 0,
                       TAG + ": Collateral safeguard limit for " + _symbol + " has been reached");

        position.setCollateral(_symbol, position.getCollateral(_symbol).add(_amount));
        CollateralReceived(_from, _symbol, _amount);
    }

    private void removeCollateral(Address from, BigInteger value, String collateralSymbol) {
        Context.require(value.compareTo(BigInteger.ZERO) > 0, TAG + ": Withdraw amount must be more than zero.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        Position position = PositionsDB.getPosition(from);

        Context.require(position.getCollateral(collateralSymbol).compareTo(value) >= 0, TAG + ": Position holds less " +
                "collateral than the requested withdrawal.");
        BigInteger assetValue = position.totalDebtInLoop(collateralSymbol, false);
        BigInteger remainingCollateral = position.getCollateral(collateralSymbol).subtract(value);

        Address collateralAddress = CollateralDB.getCollateral(collateralSymbol).getAssetAddress();
        Token collateralContract = new Token(collateralAddress);

        BigInteger remainingCollateralInLoop = remainingCollateral.multiply(collateralContract.priceInLoop()).divide(EXA);

        BigInteger lockingValue = getLockingRatio(collateralSymbol).multiply(assetValue).divide(POINTS);
        Context.require(remainingCollateralInLoop.compareTo(lockingValue) >= 0,
                TAG + ": Requested withdrawal is more than available collateral. " +
                        "total debt value: " + assetValue + " ICX " +
                        "remaining collateral value: " + remainingCollateralInLoop + " ICX " +
                        "locking value (max debt): " + lockingValue + " ICX"
        );

        position.setCollateral(collateralSymbol, remainingCollateral);
    }

    private void originateLoan(String collateralSymbol, String assetToBorrow, BigInteger amount, Address from) {
        Asset asset = AssetDB.getAsset(assetToBorrow);
        Context.require(!asset.checkForDeadMarket(), TAG + ": No new loans of " + assetToBorrow + " can be originated" +
                " since it is in a dead market state.");
        Context.require(asset.isActive(), TAG + ": Loans of inactive assets are not allowed.");

        Position position = PositionsDB.getPosition(from);
        BigInteger oldTotalDebt = totalDebts.getOrDefault(assetToBorrow, BigInteger.ZERO);

        BigInteger collateral = position.totalCollateralInLoop(collateralSymbol, false);
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(getLockingRatio(collateralSymbol));
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);

        Address borrowAssetAddress = asset.getAssetAddress();
        Token borrowAsset = new Token(borrowAssetAddress);

        BigInteger newDebt = amount.add(fee);
        BigInteger newDebtValue = borrowAsset.priceInLoop().multiply(newDebt).divide(EXA);
        BigInteger holdings = position.getDebt(collateralSymbol, assetToBorrow);
        if (holdings.equals(BigInteger.ZERO)) {
            Token bnusd = new Token(AssetDB.getAsset(BNUSD_SYMBOL).getAssetAddress());
            BigInteger dollarValue = newDebtValue.multiply(EXA).divide(bnusd.priceInLoop());
            Context.require(dollarValue.compareTo(newLoanMinimum.get()) >= 0, TAG + ": The initial loan of any " +
                    "asset must have a minimum value of " + newLoanMinimum.get().divide(EXA) + " dollars.");
            if (!AssetDB.getAsset(assetToBorrow).getBorrowers(collateralSymbol).contains(position.getId())) {
                AssetDB.getAsset(assetToBorrow).getBorrowers(collateralSymbol).append(newDebt, position.getId());
            }
        }

        BigInteger totalDebt = position.totalDebtInLoop(collateralSymbol, false);
        Context.require(totalDebt.add(newDebtValue).compareTo(maxDebtValue) <= 0,
                TAG + ": " + collateral + " collateral is insufficient" +
                        " to originate a loan of " + amount + " " + assetToBorrow +
                        " when max_debt_value = " + maxDebtValue + "," +
                        " new_debt_value = " + newDebtValue + "," +
                        " which includes a fee of " + fee + " " + assetToBorrow + "," +
                        " given an existing loan value of " + totalDebt + ".");

        BigInteger oldUserDebt = position.getTotalDebt(assetToBorrow);
        Context.call(rewards.get(), "updateRewardsData", "Loans", oldTotalDebt, from, oldUserDebt);

        position.setDebt(collateralSymbol, assetToBorrow, holdings.add(newDebt));

        borrowAsset.mintTo(from, amount);
        String logMessage = "Loan of " + amount + " " + assetToBorrow + " from Balanced.";
        OriginateLoan(from, assetToBorrow, amount, logMessage);
        borrowAsset.mintTo(Context.call(Address.class, governance.get(), "getContractAddress", "feehandler"), fee);
        FeePaid(assetToBorrow, fee, "origination");
    }

    private void transferAsset(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(AssetDB.getAsset(tokenSymbol).getAssetAddress(), "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString() + " " + tokenSymbol + " sent to " + to;
        TokenTransfer(to, amount, logMessage);
    }

    private void transferCollateral(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Context.call(CollateralDB.getCollateral(tokenSymbol).getAssetAddress(), "transfer", to, amount, data);
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

    private BigInteger stakeICX(BigInteger amount) {
        if (amount.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        expectedToken.set(CollateralDB.getCollateral(SICX_SYMBOL).getAssetAddress());
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
    public void setLockingRatio(String _symbol, BigInteger _ratio) {
        only(admin);
        lockingRatio.set(_symbol, _ratio);
    }

    @External
    public BigInteger getLockingRatio(String _symbol) {
        return lockingRatio.get(_symbol);
    }

    @External
    public void setLiquidationRatio(String _symbol, BigInteger _ratio) {
        only(admin);
        liquidationRatio.set(_symbol, _ratio);
    }
    
    @External
    public BigInteger getLiquidationRatio(String symbol) {
        return liquidationRatio.get(symbol);
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
    public void setCollateralLimit(String symbol, BigInteger limit) {
        only(admin);
        collateralLimits.set(symbol, limit);
    }

    @External(readonly = true)
    public BigInteger getCollateralLimit(String symbol) {
        return collateralLimits.get(symbol);
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
    public void BadDebtRetired(Address account, String symbol, BigInteger amount) {
    }

    @EventLog(indexed = 2)
    public void Liquidated(Address account, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void FeePaid(String symbol, BigInteger amount, String type) {
    }

    @EventLog(indexed = 2)
    public void Rebalance(Address account, String symbol, String change_in_pos, BigInteger total_batch_debt) {
    }
}
