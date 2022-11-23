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
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.utils.EnumerableSetDB;
import network.balanced.score.lib.utils.Names;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.Check.*;

public class DAOfundImpl implements DAOfund {

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    private static final String GOVERNANCE = "governance";
    private static final String ADDRESS = "address";
    private static final String FUND = "fund";
    private static final String AWARDS = "awards";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    // fund represents the total amount that is available to disburse
    private final DictDB<String, BigInteger> fund = Context.newDictDB(FUND, BigInteger.class);
    private final EnumerableSetDB<String> address = new EnumerableSetDB<>(ADDRESS, String.class);
    // Awards hold the amount that can be claimed by any user
    private final BranchDB<Address, DictDB<Address, BigInteger>> awards = Context.newBranchDB(AWARDS, BigInteger.class);

    public static final String TAG = Names.DAOFUND;

    public DAOfundImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
        }

        setGovernance(governance.get());
    }

    @External(readonly = true)
    public String name() {
        return Names.DAOFUND;
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External
    public void addAcceptedToken(Address address) {
        onlyGovernance();
        this.address.add(address.toString());
    }

    @External
    public void removeAcceptedToken(Address address) {
        onlyGovernance();
        this.address.remove(address.toString());
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External
    public void delegate(PrepDelegations[] prepDelegations) {
        onlyGovernance();
        Context.call(getStaking(), "delegate", (Object) prepDelegations);
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
    public boolean disburse(Address _recipient, Disbursement[] _amounts) {
        onlyGovernance();
        for (Disbursement asset : _amounts) {
            BigInteger amountInDaofund = fund.getOrDefault(asset.address.toString(), BigInteger.ZERO);
            BigInteger amountToBeClaimedByRecipient = awards.at(_recipient).getOrDefault(asset.address,
                    BigInteger.ZERO);

            boolean requiredCondition = amountInDaofund.compareTo(asset.amount) >= 0;
            Context.require(requiredCondition, TAG + ": Insufficient balance of asset " + asset.address.toString() +
                    " in DAOfund");

            awards.at(_recipient).set(asset.address, amountToBeClaimedByRecipient.add(asset.amount));
            fund.set(asset.address.toString(), amountInDaofund.subtract(asset.amount));
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
    public void claimRewards() {
        POLManager.claimRewards();
    }

    @External
    public void claimNetworkFees() {
        POLManager.claimNetworkFees();
    }

    @External
    public void supplyLiquidity(Address baseAddress, BigInteger baseAmount, Address quoteAddress,
                                BigInteger quoteAmount) {
        onlyGovernance();
        POLManager.supplyLiquidity(baseAddress, baseAmount, quoteAddress, quoteAmount);
    }

    @External
    public void withdrawLiquidity(BigInteger pid, BigInteger amount) {
        onlyGovernance();
        POLManager.withdrawLiquidity(pid, amount);
    }

    @External
    public void stakeLPTokens(BigInteger pid) {
        POLManager.stakeLPTokens(pid);
    }

    @External(readonly = true)
    public BigInteger getBalnEarnings() {
        return POLManager.getBalnEarnings();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getFeeEarnings() {
        return POLManager.getFeeEarnings();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        if (POLManager.isProcessingFees()) {
            POLManager.handleFee(_value);
            return;
        } else if (POLManager.isProcessingRewards()) {
            POLManager.handleRewards(_value);
            return;
        }

        String tokenContract = Context.getCaller().toString();
        if (address.contains(tokenContract)) {
            BigInteger tokenAmountInDAOfund = fund.getOrDefault(tokenContract, BigInteger.ZERO);
            fund.set(tokenContract, tokenAmountInDAOfund.add(_value));
        }
    }

    @Payable
    public void fallback() {
        Context.revert("ICX not accepted in this contract");
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
    }


}
