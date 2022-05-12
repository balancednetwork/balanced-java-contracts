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

package network.balanced.score.util.stability;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.pow;

public class Stability {

    private static final String TAG = "Balanced Peg Stability";
    private static final Address EOA_ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
    private static final BigInteger HUNDRED_PERCENTAGE = BigInteger.valueOf(100).multiply(EXA);
    private static final int bnusdDecimals = 18;

    private static final String FEE_HANDLER_ADDRESS = "fee_handler_address";
    private static final String TOKEN_LIMIT = "token_limit";
    private static final String TOKEN_DECIMALS = "token_decimals";
    private static final String BNUSD_ADDRESS = "bnusd_address";
    private static final String FEE_IN = "fee_in";
    private static final String FEE_OUT = "fee_out";

    private final VarDB<Address> feeHandler = Context.newVarDB(FEE_HANDLER_ADDRESS, Address.class);
    private final DictDB<Address, BigInteger> tokenLimits = Context.newDictDB(TOKEN_LIMIT, BigInteger.class);
    private final DictDB<Address, Integer> decimals = Context.newDictDB(TOKEN_DECIMALS, Integer.class);
    private final VarDB<Address> bnusdAddress = Context.newVarDB(BNUSD_ADDRESS, Address.class);

    private final VarDB<BigInteger> feeIn = Context.newVarDB(FEE_IN, BigInteger.class);
    private final VarDB<BigInteger> feeOut = Context.newVarDB(FEE_OUT, BigInteger.class);

    public Stability(Address _feeHandler, Address _bnusd, BigInteger _feeIn, BigInteger _feeOut) {

        if (bnusdAddress.get() == null) {
            setFeeHandler(_feeHandler);
            setBnusdAddress(_bnusd);
            setFeeIn(_feeIn);
            setFeeOut(_feeOut);
        }
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
    public void setBnusdAddress(Address _address) {
        onlyOwner();
        isContract(_address);
        bnusdAddress.set(_address);
    }

    @External(readonly = true)
    public Address getBnusdAddress() {
        return bnusdAddress.get();
    }

    @External
    public void setFeeIn(BigInteger _feeIn) {
        onlyOwner();
        Context.require(_feeIn.compareTo(BigInteger.ZERO) >= 0, TAG + ": Fee in can't be negative");
        feeIn.set(_feeIn);
    }

    @External(readonly = true)
    public BigInteger getFeeIn() {
        return feeIn.get();
    }

    @External
    public void setFeeOut(BigInteger _feeOut) {
        onlyOwner();
        Context.require(_feeOut.compareTo(BigInteger.ZERO) >= 0, TAG + ": Fee out can't be negative");
        feeOut.set(_feeOut);
    }

    @External(readonly = true)
    public BigInteger getFeeOut() {
        return feeOut.get();
    }

    @External
    public void whitelistTokens(Address address, BigInteger limit) {
        onlyOwner();
        isContract(address);
        Context.require(limit.compareTo(BigInteger.ZERO) >= 0, TAG + ": Limit can't be set negative");
        tokenLimits.set(address, limit);
        int tokenDecimal = (int) Context.call(address, "decimals");
        decimals.set(address, tokenDecimal);
    }

    @External
    public void updateLimit(Address address, BigInteger limit) {
        onlyOwner();
        Context.require(limit.compareTo(BigInteger.ZERO) >= 0, TAG + ": Limit can't be set negative");
        Context.require(tokenLimits.get(address) != null, TAG + ": Address not white listed previously");
        tokenLimits.set(address, limit);
    }

    @External(readonly = true)
    public BigInteger getLimit(Address address) {
        return tokenLimits.get(address);
    }

    private void mintBnusd(BigInteger _amount, Address _asset, Address _user, Address bnusdAddress) {
        int assetInDecimals = decimals.get(_asset);
        BigInteger equivalentBnusd = (_amount.multiply(pow(BigInteger.TEN, bnusdDecimals))).divide(pow(BigInteger.TEN
                , assetInDecimals));
        BigInteger fee = (feeIn.get().multiply(equivalentBnusd)).divide(HUNDRED_PERCENTAGE);

        Context.call(bnusdAddress, "mint", equivalentBnusd);
        Context.call(bnusdAddress, "transfer", feeHandler.get(), fee);
        Context.call(bnusdAddress, "transfer", _user, equivalentBnusd.subtract(fee));

    }

    private void returnBnusd(Address _user, BigInteger _amount, byte[] _data, Address bnusdAddress) {
        String asset = new String(_data);
        Address assetToReturn = Address.fromString(asset);

        int assetOutDecimals = decimals.get(assetToReturn);
        BigInteger fee = (feeOut.get().multiply(_amount)).divide(HUNDRED_PERCENTAGE);
        BigInteger bnusdToConvert = _amount.subtract(fee);
        BigInteger equivalentAssetAmount =
                (bnusdToConvert.multiply(pow(BigInteger.TEN, assetOutDecimals))).divide(pow(BigInteger.TEN,
                        bnusdDecimals));
        Context.require(equivalentAssetAmount.compareTo(BigInteger.ZERO) > 0, TAG + ": Asset to return can't be zero " +
                "or less");

        Context.call(bnusdAddress, "burn", bnusdToConvert);
        Context.call(bnusdAddress, "transfer", feeHandler.get(), fee);
        Context.call(assetToReturn, "transfer", _user, equivalentAssetAmount);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        BigInteger limit = tokenLimits.get(token);
        Address bnusdAddress = this.bnusdAddress.get();
        Context.require(_value.compareTo(BigInteger.ZERO) > 0, TAG + ": Transfer amount must be greater than zero");
        Context.require(limit != null || token.equals(bnusdAddress), TAG + ": Only whitelisted tokens or bnusd is " +
                "accepted.");

        if (limit != null) {
            BigInteger assetInBalance = (BigInteger) Context.call(token, "balanceOf", Context.getAddress());
            Context.require(assetInBalance.add(_value).compareTo(limit) <= 0, TAG + ": Asset to exchange with bnusd " +
                    "limit crossed.");
        }

        if (token.equals(bnusdAddress)) {
            if (_from.equals(EOA_ZERO_ADDRESS)) {
                return;
            }
            returnBnusd(_from, _value, _data, token);
        } else {
            mintBnusd(_value, token, _from, bnusdAddress);
        }
    }
}
