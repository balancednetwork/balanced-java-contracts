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

package network.balanced.score.util.stability;

import network.balanced.score.lib.interfaces.Stability;
import network.balanced.score.lib.utils.BalancedFloorLimits;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.FloorLimited;
import score.*;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.EventLog;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Math.pow;

public class StabilityImpl extends FloorLimited implements Stability {

    public static final String TAG = "Balanced Peg Stability";
    private static final Address EOA_ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
    private static final BigInteger HUNDRED_PERCENTAGE = BigInteger.valueOf(100).multiply(EXA);
    private static final BigInteger ONE_BNUSD = EXA;

    private static final String TOKEN_LIMIT = "token_limit";
    private static final String TOKEN_DECIMALS = "token_decimals";
    private static final String YIELD_BEARING = "yieldBearing";
    private static final String MAX_PRICE_DELAY = "maxPriceDelay";
    private static final String FEE_IN = "fee_in";
    private static final String FEE_OUT = "fee_out";
    private static final String ACCEPTED_TOKENS = "accepted_tokens";
    private static final String VERSION = "version";

    private final DictDB<Address, BigInteger> tokenLimits = Context.newDictDB(TOKEN_LIMIT, BigInteger.class);
    private final DictDB<Address, Integer> decimals = Context.newDictDB(TOKEN_DECIMALS, Integer.class);
    private final DictDB<Address, Boolean> yieldBearing = Context.newDictDB(YIELD_BEARING, Boolean.class);
    private final VarDB<BigInteger> maxPriceDelay = Context.newVarDB(MAX_PRICE_DELAY, BigInteger.class);

    private final VarDB<BigInteger> feeIn = Context.newVarDB(FEE_IN, BigInteger.class);
    private final VarDB<BigInteger> feeOut = Context.newVarDB(FEE_OUT, BigInteger.class);

    private final ArrayDB<Address> acceptedTokens = Context.newArrayDB(ACCEPTED_TOKENS, Address.class);
    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public StabilityImpl(Address _governance) {
        if (getAddressByName(Names.GOVERNANCE) == null) {
            setGovernance(_governance);
        }

        if (maxPriceDelay.get() == null) {
            maxPriceDelay.set(BigInteger.valueOf(7).multiply(MICRO_SECONDS_IN_A_DAY));
        }

        if (currentVersion.getOrDefault("").equals(Versions.STABILITY)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.STABILITY);
    }

    @External(readonly = true)
    public String name() {
        return Names.STABILITY;
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
    public void setFeeIn(BigInteger _feeIn) {
        onlyOwner();
        isValidPercentage(_feeIn);
        feeIn.set(_feeIn);
    }

    @External(readonly = true)
    public BigInteger getFeeIn() {
        return feeIn.get();
    }

    @External
    public void setFeeOut(BigInteger _feeOut) {
        onlyOwner();
        isValidPercentage(_feeOut);
        feeOut.set(_feeOut);
    }

    private void isValidPercentage(BigInteger _value) {
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0, TAG + ": Percentage can't be negative");
        Context.require(_value.compareTo(HUNDRED_PERCENTAGE) <= 0, TAG + ": Percentage can't be greater than hundred " +
                "percentage.");
    }

    @EventLog
    public void MintExcess(BigInteger amount) {
    }

    @External(readonly = true)
    public BigInteger getFeeOut() {
        return feeOut.get();
    }

    @External
    public void mintExcess() {
        Address bnUSD = getBnusd();
        Context.call(getLoans(), "claimInterest");
        BigInteger debt = Context.call(BigInteger.class, getLoans(), "getTotalDebt", "");
        BigInteger supply = Context.call(BigInteger.class, bnUSD, "xTotalSupply");

        BigInteger stabilityBacking = BigInteger.ZERO;
        int totalTokens = this.acceptedTokens.size();
        for (int i = 0; i < totalTokens; i++) {
            Address token = this.acceptedTokens.get(i);
            BigInteger assetDecimals = pow(BigInteger.TEN, decimals.get(token));
            BigInteger rate = getRate(token);
            BigInteger balance = Context.call(BigInteger.class, token, "balanceOf", Context.getAddress());
            BigInteger backing = rate.multiply(balance).divide(assetDecimals);
            stabilityBacking = stabilityBacking.add(backing);
        }
        BigInteger excess = stabilityBacking.subtract(supply.subtract(debt));
        if (excess.equals(BigInteger.ZERO)) {
            return;
        }

        Context.call(bnUSD, "mint", excess, new byte[0]);
        Context.call(bnUSD, "transfer", getFeehandler(), excess, new byte[0]);
        Context.call(getFeehandler(), "accrueStabilityYieldFee", excess);
        MintExcess(excess);
    }

