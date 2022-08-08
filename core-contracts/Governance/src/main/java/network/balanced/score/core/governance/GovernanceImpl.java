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

package network.balanced.score.core.governance;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.core.governance.proposal.ProposalDB;
import network.balanced.score.core.governance.proposal.ProposalManager;
import network.balanced.score.core.governance.utils.AddressManager;
import network.balanced.score.core.governance.utils.ArbitraryCallManager;
import network.balanced.score.core.governance.utils.EventLogger;
import network.balanced.score.core.governance.utils.SetupManager;
import network.balanced.score.lib.interfaces.Governance;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.onlyOwnerOrContract;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.optionalDefault;

public class GovernanceImpl implements Governance {
    public static final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public static final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public static final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public static final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> voteDuration = Context.newVarDB(VOTE_DURATION, BigInteger.class);
    public static final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public static final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);

    public GovernanceImpl() {
        if (launched.getOrDefault(null) == null) {
            launched.set(false);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Governance";
    }

    @External(readonly = true)
    public BigInteger getDay() {
        return _getDay();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        return ProposalManager.getVotersCount(vote_index);
    }

    @External(readonly = true)
    public Address getContractAddress(String contract) {
        return AddressManager.get(contract);
    }

    @External
    public void setVoteDuration(BigInteger duration) {
        onlyOwnerOrContract();
        voteDuration.set(duration);
    }

    @External(readonly = true)
    public BigInteger getVoteDuration() {
        return voteDuration.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setTimeOffset(BigInteger offset) {
        onlyOwnerOrContract();
        _setTimeOffset(offset);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setQuorum(BigInteger quorum) {
        onlyOwnerOrContract();
        Context.require(quorum.compareTo(BigInteger.ZERO) > 0, "Quorum must be between 0 and 100.");
        Context.require(quorum.compareTo(BigInteger.valueOf(100)) < 0, "Quorum must be between 0 and 100.");

        GovernanceImpl.quorum.set(quorum);
    }

    @External(readonly = true)
    public BigInteger getQuorum() {
        return quorum.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setVoteDefinitionFee(BigInteger fee) {
        onlyOwnerOrContract();
        bnusdVoteDefinitionFee.set(fee);
    }

    @External(readonly = true)
    public BigInteger getVoteDefinitionFee() {
        return bnusdVoteDefinitionFee.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setBalnVoteDefinitionCriterion(BigInteger percentage) {
        onlyOwnerOrContract();
        Context.require(percentage.compareTo(BigInteger.ZERO) >= 0, "Basis point must be between 0 and 10000.");
        Context.require(percentage.compareTo(BigInteger.valueOf(10000)) <= 0, "Basis point must be between 0 and 10000.");

        balnVoteDefinitionCriterion.set(percentage);
    }

    @External(readonly = true)
    public BigInteger getBalnVoteDefinitionCriterion() {
        return balnVoteDefinitionCriterion.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setAdmin(Address contractAddress, Address admin) {
        onlyOwnerOrContract();
        Context.call(contractAddress, "setAdmin", admin);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger snapshot, @Optional String actions) {
        actions = optionalDefault(actions, "[]");
        ProposalManager.defineVote(name, description, vote_start, snapshot, actions);
    }

    @External
    public void cancelVote(BigInteger vote_index) {
        ProposalManager.cancelVote(vote_index);
    }

    @External
    public void tryExecuteActions(String actions) {
        JsonArray actionsParsed = Json.parse(actions).asArray();
        Context.require(actionsParsed.size() <= maxActions(), TAG + ": Only " + maxActions() + " actions are allowed");
        ArbitraryCallManager.executeActions(actions);
        Context.revert(succsesfulVoteExecutionRevertID);
    }

    @External(readonly = true)
    public int maxActions() {
        return 5;
    }

    @External(readonly = true)
    public BigInteger getProposalCount() {
        return ProposalDB.getProposalCount();
    }

    @External(readonly = true)
    public List<Object> getProposals(@Optional BigInteger batch_size, @Optional BigInteger offset) {
        batch_size = optionalDefault(batch_size, BigInteger.valueOf(20));
        offset = optionalDefault(offset, BigInteger.ONE);
        return ProposalManager.getProposals(batch_size, offset);
    }

    @External
    public void castVote(BigInteger vote_index, boolean vote) {
        ProposalManager.castVote(vote_index, vote);
    }

    @External
    public void evaluateVote(BigInteger vote_index) {
        ProposalManager.evaluateVote(vote_index);
    }

    @External(readonly = true)
    public BigInteger getVoteIndex(String _name) {
        return ProposalDB.getProposalId(_name);
    }

    @External(readonly = true)
    public Map<String, Object> checkVote(BigInteger _vote_index) {
        return ProposalManager.checkVote(_vote_index);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user) {
        return ProposalManager.getVotesOfUser(vote_index, user);
    }

    @External(readonly = true)
    public BigInteger myVotingWeight(Address _address, BigInteger _day) {
        return Context.call(BigInteger.class, AddressManager.get("baln"), "stakedBalanceOfAt", _address, _day);
    }

    @External
    public void configureBalanced() {
        onlyOwner();
        SetupManager.configureBalanced();
    }

    @External
    public void launchBalanced() {
        onlyOwner();
        SetupManager.launchBalanced();
    }

    @External
    @Payable
    public void createBnusdMarket() {
        onlyOwner();
        SetupManager.createBnusdMarket();
    }

    @External
    public void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        onlyOwner();
        SetupManager.createBalnMarket(_bnUSD_amount, _baln_amount);
    }

    @External
    public void createBalnSicxMarket(BigInteger _sicx_amount, BigInteger _baln_amount) {
        onlyOwner();
        SetupManager.createBalnSicxMarket(_sicx_amount, _baln_amount);
    }

    @External
    public void setAddresses(BalancedAddresses _addresses) {
        onlyOwnerOrContract();
        AddressManager.setAddresses(_addresses);
    }

    @External(readonly = true)
    public Map<String, Address> getAddresses() {
        return AddressManager.getAddresses();
    }

    @External
    public void setAdmins() {
        onlyOwnerOrContract();
        AddressManager.setAdmins();
    }

    @External
    public void setContractAddresses() {
        onlyOwnerOrContract();
        AddressManager.setContractAddresses();
    }

    @External(readonly = true)
    public BigInteger getLaunchDay() {
        return launchDay.get();
    }

    @External(readonly = true)
    public BigInteger getLaunchTime() {
        return launchTime.get();
    }

    @External
    public void addCollateral(Address _token_address, boolean _active, String _peg, @Optional BigInteger _limit) {
        onlyOwnerOrContract();
        Address loansAddress = AddressManager.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");

        Address balancedOraclAddress = AddressManager.get("balancedOracle");
        Context.call(balancedOraclAddress, "setPeg", symbol, _peg);
        BigInteger price = Context.call(BigInteger.class, balancedOraclAddress, "getPriceInLoop", symbol);
        Context.require(price.compareTo(BigInteger.ZERO) > 0, "Balanced oracle return a invalid icx price for " + symbol + "/" + _peg);

        if (_limit.equals(BigInteger.ZERO)) {
            return;
        }

        Context.call(loansAddress, "setCollateralLimit", symbol, _limit);
    }

    @External
    public void addDexPricedCollateral(Address _token_address, boolean _active, @Optional BigInteger _limit) {
        onlyOwnerOrContract();
        Address loansAddress = AddressManager.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");
        BigInteger poolId = Context.call(BigInteger.class, AddressManager.get("dex"), "getPoolId", _token_address, AddressManager.get("bnUSD"));
        
        Address balancedOraclAddress = AddressManager.get("balancedOracle");
        Context.call(balancedOraclAddress, "addDexPricedAsset", symbol, poolId);
        BigInteger price = Context.call(BigInteger.class, balancedOraclAddress, "getPriceInLoop", symbol);
        Context.require(price.compareTo(BigInteger.ZERO) > 0, "Balanced oracle return a invalid icx price for " + symbol);

        if (_limit.equals(BigInteger.ZERO)) {
            return;
        }

        Context.call(loansAddress, "setCollateralLimit", symbol, _limit);
    }

    @External
    public void delegate(String contract, PrepDelegations[] _delegations) {
        onlyOwnerOrContract();
        Context.call(AddressManager.get(contract), "delegate", (Object) _delegations);
    }

    @External
    public void balwAdminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwnerOrContract();
        Context.call(AddressManager.get("bwt"), "adminTransfer", _from, _to, _value, _data);
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwnerOrContract();
        AddressManager.setAddress(_contract);
    }

    @External
    public void execute(String actions) {
        onlyOwner();
        ArbitraryCallManager.executeActions(actions);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }

    public static void call(Address targetAddress, String method, Object... params) {
        Context.call(targetAddress, method, params);
    }

    public static void call(BigInteger icxValue, Address targetAddress, String method, Object... params) {
        Context.call(icxValue, targetAddress, method, params);
    }

    public static <T>  T call(Class<T> retunType, Address targetAddress, String method, Object... params) {
        return Context.call(retunType, targetAddress, method, params);
    }

    public static BigInteger _getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(MICRO_SECONDS_IN_A_DAY);
    }

    public static void _setTimeOffset(BigInteger offset) {
        onlyOwnerOrContract();
        timeOffset.set(offset);
        Context.call(AddressManager.get("loans"), "setTimeOffset", offset);
        Context.call(AddressManager.get("rewards"), "setTimeOffset", offset);
        Context.call(AddressManager.get("dex"), "setTimeOffset", offset);
        Context.call(AddressManager.get("dividends"), "setTimeOffset", offset);
    }

    public static EventLogger getEventLogger() {
        return new EventLogger();
    }
}
