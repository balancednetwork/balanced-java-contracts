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

package network.balanced.score.core.feehandler;

import network.balanced.score.lib.interfaces.FeeHandler;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import score.*;
import score.annotation.External;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.onlyGovernance;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Constants.EXA;

public class FeeHandlerImpl implements FeeHandler {
    public static final String TAG = "FeeHandler";

    private static final String DIVIDEND_TOKENS = "dividend_tokens";
    private static final String LAST_BLOCK = "last_block";
    private static final String BLOCK_INTERVAL = "block_interval";
    private static final String LAST_TXHASH = "last_txhash";
    private static final String GOVERNANCE = "governance";
    private static final String ENABLED = "enabled";

    private static final String SWAP_FEES_ACCRUED = "swap_fees_accrued";
    private static final String LOANS_FEES_ACCRUED = "loans_fees_accrued";
    private static final String STABILITY_FEES_ACCRUED = "stability_fees_accrued";
    public static final String VERSION = "version";

    public static final ArrayDB<Address> acceptedDividendsTokens = Context.newArrayDB(DIVIDEND_TOKENS, Address.class);
    private static final DictDB<Address, BigInteger> lastFeeProcessingBlock = Context.newDictDB(LAST_BLOCK,
            BigInteger.class);
    private static final VarDB<BigInteger> feeProcessingInterval = Context.newVarDB(BLOCK_INTERVAL, BigInteger.class);
    private static final VarDB<byte[]> lastTxhash = Context.newVarDB(LAST_TXHASH, byte[].class);
    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Boolean> enabled = Context.newVarDB(ENABLED, Boolean.class);

    private final DictDB<Address, BigInteger> swapFeesAccruedDB = Context.newDictDB(SWAP_FEES_ACCRUED,
            BigInteger.class);
    private final VarDB<BigInteger> loanFeesAccrued = Context.newVarDB(LOANS_FEES_ACCRUED, BigInteger.class);
    private final VarDB<BigInteger> stabilityFundFeesAccrued = Context.newVarDB(STABILITY_FEES_ACCRUED,
            BigInteger.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public FeeHandlerImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
            enabled.set(true);
            setGovernance(governance.get());
            FeeRouter.balnRouteLimit.set(BigInteger.valueOf(100).multiply(EXA));
        }