    @External
    public void whitelistTokens(Address _address, BigInteger _limit, @Optional boolean yieldBearing) {
        onlyOwner();
        isContract(_address);
        Context.require(_limit.compareTo(BigInteger.ZERO) >= 0, TAG + ": Limit can't be set negative");
        Context.require(tokenLimits.get(_address) == null, TAG + ": Already whitelisted");

        int tokenDecimal = ((BigInteger) Context.call(_address, "decimals")).intValue();
        decimals.set(_address, tokenDecimal);
        BigInteger actualLimit = _limit.multiply(pow(BigInteger.TEN, tokenDecimal));
        tokenLimits.set(_address, actualLimit);
        this.yieldBearing.set(_address, yieldBearing);
        acceptedTokens.add(_address);
    }

    @External
    public void updateLimit(Address _address, BigInteger _limit) {
        onlyOwner();
        Context.require(_limit.compareTo(BigInteger.ZERO) >= 0, TAG + ": Limit can't be set negative");
        Context.require(tokenLimits.get(_address) != null, TAG + ": Address not white listed previously");

        int tokenDecimal = decimals.get(_address);
        BigInteger actualLimit = _limit.multiply(pow(BigInteger.TEN, tokenDecimal));
        tokenLimits.set(_address, actualLimit);
    }

    @External(readonly = true)
    public BigInteger getLimit(Address _address) {
        return tokenLimits.get(_address);
    }

    @External
    public void setMaxPriceDelay(BigInteger delayInDays) {
        onlyOwner();
        maxPriceDelay.set(delayInDays.multiply(MICRO_SECONDS_IN_A_DAY));
    }

