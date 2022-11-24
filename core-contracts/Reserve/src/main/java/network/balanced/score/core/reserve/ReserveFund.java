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

package network.balanced.score.core.reserve;

import network.balanced.score.lib.interfaces.Reserve;
import network.balanced.score.lib.structs.Disbursement;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.utils.Names;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.pow;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;

public class ReserveFund implements Reserve {

    private static final String GOVERNANCE = "governance";
    private static final String AWARDS = "awards";

    public static final String SICX_SYMBOL = "sICX";

    public static final String TAG = "BalancedReserveFund";
    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final BranchDB<Address, DictDB<Address, BigInteger>> awards = Context.newBranchDB(AWARDS, BigInteger.class);

    public ReserveFund(@Optional Address _governance) {
        if (governance.get() == null) {
            governance.set(_governance);
        }

        if (governance.get()  != null) {
            setGovernance(_governance);
        }
    }

    @EventLog(indexed = 2)
    protected void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    @External(readonly = true)
    public String name() {
        return Names.RESERVE;
    }

    @External
    public void transfer(Address _tokenAddress, Address _targetAddress, BigInteger _amount) {
        onlyGovernance();
        Context.call(_tokenAddress, "transfer", _targetAddress, _amount, new byte[0]);
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> getBalances() {
        Map<String, ?> collateralTokens = (Map<String, ?>) Context.call(getLoans(), "getCollateralTokens");
        Map<String, BigInteger> balances = new HashMap<>();
        for (String symbol : collateralTokens.keySet()) {
            BigInteger balance = getBalance(Address.fromString((String) collateralTokens.get(symbol)));
            balances.put(symbol, balance);
        }

        return balances;
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @External
    @SuppressWarnings("unchecked")
    public void redeem(Address _to, BigInteger _valueInLoop, String collateralSymbol) {
        Address sender = Context.getCaller();
        Address loans = getLoans();
        Context.require(sender.equals(loans), TAG + ": The redeem method can only be called by the Loans " +
                "SCORE.");

        Address oracle = getBalancedOracle();

        BigInteger remainingValue = _valueInLoop;
        Map<String, String> collateralTokens = (Map<String, String>) Context.call(getLoans(),
                "getCollateralTokens");

        String collateralAddress = collateralTokens.get(collateralSymbol);
        remainingValue = redeemAsset(collateralSymbol, collateralAddress, _to, oracle, remainingValue);
        if (remainingValue.equals(BigInteger.ZERO)) {
            return;
        }

        if (!collateralSymbol.equals(SICX_SYMBOL)) {
            remainingValue = redeemAsset(SICX_SYMBOL, collateralTokens.get(SICX_SYMBOL), _to, oracle, remainingValue);
            if (remainingValue.equals(BigInteger.ZERO)) {
                return;
            }
        }

        Address balnTokenAddress = getBaln();

        BigInteger balnRate = Context.call(BigInteger.class, oracle, "getPriceInLoop", "BALN");
        BigInteger balance = getBalance(balnTokenAddress);
        BigInteger balnToSend = remainingValue.multiply(EXA).divide(balnRate);

        Context.require(balance.compareTo(balnToSend) > 0, TAG + ": Unable to process request at this time.");

        sendToken(balnTokenAddress, _to, balnToSend, "Redeemed: ");
    }

    @External
    public boolean disburse(Address _recipient, Disbursement[] _amounts) {
        onlyGovernance();
        for (Disbursement asset : _amounts) {
            if (asset.address.equals(getSicx())) {
                BigInteger sicxAmount = getBalance(asset.address);
                BigInteger amountToBeClaimedByRecipient = awards.at(_recipient).getOrDefault(asset.address,
                        BigInteger.ZERO);

                Context.require(sicxAmount.compareTo(asset.amount) >= 0,
                        TAG + ":Insufficient balance of asset " + asset.address + " in the reserve fund.");
                awards.at(_recipient).set(asset.address, amountToBeClaimedByRecipient.add(asset.amount));
            } else if (asset.address.equals(getBaln())) {
                BigInteger balnAmount = getBalance(asset.address);
                BigInteger amountToBeClaimedByRecipient = awards.at(_recipient).getOrDefault(asset.address,
                        BigInteger.ZERO);

                Context.require(balnAmount.compareTo(asset.amount) >= 0,
                        TAG + ":Insufficient balance of asset " + asset.address + " in the reserve fund.");
                awards.at(_recipient).set(asset.address, amountToBeClaimedByRecipient.add(asset.amount));
            } else {
                Context.revert(TAG + ": Unavailable assets in the reserve fund requested.");
            }
        }
        return Boolean.TRUE;
    }

    @External
    public void claim() {
        Address sender = Context.getCaller();
        DictDB<Address, BigInteger> disbursement = awards.at(sender);

        Map<String, Address> assets = new HashMap<>();
        assets.put("BALN", getBaln());
        assets.put("sICX", getSicx());
        for (String symbol : assets.keySet()) {
            Address tokenAddress = assets.get(symbol);
            BigInteger amountToClaim = disbursement.getOrDefault(tokenAddress, BigInteger.ZERO);
            if (amountToClaim.signum() > 0) {
                disbursement.set(tokenAddress, BigInteger.ZERO);
                sendToken(tokenAddress, sender, amountToClaim, "Balanced Reserve Fund disbursement.");
            }
        }
    }

    private BigInteger redeemAsset(String symbol, String collateralAddress, Address to, Address oracle,
                                   BigInteger remainingValue) {
        BigInteger rate = Context.call(BigInteger.class, oracle, "getPriceInLoop", symbol);
        BigInteger balance = getBalance(collateralAddress);
        BigInteger decimals = getDecimals(collateralAddress);
        BigInteger totalValue = rate.multiply(balance).divide(decimals);
        if (totalValue.compareTo(remainingValue) >= 0) {
            BigInteger amountToSend = remainingValue.multiply(decimals).divide(rate);
            sendToken(collateralAddress, to, amountToSend, "To Loans: ");
            return BigInteger.ZERO;
        }

        sendToken(collateralAddress, to, balance, "To Loans: ");
        return remainingValue.subtract(totalValue);
    }

    private void sendToken(String tokenAddress, Address to, BigInteger amount, String message) {
        sendToken(Address.fromString(tokenAddress), to, amount, message);
    }

    private void sendToken(Address tokenAddress, Address to, BigInteger amount, String message) {
        String symbol = (String) Context.call(tokenAddress, "symbol");
        Context.call(tokenAddress, "transfer", to, amount, new byte[0]);
        TokenTransfer(to, amount, message + amount + symbol + " sent to " + to);
    }

    private BigInteger getBalance(Address tokenAddress) {
        return Context.call(BigInteger.class, tokenAddress, "balanceOf", Context.getAddress());
    }

    private BigInteger getBalance(String tokenAddress) {
        return getBalance(Address.fromString(tokenAddress));
    }

    private BigInteger getDecimals(String tokenAddress) {
        return getDecimals(Address.fromString(tokenAddress));
    }

    private BigInteger getDecimals(Address tokenAddress) {
        BigInteger decimals = Context.call(BigInteger.class, tokenAddress, "decimals");
        return pow(BigInteger.TEN, decimals.intValue());
    }
}
