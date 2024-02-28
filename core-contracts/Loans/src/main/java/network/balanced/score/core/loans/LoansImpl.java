/*
 * Copyright (c) 2022-2023 Balanced.network.
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
import network.balanced.score.core.loans.collateral.CollateralDB;
import network.balanced.score.core.loans.debt.DebtDB;
import network.balanced.score.core.loans.positions.Position;
import network.balanced.score.core.loans.positions.PositionsDB;
import network.balanced.score.core.loans.utils.PositionBatch;
import network.balanced.score.core.loans.utils.TokenUtils;
import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.interfaces.LoansXCall;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.BalancedFloorLimits;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.FloorLimited;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.XCallUtils;
import score.Address;
import score.Context;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.loans.LoansVariables.loansOn;
import static network.balanced.score.core.loans.LoansVariables.*;
import static network.balanced.score.core.loans.utils.Checks.loansOn;
import static network.balanced.score.core.loans.utils.LoansConstants.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static network.balanced.score.lib.utils.Math.pow;

public class LoansImpl extends FloorLimited implements Loans {

    public static final String TAG = "BalancedLoans";

    public LoansImpl(Address _governance) {
        if (governance.get() == null) {
            governance.set(_governance);
            loansOn.set(true);
            lockingRatio.set(SICX_SYMBOL, LOCKING_RATIO);
            liquidationRatio.set(SICX_SYMBOL, LIQUIDATION_RATIO);
            originationFee.set(ORIGINATION_FEE);
            redemptionFee.set(REDEMPTION_FEE);
            redemptionDaoFee.set(REDEMPTION_DAO_FEE);
            liquidationReward.set(LIQUIDATION_REWARD);
            retirementBonus.set(BAD_DEBT_RETIREMENT_BONUS);
            newLoanMinimum.set(NEW_BNUSD_LOAN_MINIMUM);
            redeemBatch.set(REDEEM_BATCH_SIZE);
            maxRetirePercent.set(MAX_RETIRE_PERCENT);
        }
        if (getAddressByName(Names.GOVERNANCE) == null) {
            setGovernance(_governance);
        }

        DebtDB.migrate();

        if (currentVersion.getOrDefault("").equals(Versions.LOANS)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.LOANS);
    }

    @External(readonly = true)
    public String name() {
        return Names.LOANS;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
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
        onlyGovernance();
        Context.call(getStaking(), "delegate", (Object) prepDelegations);
    }

    @External(readonly = true)
    public String getPositionAddress(int _index) {
        return PositionsDB.get(_index).getAddress();
    }

    @External(readonly = true)
    public Map<String, String> getAssetTokens() {
        Map<String, String> assetAndCollateral = new HashMap<>();
        assetAndCollateral.put(BNUSD_SYMBOL, getBnusd().toString());
        assetAndCollateral.putAll(CollateralDB.getCollateral());

        return assetAndCollateral;
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
    public Map<String, Object> getAccountPositions(String _owner) {
        Context.require(PositionsDB.hasPosition(_owner), _owner + " does not have a position in Balanced");
        return PositionsDB.listPosition(_owner);
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getAvailableAssets() {
        return Map.of(BNUSD_SYMBOL, DebtDB.debtData());
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
    public boolean hasDebt(String _owner) {
        return PositionsDB.getPosition(_owner).hasDebt();
    }

    @External(readonly = true)
    public int getBorrowerCount(Address collateralAddress) {
        String symbol = CollateralDB.getSymbol(collateralAddress);
        return DebtDB.getBorrowers(symbol).size();
    }

    @External(readonly = true)
    public List<Map<String, Object>> getBorrowers(Address collateralAddress, int nrOfPositions,
                                                  @Optional int startId) {
        return DebtDB.getBorrowers(collateralAddress, nrOfPositions, startId);
    }

    @External(readonly = true)
    public int getBorrowerHead(Address collateralAddress) {
        String symbol = CollateralDB.getSymbol(collateralAddress);
        return DebtDB.getBorrowers(symbol).getHeadId();
    }

    @External(readonly = true)
    public int getBorrowerTail(Address collateralAddress) {
        String symbol = CollateralDB.getSymbol(collateralAddress);
        return DebtDB.getBorrowers(symbol).getTailId();
    }

    // only adds collateral, name is kept for backwards compatibility
    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        onlyGovernance();
        if (_collateral) {
            String symbol = TokenUtils.symbol(_token_address);
            CollateralDB.addCollateral(_token_address, symbol);
            AssetAdded(_token_address, symbol, _collateral);
        }
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, String _owner) {
        Context.require(_name.equals("Loans"), TAG + ": Unsupported data source name");

        BigInteger totalSupply = DebtDB.getTotalDebt();

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
        return DebtDB.getTotalDebt();
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        checkStatus();

        Context.require(_value.signum() > 0, TAG + ": Token value should be a positive number");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");
        JsonObject json = Json.parse(unpackedData).asObject();
        Address token = Context.getCaller();

        if (token.equals(getBnusd())) {
            String collateralSymbol = json.get("_collateral").asString();
            JsonValue withdrawAmount = json.get("_withdrawAmount");
            BigInteger collateralToWithdraw = convertToNumber(withdrawAmount, BigInteger.ZERO);
            TokenUtils.burnAsset(_value);
            _returnAsset(_from, _value, collateralSymbol);
            if (BigInteger.ZERO.compareTo(collateralToWithdraw) < 0) {
                xWithdraw(_from, collateralToWithdraw, collateralSymbol);
            }
        } else {
            String collateralSymbol = CollateralDB.getSymbol(token);
            JsonValue amount = json.get("_amount");
            BigInteger requestedAmount = convertToNumber(amount, BigInteger.ZERO);

            depositCollateral(collateralSymbol, _value, _from);
            if (BigInteger.ZERO.compareTo(requestedAmount) < 0) {
                originateLoan(collateralSymbol, requestedAmount, _from);
            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        checkStatus();

        Context.require(_value.signum() > 0, TAG + ": Token value should be a positive number");

        Address token = Context.getCaller();
        if (token.equals(expectedToken.get())) {
            amountReceived.set(_value);
            expectedToken.set(null);
            return;
        }

        if (_from.equals(EOA_ZERO)) {
            return;
        }

        if (_from.equals(getReserve())) {
            return;
        }

        String collateralSymbol = CollateralDB.getSymbol(token);
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        JsonValue amount = json.get("_amount");
        BigInteger requestedAmount = convertToNumber(amount, BigInteger.ZERO);

        depositCollateral(collateralSymbol, _value, _from.toString());
        if (BigInteger.ZERO.compareTo(requestedAmount) < 0) {
            originateLoan(collateralSymbol, requestedAmount, _from.toString());
        }
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        LoansXCall.process(this, _from, _data);
    }

    public void xBorrow(String from, String _collateralToBorrowAgainst, BigInteger _amountToBorrow) {
        originateLoan(_collateralToBorrowAgainst, _amountToBorrow, from);
    }

    public void xWithdraw(String from, BigInteger _value, String _collateralSymbol) {
        removeCollateral(from, _value, _collateralSymbol);
        Address token = CollateralDB.getAddress(_collateralSymbol);
        String fromNet = NetworkAddress.valueOf(from).net();
        String nativeAddress = Context.call(String.class, getAssetManager(), "getNativeAssetAddress", token, fromNet);
        if (nativeAddress != null && canWithdraw(fromNet) ) {
            BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", fromNet, true);
            Context.call(xCallFee, getAssetManager(), "withdrawTo", token, from, _value);
        } else {
            Context.call(token, "hubTransfer", from, _value, new byte[0]);
        }
    }

    private boolean canWithdraw(String net) {
        return Context.call(Boolean.class, getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }

    @External
    public void borrow(String _collateralToBorrowAgainst, String _assetToBorrow, BigInteger _amountToBorrow) {
        checkStatus();
        loansOn();
        Context.require(_amountToBorrow.compareTo(BigInteger.ZERO) > 0, TAG + ": _amountToBorrow needs to be larger " +
                "than 0");
        Context.require(_assetToBorrow.equals(BNUSD_SYMBOL));
        originateLoan(_collateralToBorrowAgainst, _amountToBorrow, Context.getCaller().toString());
    }

    @External
    @Payable
    public void depositAndBorrow(@Optional String _asset, @Optional BigInteger _amount, @Optional Address _from,
                                 @Optional BigInteger _value) {
        checkStatus();
        loansOn();
        BigInteger deposit = Context.getValue();
        String depositor = Context.getCaller().toString();

        if (!deposit.equals(BigInteger.ZERO)) {
            Position position = PositionsDB.getPosition(depositor);
            BigInteger sicxDeposited = stakeICX(deposit);
            position.setCollateral(SICX_SYMBOL, position.getCollateral(SICX_SYMBOL).add(sicxDeposited));
            CollateralReceived(depositor, SICX_SYMBOL, sicxDeposited);
        }

        if (_asset == null || !_asset.equals(BNUSD_SYMBOL) || _amount == null
                || _amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        originateLoan(SICX_SYMBOL, _amount, depositor);
    }

    @External
    public void retireBadDebt(String _symbol, BigInteger _value) {
        checkStatus();
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");
        Address from = Context.getCaller();

        Context.require(TokenUtils.balanceOf(getBnusd(), from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");

        BigInteger totalBadDebt = BigInteger.ZERO;
        BigInteger remainingValue = _value;
        for (String collateralSymbol : CollateralDB.getCollateral().keySet()) {
            BigInteger badDebt = DebtDB.getBadDebt(collateralSymbol);
            if (badDebt.equals(BigInteger.ZERO)) {
                continue;
            }

            BigInteger badDebtAmount = badDebt.min(remainingValue);
            TokenUtils.burnAssetFrom(from, badDebtAmount);
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
        checkStatus();
        loansOn();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Address from = Context.getCaller();

        Context.require(TokenUtils.balanceOf(getBnusd(), from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");

        BigInteger badDebt = DebtDB.getBadDebt(_collateralSymbol);
        Context.require(badDebt.compareTo(BigInteger.ZERO) > 0, TAG + ": No bad debt for " + BNUSD_SYMBOL);

        BigInteger badDebtRedeemed = badDebt.min(_value);
        Context.require(badDebtRedeemed.compareTo(BigInteger.ZERO) >= 0, TAG + ": Amount retired must be greater than" +
                " zero.");
        TokenUtils.burnAssetFrom(from, badDebtRedeemed);

        BigInteger collateralToRedeem = badDebtRedeem(from, _collateralSymbol, badDebtRedeemed);

        transferCollateral(_collateralSymbol, from, collateralToRedeem, "Bad Debt redeemed.", new byte[0]);
        BadDebtRetired(from, _symbol, badDebtRedeemed);
    }

    @External
    public void returnAsset(String _symbol, BigInteger _value, @Optional String _collateralSymbol) {
        Address from = Context.getCaller();
        Context.require(_symbol.equals(BNUSD_SYMBOL));
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);

        Context.require(TokenUtils.balanceOf(getBnusd(), from).compareTo(_value) >= 0, TAG + ": Insufficient balance.");
        TokenUtils.burnAssetFrom(from, _value);
        _returnAsset(from.toString(), _value, collateralSymbol);

    }

    private void _returnAsset(String _from, BigInteger _value, String _collateralSymbol) {
        checkStatus();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount retired must be greater than zero.");

        Context.require(PositionsDB.hasPosition(_from), TAG + ": No debt repaid because, " + _from + " does not have a " +
                "position in Balanced");

        Position position = PositionsDB.getPosition(_from);
        BigInteger borrowed = position.getDebt(_collateralSymbol);
        Context.require(_value.compareTo(borrowed) <= 0, TAG + ": Repaid amount is greater than the amount in the " +
                "position of " + _from);

        BigInteger remaining = borrowed.subtract(_value);
        if (remaining.compareTo(BigInteger.ZERO) > 0) {
            position.setDebt(_collateralSymbol, remaining);
        } else {
            position.setDebt(_collateralSymbol, null);
        }

        Context.call(getRewards(), "updateBalanceAndSupply", "Loans", DebtDB.getTotalDebt(), _from, position.getTotalDebt());
        String logMessage = "Loan of " + _value + " " + BNUSD_SYMBOL + " repaid to Balanced.";
        LoanRepaid(_from, BNUSD_SYMBOL, _value, logMessage);
    }

    @External
    public void redeemCollateral(Address _collateralAddress, BigInteger _amount) {
        checkStatus();
        loansOn();
        Address caller = Context.getCaller();
        String collateralSymbol = CollateralDB.getSymbol(_collateralAddress);
        BigInteger daofundFee = redemptionDaoFee.getOrDefault(BigInteger.ZERO).multiply(_amount).divide(POINTS);

        TokenUtils.burnAssetFrom(caller, _amount);
        TokenUtils.mintAssetTo(getDaofund(), daofundFee);
        BigInteger debtToBeRepaid = _amount.subtract(daofundFee);

        BigInteger MAX_REDEMPTION = maxRetirePercent.get();
        BigInteger REDEMPTION_FEE = redemptionFee.get();
        BigInteger debtNeeded = _amount.multiply(POINTS.divide(MAX_REDEMPTION));
        BigInteger collateralRateInUSD = TokenUtils.getPriceInUSD(collateralSymbol);

        PositionBatch batch = DebtDB.getBorrowers(collateralSymbol).readDataBatch(debtNeeded, collateralSymbol);
        Map<Integer, BigInteger> positionsMap = batch.positions;
        StringBuilder changeLog = new StringBuilder("{");
        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[batch.size];
        int dataEntryIndex = 0;
        BigInteger totalCollateralSold = BigInteger.ZERO;
        for (Map.Entry<Integer, BigInteger> entry : positionsMap.entrySet()) {
            int id = entry.getKey();
            Position position = PositionsDB.uncheckedGet(id);
            BigInteger userDebt = entry.getValue();
            BigInteger userCollateral = position.getCollateral(collateralSymbol);

            BigInteger amountRepaid = userDebt.multiply(MAX_REDEMPTION).divide(POINTS);
            if (amountRepaid.compareTo(debtToBeRepaid) > 0) {
                amountRepaid = debtToBeRepaid;
            }

            BigInteger collateralSold = amountRepaid.multiply(EXA).divide(collateralRateInUSD);
            BigInteger fee = REDEMPTION_FEE.multiply(collateralSold).divide(POINTS);
            collateralSold = collateralSold.subtract(fee);
            totalCollateralSold = totalCollateralSold.add(collateralSold);

            position.setDebt(collateralSymbol, userDebt.subtract(amountRepaid));
            position.setCollateral(collateralSymbol, userCollateral.subtract(collateralSold));

            RewardsDataEntry userEntry = new RewardsDataEntry();
            userEntry._user = position.getAddress().toString();
            userEntry._balance = position.getTotalDebt();
            rewardsBatchList[dataEntryIndex] = userEntry;
            dataEntryIndex = dataEntryIndex + 1;

            debtToBeRepaid = debtToBeRepaid.subtract(amountRepaid);
            changeLog.append("'" + id + "': {" +
                    "'d': " + amountRepaid.negate() + ", " +
                    "'c': " + collateralSold.negate() + "}, ");
        }

        Context.call(getRewards(), "updateBalanceAndSupplyBatch", "Loans", DebtDB.getTotalDebt(), rewardsBatchList);
        transferCollateral(collateralSymbol, caller, totalCollateralSold, "bnUSD redeemed for collateral", new byte[0]);
        changeLog.delete(changeLog.length() - 2, changeLog.length()).append("}");
        Rebalance(Context.getCaller(), "bnUSD", changeLog.toString(), _amount);
    }

    @External(readonly = true)
    public BigInteger getRedeemableAmount(Address _collateralAddress, @Optional int nrOfPositions) {
        if (nrOfPositions == 0) {
            nrOfPositions = redeemBatch.get();
        }

        BigInteger MAX_REDEMPTION = maxRetirePercent.get();
        String collateralSymbol = CollateralDB.getSymbol(_collateralAddress);

        BigInteger totalDebt = DebtDB.getBorrowers(collateralSymbol).getTotalDebtFor(nrOfPositions, collateralSymbol);
        BigInteger redeemAmount = totalDebt.multiply(MAX_REDEMPTION).divide(POINTS);

        return redeemAmount;
    }

    @External
    public void withdrawAndUnstake(BigInteger _value) {
        checkStatus();
        loansOn();
        String from = Context.getCaller().toString();
        removeCollateral(from, _value, SICX_SYMBOL);

        JsonObject data = new JsonObject();
        data.set("method", "unstake");
        data.set("user", from);
        transferCollateral(SICX_SYMBOL, getStaking(), _value, "SICX Collateral withdrawn and unstaked",
                data.toString().getBytes());
    }

    @External
    public void withdrawCollateral(BigInteger _value, @Optional String _collateralSymbol) {
        checkStatus();
        loansOn();
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        Address from = Context.getCaller();
        removeCollateral(from.toString(), _value, collateralSymbol);
        transferCollateral(collateralSymbol, from, _value, "Collateral withdrawn.", new byte[0]);
    }

    @External
    public void sellCollateral(BigInteger collateralAmountToSell, String collateralSymbol,
                               BigInteger minimumDebtRepaid) {
        checkStatus();
        loansOn();
        Address from = Context.getCaller();
        sellUserCollateral(from.toString(), collateralAmountToSell, collateralSymbol, minimumDebtRepaid);
    }

    @External
    public void liquidate(String _owner, @Optional String _collateralSymbol) {
        checkStatus();
        loansOn();
        String collateralSymbol = optionalDefault(_collateralSymbol, SICX_SYMBOL);
        Context.require(PositionsDB.hasPosition(_owner), TAG + ": This address does not have a position on Balanced.");
        Position position = PositionsDB.getPosition(_owner);
        Standings standing = position.getStanding(collateralSymbol).standing;

        if (standing != Standings.LIQUIDATE) {
            return;
        }

        BigInteger collateral = position.getCollateral(collateralSymbol);
        BigInteger reward = collateral.multiply(liquidationReward.get()).divide(POINTS);
        BigInteger forPool = collateral.subtract(reward);
        BigInteger totalDebt = position.getDebt(collateralSymbol);
        BigInteger debt = position.getDebt(collateralSymbol);

        if (debt.compareTo(BigInteger.ZERO) > 0) {
            BigInteger badDebt = DebtDB.getBadDebt(collateralSymbol);
            DebtDB.setBadDebt(collateralSymbol, badDebt.add(debt));
            BigInteger share = forPool.multiply(debt).divide(totalDebt);
            totalDebt = totalDebt.subtract(debt);
            forPool = forPool.subtract(share);
            DebtDB.setLiquidationPool(collateralSymbol, DebtDB.getLiquidationPool(collateralSymbol).add(share));
            position.setDebt(collateralSymbol, null);
            Context.call(getRewards(), "updateBalanceAndSupply", "Loans", DebtDB.getTotalDebt(), _owner.toString(), position.getTotalDebt());
        }

        position.setCollateral(collateralSymbol, null);
        transferCollateral(collateralSymbol, Context.getCaller(), reward, "Liquidation reward of", new byte[0]);

        String logMessage = collateral + " liquidated from " + _owner;
        Liquidate(_owner, collateral, logMessage);
    }

    private BigInteger badDebtRedeem(Address from, String collateralSymbol, BigInteger badDebtAmount) {

        Address collateralAddress = CollateralDB.getAddress(collateralSymbol);

        BigInteger collateralDecimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());
        BigInteger collateralPriceInUSD = TokenUtils.getPriceInUSD(collateralSymbol);
        BigInteger inPool = DebtDB.getLiquidationPool(collateralSymbol);
        BigInteger badDebt = DebtDB.getBadDebt(collateralSymbol).subtract(badDebtAmount);

        BigInteger bonus = POINTS.add(retirementBonus.get());
        BigInteger badDebtCollateral =
                bonus.multiply(badDebtAmount).multiply(EXA).multiply(collateralDecimals).
                        divide(collateralPriceInUSD.multiply(POINTS).multiply(EXA));

        DebtDB.setBadDebt(collateralSymbol, badDebt);
        if (inPool.compareTo(badDebtCollateral) >= 0) {
            DebtDB.setLiquidationPool(collateralSymbol, inPool.subtract(badDebtCollateral));
            if (badDebt.equals(BigInteger.ZERO)) {
                transferCollateral(collateralSymbol, getReserve(), inPool.subtract(badDebtCollateral), "Sweep to " +
                        "ReserveFund:", new byte[0]);
                DebtDB.setLiquidationPool(collateralSymbol, null);
            }

            return badDebtCollateral;
        }

        DebtDB.setLiquidationPool(collateralSymbol, null);
        BigInteger remainingCollateral = badDebtCollateral.subtract(inPool);
        BigInteger remainingValue = remainingCollateral.multiply(collateralPriceInUSD).divide(collateralDecimals);
        Context.call(getReserve(), "redeem", from, remainingValue, collateralSymbol);

        return inPool;
    }

    private void depositCollateral(String _symbol, BigInteger _amount, String _from) {
        Position position = PositionsDB.getPosition(_from);

        position.setCollateral(_symbol, position.getCollateral(_symbol).add(_amount));
        CollateralReceived(_from, _symbol, _amount);
    }

    private void sellUserCollateral(String from, BigInteger collateralToSell, String collateralSymbol,
                                    BigInteger minimumDebtToRepay) {
        Context.require(collateralToSell.compareTo(BigInteger.ZERO) > 0, TAG + ": Sell amount must be more than zero.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        Position position = PositionsDB.getPosition(from);
        BigInteger userCollateral = position.getCollateral(collateralSymbol);

        Context.require(userCollateral.compareTo(collateralToSell) >= 0, TAG + ": Position holds less " +
                "collateral than the requested sell.");

        Address bnUSDAddress = getBnusd();
        BigInteger userDebt = position.getDebt(collateralSymbol);

        Address collateralAddress = CollateralDB.getAddress(collateralSymbol);
        Address dexAddress = getDex();

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

        TokenUtils.burnAssetFrom(Context.getAddress(), bnUSDReceived);

        Context.call(getRewards(), "updateBalanceAndSupply", "Loans", DebtDB.getTotalDebt(), from.toString(), position.getTotalDebt());

        String logMessage = "Loan of " + bnUSDReceived + " " + BNUSD_SYMBOL + " sold for" + collateralToSell + " " +
                collateralSymbol + " to Balanced.";
        CollateralSold(from, BNUSD_SYMBOL, collateralSymbol, bnUSDReceived, logMessage);
    }

    private void removeCollateral(String from, BigInteger value, String collateralSymbol) {
        Context.require(value.compareTo(BigInteger.ZERO) > 0, TAG + ": Withdraw amount must be more than zero.");
        Context.require(PositionsDB.hasPosition(from), TAG + ": This address does not have a position on Balanced.");

        Position position = PositionsDB.getPosition(from);

        Context.require(position.getCollateral(collateralSymbol).compareTo(value) >= 0, TAG + ": Position holds less " +
                "collateral than the requested withdrawal.");
        BigInteger debt = position.getDebt(collateralSymbol);
        BigInteger remainingCollateral = position.getCollateral(collateralSymbol).subtract(value);

        Address collateralAddress = CollateralDB.getAddress(collateralSymbol);
        BigInteger collateralDecimals = pow(BigInteger.TEN, TokenUtils.decimals(collateralAddress).intValue());

        BigInteger remainingCollateralInUSD =
                remainingCollateral.multiply(TokenUtils.getPriceInUSD(collateralSymbol)).divide(collateralDecimals);

        BigInteger lockingValue = getLockingRatio(collateralSymbol).multiply(debt).divide(POINTS);
        Context.require(remainingCollateralInUSD.compareTo(lockingValue) >= 0,
                TAG + ": Requested withdrawal is more than available collateral. " +
                        "total debt value: " + debt + " ICX " +
                        "remaining collateral value: " + remainingCollateralInUSD + " ICX " +
                        "locking value (max debt): " + lockingValue + " ICX"
        );

        position.setCollateral(collateralSymbol, remainingCollateral);
    }

    private void originateLoan(String collateralSymbol, BigInteger amount, String from) {
        DebtDB.applyInterest(collateralSymbol);

        Position position = PositionsDB.getPosition(from);
        BigInteger collateral = position.totalCollateralInUSD(collateralSymbol);
        BigInteger lockingRatio = getLockingRatio(collateralSymbol);
        Context.require(lockingRatio != null && lockingRatio.compareTo(BigInteger.ZERO) > 0,
                "Locking ratio for " + collateralSymbol + " is not set");
        BigInteger maxDebtValue = POINTS.multiply(collateral).divide(lockingRatio);
        BigInteger fee = originationFee.get().multiply(amount).divide(POINTS);

        BigInteger newDebt = amount.add(fee);
        BigInteger holdings = position.getDebt(collateralSymbol);
        if (holdings.equals(BigInteger.ZERO)) {
            Context.require(newDebt.compareTo(newLoanMinimum.get()) >= 0, TAG + ": The initial loan of any " +
                    "asset must have a minimum value of " + newLoanMinimum.get().divide(EXA) + " dollars.");
            if (!DebtDB.getBorrowers(collateralSymbol).contains(position.getId())) {
                DebtDB.getBorrowers(collateralSymbol).append(newDebt, position.getId());
            }
        }

        BigInteger totalDebt = position.getDebt(collateralSymbol);
        Context.require(totalDebt.add(newDebt).compareTo(maxDebtValue) <= 0,
                TAG + ": " + collateral + " collateral is insufficient" +
                        " to originate a loan of " + amount + " " + BNUSD_SYMBOL +
                        " when max_debt_value = " + maxDebtValue + "," +
                        " new_debt_value = " + newDebt + "," +
                        " which includes a fee of " + fee + " " + BNUSD_SYMBOL + "," +
                        " given an existing loan value of " + totalDebt + ".");

        position.setDebt(collateralSymbol, holdings.add(newDebt));
        Context.call(getRewards(), "updateBalanceAndSupply", "Loans", DebtDB.getTotalDebt(), from.toString(), position.getTotalDebt());

        originateBnUSD(from, amount, fee);
    }

    private void originateBnUSD(String from, BigInteger amount, BigInteger fee) {
        TokenUtils.mintAsset(amount);
        if (from.contains("/")) {
            String net = NetworkAddress.valueOf(from).net();
            boolean canWithdraw = Context.call(Boolean.class, getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
            if (canWithdraw) {
                BigInteger xCallFee = Context.call(BigInteger.class, getDaofund(), "claimXCallFee", net, true);
                TokenUtils.crossTransfer(xCallFee, from, amount);
            } else {
                TokenUtils.hubTransfer(from, amount);
            }
        } else {
            TokenUtils.transfer(Address.fromString(from), amount);
        }
        String logMessage = "Loan of " + amount + " " + BNUSD_SYMBOL + " from Balanced.";
        OriginateLoan(from, BNUSD_SYMBOL, amount, logMessage);
        TokenUtils.mintAssetTo(getFeehandler(), fee);
        FeePaid(BNUSD_SYMBOL, fee, "origination");
    }

    private void transferCollateral(String tokenSymbol, Address to, BigInteger amount, String msg, byte[] data) {
        Address tokenAddress = CollateralDB.getAddress(tokenSymbol);
        BalancedFloorLimits.verifyWithdraw(tokenAddress, amount);
        Context.call(tokenAddress, "transfer", to, amount, data);
        String logMessage = msg + " " + amount.toString() + " " + tokenSymbol + " sent to " + to;
        TokenTransfer(to, amount, logMessage);
    }

    public static Object call(Address targetAddress, String method, Object... params) {
        return Context.call(targetAddress, method, params);
    }

    private BigInteger stakeICX(BigInteger amount) {
        if (amount.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        expectedToken.set(CollateralDB.getAddress(SICX_SYMBOL));
        Context.call(amount, getStaking(), "stakeICX", Context.getAddress(), new byte[0]);

        BigInteger received = amountReceived.getOrDefault(BigInteger.ZERO);
        Context.require(!received.equals(BigInteger.ZERO), TAG + ": Expected sICX not received.");
        amountReceived.set(null);

        return received;
    }

    @External
    public void setLockingRatio(String _symbol, BigInteger _ratio) {
        onlyGovernance();
        Context.require(_ratio.compareTo(BigInteger.ZERO) > 0, "Locking Ratio has to be greater than 0");
        lockingRatio.set(_symbol, _ratio);
    }

    @External(readonly = true)
    public BigInteger getLockingRatio(String _symbol) {
        return lockingRatio.get(_symbol);
    }

    @External
    public void setLiquidationRatio(String _symbol, BigInteger _ratio) {
        onlyGovernance();
        Context.require(_ratio.compareTo(BigInteger.ZERO) > 0, "Liquidation Ratio has to be greater than 0");
        liquidationRatio.set(_symbol, _ratio);
    }

    @External(readonly = true)
    public BigInteger getLiquidationRatio(String _symbol) {
        return liquidationRatio.get(_symbol);
    }

    @External
    public void setOriginationFee(BigInteger _fee) {
        onlyGovernance();
        originationFee.set(_fee);
    }

    @External
    public void setRedemptionFee(BigInteger _fee) {
        onlyGovernance();
        redemptionFee.set(_fee);
    }

    @External(readonly = true)
    public BigInteger getRedemptionFee() {
        return redemptionFee.get();
    }

    @External
    public void setRedemptionDaoFee(BigInteger _fee) {
        onlyGovernance();
        redemptionDaoFee.set(_fee);
    }

    @External(readonly = true)
    public BigInteger getRedemptionDaoFee() {
        return redemptionDaoFee.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setRetirementBonus(BigInteger _points) {
        onlyGovernance();
        retirementBonus.set(_points);
    }

    @External
    public void setLiquidationReward(BigInteger _points) {
        onlyGovernance();
        liquidationReward.set(_points);
    }

    @External
    public void setNewLoanMinimum(BigInteger _minimum) {
        onlyGovernance();
        newLoanMinimum.set(_minimum);
    }

    @External
    public void setDebtCeiling(String symbol, BigInteger ceiling) {
        onlyGovernance();
        DebtDB.setDebtCeiling(symbol, ceiling);
    }

    @External(readonly = true)
    public BigInteger getDebtCeiling(String symbol) {
        return DebtDB.getDebtCeiling(symbol);
    }

    @External
    public void setInterestRate(String symbol, BigInteger rate) {
        onlyGovernance();
        DebtDB.setInterestRate(symbol, rate);
    }

    @External(readonly = true)
    public BigInteger getInterestRate(String symbol) {
        return DebtDB.getInterestRate(symbol);
    }

    @External
    public void setSavingsRateShare(BigInteger share) {
        onlyGovernance();
        DebtDB.setSavingsRateShare(share);
    }

    @External(readonly = true)
    public BigInteger getSavingsRateShare() {
        return DebtDB.getSavingsRateShare();
    }

    @External
    public void applyInterest() {
        for (String collateralSymbol : CollateralDB.getCollateral().keySet()) {
            DebtDB.applyInterest(collateralSymbol);
        }
    }

    @External
    public void claimInterest() {
        DebtDB.claimInterest();
    }

    @External(readonly = true)
    public BigInteger getTotalDebt(@Optional String assetSymbol) {
        return DebtDB.getTotalDebt();
    }

    @External(readonly = true)
    public BigInteger getTotalCollateralDebt(String collateral, @Optional String assetSymbol) {
        return DebtDB.getCollateralDebt(collateral);
    }

    @External
    public void setTimeOffset(BigInteger deltaTime) {
        onlyGovernance();
        timeOffset.set(deltaTime);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        onlyGovernance();
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0 &&
                        _value.compareTo(BigInteger.valueOf(10_000)) <= 0,
                "Input parameter must be in the range 0 to 10000 points.");
        maxRetirePercent.set(_value);
    }

    @External(readonly = true)
    public BigInteger getMaxRetirePercent() {
        return maxRetirePercent.get();
    }

    @External
    public void setRedeemBatchSize(int _value) {
        onlyGovernance();
        redeemBatch.set(_value);
    }

    @External(readonly = true)
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("locking ratio", lockingRatio.get(SICX_SYMBOL));
        parameters.put("liquidation ratio", liquidationRatio.get(SICX_SYMBOL));
        parameters.put("origination fee", originationFee.get());
        parameters.put("redemption fee", redemptionFee.get());
        parameters.put("liquidation reward", liquidationReward.get());
        parameters.put("new loan minimum", newLoanMinimum.get());
        parameters.put("time offset", timeOffset.getOrDefault(BigInteger.ZERO));
        parameters.put("retire percent max", maxRetirePercent.get());

        return parameters;
    }

    @Payable
    public void fallback() {
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
    public void CollateralReceived(String account, String symbol, BigInteger value) {
    }

    @EventLog(indexed = 3)
    public void OriginateLoan(String recipient, String symbol, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void LoanRepaid(String account, String symbol, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void CollateralSold(String account, String assetSymbol, String collateralSymbol, BigInteger amount,
                               String note) {
    }

    @EventLog(indexed = 3)
    public void BadDebtRetired(Address account, String symbol, BigInteger amount) {
    }

    @EventLog(indexed = 2)
    public void Liquidate(String account, BigInteger amount, String note) {
    }

    @EventLog(indexed = 3)
    public void FeePaid(String symbol, BigInteger amount, String type) {
    }

    @EventLog(indexed = 2)
    public void Rebalance(Address account, String symbol, String change_in_pos, BigInteger total_batch_debt) {
    }
}