        if (currentVersion.getOrDefault("").equals(Versions.FEEHANDLER)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.FEEHANDLER);
    }

    @External(readonly = true)
    public String name() {
        return Names.FEEHANDLER;
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

    // acceptedDividendsTokens DB is used here to add the token types of fees generated by the DEX to swapFees DB
    @External
    public void setSwapFeesAccruedDB() {
        int acceptedDividendsTokenCount = acceptedDividendsTokens.size();
        for (int i = 0; i < acceptedDividendsTokenCount; i++) {
            Address thisToken = acceptedDividendsTokens.get(i);
            swapFeesAccruedDB.set(thisToken, swapFeesAccruedDB.getOrDefault(thisToken, BigInteger.ZERO));
        }
    }

    @External
    public void enable() {
        onlyGovernance();
        enabled.set(true);
    }

    @External
    public void disable() {
        onlyGovernance();
        enabled.set(false);
    }

    @External(readonly = true)
    public boolean isEnabled() {
        return enabled.getOrDefault(false);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        onlyGovernance();
        Context.require(_tokens.length <= 10, TAG + ": There can be a maximum of 10 accepted dividend tokens.");
        for (Address address : _tokens) {
            isContract(address);
        }

        final int currentTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < currentTokensCount; i++) {
            acceptedDividendsTokens.removeLast();
        }

        for (Address token : _tokens) {
            acceptedDividendsTokens.add(token);
        }
    }

    @External(readonly = true)
    public List<Address> getAcceptedDividendTokens() {
        List<Address> tokens = new ArrayList<>();
        int acceptedDividendsTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < acceptedDividendsTokensCount; i++) {
            tokens.add(acceptedDividendsTokens.get(i));
        }
        return tokens;
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        onlyGovernance();
        feeProcessingInterval.set(_interval);
    }

    @External(readonly = true)
    public BigInteger getFeeProcessingInterval() {
        return feeProcessingInterval.get();
    }

    @External
    public void setRoute(Address token, Address[] _path) {
        onlyGovernance();
        FeeRouter.setRoute(token, _path);
    }

    @External
    public void setBalnRouteLimit(BigInteger limit) {
        onlyGovernance();
        FeeRouter.balnRouteLimit.set(limit);
    }

    @External(readonly = true)
    public BigInteger getBalnRouteLimit() {
        return FeeRouter.balnRouteLimit.get();
    }

    @External
    public void addDefaultRoute(Address token) {
        FeeRouter.addDefaultRoute(token);
    }

    @External
    public void calculateRouteLimit(Address token) {
        FeeRouter.calculateRouteLimit(token);
    }


    @External(readonly = true)
    public BigInteger getRouteLimit(Address _fromToken) {
        return FeeRouter.routeLimit.getOrDefault(_fromToken, BigInteger.ZERO);
    }

    @External
    public void deleteRoute(Address token) {
        onlyGovernance();
        FeeRouter.deleteRoute(token);
    }

    @External
    public void routeFees() {
        FeeRouter.routeFees();
    }

    @External
    public void routeToken(Address token, Address[] path) {
        FeeRouter.routeToken(token, path);
    }

    @External(readonly = true)
    public List<String> getRoute(Address _fromToken) {
        return FeeRouter.getRoute(_fromToken);
    }

    @External(readonly = true)
    public List<Address> getRoutedTokens() {
        return FeeRouter.getRoutedTokens();
    }

    @External(readonly = true)
    public int getNextAllowedAddressIndex() {
        return FeeRouter.routeIndex.get();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        collectFeeData(token, _from, _value);

        if (Arrays.equals(lastTxhash.getOrDefault(new byte[0]), Context.getTransactionHash())) {
            return;
        } else if (!isTimeForFeeProcessing(token)) {
            return;
        }

        lastTxhash.set(Context.getTransactionHash());
        lastFeeProcessingBlock.set(token, BigInteger.valueOf(Context.getBlockHeight()));

        int acceptedDividendsTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < acceptedDividendsTokensCount; i++) {
            if (acceptedDividendsTokens.get(i).equals(token)) {
                BigInteger balance = getTokenBalance(token);
                BigInteger burnAmount = balance.divide(BigInteger.TWO);
                transferToken(token, getICONBurner(), burnAmount, new byte[0]);
                transferToken(token, getDividends(), balance.subtract(burnAmount), new byte[0]);
                return;
            }
        }
    }

    @External(readonly = true)
    public BigInteger getLoanFeesAccrued() {
        return loanFeesAccrued.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getStabilityFundFeesAccrued() {
        return stabilityFundFeesAccrued.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getSwapFeesAccruedByToken(Address token) {
        return swapFeesAccruedDB.getOrDefault(token, BigInteger.ZERO);
    }

    private void collectFeeData(Address sender, Address _from, BigInteger _value) {
        BigInteger accruedFees = swapFeesAccruedDB.get(sender);
        if (_from.equals(EOA_ZERO) && accruedFees != null) {
            loanFeesAccrued.set(loanFeesAccrued.getOrDefault(BigInteger.ZERO).add(_value));
        } else if (accruedFees != null && _from.equals(getDex())) {
            swapFeesAccruedDB.set(sender, accruedFees.add(_value));
        } else if (_from.equals(getStabilityFund())) {
            stabilityFundFeesAccrued.set(stabilityFundFeesAccrued.getOrDefault(BigInteger.ZERO).add(_value));
        }
    }

    private boolean isTimeForFeeProcessing(Address _token) {
        if (enabled.getOrDefault(Boolean.FALSE).equals(false)) {
            return false;
        }

        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger lastConversion = lastFeeProcessingBlock.getOrDefault(_token, BigInteger.ZERO);
        BigInteger targetBlock = lastConversion.add(feeProcessingInterval.getOrDefault(BigInteger.ZERO));

        return blockHeight.compareTo(targetBlock) >= 0;
    }

    private BigInteger getTokenBalance(Address _token) {
        return (BigInteger) Context.call(_token, "balanceOf", Context.getAddress());
    }

    private void transferToken(Address _token, Address _to, BigInteger _amount, byte[] _data) {
        Context.call(_token, "transfer", _to, _amount, _data);
    }
}
