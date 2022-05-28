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

package network.balanced.score.core.dex;

import network.balanced.score.core.dex.db.NodeDB;
import network.balanced.score.lib.interfaces.Dex;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.utils.Check.isValidPoolId;
import static network.balanced.score.core.dex.utils.Const.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.pow;

public abstract class AbstractDex implements Dex {

    public AbstractDex(Address _governance) {
        if (governance.get() == null) {
            governance.set(_governance);

            // Set Default Fee Rates
            poolLpFee.set(BigInteger.valueOf(15L));
            poolBalnFee.set(BigInteger.valueOf(15L));
            icxConversionFee.set(BigInteger.valueOf(70L));
            icxBalnFee.set(BigInteger.valueOf(30L));

            nonce.set(2);
            currentDay.set(BigInteger.valueOf(1L));
            namedMarkets.set(SICXICX_MARKET_NAME, SICXICX_POOL_ID);
            marketsToNames.set(SICXICX_POOL_ID, SICXICX_MARKET_NAME);
        }
    }

    @EventLog(indexed = 2)
    public void Swap(BigInteger _id, Address _baseToken, Address _fromToken, Address _toToken,
                     Address _sender, Address _receiver, BigInteger _fromValue, BigInteger _toValue,
                     BigInteger _timestamp, BigInteger _lpFees, BigInteger _balnFees, BigInteger _poolBase,
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

    @EventLog(indexed = 1)
    public void Snapshot(BigInteger _id) {
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
        quoteCoins.add(_address);
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
    public void setBnusd(Address _address) {
        only(admin);
        isContract(_address);
        bnUSD.set(_address);
        quoteCoins.add(_address);
    }

    @External(readonly = true)
    public Address getBnusd() {
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
    public void setFeeHandler(Address _address) {
        only(admin);
        isContract(_address);
        feeHandler.set(_address);
    }

    @External(readonly = true)
    public Address getFeeHandler() {
        return feeHandler.get();
    }

    @External
    public void setStakedLp(Address _address) {
        only(admin);
        isContract(_address);
        stakedLp.set(_address);
    }

    @External(readonly = true)
    public Address getStakedLp() {
        return stakedLp.get();
    }

    @External
    public void setPoolLpFee(BigInteger _value) {
        only(admin);
        poolLpFee.set(_value);
    }

    @External
    public void setPoolBalnFee(BigInteger _value) {
        only(admin);
        poolBalnFee.set(_value);
    }

    @External
    public void setIcxConversionFee(BigInteger _value) {
        only(admin);
        icxConversionFee.set(_value);
    }

    @External
    public void setIcxBalnFee(BigInteger _value) {
        only(admin);
        icxBalnFee.set(_value);
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        only(admin);
        namedMarkets.set(_name, _id.intValue());
        marketsToNames.set(_id.intValue(), _name);
    }

    @External
    public void turnDexOn() {
        only(admin);
        dexOn.set(true);
    }

    @External(readonly = true)
    public boolean getDexOn() {
        return dexOn.get();
    }

    @External
    public void addQuoteCoin(Address _address) {
        only(admin);
        quoteCoins.add(_address);
    }

    @External(readonly = true)
    public boolean isQuoteCoinAllowed(Address _address) {
        return quoteCoins.contains(_address);
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger timeDelta = blockTime.subtract(timeOffset.get());
        return timeDelta.divide(U_SECONDS_DAY);
    }

    @External
    public void setTimeOffset(BigInteger _delta_time) {
        only(admin);
        timeOffset.set(_delta_time);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.get();
    }

    @External
    public void setContinuousRewardsDay(BigInteger _continuous_rewards_day) {
        only(admin);
        Context.require(_continuous_rewards_day.compareTo(getDay()) > 0, TAG + ": Continuous reward day must be " +
                "greater than current day.");
        continuousRewardsDay.set(_continuous_rewards_day);
    }

    @External(readonly = true)
    public BigInteger getContinuousRewardsDay() {
        return continuousRewardsDay.get();
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, BigInteger _id) {
        if (_id.intValue() == SICXICX_POOL_ID) {
            BigInteger orderId = icxQueueOrderId.get(_owner);
            if (orderId == null) {
                return BigInteger.ZERO;
            }
            return icxQueue.getNode(orderId).getSize();
        } else {
            BigInteger balance = DexDBVariables.balance.at(_id.intValue()).getOrDefault(_owner, BigInteger.ZERO);
            if (stakedLp.get() != null && (continuousRewardsDay.get() == null || getDay().compareTo(continuousRewardsDay.get()) < 0)) {
                BigInteger stakedBalance = (BigInteger) Context.call(stakedLp.get(), "balanceOf", _owner, _id);
                balance = balance.add(stakedBalance);
            }
            return balance;
        }
    }

    @External(readonly = true)
    public Address getPoolBase(BigInteger _id) {
        return poolBase.get(_id.intValue());
    }

    @External(readonly = true)
    public BigInteger getPrice(BigInteger _id) {
        return this.getBasePriceInQuote(_id);
    }

    @External(readonly = true)
    public BigInteger getBasePriceInQuote(BigInteger _id) {
        isValidPoolId(_id);

        if (_id.intValue() == SICXICX_POOL_ID) {
            return getSicxRate();
        }
        Address poolQuoteAddress = poolQuote.get(_id.intValue());
        Address poolBaseAddress = poolBase.get(_id.intValue());

        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(_id.intValue());
        BigInteger poolQuote = totalTokensInPool.get(poolQuoteAddress);
        BigInteger poolBase = totalTokensInPool.get(poolBaseAddress);

        return (poolQuote.multiply(EXA)).divide(poolBase);
    }

    @External(readonly = true)
    public BigInteger totalSupply(BigInteger _id) {
        if (_id.intValue() == SICXICX_POOL_ID) {
            return icxQueueTotal.getOrDefault(BigInteger.ZERO);
        }

        return poolLpTotal.getOrDefault(_id.intValue(), BigInteger.ZERO);
    }

    protected boolean isLockingPool(Integer id) {
        boolean stakedLpLaunched = stakedLp.get() != null;
        boolean restrictedPoolId = (id < FIRST_NON_BALANCED_POOL || id == USDS_BNUSD_ID || id == IUSDT_BNUSD_ID);

        return (restrictedPoolId && !stakedLpLaunched) || id.equals(SICXICX_POOL_ID);
    }

    private Boolean isReentrantTx() {
        boolean reentrancyStatus = false;
        byte[] txHash = Context.getTransactionHash();
        if (Arrays.equals(txHash, currentTx.getOrDefault(new byte[0]))) {
            reentrancyStatus = true;
        } else {
            currentTx.set(txHash);
        }

        return reentrancyStatus;
    }

    protected void revertOnIncompleteRewards() {
        Context.require(rewardsDone.getOrDefault(false), TAG + ": Rewards distribution in progress, " +
                "please try again shortly");
    }

    protected void revertOnWithdrawalLock(Address user, Integer id) {
        BigInteger depositTime = withdrawLock.at(id).get(user);
        if (depositTime.add(WITHDRAW_LOCK_TIMEOUT).compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) > 0) {
            Context.revert(TAG + ":  Assets must remain in the pool for 24 hours, please try again later.");
        }
    }

    protected void exchange(Address fromToken, Address toToken, Address sender,
                            Address receiver, BigInteger value, BigInteger minimumReceive) {

        if (minimumReceive == null) {
            minimumReceive = BigInteger.ZERO;
        }
        BigInteger lpFees = value.multiply(poolLpFee.get()).divide(FEE_SCALE);
        BigInteger balnFees = value.multiply(poolBalnFee.get()).divide(FEE_SCALE);
        BigInteger fees = lpFees.add(balnFees);

        BigInteger newValue = value.subtract(fees);

        boolean isSell = false;


        int id = poolId.at(fromToken).getOrDefault(toToken, 0);

        isValidPoolId(id);

        Context.require(id != SICXICX_POOL_ID, TAG + ":  Not supported on this API, use the ICX swap API.");

        if (fromToken.equals(getPoolBase(BigInteger.valueOf(id)))) {
            isSell = true;
        }

        //TODO(galen): Check with scott if we should even still implement active pools.

        Context.require(active.getOrDefault(id, false), TAG + " Pool is not active");
        // We consider the trade in terms of toToken (token we are trading to), and
        // fromToken (token we are trading away) in the pool. It must obey the xy=k
        // constant product formula.

        BigInteger oldFromToken = poolTotal.at(id).get(fromToken);
        BigInteger oldToToken = poolTotal.at(id).get(toToken);

        // We perturb the pool by the asset we are trading in, less fees.
        // Fees are credited to LPs at the end of the process.
        BigInteger newFromToken = oldFromToken.add(newValue);

        // Compute the new fromToken according to the constant product formula
        BigInteger newToToken = (oldFromToken.multiply(oldToToken)).divide(newFromToken);

        // Send the trader the amount of toToken removed from the pool by the constant
        // product formula
        BigInteger sendAmount = oldToToken.subtract(newToToken);

        // Revert the transaction if the below slippage, as specified in _minimum_receive
        Context.require(sendAmount.compareTo(minimumReceive) >= 0,
                TAG + ": MinimumReceiveError: Receive amount " + sendAmount + " below supplied minimum");

        // Apply fees to fromToken after computing constant product. lpFees
        // are credited to the LPs, the rest are sent to BALN holders.
        newFromToken = newFromToken.add(lpFees);

        // Save updated pool totals
        poolTotal.at(id).set(fromToken, newFromToken);
        poolTotal.at(id).set(toToken, newToToken);

        // Capture details for eventlogs
        BigInteger totalBase = isSell ? newFromToken : newToToken;
        BigInteger totalQuote = isSell ? newToToken : newFromToken;

        BigInteger sendPrice = (EXA.multiply(value)).divide(sendAmount);

        // Send the trader their funds
        Context.call(toToken, "transfer", receiver, sendAmount, null);

        // Send the platform fees to the feehandler SCORE
        Context.call(fromToken, "transfer", feeHandler.get(), balnFees, null);

        // Broadcast pool ending price
        BigInteger endingPrice = this.getPrice(BigInteger.valueOf(id));
        BigInteger effectiveFillPrice = sendPrice;

        if (!isSell) {
            effectiveFillPrice = (EXA.multiply(sendAmount)).divide(newValue);
        }

        if (fromToken.equals(baln.get()) || toToken.equals(baln.get())) {
            updateBalnSnapshot(id);
        }

        Swap(BigInteger.valueOf(id), poolBase.get(id), fromToken, toToken,
                sender, receiver, value,
                sendAmount, BigInteger.valueOf(Context.getBlockTimestamp()),
                lpFees, balnFees, totalBase, totalQuote, endingPrice, effectiveFillPrice);
    }

    protected BigInteger getSicxRate() {
        return (BigInteger) Context.call(staking.get(), "getTodayRate()");
    }

    protected BigInteger getRewardableAmount(Address tokenAddress) {
        if (tokenAddress == null) {
            return BigInteger.TEN.multiply(EXA);
        } else if (sicx.get().equals(tokenAddress)) {
            return (BigInteger.TEN.multiply(EXA.multiply(EXA))).divide(getSicxRate());
        } else if (bnUSD.get().equals(tokenAddress)) {
            return BigInteger.TEN.multiply(EXA);
        }

        return BigInteger.ZERO;

    }

    protected void swapIcx(Address sender, BigInteger value) {
        revertOnIncompleteRewards();
        BigInteger sicxIcxPrice = getSicxRate();

        BigInteger oldIcxTotal = icxQueueTotal.get();
        List<RewardsDataEntry> oldData = new ArrayList<>();

        BigInteger balnFees = (value.multiply(icxBalnFee.get())).divide(FEE_SCALE);
        BigInteger conversionFees = value.multiply(icxConversionFee.get()).divide(FEE_SCALE);

        BigInteger orderSize = value.subtract(balnFees.add(conversionFees));
        BigInteger orderIcxValue = (orderSize.multiply(sicxIcxPrice)).divide(EXA);

        BigInteger lpSicxSize = orderSize.add(conversionFees);

        Context.require(orderIcxValue.compareTo(icxQueueTotal.get()) <= 0, TAG + ": InsufficientLiquidityError: Not " +
                "enough ICX suppliers.");
        boolean filled = false;
        BigInteger orderRemainingIcx = orderIcxValue;
        int iterations = 0;
        while (!filled) {
            iterations += 1;
            if ((icxQueue.size().equals(BigInteger.ZERO)) || (iterations > ICX_QUEUE_FILL_DEPTH)) {
                Context.revert(TAG + ": InsufficientLiquidityError: Unable to fill " + orderRemainingIcx + "ICX.");
            }
            NodeDB counterpartyOrder = icxQueue.getHeadNode();
            Address counterpartyAddress = counterpartyOrder.getUser();
            BigInteger counterpartyIcx = counterpartyOrder.getSize();
            boolean counterpartyFilled = false;

            RewardsDataEntry rewardsEntry = new RewardsDataEntry();
            rewardsEntry._user = counterpartyAddress;
            rewardsEntry._balance = counterpartyIcx;

            oldData.add(rewardsEntry);
            BigInteger matchedIcx = counterpartyIcx.min(orderRemainingIcx);
            orderRemainingIcx = orderRemainingIcx.subtract(matchedIcx);

            if (matchedIcx.equals(counterpartyIcx)) {
                counterpartyFilled = true;
            }

            BigInteger lpSicxEarnings = (lpSicxSize.multiply(matchedIcx)).divide(orderIcxValue);
            sicxEarnings.set(counterpartyAddress,
                    sicxEarnings.getOrDefault(counterpartyAddress, BigInteger.ZERO).add(lpSicxEarnings));

            if (counterpartyFilled) {
                icxQueue.removeHead();
                icxQueueOrderId.set(counterpartyAddress, null);
                activeAddresses.get(SICXICX_POOL_ID).remove(counterpartyAddress);
            } else {
                BigInteger newCounterpartyValue = counterpartyOrder.getSize();
                counterpartyOrder.setSize(newCounterpartyValue);
            }

            updateAccountSnapshot(counterpartyAddress, SICXICX_POOL_ID);

            if (orderRemainingIcx.compareTo(BigInteger.ZERO) != 0) {
                filled = true;
            }


        }
        icxQueueTotal.set(oldIcxTotal.subtract(orderIcxValue));
        updateTotalSupplySnapshot(SICXICX_POOL_ID);

        BigInteger effectiveFillPrice = (EXA.multiply(orderIcxValue)).divide(value);

        Swap(BigInteger.valueOf(SICXICX_POOL_ID), sicx.get(), sicx.get(), null, sender,
                sender, value, orderIcxValue, BigInteger.valueOf(Context.getBlockTimestamp()), conversionFees,
                balnFees, icxQueueTotal.get(), BigInteger.ZERO, getSicxRate(), effectiveFillPrice);
        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, List.of(oldData));
        Context.call(sicx.get(), "transfer", feeHandler.get(), balnFees);
        Context.transfer(sender, orderIcxValue);
    }


    protected BigInteger getUnitValue(Address tokenAddress) {
        if (tokenAddress == null) {
            return EXA;
        } else {
            return pow(BigInteger.TEN, tokenPrecisions.get(tokenAddress).intValue());
        }
    }

    protected void revertBelowMinimum(BigInteger value, Address quoteToken) {
        BigInteger minAmount = getRewardableAmount(quoteToken);
        if (value.compareTo(minAmount) < 0) {
            BigInteger readableMin = minAmount.divide(getUnitValue(quoteToken));
            Context.revert(TAG + ": Total liquidity provided must be above " + readableMin + " quote currency");
        }
    }


    // Day Change Functions
    protected void takeNewDaySnapshot() {
        BigInteger day = this.getDay();
        if (day.compareTo(currentDay.get()) > 0) {
            currentDay.set(day);
            Snapshot(day);
            rewardsDone.set(false);
            // TODO dividends is done daily now, not once in 7 days
            if (day.mod(BigInteger.valueOf(7)).equals(BigInteger.ZERO)) {
                dividendsDone.set(false);
            }
        }
    }

    protected void checkDistributions() {
        if (this.isReentrantTx()) {
            return;
        }

        if (!rewardsDone.getOrDefault(false)) {
            rewardsDone.set((Boolean) Context.call(rewards.get(), "distribute"));
        } else if (!dividendsDone.getOrDefault(false)) {
            dividendsDone.set((Boolean) Context.call(dividends.get(), "distribute"));
        }
    }

    protected void updateAccountSnapshot(Address account, Integer id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentValue = balanceOf(account, BigInteger.valueOf(id));
        BigInteger length = accountBalanceSnapshot.at(id).at(account).at("length").getOrDefault(BigInteger.ZERO,
                BigInteger.ZERO);
        BigInteger lastSnapshotId;
        if (length.equals(BigInteger.ZERO)) {
            accountBalanceSnapshot.at(id).at(account).at("ids").set(length, currentId);
            accountBalanceSnapshot.at(id).at(account).at("values").set(length, currentValue);
            accountBalanceSnapshot.at(id).at(account).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        }
        lastSnapshotId = accountBalanceSnapshot.at(id).at(account).at("ids").get(length.subtract(BigInteger.ONE));
        if (lastSnapshotId.compareTo(currentId) < 0) {
            accountBalanceSnapshot.at(id).at(account).at("ids").set(length, currentId);
            accountBalanceSnapshot.at(id).at(account).at("values").set(length, currentValue);
            accountBalanceSnapshot.at(id).at(account).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
        } else {
            accountBalanceSnapshot.at(id).at(account).at("values").set(length.subtract(BigInteger.ONE), currentValue);

        }
    }

    protected void updateBalnSnapshot(Integer id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentValue = poolTotal.at(id).get(baln.get());
        BigInteger length = balnSnapshot.at(id).at("length").getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        BigInteger lastSnapshotId;

        if (length.equals(BigInteger.ZERO)) {
            balnSnapshot.at(id).at("ids").set(length, currentId);
            balnSnapshot.at(id).at("values").set(length, currentValue);
            balnSnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        }
        lastSnapshotId = balnSnapshot.at(id).at("ids").get(length.subtract(BigInteger.ONE));
        if (lastSnapshotId.compareTo(currentId) < 0) {
            balnSnapshot.at(id).at("ids").set(length, currentId);
            balnSnapshot.at(id).at("values").set(length, currentValue);
            balnSnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));

        } else {
            balnSnapshot.at(id).at("values").set(length.subtract(BigInteger.ONE), currentValue);
        }
    }

    protected void updateTotalSupplySnapshot(Integer id) {
        BigInteger currentId = currentDay.get();
        BigInteger currentValue = totalSupply(BigInteger.valueOf(id));
        BigInteger length = totalSupplySnapshot.at(id).at("length").getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        BigInteger lastSnapshotId;

        if (length.equals(BigInteger.ZERO)) {
            totalSupplySnapshot.at(id).at("ids").set(length, currentId);
            totalSupplySnapshot.at(id).at("values").set(length, currentValue);
            totalSupplySnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        }
        lastSnapshotId = totalSupplySnapshot.at(id).at("ids").get(length.subtract(BigInteger.ONE));
        if (lastSnapshotId.compareTo(currentId) < 0) {
            totalSupplySnapshot.at(id).at("ids").set(length, currentId);
            totalSupplySnapshot.at(id).at("values").set(length, currentValue);
            totalSupplySnapshot.at(id).at("length").set(BigInteger.ZERO, length.add(BigInteger.ONE));

        } else {
            totalSupplySnapshot.at(id).at("values").set(length.subtract(BigInteger.ONE), currentValue);
        }
    }

    protected void _transfer(Address from, Address to, BigInteger value, Integer id, byte[] data) {
        Context.require(value.compareTo(BigInteger.ZERO) >= 0, TAG + ": Transferring value cannot be less than 0.");
        BigInteger fromBalance = balance.at(id).getOrDefault(from, BigInteger.ZERO);

        Context.require(fromBalance.compareTo(value) >= 0, TAG + ": Out of balance");
        if (isLockingPool(id)) {
            Context.revert(TAG + ": untransferrable token id");
        }
        BigInteger toBalance = balance.at(id).getOrDefault(to, BigInteger.ZERO);
        balance.at(id).set(from, fromBalance.subtract(value));
        balance.at(id).set(to, toBalance.add(value));
        if (!to.equals(stakedLp.get()) && !from.equals(stakedLp.get())) {
            if (value.compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(id).add(to);
            }

            if (balance.at(id).getOrDefault(from, BigInteger.ZERO).equals(BigInteger.ZERO)) {
                activeAddresses.get(id).remove(from);
            }
        }
        TransferSingle(from, from, to, BigInteger.valueOf(id), value);

        updateAccountSnapshot(from, id);
        updateAccountSnapshot(to, id);

        if (to.isContract()) {
            Context.call(to, "onIRC31Received", from, from, id, value, data);
        }
    }
}
