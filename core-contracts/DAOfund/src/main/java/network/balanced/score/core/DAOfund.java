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

import network.balanced.score.core.utils.EnumerableSetDB;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.core.utils.Checks.*;
import static network.balanced.score.core.utils.Constants.*;

public class DAOfund {

    @EventLog(indexed = 2)
    public void FundTransfer(Address DESTINATION, BigInteger amount, String note) {
    }

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<Address> loansScore = Context.newVarDB(LOANS_SCORE, Address.class);
    private final DictDB<String, BigInteger> fund = Context.newDictDB(FUND, BigInteger.class);
    private final EnumerableSetDB<String> symbol = new EnumerableSetDB<>(SYMBOL, String.class);
    private final EnumerableSetDB<String> address = new EnumerableSetDB<>(ADDRESS, String.class);
    private final BranchDB<Address, DictDB<Address, BigInteger>> awards = Context.newBranchDB(AWARDS, BigInteger.class);

    public static final String TAG = "Balanced DAOfund";

    public static class Disbursement {
        public Address address;
        public BigInteger amount;
    }

    public DAOfund(@Optional Address _governance) {
        if (_governance != null) {
            ensureContract(_governance);
            governance.set(_governance);
        }
    }

    private void ensureContract(Address _address) {
        Context.require(_address.isContract(), TAG + "Address provided is an EOA address. A contract address is " +
                "required.");
    }

    @External
    @SuppressWarnings("unchecked")
    public void addSymbolToSetdb() {
        onlyOwner();
        Map<String, ?> assets = (Map<String, ?>) Context.call(loansScore.get(), "getAssetTokens");

        for (String symbol : assets.keySet()) {
            this.symbol.add(symbol);
        }
    }

    @External
    public void addAddressToSetdb() {
        onlyOwner();
        for (int symbolIndex = 0; symbolIndex < symbol.length(); symbolIndex++) {
            String tokenSymbol = symbol.at(symbolIndex);
            String address = TOKEN_ADDRESSES.getOrDefault(tokenSymbol, "");
            Context.require(!address.equals(""), TAG + ": Address not found for symbol: " + tokenSymbol);
            this.address.add(address);
            fund.set(address, fund.getOrDefault(tokenSymbol, BigInteger.ZERO));
            fund.set(tokenSymbol, BigInteger.ZERO);
        }
    }

    @External
    public void setTokenBalance(Address _token) {
        onlyOwner();
        BigInteger balance = (BigInteger) Context.call(_token, "balanceOf");
        if (balance.signum() > 0) {
            fund.set(_token.toString(), balance);
        }
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        ensureContract(_address);
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

    @External
    public void setLoans(Address _address) {
        onlyAdmin();
        ensureContract(_address);
        loansScore.set(_address);
    }

    @External(readonly = true)
    public Address getLoans() {
        return loansScore.get();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalances() {
        Map<String, BigInteger> balances = new HashMap<>();

        for (int addressIndex = 0; addressIndex < address.length(); addressIndex++) {
            String tokenAddress = address.at(addressIndex);
            balances.put(tokenAddress, fund.getOrDefault(tokenAddress, BigInteger.ZERO));
        }
        return balances;
    }

    @External
    public boolean disburse(Address _recipient, Disbursement[] _amounts) {
        onlyGovernance();
        for (Disbursement asset : _amounts) {
            BigInteger amountInDaofund = fund.getOrDefault(asset.address.toString(), BigInteger.ZERO);
            BigInteger amountToBeClaimedByRecipient = awards.at(_recipient).getOrDefault(asset.address,
                    BigInteger.ZERO);

            boolean requiredCondition = amountInDaofund.compareTo(asset.amount) > -1;
            Context.require(requiredCondition, TAG + ": Insufficient balance of asset " + asset.address.toString() +
                    " in DAOfund");

            awards.at(_recipient).set(asset.address, amountToBeClaimedByRecipient.add(asset.amount));
            fund.set(asset.address.toString(), amountInDaofund.subtract(asset.amount));
        }
        return Boolean.TRUE;
    }

    @External
    @SuppressWarnings("unchecked")
    public void claim() {
        Address sender = Context.getCaller();
        DictDB<Address, BigInteger> disbursement = awards.at(sender);

        Map<String, String> assets = (Map<String, String>) Context.call(loansScore.get(), "getAssetTokens");

        for (String symbol : assets.keySet()) {
            Address tokenAddress = Address.fromString(assets.get(symbol));
            BigInteger amountToClaim = disbursement.getOrDefault(tokenAddress, BigInteger.ZERO);
            if (amountToClaim.signum() > 0) {
                disbursement.set(tokenAddress, BigInteger.ZERO);
                sendToken(symbol, tokenAddress, sender, amountToClaim, "Balanced DAOfund disbursement");
            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        String tokenContract = Context.getCaller().toString();
        BigInteger tokenAmountInDAOfund = fund.getOrDefault(tokenContract, BigInteger.ZERO);
        if (!address.contains(tokenContract)) {
            address.add(tokenContract);
        }
        fund.set(tokenContract, tokenAmountInDAOfund.add(_value));
    }

    private void sendICX (Address to, BigInteger amount, String message) {
        try {
            Context.transfer(to, amount);
            FundTransfer(to, amount, message + " " + amount + " ICX sent to " + to);
        } catch (Exception e) {
            Context.println(TAG + ":Error in ICX transfer: " + e.getMessage());
            Context.revert(TAG + ": " + amount + " ICX not sent to " + to);
        }
    }

    private void sendToken(String symbol, Address token, Address to, BigInteger amount, String message) {
        try {
            Context.call(token, "transfer", to, amount, new byte[0]);
            TokenTransfer(to, amount, message + " " + amount + " " + symbol + " sent to " + to);
        } catch (Exception e) {
            Context.println(TAG + ":Error in Token Transfer: " + e.getMessage());
            Context.revert(TAG + " " + amount + " " + symbol + " not sent to " + to);
        }
    }

    @Payable
    public void fallback() {
        Context.revert("ICX not accepted in this contract");
    }

}
