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
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.utils.Check.isValidPoolId;
import static network.balanced.score.core.dex.utils.Const.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.utils.Math.pow;

public abstract class AbstractDex implements Dex {

    AbstractDex(Address _governance) {
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
    public void setFeehandler(Address _address) {
        only(admin);
        isContract(_address);
        feeHandler.set(_address);
    }

    @External(readonly = true)
    public Address getFeehandler() {
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

    @External(readonly = true)
    public Map<String, BigInteger> getFees() {
        BigInteger icxBalnFees = icxBalnFee.get();
        BigInteger icxConversionFees = icxConversionFee.get();
        BigInteger poolBalnFees = poolBalnFee.get();
        BigInteger poolLpFees = poolLpFee.get();
        Map<String, BigInteger> feesMapping = new HashMap<>();
        feesMapping.put("icx_total", icxBalnFees.add(icxConversionFees));
        feesMapping.put("pool_total", poolBalnFees.add(poolLpFees));
        feesMapping.put("pool_lp_fee", poolLpFees);
        feesMapping.put("pool_baln_fee", poolBalnFees);
        feesMapping.put("icx_conversion_fee", icxConversionFees);
        feesMapping.put("icx_baln_fee", icxBalnFees);
        return feesMapping;
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        only(admin);
        namedMarkets.set(_name, _id.intValue());
        marketsToNames.set(_id.intValue(), _name);
    }

    @External(readonly = true)
    public String getPoolName(BigInteger _id) {
        return marketsToNames.get(_id.intValue());
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
        return timeDelta.divide(MICRO_SECONDS_IN_A_DAY);
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
    public boolean precompute(BigInteger snap, BigInteger batch_size) {
        return true;
    }

    @External(readonly = true)
    public BigInteger getDeposit(Address _tokenAddress, Address _user) {
        return deposit.at(_tokenAddress).getOrDefault(_user, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getSicxEarnings(Address _user) {
        return sicxEarnings.getOrDefault(_user, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getWithdrawLock(BigInteger _id, Address _owner) {
        return withdrawLock.at(_id.intValue()).getOrDefault(_owner, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getPoolId(Address _token1Address, Address _token2Address) {
        return BigInteger.valueOf(poolId.at(_token1Address).get(_token2Address));
    }

    @External(readonly = true)
    public BigInteger getNonce() {
        return BigInteger.valueOf(nonce.getOrDefault(0));
    }

    @External(readonly = true)
    public List<String> getNamedPools() {
        List<String> namedPools = new ArrayList<>();
        namedPools.addAll(namedMarkets.keys());
        return namedPools;
    }

    @External(readonly = true)
    public BigInteger lookupPid(String _name) {
        return BigInteger.valueOf(namedMarkets.get(_name));
    }

    @External(readonly = true)
    public BigInteger getPoolTotal(BigInteger _id, Address _token) {
        return poolTotal.at(_id.intValue()).getOrDefault(_token, BigInteger.ZERO);
    }

    @External(readonly = true)
    public Address getPoolBase(BigInteger _id) {
        return poolBase.get(_id.intValue());
    }

    @External(readonly = true)
    public Address getPoolQuote(BigInteger _id) {
        return poolQuote.get(_id.intValue());
    }

    @External(readonly = true)
    public BigInteger getQuotePriceInBase(BigInteger _id) {
        isValidPoolId(_id);

        if (_id.intValue() == SICXICX_POOL_ID) {
            return ((EXA.multiply(EXA)).divide(getSicxRate()));
        }

        return priceOfAInB(_id.intValue(), poolQuote, poolBase);
    }

    @External(readonly = true)
    public BigInteger getBasePriceInQuote(BigInteger _id) {
        isValidPoolId(_id);

        if (_id.intValue() == SICXICX_POOL_ID) {
            return getSicxRate();
        }

        return priceOfAInB(_id.intValue(), poolBase, poolQuote);
    }

    private BigInteger priceOfAInB(Integer id, DictDB<Integer, Address> tokenA, DictDB<Integer, Address> tokenB) {
        Address ATokenAddress = tokenA.get(id);
        Address BTokenAddress = tokenB.get(id);

        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(id);
        BigInteger ATokenTotal = totalTokensInPool.get(ATokenAddress);
        BigInteger BTokenTotal = totalTokensInPool.get(BTokenAddress);

        return BTokenTotal.multiply(EXA).divide(ATokenTotal);
    }

    @External(readonly = true)
    public BigInteger getBalnPrice() {
        return getBasePriceInQuote(BigInteger.valueOf(poolId.at(baln.get()).get(bnUSD.get())));
    }

    @External(readonly = true)
    public BigInteger getSicxBnusdPrice() {
        return getBasePriceInQuote(BigInteger.valueOf(poolId.at(sicx.get()).get(bnUSD.get())));
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        Integer _id = namedMarkets.get(_name);
        if (_id.equals(SICXICX_POOL_ID)) {
            BigInteger icxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO);
            return (icxTotal.multiply(getSicxBnusdPrice())).divide(getSicxRate());
        }

        Address poolQuoteToken = poolQuote.get(_id);
        Address sicxAddress = sicx.get();
        Address bnusdAddress = bnUSD.get();

        if (poolQuoteToken.equals(sicxAddress)) {
            BigInteger sicxTotal = poolTotal.at(_id).get(sicxAddress).multiply(BigInteger.TWO);
            return getSicxBnusdPrice().multiply(sicxTotal).divide(EXA);
        } else if (poolQuoteToken.equals(bnusdAddress)) {
            return poolTotal.at(_id).get(bnusdAddress).multiply(BigInteger.TWO);
        }
        return BigInteger.ZERO;
    }

    @External(readonly = true)
    public BigInteger getPrice(BigInteger _id) {
        return this.getBasePriceInQuote(_id);
    }

    @External(readonly = true)
    public BigInteger getPriceByName(String _name) {
        return getPrice(BigInteger.valueOf(namedMarkets.get(_name)));
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
    public Map<String, Object> getPoolStats(BigInteger _id) {
        isValidPoolId(_id);
        Map<String, Object> poolStats = new HashMap<>();
        if (_id.intValue() == SICXICX_POOL_ID) {
            poolStats.put("base_token", sicx.get());
            poolStats.put("quote_token", null);
            poolStats.put("base", BigInteger.ZERO);
            poolStats.put("quote", icxQueueTotal.getOrDefault(BigInteger.ZERO));
            poolStats.put("total_supply", icxQueueTotal.getOrDefault(BigInteger.ZERO));
            poolStats.put("price", getPrice(_id));
            poolStats.put("name", SICXICX_MARKET_NAME);
            poolStats.put("base_decimals", 18);
            poolStats.put("quote_decimals", 18);
            poolStats.put("min_quote", getRewardableAmount(null));
        } else {
            Address baseToken = poolBase.get(_id.intValue());
            Address quoteToken = poolQuote.get(_id.intValue());
            String name = marketsToNames.get(_id.intValue());
            DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(_id.intValue());

            poolStats.put("base", totalTokensInPool.get(baseToken));
            poolStats.put("quote", totalTokensInPool.get(quoteToken));
            poolStats.put("base_token", baseToken);
            poolStats.put("quote_token", quoteToken);
            poolStats.put("total_supply", poolLpTotal.get(_id.intValue()));
            poolStats.put("price", getPrice(_id));
            poolStats.put("name", name);
            poolStats.put("base_decimals", tokenPrecisions.get(baseToken));
            poolStats.put("quote_decimals", tokenPrecisions.get(quoteToken));
            poolStats.put("min_quote", getRewardableAmount(quoteToken));
        }
        return poolStats;
    }

    @External(readonly = true)
    public Map<String, Object> getPoolStatsForPair(Address _base, Address _quote) {
        BigInteger poolId = getPoolId( _base, _quote);
        Map<String, Object> poolStats = getPoolStats(poolId);
        Map<String, Object> poolStatsWithId = new HashMap<>();
        poolStatsWithId.put("id", poolId);
        poolStatsWithId.putAll(poolStats);

        return poolStatsWithId;
    }

    @External(readonly = true)
    public BigInteger totalDexAddresses(BigInteger _id) {
        return BigInteger.valueOf(activeAddresses.get(_id.intValue()).length());
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalanceAndSupply(String _name, Address _owner) {
        if (_name.equals(SICXICX_MARKET_NAME)) {
            Map<String, BigInteger> rewardsData = new HashMap<>();
            rewardsData.put("_balance", balanceOf(_owner, BigInteger.valueOf(SICXICX_POOL_ID)));
            rewardsData.put("_totalSupply", totalSupply(BigInteger.valueOf(SICXICX_POOL_ID)));
            return rewardsData;
        }
        BigInteger poolId = lookupPid(_name);
        Context.require(poolId != null, TAG + ": Unsupported data source name");

        Address stakedLpAddress = stakedLp.get();
        BigInteger totalSupply = (BigInteger) Context.call(stakedLpAddress, "totalStaked", poolId);
        BigInteger balance = (BigInteger) Context.call(stakedLpAddress, "balanceOf", _owner, poolId);
        Map<String, BigInteger> rewardsData = new HashMap<>();
        rewardsData.put("_balance", balance);
        rewardsData.put("_totalSupply", totalSupply);
        return rewardsData;
    }

    @External(readonly = true)
    public BigInteger getTotalValue(String _name, BigInteger _snapshot_id) {
        return totalSupply(BigInteger.valueOf(namedMarkets.get(_name)));
    }

    @External(readonly = true)
    public BigInteger getBalnSnapshot(String _name, BigInteger _snapshot_id) {
        return totalBalnAt(BigInteger.valueOf(namedMarkets.get(_name)), _snapshot_id, false);
    }

    @External
    public void permit(BigInteger _id, boolean _permission) {
        only(admin);
        active.set(_id.intValue(), _permission);
    }

    @External
    public void addLpAddresses(BigInteger _poolId, Address[] _addresses) {
        only(admin);
        for (Address address : _addresses) {
            if (balanceOf(address, _poolId).compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(_poolId.intValue()).add(address);
            }
        }
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
            return getICXBalance(_owner);
        } else {
            return DexDBVariables.balance.at(_id.intValue()).getOrDefault(_owner, BigInteger.ZERO);
        }
    }

    @External(readonly = true)
    public BigInteger totalSupply(BigInteger _id) {
        if (_id.intValue() == SICXICX_POOL_ID) {
            return icxQueueTotal.getOrDefault(BigInteger.ZERO);
        }

        return poolLpTotal.getOrDefault(_id.intValue(), BigInteger.ZERO);
    }

    protected BigInteger getSicxRate() {
        return (BigInteger) Context.call(staking.get(), "getTodayRate");
    }

    boolean isRestrictedPoolId(Integer id) {
        return (id < FIRST_NON_BALANCED_POOL || id == USDS_BNUSD_ID || id == IUSDT_BNUSD_ID);
    }

    boolean isLockingPool(Integer id) {
        boolean stakedLpLaunched = stakedLp.get() != null;
        boolean restrictedPoolId = isRestrictedPoolId(id);
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

    void revertOnIncompleteRewards() {
        Context.require(rewardsDone.getOrDefault(false), TAG + ": Rewards distribution in progress, " +
                "please try again shortly");
    }

    void revertOnWithdrawalLock(Address user, Integer id) {
        if (continuousRewardsDay.get() != null && getDay().compareTo(continuousRewardsDay.get()) > 0) {
            return;
        }
        BigInteger depositTime = withdrawLock.at(id).getOrDefault(user, BigInteger.ZERO);
        Context.require(depositTime.add(WITHDRAW_LOCK_TIMEOUT).compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) <= 0,
                TAG + ":  Assets must remain in the pool for 24 hours, please try again later.");
    }

    BigInteger getRewardableAmount(Address tokenAddress) {
        if (tokenAddress == null) {
            return BigInteger.TEN.multiply(EXA);
        } else if (sicx.get().equals(tokenAddress)) {
            return (BigInteger.TEN.multiply(EXA.multiply(EXA))).divide(getSicxRate());
        } else if (bnUSD.get().equals(tokenAddress)) {
            return BigInteger.TEN.multiply(EXA);
        }
        return BigInteger.ZERO;
    }

    void exchange(Address fromToken, Address toToken, Address sender,
                  Address receiver, BigInteger value, BigInteger minimumReceive) {

        if (minimumReceive == null) {
            minimumReceive = BigInteger.ZERO;
        }

        int id = getPoolId(fromToken, toToken).intValue();
        isValidPoolId(id);
        Context.require(id != SICXICX_POOL_ID, TAG + ":  Not supported on this API, use the ICX swap API.");
        Context.require(active.getOrDefault(id, false), TAG + ": Pool is not active");

        BigInteger lpFees = value.multiply(poolLpFee.get()).divide(FEE_SCALE);
        BigInteger balnFees = value.multiply(poolBalnFee.get()).divide(FEE_SCALE);
        BigInteger fees = lpFees.add(balnFees);

        Address poolBaseToken = poolBase.get(id);
        boolean isSell = fromToken.equals(poolBaseToken);

        // We consider the trade in terms of toToken (token we are trading to), and fromToken (token we are trading
        // away) in the pool. It must obey the xy=k constant product formula.

        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(id);
        BigInteger oldFromToken = totalTokensInPool.get(fromToken);
        BigInteger oldToToken = totalTokensInPool.get(toToken);

        // We perturb the pool by the asset we are trading in less fees.
        // Fees are credited to LPs at the end of the process.
        BigInteger inputWithoutFees = value.subtract(fees);
        BigInteger newFromToken = oldFromToken.add(inputWithoutFees);

        // Compute the new fromToken according to the constant product formula
        BigInteger newToToken = (oldFromToken.multiply(oldToToken)).divide(newFromToken);

        // Send the trader the amount of toToken removed from the pool by the constant product formula
        BigInteger sendAmount = oldToToken.subtract(newToToken);

        Context.require(sendAmount.compareTo(BigInteger.ZERO) > 0, TAG + ": Invalid output amount in trade.");
        // Revert the transaction if the below slippage, as specified in _minimum_receive
        Context.require(sendAmount.compareTo(minimumReceive) >= 0,
                TAG + ": MinimumReceiveError: Receive amount " + sendAmount + " below supplied minimum");

        // Apply fees to fromToken after computing constant product. lpFees are credited to the LPs, the rest are
        // sent to BALN holders.
        newFromToken = newFromToken.add(lpFees);

        // Save updated pool totals
        totalTokensInPool.set(fromToken, newFromToken);
        totalTokensInPool.set(toToken, newToToken);

        // Capture details for event logs
        BigInteger totalBase = isSell ? newFromToken : newToToken;
        BigInteger totalQuote = isSell ? newToToken : newFromToken;

        // Send the trader their funds
        Context.call(toToken, "transfer", receiver, sendAmount);

        // Send the platform fees to the fee handler SCORE
        Context.call(fromToken, "transfer", feeHandler.get(), balnFees);

        // Broadcast pool ending price
        BigInteger effectiveFillPrice = (value.multiply(EXA)).divide(sendAmount);
        BigInteger endingPrice = totalQuote.multiply(EXA).divide(totalBase);

        if (!isSell) {
            effectiveFillPrice = (sendAmount.multiply(EXA)).divide(value);
        }

        Address balnTokenAddress = baln.get();
        if (fromToken.equals(balnTokenAddress)) {
            updateBalnSnapshot(id, newFromToken);
        } else if (toToken.equals(balnTokenAddress)) {
            updateBalnSnapshot(id, newToToken);
        }

        Swap(BigInteger.valueOf(id), poolBaseToken, fromToken, toToken, sender, receiver, value, sendAmount,
                BigInteger.valueOf(Context.getBlockTimestamp()), lpFees, balnFees, totalBase, totalQuote, endingPrice
                , effectiveFillPrice);
    }

    void swapIcx(Address sender, BigInteger value) {
        revertOnIncompleteRewards();
        BigInteger sicxIcxPrice = getSicxRate();

        BigInteger oldIcxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO);
        List<RewardsDataEntry> oldData = new ArrayList<>();

        BigInteger balnFees = (value.multiply(icxBalnFee.get())).divide(FEE_SCALE);
        BigInteger conversionFees = value.multiply(icxConversionFee.get()).divide(FEE_SCALE);
        BigInteger orderSize = value.subtract(balnFees.add(conversionFees));
        BigInteger orderIcxValue = (orderSize.multiply(sicxIcxPrice)).divide(EXA);
        BigInteger lpSicxSize = orderSize.add(conversionFees);

        Context.require(orderIcxValue.compareTo(oldIcxTotal) <= 0,
                TAG + ": InsufficientLiquidityError: Not enough ICX suppliers.");

        boolean filled = false;
        BigInteger orderRemainingIcx = orderIcxValue;
        int iterations = 0;
        while (!filled) {
            iterations += 1;
            if ((icxQueue.size().equals(BigInteger.ZERO)) || (iterations > ICX_QUEUE_FILL_DEPTH)) {
                Context.revert(TAG + ": InsufficientLiquidityError: Unable to fill " + orderRemainingIcx + " ICX.");
            }
            NodeDB counterpartyOrder = icxQueue.getHeadNode();
            Address counterpartyAddress = counterpartyOrder.getUser();
            BigInteger counterpartyIcx = counterpartyOrder.getSize();

            RewardsDataEntry rewardsEntry = new RewardsDataEntry();
            rewardsEntry._user = counterpartyAddress;
            rewardsEntry._balance = counterpartyIcx;

            oldData.add(rewardsEntry);
            BigInteger matchedIcx = counterpartyIcx.min(orderRemainingIcx);
            orderRemainingIcx = orderRemainingIcx.subtract(matchedIcx);

            boolean counterpartyFilled = matchedIcx.equals(counterpartyIcx);
            if (counterpartyFilled) {
                icxQueue.removeHead();
                icxQueueOrderId.set(counterpartyAddress, null);
                activeAddresses.get(SICXICX_POOL_ID).remove(counterpartyAddress);
                updateAccountSnapshot(counterpartyAddress, SICXICX_POOL_ID, null);
            } else {
                BigInteger newCounterpartyValue = counterpartyIcx.subtract(matchedIcx);
                counterpartyOrder.setSize(newCounterpartyValue);
                updateAccountSnapshot(counterpartyAddress, SICXICX_POOL_ID, newCounterpartyValue);
            }

            BigInteger lpSicxEarnings = (lpSicxSize.multiply(matchedIcx)).divide(orderIcxValue);
            BigInteger newSicxEarnings = getSicxEarnings(counterpartyAddress).add(lpSicxEarnings);
            sicxEarnings.set(counterpartyAddress, newSicxEarnings);

            if (orderRemainingIcx.compareTo(BigInteger.ZERO) == 0) {
                filled = true;
            }
        }
        BigInteger newIcxTotal = oldIcxTotal.subtract(orderIcxValue);
        icxQueueTotal.set(newIcxTotal);
        updateTotalSupplySnapshot(SICXICX_POOL_ID, newIcxTotal);
        BigInteger effectiveFillPrice = (orderIcxValue.multiply(EXA)).divide(value);
        Address sicxAddress = sicx.get();
        Swap(BigInteger.valueOf(SICXICX_POOL_ID), sicxAddress, sicxAddress, EOA_ZERO, sender, sender, value,
                orderIcxValue, BigInteger.valueOf(Context.getBlockTimestamp()), conversionFees, balnFees, newIcxTotal
                , BigInteger.ZERO, sicxIcxPrice, effectiveFillPrice);
        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal,
                oldData);
        Context.call(sicxAddress, "transfer", feeHandler.get(), balnFees);
        Context.transfer(sender, orderIcxValue);
    }


    private BigInteger getUnitValue(Address tokenAddress) {
        if (tokenAddress == null) {
            return EXA;
        } else {
            return pow(BigInteger.TEN, tokenPrecisions.get(tokenAddress).intValue());
        }
    }

    void revertBelowMinimum(BigInteger value, Address quoteToken) {
        BigInteger minAmount = getRewardableAmount(quoteToken);
        if (value.compareTo(minAmount) < 0) {
            BigInteger readableMin = minAmount.divide(getUnitValue(quoteToken));
            Context.revert(TAG + ": Total liquidity provided must be above " + readableMin + " quote currency");
        }
    }

    // Day Change Functions
    void takeNewDaySnapshot() {
        BigInteger day = this.getDay();
        if (day.compareTo(currentDay.get()) > 0) {
            currentDay.set(day);
            Snapshot(day);
            rewardsDone.set(false);
            dividendsDone.set(false);
        }
    }

    void checkDistributions() {
        if (this.isReentrantTx()) {
            return;
        }

        if (!rewardsDone.getOrDefault(false)) {
            rewardsDone.set((Boolean) Context.call(rewards.get(), "distribute"));
        } else if (!dividendsDone.getOrDefault(false)) {
            dividendsDone.set((Boolean) Context.call(dividends.get(), "distribute"));
        }
    }

    void updateAccountSnapshot(Address account, Integer id, BigInteger newValueToUpdate) {
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = accountBalanceSnapshot.at(id).at(account);
        updateSnapshotWithNewValue(newValueToUpdate, snapshot);
    }

    void updateBalnSnapshot(Integer id, BigInteger newValueToUpdate) {
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = balnSnapshot.at(id);
        updateSnapshotWithNewValue(newValueToUpdate, snapshot);
    }

    void updateTotalSupplySnapshot(Integer id, BigInteger newValueToUpdate) {
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = totalSupplySnapshot.at(id);
        updateSnapshotWithNewValue(newValueToUpdate, snapshot);
    }

    private void updateSnapshotWithNewValue(BigInteger newValue,
                                            BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot) {
        BigInteger currentId = currentDay.get();
        BigInteger length = snapshot.at(LENGTH).getOrDefault(BigInteger.ZERO, BigInteger.ZERO);

        if (length.equals(BigInteger.ZERO)) {
            snapshot.at(IDS).set(length, currentId);
            snapshot.at(VALUES).set(length, newValue);
            snapshot.at(LENGTH).set(BigInteger.ZERO, length.add(BigInteger.ONE));
            return;
        }

        BigInteger lastSnapshotId = snapshot.at(IDS).get(length.subtract(BigInteger.ONE));
        if (lastSnapshotId.compareTo(currentId) < 0) {
            snapshot.at(IDS).set(length, currentId);
            snapshot.at(VALUES).set(length, newValue);
            snapshot.at(LENGTH).set(BigInteger.ZERO, length.add(BigInteger.ONE));
        } else {
            snapshot.at(VALUES).set(length.subtract(BigInteger.ONE), newValue);
        }
    }

    BigInteger snapshotValueAt(BigInteger _snapshot_id,
                               BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot) {
        Context.require(_snapshot_id.compareTo(BigInteger.ZERO) >= 0,
                TAG + ": Snapshot id is equal to or greater then Zero.");
        BigInteger low = BigInteger.ZERO;
        BigInteger high = snapshot.at(LENGTH).getOrDefault(BigInteger.ZERO, BigInteger.ZERO);

        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (snapshot.at(IDS).getOrDefault(mid, BigInteger.ZERO).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }
        }

        if (snapshot.at(IDS).getOrDefault(BigInteger.ZERO, BigInteger.ZERO).equals(_snapshot_id)) {
            return snapshot.at(VALUES).getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        } else if (low.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        BigInteger matchedIndex = low.subtract(BigInteger.ONE);
        return snapshot.at(VALUES).getOrDefault(matchedIndex, BigInteger.ZERO);
    }

    void _transfer(Address from, Address to, BigInteger value, Integer id, byte[] data) {

        Context.require(!isLockingPool(id), TAG + ": Nontransferable token id");
        Context.require(value.compareTo(BigInteger.ZERO) >= 0,
                TAG + ": Transferring value cannot be less than 0.");

        DictDB<Address, BigInteger> poolLpBalanceOfUser = balance.at(id);
        BigInteger fromBalance = poolLpBalanceOfUser.getOrDefault(from, BigInteger.ZERO);

        Context.require(fromBalance.compareTo(value) >= 0, TAG + ": Out of balance");

        BigInteger toBalance = poolLpBalanceOfUser.getOrDefault(to, BigInteger.ZERO);
        poolLpBalanceOfUser.set(from, fromBalance.subtract(value));
        poolLpBalanceOfUser.set(to, toBalance.add(value));
        Address stakedLpAddress = stakedLp.get();

        if (!from.equals(stakedLpAddress) && !to.equals(stakedLpAddress)) {
            if (value.compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(id).add(to);
            }

            if ((fromBalance.subtract(value)).equals(BigInteger.ZERO)) {
                activeAddresses.get(id).remove(from);
            }
        }
        TransferSingle(from, from, to, BigInteger.valueOf(id), value);

        if (!from.equals(stakedLpAddress)) {
            updateAccountSnapshot(from, id, fromBalance.subtract(value));
        }
        if (!to.equals(stakedLpAddress)) {
            updateAccountSnapshot(to, id, toBalance.add(value));
        }

        if (to.isContract()) {
            Context.call(to, "onIRC31Received", from, from, id, value, data);
        }
    }
}
