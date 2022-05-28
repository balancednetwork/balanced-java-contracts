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


import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.core.dex.db.NodeDB;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.Context;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.utils.Check.isOn;
import static network.balanced.score.core.dex.utils.Check.isValidPoolId;
import static network.balanced.score.core.dex.utils.Const.*;
import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.convertToNumber;

public class DexImpl extends AbstractDex {

    public DexImpl(Address _governance) {
        super(_governance);
    }


    @Payable
    public void fallback() {
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        BigInteger orderValue = Context.getValue();
        Context.require(orderValue.compareTo(EXA.multiply(BigInteger.TEN)) >= 0, TAG + ": Minimum pool contribution " +
                "is 10 ICX");
        Address user = Context.getCaller();
        BigInteger oldOrderValue = BigInteger.ZERO;
        BigInteger orderId = icxQueueOrderId.getOrDefault(user, BigInteger.ZERO);


        withdrawLock.at(SICXICX_POOL_ID).set(user, BigInteger.valueOf(Context.getBlockTimestamp()));
        // Upsert Order, so we can bump to the back of the queue
        if (orderId.compareTo(BigInteger.ZERO) > 0) {
            NodeDB node = icxQueue.getNode(orderId);
            oldOrderValue = node.getSize();
            orderValue = orderValue.add(oldOrderValue);
            icxQueue.remove(orderId);
        }

        // Insert order to the back of the queue
        BigInteger nextTailId = icxQueue.getTailId().add(BigInteger.ONE);
        orderId = icxQueue.append(orderValue, user, nextTailId);
        icxQueueOrderId.set(user, orderId);

        BigInteger oldIcxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO);

        // Update total ICX queue size
        BigInteger currentIcxTotal = oldIcxTotal.add(orderValue);

        icxQueueTotal.set(currentIcxTotal);

        revertBelowMinimum(orderValue, null);
        activeAddresses.get(SICXICX_POOL_ID).add(user);


        updateAccountSnapshot(user, SICXICX_POOL_ID);
        updateTotalSupplySnapshot(SICXICX_POOL_ID);

