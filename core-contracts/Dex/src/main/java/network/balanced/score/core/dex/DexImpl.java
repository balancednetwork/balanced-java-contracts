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


import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.core.dex.db.NodeDB;
import network.balanced.score.lib.interfaces.DexXCall;
import network.balanced.score.lib.utils.*;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.utils.Check.isDexOn;
import static network.balanced.score.core.dex.utils.Check.isValidPercent;
import static network.balanced.score.core.dex.utils.Const.*;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.checkStatus;
import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static score.Context.require;

public class DexImpl extends AbstractDex {

    public DexImpl(Address _governance) {
        super(_governance);
        if (currentVersion.getOrDefault("").equals(Versions.DEX)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.DEX);
        NATIVE_NID = (String) Context.call(getXCall(), "getNetworkId");
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @Payable
    public void fallback() {
        Address user = Context.getCaller();
        if (user.equals(BalancedAddressManager.getDaofund())) {
            return;
        }
        isDexOn();
        checkStatus();

        BigInteger orderValue = Context.getValue();
        require(orderValue.compareTo(BigInteger.TEN.multiply(EXA)) >= 0,
                TAG + ": Minimum pool contribution is 10 ICX");


        BigInteger oldOrderValue = BigInteger.ZERO;
        BigInteger orderId = icxQueueOrderId.getOrDefault(user, BigInteger.ZERO);

        // Upsert Order, so we can bump to the back of the queue
        if (orderId.compareTo(BigInteger.ZERO) > 0) {
            NodeDB node = icxQueue.getNode(orderId);
            oldOrderValue = node.getSize();
            orderValue = orderValue.add(oldOrderValue);
            icxQueue.remove(orderId);
        }

        // Insert order to the back of the queue
        BigInteger nextTailId = icxQueue.getTailId().add(BigInteger.ONE);
        icxQueue.append(orderValue, user, nextTailId);
        icxQueueOrderId.set(user, nextTailId);

        // Update total ICX queue size
        BigInteger oldIcxTotal = icxQueueTotal.getOrDefault(BigInteger.ZERO);
        BigInteger currentIcxTotal = oldIcxTotal.add(orderValue).subtract(oldOrderValue);
        icxQueueTotal.set(currentIcxTotal);

        activeAddresses.get(SICXICX_POOL_ID).add(user);

        sendRewardsData(user, orderValue, currentIcxTotal);
    }

    @External
    public void cancelSicxicxOrder() {
        isDexOn();
        checkStatus();

        Address user = Context.getCaller();
        BigInteger orderId = icxQueueOrderId.getOrDefault(user, BigInteger.ZERO);

        require(orderId.compareTo(BigInteger.ZERO) > 0, TAG + ": No open order in sICX/ICX queue.");

        NodeDB order = icxQueue.getNode(orderId);
        BigInteger withdrawAmount = order.getSize();
        BigInteger oldIcxTotal = icxQueueTotal.get();
        BigInteger currentIcxTotal = oldIcxTotal.subtract(withdrawAmount);

        icxQueueTotal.set(currentIcxTotal);
        icxQueue.remove(orderId);
        icxQueueOrderId.set(user, null);
        activeAddresses.get(SICXICX_POOL_ID).remove(user);

        sendRewardsData(user, BigInteger.ZERO, currentIcxTotal);

        BalancedFloorLimits.verifyNativeWithdraw(withdrawAmount);
        Context.transfer(user, withdrawAmount);
    }

    private void sendRewardsData(Address user, BigInteger amount, BigInteger oldIcxTotal) {
        Context.call(getRewards(), "updateBalanceAndSupply", SICXICX_MARKET_NAME, oldIcxTotal, user.toString(), amount);
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        isDexOn();

        String unpackedData = new String(_data);
        require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");
        JsonObject json = Json.parse(unpackedData).asObject();
        String method = json.get("method").asString();
        Address fromToken = Context.getCaller();

        require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Invalid token transfer value");

        if (method.equals("_deposit")) {
            JsonObject params = json.get("params").asObject();
            String to = _from;
            if(params.get("address")!=null){
                to = params.get("address").asString();
            }
            deposit(fromToken, NetworkAddress.valueOf(to), _value);
        } else {
            // If no supported method was sent, revert the transaction
            Context.revert(100, TAG + ": Unsupported method supplied");
        }
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        DexXCall.process(this, _from, _data);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        isDexOn();
        checkStatus();

        // Parse the transaction data submitted by the user
        String unpackedData = new String(_data);
        require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        Address fromToken = Context.getCaller();
        require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Invalid token transfer value");

        // Call an internal method based on the "method" param sent in tokenFallBack
        switch (method) {
            case "_deposit": {
                NetworkAddress from = new NetworkAddress(NATIVE_NID, _from);
                deposit(fromToken, from, _value);
                break;
            }
            case "_swap_icx": {
                require(fromToken.equals(getSicx()),
                        TAG + ": InvalidAsset: _swap_icx can only be called with sICX");
                swapIcx(_from, _value);
                break;
            }
            case "_swap": {

                // Parse the slippage sent by the user in minimumReceive.
                // If none is sent, use the maximum.
                JsonObject params = json.get("params").asObject();
                BigInteger minimumReceive = BigInteger.ZERO;
                if (params.contains("minimumReceive")) {
                    minimumReceive = convertToNumber(params.get("minimumReceive"));
                    require(minimumReceive.signum() >= 0,
                            TAG + ": Must specify a positive number for minimum to receive");
                }

                // Check if an alternative recipient of the swap is set.
                Address receiver;
                if (params.contains("receiver")) {
                    receiver = Address.fromString(params.get("receiver").asString());
                } else {
                    receiver = _from;
                }

                // Get destination coin from the swap
                require(params.contains("toToken"), TAG + ": No toToken specified in swap");
                Address toToken = Address.fromString(params.get("toToken").asString());

                // Perform the swap
                exchange(fromToken, toToken, _from, receiver, _value, minimumReceive);
                break;
            }
            case "_donate": {
                JsonObject params = json.get("params").asObject();
                require(params.contains("toToken"), TAG + ": No toToken specified in swap");
                Address toToken = Address.fromString(params.get("toToken").asString());
                donate(fromToken, toToken, _value);
                break;
            }
            default:
                // If no supported method was sent, revert the transaction
                Context.revert(100, TAG + ": Unsupported method supplied");
                break;
        }
    }

    @External
    public void transfer(Address _to, BigInteger _value, BigInteger _id, @Optional byte[] _data) {
        isDexOn();
        checkStatus();
        if (_data == null) {
            _data = new byte[0];
        }
        NetworkAddress from = new NetworkAddress(NATIVE_NID, Context.getCaller());
        NetworkAddress to = new NetworkAddress(NATIVE_NID, _to);
        _transfer(from, to, _value, _id.intValue(), _data);
    }

    public void xWithdraw(String from, Address _token, BigInteger _value) {
        NetworkAddress sender = NetworkAddress.valueOf(from);
        _withdraw(sender, _token, _value);
    }

    @External
    public void withdraw(Address _token, BigInteger _value) {
        Address caller = Context.getCaller();
        NetworkAddress sender = new NetworkAddress(NATIVE_NID, caller);
        _withdraw(sender, _token, _value);
    }

    private void _withdraw(NetworkAddress sender, Address _token, BigInteger _value) {
        isDexOn();
        checkStatus();
        require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Must specify a positive amount");
        NetworkAddressDictDB<BigInteger> depositDetails = deposit.at(_token);

        BigInteger deposit_amount = depositDetails.getOrDefault(sender, BigInteger.ZERO);
        require(_value.compareTo(deposit_amount) <= 0, TAG + ": Insufficient Balance");

        depositDetails.set(sender, deposit_amount.subtract(_value));

        Withdraw(_token, sender.toString(), _value);
        BalancedFloorLimits.verifyWithdraw(_token, _value);

        //transfer the token
        TokenTransfer.transfer(_token, sender.toString(), _value);
    }

    @External(readonly = true)
    public BigInteger depositOfUser(Address _owner, Address _token) {
        NetworkAddress owner = new NetworkAddress(NATIVE_NID, _owner);
        NetworkAddressDictDB<BigInteger> depositDetails = deposit.at(_token);
        return depositDetails.getOrDefault(owner, BigInteger.ZERO);
    }

    public void xRemove(String from, BigInteger id, BigInteger value, @Optional Boolean _withdraw) {
        NetworkAddress _from = NetworkAddress.valueOf(from);
        _remove(id, _from, value, _withdraw);
    }

    @External
    public void remove(BigInteger _id, BigInteger _value, @Optional boolean _withdraw) {
        NetworkAddress owner = new NetworkAddress(NATIVE_NID, Context.getCaller());
        _remove(_id, owner, _value, _withdraw);
    }


    void _remove(BigInteger _id, NetworkAddress _user, BigInteger _value, @Optional boolean _withdraw) {
        isDexOn();
        checkStatus();
        Address baseToken = poolBase.get(_id.intValue());
        require(baseToken != null, TAG + ": invalid pool id");
        NetworkAddressDictDB<BigInteger> userLPBalance = balance.at(_id.intValue());
        BigInteger userBalance = userLPBalance.getOrDefault(_user, BigInteger.ZERO);

        require(active.getOrDefault(_id.intValue(), false), TAG + ": Pool is not active");
        require(_value.compareTo(BigInteger.ZERO) > 0, TAG + " Cannot withdraw a negative or zero balance");
        require(_value.compareTo(userBalance) <= 0, TAG + ": Insufficient balance");

        Address quoteToken = poolQuote.get(_id.intValue());
        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(_id.intValue());
        BigInteger totalBase = totalTokensInPool.get(baseToken);
        BigInteger totalQuote = totalTokensInPool.get(quoteToken);
        BigInteger totalLPToken = poolLpTotal.get(_id.intValue());

        BigInteger userQuoteLeft = ((userBalance.subtract(_value)).multiply(totalQuote)).divide(totalLPToken);

        if (userQuoteLeft.compareTo(getRewardableAmount(quoteToken)) < 0) {
            _value = userBalance;
        }

        BigInteger baseToWithdraw = _value.multiply(totalBase).divide(totalLPToken);
        BigInteger quoteToWithdraw = _value.multiply(totalQuote).divide(totalLPToken);

        BigInteger newBase = totalBase.subtract(baseToWithdraw);
        BigInteger newQuote = totalQuote.subtract(quoteToWithdraw);
        BigInteger newUserBalance = userBalance.subtract(_value);
        BigInteger newTotal = totalLPToken.subtract(_value);

        require(newTotal.compareTo(MIN_LIQUIDITY) >= 0,
                TAG + ": Cannot withdraw pool past minimum LP token amount");

        totalTokensInPool.set(baseToken, newBase);
        totalTokensInPool.set(quoteToken, newQuote);
        userLPBalance.set(_user, newUserBalance);
        poolLpTotal.set(_id.intValue(), newTotal);

        RemoveV2(_id, _user.toString(), _value, baseToWithdraw, quoteToWithdraw);
//        TransferSingle(_user, _user, MINT_ADDRESS, _id, _value);

        NetworkAddressDictDB<BigInteger> userBaseDeposit = deposit.at(baseToken);
        BigInteger depositedBase = userBaseDeposit.getOrDefault(_user, BigInteger.ZERO);
        userBaseDeposit.set(_user, depositedBase.add(baseToWithdraw));

        NetworkAddressDictDB<BigInteger> userQuoteDeposit = deposit.at(quoteToken);
        BigInteger depositedQuote = userQuoteDeposit.getOrDefault(_user, BigInteger.ZERO);
        userQuoteDeposit.set(_user, depositedQuote.add(quoteToWithdraw));

        if (_withdraw) {
            xWithdraw(_user.toString(), baseToken, baseToWithdraw);
            xWithdraw(_user.toString(), quoteToken, quoteToWithdraw);
        }

    }

    public void xAdd(String from, Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
                     @Optional Boolean _withdraw_unused, @Optional BigInteger _slippagePercentage) {
        isDexOn();
        checkStatus();
        isValidPercent(_slippagePercentage.intValue());

        addInternal(NetworkAddress.parse(from), _baseToken, _quoteToken, _baseValue, _quoteValue,
                _withdraw_unused, _slippagePercentage);
    }

    public void addInternal(NetworkAddress _from, Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
                            @Optional Boolean _withdraw_unused, @Optional BigInteger _slippagePercentage) {
        // We check if there is a previously seen pool with this id.
        // If none is found (return 0), we create a new pool.
        Integer id = poolId.at(_baseToken).getOrDefault(_quoteToken, 0);

        require(_baseToken != _quoteToken, TAG + ": Pool must contain two token contracts");
        // Check base/quote balances are valid
        require(_baseValue.compareTo(BigInteger.ZERO) > 0,
                TAG + ": Cannot send 0 or negative base token");
        require(_quoteValue.compareTo(BigInteger.ZERO) > 0,
                TAG + ": Cannot send 0 or negative quote token");

        BigInteger userDepositedBase = deposit.at(_baseToken).getOrDefault(_from, BigInteger.ZERO);
        BigInteger userDepositedQuote = deposit.at(_quoteToken).getOrDefault(_from, BigInteger.ZERO);

        // Check deposits are sufficient to cover balances
        require(userDepositedBase.compareTo(_baseValue) >= 0,
                TAG + ": Insufficient base asset funds deposited");
        require(userDepositedQuote.compareTo(_quoteValue) >= 0,
                TAG + ": Insufficient quote asset funds deposited");

        BigInteger baseToCommit = _baseValue;
        BigInteger quoteToCommit = _quoteValue;

        // Initialize pool total variables
        BigInteger liquidity;
        BigInteger poolBaseAmount = BigInteger.ZERO;
        BigInteger poolQuoteAmount = BigInteger.ZERO;
        BigInteger poolLpAmount = poolLpTotal.getOrDefault(id, BigInteger.ZERO);
        BigInteger userLpAmount = balance.at(id).getOrDefault(_from, BigInteger.ZERO);

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

            active.set(id, true);
            poolBase.set(id, _baseToken);
            poolQuote.set(id, _quoteToken);

            liquidity = (_baseValue.multiply(_quoteValue)).sqrt();
            require(liquidity.compareTo(MIN_LIQUIDITY) >= 0,
                    TAG + ": Initial LP tokens must exceed " + MIN_LIQUIDITY);
            MarketAdded(BigInteger.valueOf(id), _baseToken, _quoteToken, _baseValue, _quoteValue);
        } else {
            // Pool already exists, supply in the permitted order.
            Address poolBaseAddress = poolBase.get(id);
            Address poolQuoteAddress = poolQuote.get(id);

            require((poolBaseAddress.equals(_baseToken)) && (poolQuoteAddress.equals(_quoteToken)),
                    TAG + ": Must supply " + _baseToken.toString() + " as base and " + _quoteToken.toString() +
                            " as quote");

            // We can only commit in the ratio of the pool. We determine this as:
            // Min(ratio of quote from base, ratio of base from quote)
            // Any assets not used are refunded

            DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(id);
            poolBaseAmount = totalTokensInPool.get(_baseToken);
            poolQuoteAmount = totalTokensInPool.get(_quoteToken);

            BigInteger poolPrice = poolBaseAmount.multiply(EXA).divide(poolQuoteAmount);
            BigInteger priceOfAssetToCommit = baseToCommit.multiply(EXA).divide(quoteToCommit);

            require(
                    (priceOfAssetToCommit.subtract(poolPrice)).abs().compareTo(_slippagePercentage.multiply(poolPrice).divide(POINTS)) <= 0,
                    TAG + " : insufficient slippage provided"
            );

            BigInteger baseFromQuote = _quoteValue.multiply(poolBaseAmount).divide(poolQuoteAmount);
            BigInteger quoteFromBase = _baseValue.multiply(poolQuoteAmount).divide(poolBaseAmount);

            if (quoteFromBase.compareTo(_quoteValue) <= 0) {
                quoteToCommit = quoteFromBase;
            } else {
                baseToCommit = baseFromQuote;
            }

            BigInteger liquidityFromBase = (poolLpAmount.multiply(baseToCommit)).divide(poolBaseAmount);
            BigInteger liquidityFromQuote = (poolLpAmount.multiply(quoteToCommit)).divide(poolQuoteAmount);

            liquidity = liquidityFromBase.min(liquidityFromQuote);
            require(liquidity.compareTo(BigInteger.ZERO) >= 0,
                    TAG + ": LP tokens to mint is less than zero");
        }

        // Apply the funds to the pool
        poolBaseAmount = poolBaseAmount.add(baseToCommit);
        poolQuoteAmount = poolQuoteAmount.add(quoteToCommit);

        DictDB<Address, BigInteger> totalTokensInPool = poolTotal.at(id);
        totalTokensInPool.set(_baseToken, poolBaseAmount);
        totalTokensInPool.set(_quoteToken, poolQuoteAmount);

        // Deduct the user's deposit
        userDepositedBase = userDepositedBase.subtract(baseToCommit);
        deposit.at(_baseToken).set(_from, userDepositedBase);
        userDepositedQuote = userDepositedQuote.subtract(quoteToCommit);
        deposit.at(_quoteToken).set(_from, userDepositedQuote);

        // Credit the user LP Tokens
        userLpAmount = userLpAmount.add(liquidity);
        poolLpAmount = poolLpAmount.add(liquidity);

        balance.at(id).set(_from, userLpAmount);
        poolLpTotal.set(id, poolLpAmount);
        AddV2(BigInteger.valueOf(id), _from.toString(), liquidity, baseToCommit, quoteToCommit);
        HubTransferSingle(BigInteger.valueOf(id), MINT_ADDRESS.toString(), _from.toString(), liquidity, new byte[0]);

        if (userDepositedBase.compareTo(BigInteger.ZERO) > 0) {
            xWithdraw(_from.toString(), _baseToken, userDepositedBase);
        }

        if (userDepositedQuote.compareTo(BigInteger.ZERO) > 0) {
            xWithdraw(_from.toString(), _quoteToken, userDepositedQuote);
        }
    }