    @External(readonly = true)
    public BigInteger getMaxPriceDelay() {
        return maxPriceDelay.get().divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External(readonly = true)
    public List<Address> getAcceptedTokens() {
        List<Address> acceptedTokens = new ArrayList<>();
        int totalTokens = this.acceptedTokens.size();
        for (int i = 0; i < totalTokens; i++) {
            acceptedTokens.add(this.acceptedTokens.get(i));
        }
        return acceptedTokens;
    }

    @SuppressWarnings("unchecked")
    private BigInteger getRate(Address _asset) {
        if (!yieldBearing.getOrDefault(_asset, false)) {
            return ONE_BNUSD;
        }

        String symbol = Context.call(String.class, _asset, "symbol");
        Map<String, BigInteger> priceData = (Map<String, BigInteger>) Context.call(getBalancedOracle(),
                "getPriceDataInUSD", symbol);
        BigInteger rate = priceData.get("rate");
        BigInteger timestamp = priceData.get("timestamp");
        BigInteger currentTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger delay = maxPriceDelay.get();
        if (delay != null) {
            Context.require(currentTimestamp.subtract(timestamp).compareTo(delay) <= 0,
                    TAG + ": Price for " + symbol + " has to be updated before using the stability fund");
        }

        return rate;
    }

    private void assetToBnUSD(BigInteger _amount, Address _asset, Address _user, Address bnusdAddress) {
        int assetInDecimals = decimals.get(_asset);
        BigInteger equivalentBnusd = (_amount.multiply(getRate(_asset))).divide(pow(BigInteger.TEN, assetInDecimals));
        Context.require(equivalentBnusd.compareTo(BigInteger.ZERO) > 0, TAG + ": Bnusd amount must be greater than " +
                "zero");
        BigInteger fee = (feeIn.getOrDefault(BigInteger.ZERO).multiply(equivalentBnusd)).divide(HUNDRED_PERCENTAGE);

        Context.call(bnusdAddress, "mint", equivalentBnusd, new byte[0]);
        if (fee.compareTo(BigInteger.ZERO) > 0) {
            Context.call(bnusdAddress, "transfer", getFeehandler(), fee, new byte[0]);
        }
        Context.call(bnusdAddress, "transfer", _user, equivalentBnusd.subtract(fee), new byte[0]);
    }

    private void yieldAssetToAsset(BigInteger _amount, Address inAsset, Address _user, Address outAsset) {
        BigInteger assetInDecimals = pow(BigInteger.TEN, decimals.get(inAsset));
        BigInteger assetOutDecimals = pow(BigInteger.TEN, decimals.get(outAsset));
        BigInteger fee = (feeIn.getOrDefault(BigInteger.ZERO).multiply(_amount)).divide(HUNDRED_PERCENTAGE);

        BigInteger amount = _amount.subtract(fee);
        BigInteger value = (amount.multiply(getRate(inAsset))).divide(assetInDecimals);
        BigInteger equivalentAmount = value.multiply(assetOutDecimals).divide(EXA);
        Context.require(equivalentAmount.compareTo(BigInteger.ZERO) > 0, TAG + ": Amount must be greater than zero");

        if (fee.compareTo(BigInteger.ZERO) > 0) {
            Context.call(inAsset, "transfer", getFeehandler(), fee, new byte[0]);
        }
        Context.call(outAsset, "transfer", _user, equivalentAmount, new byte[0]);
    }

    private void bnUSDToStable(Address _user, BigInteger _amount, byte[] _data, Address bnusdAddress) {
        String asset = new String(_data);
        Address assetToReturn = Address.fromString(asset);

        Context.require(tokenLimits.get(assetToReturn) != null, TAG + ": Whitelisted tokens can only be sent");

        int assetOutDecimals = decimals.get(assetToReturn);
        BigInteger fee = (feeOut.get().multiply(_amount)).divide(HUNDRED_PERCENTAGE);
        Context.require(fee.compareTo(BigInteger.ZERO) > 0, TAG + ": Fee must be greater than zero");

        BigInteger bnusdToConvert = _amount.subtract(fee);
        BigInteger equivalentAssetAmount = (bnusdToConvert.multiply(pow(BigInteger.TEN, assetOutDecimals)))
                .divide(getRate(assetToReturn));
        Context.require(equivalentAssetAmount.compareTo(BigInteger.ZERO) > 0, TAG + ": Asset to return can't be zero " +
                "or less");

        BigInteger assetOutBalance = (BigInteger) Context.call(assetToReturn, "balanceOf", Context.getAddress());
        Context.require(equivalentAssetAmount.compareTo(assetOutBalance) <= 0, TAG + ": Insufficient asset out " +
                "balance in the contract");
        Context.call(bnusdAddress, "burn", bnusdToConvert);
        Context.call(bnusdAddress, "transfer", getFeehandler(), fee, new byte[0]);
        BalancedFloorLimits.verifyWithdraw(assetToReturn, equivalentAssetAmount);
        Context.call(assetToReturn, "transfer", _user, equivalentAssetAmount, new byte[0]);
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), TAG + ": Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String to = json.get("receiver").asString();
        String toAsset = json.getString("toAsset", "");
        tokenFallback(Address.fromString(to), _value, toAsset.getBytes());
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        if (_from.equals(EOA_ZERO_ADDRESS)) {
            return;
        }

        Address token = Context.getCaller();
        BigInteger limit = tokenLimits.get(token);
        Address bnusdAddress = getBnusd();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Transfer amount must be greater than zero");

        if (token.equals(bnusdAddress)) {
            bnUSDToStable(_from, _value, _data, token);
            return;
        }

        Context.require(limit != null, TAG + ": Only whitelisted tokens or bnusd is accepted.");
        BigInteger assetInBalance = (BigInteger) Context.call(token, "balanceOf", Context.getAddress());
        Context.require(assetInBalance.compareTo(limit) <= 0, TAG + ": Asset to exchange with bnusd " +
                "limit crossed.");
        if (yieldBearing.getOrDefault(token, false) && _data != null) {
            String asset = new String(_data);
            if (_data.length > 0 && !asset.equals("None")) {
                Address toAsset = Address.fromString(asset);
                Context.require(tokenLimits.get(toAsset) != null, TAG + ": Only whitelisted tokens is allowed");
                Context.require(!yieldBearing.getOrDefault(toAsset, false),
                        TAG + ": Only swaps to non yield bering assets is allowed");
                yieldAssetToAsset(_value, token, _from, toAsset);
                return;
            }
        }

        assetToBnUSD(_value, token, _from, bnusdAddress);
    }
}