        List<RewardsDataEntry> rewardsList = new ArrayList<>();
        RewardsDataEntry rewardsEntry = new RewardsDataEntry();
        rewardsEntry._user = user;
        rewardsEntry._balance = oldOrderValue;
        rewardsList.add(rewardsEntry);
        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, rewardsList);
    }

    @External
    public void cancelSicxicxOrder() {
        isOn(dexOn);
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        Address user = Context.getCaller();
        Context.require(icxQueueOrderId.getOrDefault(user, BigInteger.ZERO).compareTo(BigInteger.ZERO) > 0, TAG +
                ": No open order in sICX/ICX queue.");
        revertOnWithdrawalLock(user, SICXICX_POOL_ID);

        BigInteger orderId = icxQueueOrderId.get(user);
        NodeDB order = icxQueue.getNode(orderId);

        BigInteger withdrawAmount = order.getSize();
        BigInteger oldIcxTotal = icxQueueTotal.get();
        BigInteger currentIcxTotal = oldIcxTotal.subtract(withdrawAmount);

        icxQueueTotal.set(currentIcxTotal);
        icxQueue.remove(orderId);
        icxQueueOrderId.set(user, null);

        Context.transfer(user, withdrawAmount);
        activeAddresses.get(SICXICX_POOL_ID).remove(user);

        updateAccountSnapshot(user, SICXICX_POOL_ID);
        updateTotalSupplySnapshot(SICXICX_POOL_ID);

        List<RewardsDataEntry> rewardsList = new ArrayList<>();
        RewardsDataEntry rewardsEntry = new RewardsDataEntry();
        rewardsEntry._user = user;
        rewardsEntry._balance = withdrawAmount;
        rewardsList.add(rewardsEntry);

        Context.call(rewards.get(), "updateBatchRewardsData", SICXICX_MARKET_NAME, oldIcxTotal, rewardsList);
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
        Address fromToken = Context.getCaller();

        Context.require(_value.compareTo(BigInteger.valueOf(0)) > -1, "Invalid token transfer value");

        // Call an internal method based on the "method" param sent in tokenFallBack
        switch (method) {
            case "_deposit":

                BigInteger userBalance = deposit.at(fromToken).getOrDefault(_from, BigInteger.valueOf(0));
                userBalance = userBalance.add(_value);
                deposit.at(fromToken).set(_from, userBalance);
                Deposit(fromToken, _from, _value);

                if (tokenPrecisions.get(fromToken) == null) {
                    BigInteger decimalValue = (BigInteger) Context.call(fromToken, "decimals");
                    tokenPrecisions.set(fromToken, decimalValue);
                }
                break;
            case "_swap_icx":
                Context.require(fromToken.equals(sicx.get()), TAG + ": InvalidAsset: _swap_icx can only be called " +
                        "with sICX");
                swapIcx(_from, _value);

                break;
            case "_swap":

                // Parse the slippage sent by the user in minimumReceive.
                // If none is sent, use the maximum.
                JsonObject params = json.get("params").asObject();
                BigInteger minimumReceive = BigInteger.ZERO;
                if (params.contains("minimumReceive")) {
                    minimumReceive = convertToNumber(params.get("minimumReceive"));
                    Context.require(
                            minimumReceive.signum() >= 0,
                            TAG + "Must specify a positive number for minimum to receive"
                    );
                }

                // Check if an alternative recipient of the swap is set.
                Address receiver;
                if (params.contains("receiver")) {
                    receiver = Address.fromString(params.get("receiver").asString());
                } else {
                    receiver = _from;
                }

                // Get destination coin from the swap
                Context.require(params.contains("toToken"), TAG + ": No toToken specified in swap");
                Address toToken = Address.fromString(params.get("toToken").asString());

                // Perform the swap
                exchange(fromToken, toToken, _from, receiver, _value, minimumReceive);

                break;
            default:
                // If no supported method was sent, revert the transaction
                Context.revert(100, TAG + "Unsupported method supplied");
                break;
        }
    }

    @External
    public void transfer(Address _to, BigInteger _value, BigInteger _id, @Optional byte[] _data) {
        isOn(dexOn);
        if (_data == null) {
            _data = new byte[0];
        }
        _transfer(Context.getCaller(), _to, _value, _id.intValue(), _data);
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        Context.revert(TAG + ": IRC31 Tokens not accepted");
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
        return withdrawLock.at(_id.intValue()).get(_owner);
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
        return poolTotal.at(_id.intValue()).get(_token);
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
        Address poolQuoteAddress = poolQuote.get(_id.intValue());
        Address poolBaseAddress = poolBase.get(_id.intValue());

        BigInteger poolQuote = poolTotal.at(_id.intValue()).get(poolQuoteAddress);
        BigInteger poolBase = poolTotal.at(_id.intValue()).get(poolBaseAddress);

        return (poolBase.multiply(EXA)).divide(poolQuote);
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
            BigInteger icxTotal = icxQueueTotal.get();
            return (icxTotal.multiply(getSicxBnusdPrice())).divide(getSicxRate());
        } else if (poolQuote.get(_id).equals(sicx.get())) {
            BigInteger sicxTotal = (poolTotal.at(_id).get(sicx.get())).multiply(BigInteger.TWO);
            return (getSicxBnusdPrice().multiply(sicxTotal)).divide(EXA);
        } else if (poolQuote.get(_id).equals(bnUSD.get())) {
            return (poolTotal.at(_id).get(bnUSD.get())).multiply(BigInteger.TWO);
        }

        return BigInteger.ZERO;

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
    public String getPoolName(BigInteger _id) {
        return marketsToNames.get(_id.intValue());
    }

    @External(readonly = true)
    public Map<String, Object> getPoolStats(BigInteger _id) {
        isValidPoolId(_id);
        Map<String, Object> poolStats = new HashMap<>();
        if (_id.intValue() == SICXICX_POOL_ID) {
            poolStats.put("base_token", sicx.get());
            poolStats.put("quote_token", null);
            poolStats.put("base", BigInteger.ZERO);
            poolStats.put("quote", icxQueueTotal.get());
            poolStats.put("total_supply", icxQueueTotal.get());
            poolStats.put("price", getPrice(_id));
            poolStats.put("name", SICXICX_MARKET_NAME);
            poolStats.put("base_decimals", 18);
            poolStats.put("quote_decimals", 18);
            poolStats.put("min_quote", getRewardableAmount(null));
        } else {
            Address baseToken = poolBase.get(_id.intValue());
            Address quoteToken = poolQuote.get(_id.intValue());
            String name = marketsToNames.get(_id.intValue());

            poolStats.put("base", poolTotal.at(_id.intValue()).get(baseToken));
            poolStats.put("quote", poolTotal.at(_id.intValue()).get(quoteToken));
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
    public int totalDexAddresses(BigInteger _id) {
        return activeAddresses.get(_id.intValue()).length();
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
        if (poolId != null) {
            BigInteger totalSupply = (BigInteger) Context.call(stakedLp.get(), "totalStaked", poolId);
            BigInteger balance = (BigInteger) Context.call(stakedLp.get(), "balanceOf", _owner, poolId);
            Map<String, BigInteger> rewardsData = new HashMap<>();
            rewardsData.put("_balance", balance);
            rewardsData.put("_totalSupply", totalSupply);
            return rewardsData;
        } else {
            Context.revert(TAG + ": Unsupported data source name");
        }
        return null;
    }

    @External(readonly = true)
    public BigInteger balanceOfAt(Address _account, BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {
        BigInteger matchedIndex;
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert(TAG + ": Snapshot id is equal to or greater then Zero.");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high =
                accountBalanceSnapshot.at(_id.intValue()).at(_account).at("length").getOrDefault(BigInteger.ZERO,
                        BigInteger.ZERO);

        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (accountBalanceSnapshot.at(_id.intValue()).at(_account).at("ids").getOrDefault(mid, BigInteger.ZERO).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }

        }

        if (accountBalanceSnapshot.at(_id.intValue()).at(_account).at("ids").getOrDefault(BigInteger.ZERO,
                BigInteger.ZERO).equals(_snapshot_id)) {
            return accountBalanceSnapshot.at(_id.intValue()).at(_account).at("values").getOrDefault(BigInteger.ZERO,
                    BigInteger.ZERO);
        } else if (low.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        matchedIndex = low.subtract(BigInteger.ONE);

        return accountBalanceSnapshot.at(_id.intValue()).at(_account).at("values").getOrDefault(matchedIndex,
                BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalSupplyAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {
        BigInteger matchedIndex;
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Snapshot id is equal to or greater then Zero");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = totalSupplySnapshot.at(_id.intValue()).at("length").getOrDefault(BigInteger.ZERO,
                BigInteger.ZERO);
        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (totalSupplySnapshot.at(_id.intValue()).at("ids").getOrDefault(mid, BigInteger.ZERO).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }
        }
        if (totalSupplySnapshot.at(_id.intValue()).at("ids").getOrDefault(BigInteger.ZERO, BigInteger.ZERO).equals(_snapshot_id)) {
            return totalSupplySnapshot.at(_id.intValue()).at("values").getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        } else if (low.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        matchedIndex = low.subtract(BigInteger.ONE);

        return totalSupplySnapshot.at(_id.intValue()).at("values").getOrDefault(matchedIndex, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {
        BigInteger matchedIndex;
        if (_snapshot_id.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Snapshot id is equal to or greater then Zero");
        }
        BigInteger low = BigInteger.ZERO;
        BigInteger high = balnSnapshot.at(_id.intValue()).at("length").getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        while (low.compareTo(high) < 0) {
            BigInteger mid = (low.add(high)).divide(BigInteger.TWO);
            if (balnSnapshot.at(_id.intValue()).at("ids").getOrDefault(mid, BigInteger.ZERO).compareTo(_snapshot_id) > 0) {
                high = mid;
            } else {
                low = mid.add(BigInteger.ONE);
            }
        }
        if (balnSnapshot.at(_id.intValue()).at("ids").getOrDefault(BigInteger.ZERO, BigInteger.ZERO).equals(_snapshot_id)) {
            return balnSnapshot.at(_id.intValue()).at("values").getOrDefault(BigInteger.ZERO, BigInteger.ZERO);
        } else if (low.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        matchedIndex = low.subtract(BigInteger.ONE);

        return balnSnapshot.at(_id.intValue()).at("values").getOrDefault(matchedIndex, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getTotalValue(String _name, BigInteger _snapshot_id) {
        return totalSupply(BigInteger.valueOf(namedMarkets.get(_name)));
    }

    @External(readonly = true)
    public BigInteger getBalnSnapshot(String _name, BigInteger _snapshot_id) {
        return totalBalnAt(BigInteger.valueOf(namedMarkets.get(_name)), _snapshot_id, false);
    }

    @External(readonly = true)
    public Map<String, Object> loadBalancesAtSnapshot(BigInteger _id, BigInteger _snapshot_id, BigInteger _limit,
                                                      @Optional BigInteger _offset) {
        if (_offset == null) {
            _offset = BigInteger.ZERO;
        }
        Context.require(_snapshot_id.compareTo(BigInteger.ZERO) >= 0, TAG +
                ":  Snapshot id is equal to or greater then Zero.");
        Context.require(_id.compareTo(BigInteger.ZERO) >= 0, TAG + ":  Pool id is equal to or greater then Zero.");
        Context.require(_offset.compareTo(BigInteger.ZERO) >= 0, TAG + ":  Offset is equal to or greater then Zero.");
        Map<String, Object> snapshotData = new HashMap<>();
        for (Address addr : activeAddresses.get(_id.intValue()).range(_offset, _offset.add(_limit))) {
            BigInteger snapshotBalance = balanceOf(addr, _id);
            if (!snapshotBalance.equals(BigInteger.ZERO)) {
                snapshotData.put(addr.toString(), snapshotBalance);
            }

        }
        return snapshotData;
    }

    @External(readonly = true)
    public Map<String, Object> getDataBatch(String _name, BigInteger _snapshot_id, BigInteger _limit, @Optional BigInteger _offset) {
        if (_offset == null) {
            _offset = BigInteger.ZERO;
        }
        BigInteger pid = BigInteger.valueOf(namedMarkets.get(_name));
        BigInteger total = BigInteger.valueOf(totalDexAddresses(pid));
        BigInteger clampedOffset = _offset.min(total);
        BigInteger clamped_limit = _limit.min(total.subtract(clampedOffset));
        return loadBalancesAtSnapshot(pid, _snapshot_id, clamped_limit, clampedOffset);
    }

    @External
    public void permit(BigInteger _id, boolean _permission) {
        only(governance);
        active.set(_id.intValue(), _permission);
    }

    @External
    public void withdraw(Address _token, BigInteger _value) {
        isOn(dexOn);
        Address sender = Context.getCaller();
        BigInteger deposit_amount = deposit.at(_token).getOrDefault(sender, BigInteger.ZERO);

        Context.require(_value.compareTo(deposit_amount) <= 0, TAG + ": Insufficient Balance");
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Must specify a positive amount");

        deposit.at(_token).set(sender, deposit_amount.subtract(_value));

        Context.call(_token, "transfer", sender, _value);
        Withdraw(_token, sender, _value);
    }

    @External
    public void remove(BigInteger _id, BigInteger _value, boolean _withdraw) {
        isOn(dexOn);
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        Address user = Context.getCaller();

        revertOnWithdrawalLock(user, _id.intValue());
        BigInteger userBalance = balance.at(_id.intValue()).getOrDefault(user, BigInteger.ZERO);

        //TODO(galen): Should we even reimplement inactive pool by id?

        if (!active.get(_id.intValue())) {
            Context.revert(TAG + ": Pool is not active.");
        }
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + " Cannot withdraw a negative balance");
        Context.require(_value.compareTo(userBalance) <= 0, TAG + ": Insufficient balance");

        Address baseToken = poolBase.get(_id.intValue());
        Address quoteToken = poolQuote.get(_id.intValue());

        BigInteger totalBase = poolTotal.at(_id.intValue()).get(baseToken);
        BigInteger totalQuote = poolTotal.at(_id.intValue()).get(quoteToken);
        BigInteger totalLPToken = poolLpTotal.get(_id.intValue());

        BigInteger userQuoteLeft = ((userBalance.subtract(_value)).multiply(totalQuote)).divide(totalLPToken);

        if (userQuoteLeft.compareTo(getRewardableAmount(quoteToken)) < 0) {
            _value = userBalance;
            activeAddresses.get(_id.intValue()).remove(user);
        }


        BigInteger baseToWithdraw = (totalBase.multiply(_value)).divide(totalLPToken);
        BigInteger quoteToWithdraw = totalQuote.multiply(_value).divide(totalLPToken);

        BigInteger newBase = totalBase.subtract(baseToWithdraw);
        BigInteger newQuote = totalQuote.subtract(quoteToWithdraw);
        BigInteger newUserBalance = userBalance.subtract(_value);
        BigInteger newTotal = totalLPToken.subtract(_value);

        Context.require(newTotal.compareTo(MIN_LIQUIDITY) >= 0, TAG + ": Cannot withdraw pool past minimum LP token " +
                "amount");

        poolTotal.at(_id.intValue()).set(baseToken, newBase);
        poolTotal.at(_id.intValue()).set(quoteToken, newQuote);
        balance.at(_id.intValue()).set(user, newUserBalance);
        poolLpTotal.set(_id.intValue(), newTotal);

        Remove(_id, user, _value, baseToWithdraw, quoteToWithdraw);
        TransferSingle(user, user, DEX_ZERO_SCORE_ADDRESS, _id, _value);
        BigInteger depositedBase = deposit.at(baseToken).getOrDefault(user, BigInteger.ZERO);
        deposit.at(baseToken).set(user, depositedBase.add(baseToWithdraw));

        BigInteger depositedQuote = deposit.at(quoteToken).getOrDefault(user, BigInteger.ZERO);
        deposit.at(quoteToken).set(user, depositedQuote.add(quoteToWithdraw));

        updateAccountSnapshot(user, _id.intValue());
        updateTotalSupplySnapshot(_id.intValue());
        if (baseToken.equals(baln.get())) {
            updateBalnSnapshot(_id.intValue());
        }

        if (_withdraw) {
            withdraw(baseToken, baseToWithdraw);
            withdraw(quoteToken, quoteToWithdraw);
        }

    }

    @External
    public void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
                    @Optional boolean _withdraw_unused) {
        isOn(dexOn);
        if (!_withdraw_unused) {
            _withdraw_unused = true;
        }
        takeNewDaySnapshot();
        checkDistributions();
        revertOnIncompleteRewards();

        Address user = Context.getCaller();

        // We check if there is a previously seen pool with this id.
        // If none is found (return 0), we create a new pool.
        Integer id = poolId.at(_baseToken).getOrDefault(_quoteToken, 0);

        Context.require(_baseToken != _quoteToken, TAG + ": Pool must contain two token contracts");

        // Check base/quote balances are valid
        Context.require(_baseValue.compareTo(BigInteger.ZERO) > 0, TAG + ": Cannot send 0 or negative base token");
        Context.require(_quoteValue.compareTo(BigInteger.ZERO) > 0, TAG + ": Cannot send 0 or negative quote token");

        BigInteger userDepositedBase = deposit.at(_baseToken).getOrDefault(user, BigInteger.ZERO);
        BigInteger userDepositedQuote = deposit.at(_quoteToken).getOrDefault(user, BigInteger.ZERO);

        // Check deposits are sufficient to cover balances
        Context.require(userDepositedBase.compareTo(_baseValue) >= 0, TAG + ": Insufficient base asset funds deposited");
        Context.require(userDepositedQuote.compareTo(_quoteValue) >= 0, TAG + ": Insufficient quote asset funds deposited");

        BigInteger baseToCommit = _baseValue;
        BigInteger quoteToCommit = _quoteValue;

        // Initialize pool total variables
        BigInteger liquidity;
        BigInteger poolBaseAmount = BigInteger.ZERO;
        BigInteger poolQuoteAmount = BigInteger.ZERO;
        BigInteger poolLpAmount = BigInteger.ZERO;
        BigInteger userLpAmount = BigInteger.ZERO;

        // We need to only supply new base and quote in the pool ratio.
        // If there isn't a pool yet, we can form one with the supplied ratios.

        if (id == 0) {
            // No pool exists for this pair, we should create a new one.

            // Issue the next pool id to this pool
            if (!quoteCoins.contains(_quoteToken)) {
                Context.revert(TAG + " :  QuoteNotAllowed: Supplied quote token not in permitted set.");
            }
            Integer nextPoolNonce = nonce.get();
            poolId.at(_baseToken).set(_quoteToken, nextPoolNonce);
            poolId.at(_quoteToken).set(_baseToken, nextPoolNonce);
            id = nextPoolNonce;

            nonce.set(nextPoolNonce + 1);
            //TODO(galen): Discuss with scott whether we should even use active pools, and activate here if so.
            active.set(id, true);
            poolBase.set(id, _baseToken);
            poolQuote.set(id, _quoteToken);

            liquidity = (_baseValue.multiply(_quoteValue)).sqrt();
            Context.require(liquidity.compareTo(MIN_LIQUIDITY) >= 0,
                    TAG + ": Initial LP tokens must exceed " + MIN_LIQUIDITY.toString(10));
            MarketAdded(BigInteger.valueOf(id), _baseToken, _quoteToken, _baseValue, _quoteValue);
        } else {
            // Pool already exists, supply in the permitted order.
            Address poolBaseAddress = poolBase.get(id);
            Address poolQuoteAddress = poolQuote.get(id);

            Context.require((poolBaseAddress.equals(_baseToken)) && (poolQuoteAddress.equals(_quoteToken)), TAG + ": " +
                    "Must supply " + _baseToken.toString() + " as base and " + _quoteToken.toString() + " as quote");

            // We can only commit in the ratio of the pool. We determine this as:
            // Min(ratio of quote from base, ratio of base from quote)
            // Any assets not used are refunded

            poolBaseAmount = poolTotal.at(id).get(_baseToken);
            poolQuoteAmount = poolTotal.at(id).get(_quoteToken);
            poolLpAmount = poolLpTotal.get(id);

            BigInteger baseFromQuote = _quoteValue.multiply(poolBaseAmount).divide(poolQuoteAmount);
            BigInteger quoteFromBase = _baseValue.multiply(poolQuoteAmount).divide(poolBaseAmount);

            if (quoteToCommit.compareTo(_quoteValue) <= 0) {
                quoteToCommit = quoteFromBase;
            } else {
                baseToCommit = baseFromQuote;
            }

            BigInteger liquidityFromBase = (poolLpAmount.multiply(baseToCommit)).divide(poolBaseAmount);
            BigInteger liquidityFromQuote = (poolLpAmount.multiply(quoteToCommit)).divide(poolQuoteAmount);

            liquidity = liquidityFromBase.min(liquidityFromQuote);

            Context.require(liquidity.compareTo(BigInteger.ZERO) >= 0);

            // Set user previous LP Balance
            userLpAmount = balance.at(id).getOrDefault(user, BigInteger.ZERO);
        }

        // Apply the funds to the pool

        poolBaseAmount = poolBaseAmount.add(baseToCommit);
        poolQuoteAmount = poolQuoteAmount.add(quoteToCommit);

        poolTotal.at(id).set(_baseToken, poolBaseAmount);
        poolTotal.at(id).set(_quoteToken, poolQuoteAmount);

        // Deduct the user's deposit
        userDepositedBase = userDepositedBase.subtract(baseToCommit);
        deposit.at(_baseToken).set(user, userDepositedBase);
        userDepositedQuote = userDepositedQuote.subtract(quoteToCommit);
        deposit.at(_quoteToken).set(user, userDepositedQuote);

        // Credit the user LP Tokens
        userLpAmount = userLpAmount.add(liquidity);
        poolLpAmount = poolLpAmount.add(liquidity);

        balance.at(id).set(user, userLpAmount);
        poolLpTotal.set(id, poolLpAmount);

        if (isLockingPool(id)) {
            withdrawLock.at(id).set(user, BigInteger.valueOf(Context.getBlockTimestamp()));
        }
        Add(BigInteger.valueOf(id), user, liquidity, baseToCommit, quoteToCommit);

        TransferSingle(user, MINT_ADDRESS, user, BigInteger.valueOf(id), liquidity);

        BigInteger userQuoteHoldings =
                (balance.at(id).get(user).multiply(poolTotal.at(id).get(_quoteToken))).divide(totalSupply(BigInteger.valueOf(id)));
        if (isLockingPool(id)) {
            revertBelowMinimum(userQuoteHoldings, _quoteToken);
        }
        activeAddresses.get(id).add(user);
        updateAccountSnapshot(user, id);
        updateTotalSupplySnapshot(id);
        if (_baseToken.equals(baln.get())) {
            updateBalnSnapshot(id);
        }

        if (_withdraw_unused) {
            if (userDepositedBase.compareTo(BigInteger.ZERO) > 0) {
                withdraw(_baseToken, userDepositedBase);
            }

            if (userDepositedQuote.compareTo(BigInteger.ZERO) > 0) {
                withdraw(_quoteToken, userDepositedQuote);
            }
        }
    }

    @External
    public void withdrawSicxEarnings(@Optional BigInteger _value) {
        isOn(dexOn);
        if (_value == null) {
            _value = BigInteger.ZERO;
        }
        Address sender = Context.getCaller();
        BigInteger sicxEarning = sicxEarnings.getOrDefault(sender, BigInteger.ZERO);
        if (_value.equals(BigInteger.ZERO)) {
            _value = sicxEarning;
        }

        if (_value.compareTo(sicxEarning) > 0) {
            Context.revert(TAG + ": Insufficient balance.");
        }
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": InvalidAmountError: Please send a positive amount.");
        sicxEarnings.set(sender, sicxEarning.subtract(_value));
        Context.call(sicx.get(), "transfer", sender, _value);
        ClaimSicxEarnings(sender, _value);
    }

    @External
    public void addLpAddresses(BigInteger _poolId, Address[] _addresses) {
        only(governance);
        for (Address address : _addresses) {
            if (balanceOf(address, _poolId).compareTo(BigInteger.ZERO) > 0) {
                activeAddresses.get(_poolId.intValue()).add(address);
            }
        }
    }
}