    @External
    public void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
                    @Optional boolean _withdraw_unused, @Optional BigInteger _slippagePercentage) {
        isDexOn();
        checkStatus();
        isValidPercent(_slippagePercentage.intValue());

        NetworkAddress user = new NetworkAddress(NATIVE_NID, Context.getCaller());
        addInternal(user, _baseToken, _quoteToken, _baseValue, _quoteValue,
                _withdraw_unused, _slippagePercentage);

    }

    @External
    public void withdrawSicxEarnings(@Optional BigInteger _value) {
        isDexOn();
        checkStatus();
        if (_value == null) {
            _value = BigInteger.ZERO;
        }
        Address sender = Context.getCaller();
        BigInteger sicxEarning = getSicxEarnings(sender);
        if (_value.equals(BigInteger.ZERO)) {
            _value = sicxEarning;
        }

        require(_value.compareTo(BigInteger.ZERO) > 0,
                TAG + ": InvalidAmountError: Please send a positive amount.");
        require(_value.compareTo(sicxEarning) <= 0, TAG + ": Insufficient balance.");

        sicxEarnings.set(sender, sicxEarning.subtract(_value));
        ClaimSicxEarnings(sender, _value);
        Address sICX = getSicx();
        BalancedFloorLimits.verifyWithdraw(sICX, _value);
        Context.call(sICX, "transfer", sender, _value);
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        checkStatus();
        Context.revert(TAG + ": IRC31 Tokens not accepted");
    }

    // TODO remove when dividends no longer use this
    @External(readonly = true)
    public BigInteger balanceOfAt(Address _account, BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {

        int poolId = _id.intValue();
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = accountBalanceSnapshot.at(poolId).at(_account);
        return snapshotValueAt(_snapshot_id, snapshot);

    }

    @External(readonly = true)
    public BigInteger totalSupplyAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {

        int poolId = _id.intValue();
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = totalSupplySnapshot.at(poolId);
        return snapshotValueAt(_snapshot_id, snapshot);
    }

    @External(readonly = true)
    public BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id, @Optional boolean _twa) {

        int poolId = _id.intValue();
        BranchDB<String, DictDB<BigInteger, BigInteger>> snapshot = balnSnapshot.at(poolId);
        return snapshotValueAt(_snapshot_id, snapshot);
    }

}
