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

package network.balanced.score.core.batchDisbursement;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.batchDisbursement.Checks.onlyGovernance;
import static network.balanced.score.core.batchDisbursement.Checks.onlyOwner;

public class BatchDisbursement {

    public static final VarDB<Address> governance = Context.newVarDB("governance", Address.class);
    public final BranchDB<Address, DictDB<Address, BigInteger>> userClaimableTokens = Context.newBranchDB(
            "user_claimable_tokens", BigInteger.class);
    public final VarDB<Address> daofund = Context.newVarDB("daofund", Address.class);
    public final VarDB<Address> reserveFund = Context.newVarDB("reserve_fund", Address.class);
    public final EnumerableSetDB<Address> allowedTokenAddress = new EnumerableSetDB<>("allowed_token_address",
            Address.class);

    public static final String TAG = "BatchDisbursement";

    public BatchDisbursement(Address _governance) {
        Context.require(_governance.isContract(), TAG + ": Governance address should be a contract");
        governance.set(_governance);
    }

    public static class Disbursement {
        public Address tokenAddress;
        public BigInteger tokenAmount;
    }

    public static class DisbursementRecipient {
        public Address recipient;
        public Disbursement[] disbursement;
    }

    /*
     * Event Logs
     */

    @EventLog(indexed = 1)
    public void Claim(Address user, Address tokenAddress, BigInteger amount) {
    }

    @EventLog(indexed = 1)
    protected void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    /*
     * Setters and getters
     */
    @External(readonly = true)
    public String name() {
        return "Balanced Batch Disbursement";
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA Address. Contract address required");
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setDaofund(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA Address. Contract address required");
        daofund.set(_address);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return daofund.get();
    }

    @External
    public void setReserveFund(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA Address. Contract address required");
        reserveFund.set(_address);
    }

    @External(readonly = true)
    public Address getReserveFund() {
        return reserveFund.get();
    }

    @External(readonly = true)
    public Map<Address, BigInteger> getTokenBalances() {
        Map<Address, BigInteger> balances = new HashMap<>();
        for (int arrayIndex = 0; arrayIndex < allowedTokenAddress.length(); arrayIndex++) {
            Address tokenAddress = allowedTokenAddress.at(arrayIndex);
            BigInteger balance = (BigInteger) Context.call(tokenAddress, "balanceOf", Context.getAddress());
            balances.put(tokenAddress, balance);
        }
        return balances;
    }

    @External
    public void uploadDisbursementData(DisbursementRecipient[] disbursementRecipients) {
        onlyOwner();

        for (DisbursementRecipient disbursementRecipient : disbursementRecipients) {
            Address user = disbursementRecipient.recipient;
            DictDB<Address, BigInteger> userTokens = userClaimableTokens.at(user);
            for (Disbursement disbursement : disbursementRecipient.disbursement) {
                Address token = disbursement.tokenAddress;
                BigInteger currentAmount = userTokens.getOrDefault(token, BigInteger.ZERO);
                userTokens.set(disbursement.tokenAddress, currentAmount.add(disbursement.tokenAmount));
            }
        }
    }

    @External(readonly = true)
    public Map<String, Object> getDisbursementDetail(Address _user) {

        Map<String, Object> userClaimableTokens = new HashMap<>();
        DictDB<Address, BigInteger> userTokens = this.userClaimableTokens.at(_user);

        for (int arrayIndex = 0; arrayIndex < allowedTokenAddress.length(); arrayIndex++) {
            Address token = allowedTokenAddress.at(arrayIndex);
            userClaimableTokens.put(token.toString(), userTokens.getOrDefault(token, BigInteger.ZERO));
        }
        return Map.of("user", _user, "claimableTokens", userClaimableTokens);
    }

    @External
    public void claim() {
        Address sender = Context.getCaller();
        DictDB<Address, BigInteger> userTokens = userClaimableTokens.at(sender);

        for (int arrayIndex = 0; arrayIndex < allowedTokenAddress.length(); arrayIndex++) {
            Address token = allowedTokenAddress.at(arrayIndex);
            BigInteger tokenAmount = userTokens.getOrDefault(token, BigInteger.ZERO);
            if (tokenAmount != null && tokenAmount.signum() > 0) {
                userTokens.set(token, BigInteger.ZERO);
                sendToken(token, sender, tokenAmount, TAG + ": tokens claimed");
                Claim(sender, token, tokenAmount);
            }
        }
    }

    @External
    public void batchDisburse(Address _source) {
        onlyGovernance();
        Context.call(_source, "claim");
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address tokenContract = Context.getCaller();

        Context.require(_from.equals(getDaofund()) || _from.equals(getReserveFund()), TAG+ ": Only receivable from " +
                "daofund or reserve contract");
        if (!allowedTokenAddress.contains(tokenContract)) {
            allowedTokenAddress.add(tokenContract);
        }
    }

    private void sendToken(Address tokenAddress, Address to, BigInteger amount, String message) {
        try {
            Context.call(tokenAddress, "transfer", to, amount, new byte[0]);
            TokenTransfer(to, amount, message + ": " + amount + "" + tokenAddress + " tokens sent to " + to);
        } catch (Exception e) {
            Context.println(e.getMessage());
            Context.revert(TAG + ": Error in sending tokens to user- " + to);
        }
    }

}
