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

package network.balanced.score.core;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.core.Checks.*;

public class ReserveFund {

    private static final String GOVERNANCE = "governance";
    private static final String ADMIN = "admin";
    private static final String LOANS_SCORE = "loans_score";
    private static final String BALN_TOKEN = "baln_token";
    private static final String SICX_TOKEN = "sicx_token";
    private static final String BALN = "baln";
    private static final String SICX = "sicx";

    public static final String TAG = "BalancedReserveFund";

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<Address> loansScore = Context.newVarDB(LOANS_SCORE, Address.class);
    private final VarDB<Address> balnToken = Context.newVarDB(BALN_TOKEN, Address.class);
    private final VarDB<Address> sicxToken = Context.newVarDB(SICX_TOKEN, Address.class);
    private final VarDB<BigInteger> baln = Context.newVarDB(BALN, BigInteger.class);
    private final VarDB<BigInteger> sicx = Context.newVarDB(SICX, BigInteger.class);

    public ReserveFund(@Optional Address governance) {
        if (governance != null) {
            Context.require(governance.isContract(), "ReserveFund: Governance address should be a contract");
            ReserveFund.governance.set(governance);
        }
    }

    @EventLog(indexed = 2)
    protected void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Reserve Fund";
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract address is " +
                "required.");
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        onlyGovernance();
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> getBalances() {
        Map<String, ?> assets = (Map<String, ?>) Context.call(loansScore.getOrDefault(defaultAddress),
                "getCollateralTokens");
        Map<String, BigInteger> balances = new HashMap<>();
        for (String symbol : assets.keySet()) {
            BigInteger balance = (BigInteger) Context.call(Address.fromString((String) assets.get(symbol)), "balanceOf",
                    Context.getAddress());
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                balances.put(symbol, balance);
            }
        }
        return balances;
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address tokenContract = Context.getCaller();
        if (tokenContract.equals(balnToken.get())) {
            baln.set(baln.getOrDefault(BigInteger.ZERO).add(_value));
        } else if (tokenContract.equals(sicxToken.get())) {
            sicx.set(sicx.getOrDefault(BigInteger.ZERO).add(_value));
        } else {
            Context.revert(TAG + ": The Reserve Fund can only accept BALN or sICX tokens. Deposit not accepted from " +
                    tokenContract + "Only accepted from BALN = " + balnToken.get() + "Or sICX = " + sicxToken.get());
        }
    }

    @External
    public BigInteger redeem(Address _to, BigInteger _amount, BigInteger _sicx_rate) {
        Address sender = Context.getCaller();
        Address loansScoreAddress = loansScore.get();
        Context.require(sender.equals(loansScoreAddress), TAG + ": The redeem method can only be called by the Loans SCORE.");

        BigInteger sicxAmount = sicx.getOrDefault(BigInteger.ZERO);
        BigInteger sicxToSend;
        Address balnTokenAddress = balnToken.get();
        Address sicxTokenAddress = sicxToken.get();

        if (_amount.compareTo(sicxAmount) < 1) {
            sicxToSend = _amount;
        } else {
            sicxToSend = sicxAmount;
            BigInteger balnRate = (BigInteger) Context.call(balnTokenAddress, "priceInLoop");
            BigInteger balnToSend = _amount.subtract(sicxAmount).multiply(_sicx_rate).divide(balnRate);
            BigInteger balnRemaining = baln.getOrDefault(BigInteger.ZERO).subtract(balnToSend);
            Context.require(balnRemaining.signum() > -1, TAG + ": Unable to process request at this time.");
            baln.set(balnRemaining);
            sendToken(balnTokenAddress, _to, balnToSend, "Redeemed: ");
        }
        BigInteger newSicxBalance = sicxAmount.subtract(sicxToSend);
        Context.require(newSicxBalance.signum()> -1, TAG + ": sICX balance can't be set negative");
        sicx.set(sicxAmount.subtract(sicxToSend));
        sendToken(sicxTokenAddress, loansScoreAddress, sicxToSend, "To Loans: ");
        return sicxToSend;
    }


    private void sendToken(Address tokenAddress, Address to, BigInteger amount, String message) {
        String symbol = "";
        try {
            symbol = (String) Context.call(tokenAddress, "symbol");
            Context.call(tokenAddress, "transfer", to, amount, new byte[0]);
            TokenTransfer(to, amount, message + amount + symbol + " sent to " + to);
        } catch (Exception e) {
            Context.revert(TAG + amount + symbol + " not sent to " + to);
        }
    }

    @Payable
    public void fallback() {
    }

}
