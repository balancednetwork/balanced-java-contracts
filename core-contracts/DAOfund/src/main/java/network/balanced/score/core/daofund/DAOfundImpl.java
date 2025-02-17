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

package network.balanced.score.core.daofund;

import network.balanced.score.lib.interfaces.DAOfund;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.structs.ProtocolConfig;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.EnumerableSetDB;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.XCallUtils;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.*;

public class DAOfundImpl implements DAOfund {

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    private static final String GOVERNANCE = "governance";
    private static final String ADDRESS = "address";
    private static final String AWARDS = "awards";
    public static final String VERSION = "version";
    public static final String XCALL_FEE_PERMISSIONS = "xcall_fee_permissions";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);

    // Deprecated, kept for backward compatibility
    private final EnumerableSetDB<String> address = new EnumerableSetDB<>(ADDRESS, String.class);
    // Awards hold the amount that can be claimed by any user. Only used for now for claiming old disbursements
    private final BranchDB<Address, DictDB<Address, BigInteger>> awards = Context.newBranchDB(AWARDS, BigInteger.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    BranchDB<Address,DictDB<String, Boolean>> xCallFeePermissions = Context.newBranchDB(XCALL_FEE_PERMISSIONS, Boolean.class);

    public static final String TAG = Names.DAOFUND;

    public DAOfundImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            governance.set(_governance);
            setGovernance(governance.get());
            POLManager.setPOLSupplySlippage(BigInteger.valueOf(1_000));
        }

        if (this.currentVersion.getOrDefault("").equals(Versions.DAOFUND)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.DAOFUND);

    }

    @External(readonly = true)
    public String name() {
        return Names.DAOFUND;
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
    public void delegate(PrepDelegations[] prepDelegations) {
        onlyGovernance();
        Context.call(getStaking(), "delegate", (Object) prepDelegations);
    }

    // Deprecated, kept for backward compatibility
    @External(readonly = true)
    public Map<String, BigInteger> getBalances() {
        Map<String, BigInteger> balances = new HashMap<>();
        Address daoAddress = Context.getAddress();
        int addressSize = address.length();
        for (int addressIndex = 0; addressIndex < addressSize; addressIndex++) {
            String tokenAddressString = address.at(addressIndex);
            Address tokenAddress = Address.fromString(tokenAddressString);
            BigInteger balance = Context.call(BigInteger.class, tokenAddress, "balanceOf", daoAddress);
            balances.put(tokenAddressString, balance);
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

    @External
    public void disburse(Address token, Address recipient, BigInteger amount, @Optional byte[] data) {
        onlyGovernance();
        BigInteger balance = Context.call(BigInteger.class, token, "balanceOf", Context.getAddress());
        boolean requiredCondition = balance.compareTo(amount) >= 0;
        Context.require(requiredCondition, TAG + ": Insufficient balance of asset " + token.toString() +
                " in DAOfund");
        Context.call(token, "transfer", recipient, amount, data);
        TokenTransfer(recipient, amount,
                "Balanced DAOfund disbursement " + amount + " sent to " + recipient.toString());
    }

    @External
    public void disburseICX(Address recipient, BigInteger amount) {
        onlyGovernance();
        Context.transfer(recipient, amount);
    }

    /**
     * Any funds that are authorized for disbursement through Balanced Governance may be claimed using this method.
     */
    @External
    public void claim() {
        checkStatus();
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
        checkStatus();
        POLManager.claimRewards();
    }

    @External
    public void claimNetworkFees() {
        checkStatus();
        POLManager.claimNetworkFees();
    }

    @External
    public void supplyLiquidity(Address baseAddress, BigInteger baseAmount, Address quoteAddress,
                                BigInteger quoteAmount) {
        onlyGovernance();
        POLManager.supplyLiquidity(baseAddress, baseAmount, quoteAddress, quoteAmount);
    }

    @External
    public void unstakeLpTokens(BigInteger pid, BigInteger amount) {
        onlyGovernance();
        POLManager.unstake(pid, amount);
    }

    @External
    public void withdrawLiquidity(BigInteger pid, BigInteger amount) {
        onlyGovernance();
        POLManager.withdraw(pid, amount);
    }

    @External
    public void stakeLpTokens(BigInteger pid, @Optional BigInteger amount) {
        onlyGovernance();
        if (amount.equals(BigInteger.ZERO)) {
            amount = Context.call(BigInteger.class, getDex(), "balanceOf", Context.getAddress(), pid);
        }

        POLManager.stake(pid, amount);
    }

    @External
    public void setPOLSupplySlippage(BigInteger points) {
        onlyGovernance();
        POLManager.setPOLSupplySlippage(points);
    }

    @External(readonly = true)
    public BigInteger getPOLSupplySlippage() {
        return POLManager.getPOLSupplySlippage();
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
    public void setXCallFeePermission(Address contract, String net, boolean permission) {
        onlyGovernance();
        xCallFeePermissions.at(contract).set(net, permission);
    }

    @External(readonly = true)
    public boolean getXCallFeePermission(Address contract, String net) {
        return xCallFeePermissions.at(contract).getOrDefault(net, false);
    }

    @External
    public BigInteger claimXCallFee(String net, boolean response) {
        Address contract = Context.getCaller();
        Context.require(xCallFeePermissions.at(contract).getOrDefault(net, false), contract + " is not allowed to use fees from daofund");
        Map<String, String[]> protocol = XCallUtils.getProtocols(net);
        BigInteger fee = Context.call(BigInteger.class, BalancedAddressManager.getXCall(), "getFee", net, response, protocol.get(ProtocolConfig.sourcesKey));
        Context.require(fee.compareTo(Context.getBalance(Context.getAddress())) <= 0, "Daofund out of Balance" );
        if (fee.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        }

        Context.transfer(contract, fee);
        return fee;
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        if (POLManager.isProcessingFees()) {
            POLManager.handleFee(_value);
        } else if (POLManager.isProcessingRewards()) {
            POLManager.handleRewards(_value);
        }
    }

    @Payable
    public void fallback() {
    }

    @External
    public void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data) {
        checkStatus();
    }

    @External
    public void onXIRC31Received(String _operator, String _from, BigInteger _id, BigInteger _value, byte[] _data) {
        checkStatus();
    }

}
