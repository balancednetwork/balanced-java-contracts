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

package network.balanced.score.core.dex;

import network.balanced.score.core.dex.db.NodeDB;
import network.balanced.score.lib.interfaces.Dex;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.BalancedEmergencyHandling;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.utils.Check.isValidPoolId;
import static network.balanced.score.core.dex.utils.Const.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.onlyGovernance;
import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.utils.Math.pow;

public abstract class AbstractDex extends BalancedEmergencyHandling implements Dex {

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
        setGovernance(governance.get());
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

    @External(readonly = true)
    public String name() {
        return TAG;
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
    public void setPoolLpFee(BigInteger _value) {
        onlyGovernance();
        poolLpFee.set(_value);
    }

    @External
    public void setPoolBalnFee(BigInteger _value) {
        onlyGovernance();
        poolBalnFee.set(_value);
    }

    @External
    public void setIcxConversionFee(BigInteger _value) {
        onlyGovernance();
        icxConversionFee.set(_value);
    }

    @External
    public void setIcxBalnFee(BigInteger _value) {
        onlyGovernance();
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
        onlyGovernance();
        namedMarkets.set(_name, _id.intValue());
        marketsToNames.set(_id.intValue(), _name);
    }

    @External(readonly = true)
    public String getPoolName(BigInteger _id) {
        return marketsToNames.get(_id.intValue());
    }

    @External
    public void addQuoteCoin(Address _address) {
        onlyGovernance();
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
        onlyGovernance();
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
        return getBasePriceInQuote(BigInteger.valueOf(poolId.at(getBaln()).get(getBnusd())));
    }

    @External(readonly = true)
    public BigInteger getSicxBnusdPrice() {
        return getBasePriceInQuote(BigInteger.valueOf(poolId.at(getSicx()).get(getBnusd())));
    }

    @External(readonly = true)
    public BigInteger getBnusdValue(String _name) {
        // Should eventually only handle sICX/ICX
        Integer _id = namedMarkets.get(_name);
        return getLPBnusdValue(_id);
    }

    @External(readonly = true)
    public BigInteger getLPBnusdValue(int _id) {
        if (_id == SICXICX_POOL_ID) {
            BigInteger icxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO);
            return (icxTotal.multiply(getSicxBnusdPrice())).divide(getSicxRate());
        }

        Address poolQuoteToken = poolQuote.get(_id);
        Address sicxAddress = getSicx();
        Address bnusdAddress = getBnusd();

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
            poolStats.put("base_token", getSicx());
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
        BigInteger poolId = getPoolId(_base, _quote);
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

        Address stakedLpAddress = getStakedLp();
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

    @External
    public void permit(BigInteger _id, boolean _permission) {
        onlyGovernance();
        active.set(_id.intValue(), _permission);
    }

    @External
    public void addLpAddresses(BigInteger _poolId, Address[] _addresses) {
        onlyGovernance();
        for (Address address : _addresses) {
            if (balanceOf(address, _poolId).compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(_poolId.intValue()).add(address);
            }
        }
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

    @External
    public void delegate(PrepDelegations[] prepDelegations) {
        onlyGovernance();
        Context.call(getStaking(), "delegate", (Object) prepDelegations);
    }

    protected BigInteger getSicxRate() {
        return (BigInteger) Context.call(getStaking(), "getTodayRate");
    }

    boolean isLockingPool(Integer id) {
        return id.equals(SICXICX_POOL_ID);
    }

    BigInteger getRewardableAmount(Address tokenAddress) {
        if (tokenAddress == null) {
            return BigInteger.TEN.multiply(EXA);
        } else if (getSicx().equals(tokenAddress)) {
            return (BigInteger.TEN.multiply(EXA.multiply(EXA))).divide(getSicxRate());
        } else if (getBnusd().equals(tokenAddress)) {
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
        BigInteger initialBalnFees = balnFees;
        BigInteger fees = lpFees.add(balnFees);

        Address poolBaseToken = poolBase.get(id);
        boolean isSell = fromToken.equals(poolBaseToken);
        Address poolQuoteToken = isSell ? toToken : fromToken;

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

        if (isSell) {
            oldFromToken = newFromToken;
            oldToToken = newToToken;

            newFromToken = oldFromToken.add(balnFees);
            newToToken = (oldFromToken.multiply(oldToToken)).divide(newFromToken);

            balnFees = oldToToken.subtract(newToToken);
        }

        // Save updated pool totals
        totalTokensInPool.set(fromToken, newFromToken);
        totalTokensInPool.set(toToken, newToToken);

        // Capture details for event logs
        BigInteger totalBase = isSell ? newFromToken : newToToken;
        BigInteger totalQuote = isSell ? newToToken : newFromToken;

        // Send the trader their funds
        Context.call(toToken, "transfer", receiver, sendAmount);

        // Send the platform fees to the fee handler SCORE
        Context.call(poolQuoteToken, "transfer", getFeehandler(), balnFees);

        // Broadcast pool ending price
        BigInteger effectiveFillPrice = (value.multiply(EXA)).divide(sendAmount);
        BigInteger endingPrice = totalQuote.multiply(EXA).divide(totalBase);

        if (!isSell) {
            effectiveFillPrice = (sendAmount.multiply(EXA)).divide(value);
        }

        Swap(BigInteger.valueOf(id), poolBaseToken, fromToken, toToken, sender, receiver, value, sendAmount,
                BigInteger.valueOf(Context.getBlockTimestamp()), lpFees, initialBalnFees, totalBase, totalQuote,
                endingPrice, effectiveFillPrice);
    }

    void donate(Address fromToken, Address toToken, BigInteger value) {
        int id = getPoolId(fromToken, toToken).intValue();
        isValidPoolId(id);
        Context.require(id != SICXICX_POOL_ID, TAG + ":  Not supported on this API, use the ICX swap API.");
        Context.require(active.getOrDefault(id, false), TAG + ": Pool is not active");

        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(id);
        BigInteger oldFromToken = totalTokensInPool.get(fromToken);

        BigInteger newFromToken = oldFromToken.add(value);

        totalTokensInPool.set(fromToken, newFromToken);
    }

    void swapIcx(Address sender, BigInteger value) {
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
            } else {
                BigInteger newCounterpartyValue = counterpartyIcx.subtract(matchedIcx);
                counterpartyOrder.setSize(newCounterpartyValue);
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
        BigInteger effectiveFillPrice = (orderIcxValue.multiply(EXA)).divide(value);
        Address sicxAddress = getSicx();
        Swap(BigInteger.valueOf(SICXICX_POOL_ID), sicxAddress, sicxAddress, EOA_ZERO, sender, sender, value,
                orderIcxValue, BigInteger.valueOf(Context.getBlockTimestamp()), conversionFees, balnFees, newIcxTotal
                , BigInteger.ZERO, sicxIcxPrice, effectiveFillPrice);

        Context.call(getRewards(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, oldData);
        Context.call(sicxAddress, "transfer", getFeehandler(), balnFees);
        Context.transfer(sender, orderIcxValue);
    }


    private BigInteger getUnitValue(Address tokenAddress) {
        if (tokenAddress == null) {
            return EXA;
        } else {
            return pow(BigInteger.TEN, tokenPrecisions.get(tokenAddress).intValue());
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
        Address stakedLpAddress = getStakedLp();

        if (!from.equals(stakedLpAddress) && !to.equals(stakedLpAddress)) {
            if (value.compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(id).add(to);
            }

            if ((fromBalance.subtract(value)).equals(BigInteger.ZERO)) {
                activeAddresses.get(id).remove(from);
            }
        }
        TransferSingle(from, from, to, BigInteger.valueOf(id), value);

        if (to.isContract()) {
            Context.call(to, "onIRC31Received", from, from, id, value, data);
        }
    }
}
