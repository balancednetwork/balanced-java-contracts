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

package network.balanced.score.core.daofund;

import network.balanced.score.lib.interfaces.DAOfund;
import network.balanced.score.lib.interfaces.LoansScoreInterface;
import network.balanced.score.lib.structs.DisbursementString;
import network.balanced.score.lib.utils.EnumerableSetDB;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;

public class DAOfundImpl implements DAOfund {

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    private static final String GOVERNANCE = "governance";
    private static final String ADMIN = "admin";
    private static final String LOANS_SCORE = "loans_score";
    private static final String ADDRESS = "address";
    private static final String FUND = "fund";
    private static final String AWARDS = "awards";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private final VarDB<Address> loansScore = Context.newVarDB(LOANS_SCORE, Address.class);
    // fund represents the total amount that is available to disburse
    private final DictDB<String, BigInteger> fund = Context.newDictDB(FUND, BigInteger.class);
    private final EnumerableSetDB<String> address = new EnumerableSetDB<>(ADDRESS, String.class);
    // Awards hold the amount that can be claimed by any user
    private final BranchDB<Address, DictDB<Address, BigInteger>> awards = Context.newBranchDB(AWARDS, BigInteger.class);

    public static final String TAG = "Balanced DAOfund";

    public DAOfundImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
        }
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    //Setup methods
    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setLoans(Address _address) {
        only(admin);
        isContract(_address);
        loansScore.set(_address);
    }

    @External(readonly = true)
    public Address getLoans() {
        return loansScore.get();
    }

    /**
     * This method fetch the asset tokens from loans contract and add it to address enumerable set. Loans provide a
     * map of token symbol and its address.
     */
    @External
    public void addAddressToSetdb() {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(loansScore.get());
        Map<String, String> assets = loans.getAssetTokens();

        for (Map.Entry<String, String> tokenSymbolAddress : assets.entrySet()) {
            String address = tokenSymbolAddress.getValue();
            this.address.add(address);
        }
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

    @External(readonly = true)
    public Map<String, Object> getDisbursementDetail(Address _user) {

        Map<String, BigInteger> userClaimableTokens = new HashMap<>();
        DictDB<Address, BigInteger> userTokens = this.awards.at(_user);

        for (int addressIndex = 0; addressIndex < address.length(); addressIndex++) {
            Address token = Address.fromString(address.at(addressIndex));
            BigInteger tokenAmount = userTokens.getOrDefault(token, BigInteger.ZERO);
            userClaimableTokens.put(token.toString(), tokenAmount);
        }
        return Map.of("user", _user, "claimableTokens", userClaimableTokens);
    }

    //Operational methods

    /**
     * Disbursement method will be called from the governance SCORE when a vote passes approving an expenditure by
     * the DAO.
     *
     * @param _recipient Disbursement recipient address
     * @param _amounts   Amounts of each asset type to disburse
     */
    @External
    public boolean disburse(Address _recipient, DisbursementString[] _amounts) {
        only(governance);
        for (DisbursementString asset : _amounts) {
            BigInteger amountInDaofund = fund.getOrDefault(asset.address.toString(), BigInteger.ZERO);
            BigInteger amountToBeClaimedByRecipient = awards.at(_recipient).getOrDefault(asset.address,
                    BigInteger.ZERO);

            BigInteger amount = new BigInteger(asset.amount);
            boolean requiredCondition = amountInDaofund.compareTo(amount) >= 0;
            Context.require(requiredCondition, TAG + ": Insufficient balance of asset " + asset.address.toString() +
                    " in DAOfund");

            awards.at(_recipient).set(asset.address, amountToBeClaimedByRecipient.add(amount));
            fund.set(asset.address.toString(), amountInDaofund.subtract(amount));
        }
        return Boolean.TRUE;
    }

    /**
     * Any funds that are authorized for disbursement through Balanced Governance may be claimed using this method.
     */
    @External
    public void claim() {
        Address sender = Context.getCaller();
        DictDB<Address, BigInteger> disbursement = awards.at(sender);

        for (int addressIndex = 0; addressIndex < address.length(); addressIndex++) {
            Address tokenAddress = Address.fromString(address.at(addressIndex));
            BigInteger amountToClaim = disbursement.getOrDefault(tokenAddress, BigInteger.ZERO);
            if (amountToClaim.signum() > 0) {
                disbursement.set(tokenAddress, BigInteger.ZERO);
                Context.call(tokenAddress, "transfer", sender, amountToClaim, new byte[0]);
                TokenTransfer(sender, amountToClaim,
                        "Balanced DAOfund disbursement " + amountToClaim + " sent to " + sender.toString());
            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        String tokenContract = Context.getCaller().toString();
        Context.require(address.contains(tokenContract), TAG + ": Daofund can't receive this token");

        BigInteger tokenAmountInDAOfund = fund.getOrDefault(tokenContract, BigInteger.ZERO);
        fund.set(tokenContract, tokenAmountInDAOfund.add(_value));
    }

    @Payable
    public void fallback() {
        Context.revert("ICX not accepted in this contract");
    }

}
