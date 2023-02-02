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
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.*;
import score.annotation.External;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.pow;

public class StabilityImpl implements Stability {

    public static final String TAG = "Balanced Peg Stability";
    private static final Address EOA_ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
    private static final BigInteger HUNDRED_PERCENTAGE = BigInteger.valueOf(100).multiply(EXA);
    private static final BigInteger ONE_BNUSD = EXA;

    private static final String FEE_HANDLER_ADDRESS = "fee_handler_address";
    private static final String TOKEN_LIMIT = "token_limit";
    private static final String TOKEN_DECIMALS = "token_decimals";
    private static final String BNUSD_ADDRESS = "bnusd_address";
    private static final String FEE_IN = "fee_in";
    private static final String FEE_OUT = "fee_out";
    private static final String ACCEPTED_TOKENS = "accepted_tokens";
    private static final String VERSION = "version";

    private final VarDB<Address> feeHandler = Context.newVarDB(FEE_HANDLER_ADDRESS, Address.class);
    private final DictDB<Address, BigInteger> tokenLimits = Context.newDictDB(TOKEN_LIMIT, BigInteger.class);
    private final DictDB<Address, Integer> decimals = Context.newDictDB(TOKEN_DECIMALS, Integer.class);
    private final VarDB<Address> bnusdAddress = Context.newVarDB(BNUSD_ADDRESS, Address.class);

    private final VarDB<BigInteger> feeIn = Context.newVarDB(FEE_IN, BigInteger.class);
    private final VarDB<BigInteger> feeOut = Context.newVarDB(FEE_OUT, BigInteger.class);

    private final ArrayDB<Address> acceptedTokens = Context.newArrayDB(ACCEPTED_TOKENS, Address.class);
    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public StabilityImpl(Address _feeHandler, Address _bnusd, BigInteger _feeIn, BigInteger _feeOut) {

        if (bnusdAddress.get() == null) {
            setFeeHandler(_feeHandler);
            setBnusd(_bnusd);
            setFeeIn(_feeIn);
            setFeeOut(_feeOut);
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
    public void setFeeHandler(Address _address) {
        onlyOwner();
        isContract(_address);
        feeHandler.set(_address);
    }

    @External(readonly = true)
    public Address getFeeHandler() {
        return feeHandler.get();
    }

    @External
    public void setBnusd(Address _address) {
        onlyOwner();
        isContract(_address);
        bnusdAddress.set(_address);
    }

    @External(readonly = true)
    public Address getBnusd() {
        return bnusdAddress.get();
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

    @External(readonly = true)
    public BigInteger getFeeOut() {
        return feeOut.get();
    }

    @External
    public void whitelistTokens(Address _address, BigInteger _limit) {
        onlyOwner();
        isContract(_address);
        Context.require(_limit.compareTo(BigInteger.ZERO) >= 0, TAG + ": Limit can't be set negative");
        Context.require(tokenLimits.get(_address) == null, TAG + ": Already whitelisted");

        int tokenDecimal = ((BigInteger) Context.call(_address, "decimals")).intValue();
        decimals.set(_address, tokenDecimal);
        BigInteger actualLimit = _limit.multiply(pow(BigInteger.TEN, tokenDecimal));
        tokenLimits.set(_address, actualLimit);
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

    @External(readonly = true)
    public List<Address> getAcceptedTokens() {
        List<Address> acceptedTokens = new ArrayList<>();
        int totalTokens = this.acceptedTokens.size();
        for (int i = 0; i < totalTokens; i++) {
            acceptedTokens.add(this.acceptedTokens.get(i));
        }
        return acceptedTokens;
    }

    private void mintBnusd(BigInteger _amount, Address _asset, Address _user, Address bnusdAddress) {
        int assetInDecimals = decimals.get(_asset);
        BigInteger equivalentBnusd = (_amount.multiply(ONE_BNUSD)).divide(pow(BigInteger.TEN, assetInDecimals));
        Context.require(equivalentBnusd.compareTo(BigInteger.ZERO) > 0, TAG + ": Bnusd amount must be greater than " +
                "zero");
        BigInteger fee = (feeIn.get().multiply(equivalentBnusd)).divide(HUNDRED_PERCENTAGE);
        Context.require(fee.compareTo(BigInteger.ZERO) > 0, TAG + ": Fee must be greater than zero");

        Context.call(bnusdAddress, "mint", equivalentBnusd);
        Context.call(bnusdAddress, "transfer", feeHandler.get(), fee);
        Context.call(bnusdAddress, "transfer", _user, equivalentBnusd.subtract(fee));
    }

    private void returnBnusd(Address _user, BigInteger _amount, byte[] _data, Address bnusdAddress) {
        String asset = new String(_data);
        Address assetToReturn = Address.fromString(asset);

        Context.require(tokenLimits.get(assetToReturn) != null, TAG + ": Whitelisted tokens can only be sent");

        int assetOutDecimals = decimals.get(assetToReturn);
        BigInteger fee = (feeOut.get().multiply(_amount)).divide(HUNDRED_PERCENTAGE);
        Context.require(fee.compareTo(BigInteger.ZERO) > 0, TAG + ": Fee must be greater than zero");

        BigInteger bnusdToConvert = _amount.subtract(fee);
        BigInteger equivalentAssetAmount =
                (bnusdToConvert.multiply(pow(BigInteger.TEN, assetOutDecimals))).divide(ONE_BNUSD);
        Context.require(equivalentAssetAmount.compareTo(BigInteger.ZERO) > 0, TAG + ": Asset to return can't be zero " +
                "or less");

        BigInteger assetOutBalance = (BigInteger) Context.call(assetToReturn, "balanceOf", Context.getAddress());
        Context.require(equivalentAssetAmount.compareTo(assetOutBalance) <= 0, TAG + ": Insufficient asset out " +
                "balance in the contract");

        Context.call(bnusdAddress, "burn", bnusdToConvert);
        Context.call(bnusdAddress, "transfer", feeHandler.get(), fee);
        Context.call(assetToReturn, "transfer", _user, equivalentAssetAmount);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        if (_from.equals(EOA_ZERO_ADDRESS)) {
            return;
        }

        Address token = Context.getCaller();
        BigInteger limit = tokenLimits.get(token);
        Address bnusdAddress = this.bnusdAddress.get();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Transfer amount must be greater than zero");

        if (token.equals(bnusdAddress)) {
            returnBnusd(_from, _value, _data, token);
        } else if (limit != null) {
            BigInteger assetInBalance = (BigInteger) Context.call(token, "balanceOf", Context.getAddress());
            Context.require(assetInBalance.add(_value).compareTo(limit) <= 0, TAG + ": Asset to exchange with bnusd " +
                    "limit crossed.");
            mintBnusd(_value, token, _from, bnusdAddress);
        } else {
            throw new UserRevertException(TAG + ": Only whitelisted tokens or bnusd is accepted.");
        }

    }
}
