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

package network.balanced.score.core.governance;

import network.balanced.score.core.governance.proposal.ProposalDB;
import network.balanced.score.core.governance.proposal.ProposalManager;
import network.balanced.score.core.governance.utils.ContractManager;
import network.balanced.score.core.governance.utils.EmergencyManager;
import network.balanced.score.core.governance.utils.SetupManager;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.ArbitraryCallManager;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import score.annotation.EventLog;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.governance.utils.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

public class GovernanceImpl implements Governance {
    public static final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public static final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public static final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public static final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> maxVoteDuration = Context.newVarDB(MAX_VOTE_DURATION, BigInteger.class);
    public static final VarDB<BigInteger> minVoteDuration = Context.newVarDB(MIN_VOTE_DURATION, BigInteger.class);
    public static final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public static final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public static final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);
    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public GovernanceImpl() {
        if (launched.getOrDefault(null) == null) {
            launched.set(false);
            setVoteDurationLimits(BigInteger.ONE, BigInteger.valueOf(14));
            return;
        }

        if (currentVersion.getOrDefault("").equals(Versions.GOVERNANCE)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.GOVERNANCE);
    }

    @External(readonly = true)
    public String name() {
        return Names.GOVERNANCE;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External(readonly = true)
    public BigInteger getDay() {
        return _getDay();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        return ProposalManager.getVotersCount(vote_index);
    }

    @External
    public void changeScoreOwner(Address score, Address newOwner) {
        onlyOwnerOrContract();
        Address SYSTEM_SCORE_ADDRESS = getSystemScoreAddress();
        Context.call(SYSTEM_SCORE_ADDRESS, "setScoreOwner", score, newOwner);
    }

    @External
    public void setVoteDurationLimits(BigInteger min, BigInteger max) {
        onlyOwnerOrContract();
        Context.require(min.compareTo(BigInteger.ONE) >= 0, "Minimum vote duration has to be above 1");
        minVoteDuration.set(min);
        maxVoteDuration.set(max);
    }

    @External(readonly = true)
    public BigInteger getMinVoteDuration() {
        return minVoteDuration.get();
    }

    @External(readonly = true)
    public BigInteger getMaxVoteDuration() {
        return maxVoteDuration.get();
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
        Context.require(percentage.compareTo(BigInteger.valueOf(10000)) <= 0, "Basis point must be between 0 and " +
                "10000.");

        balnVoteDefinitionCriterion.set(percentage);
    }

    @External(readonly = true)
    public BigInteger getBalnVoteDefinitionCriterion() {
        return balnVoteDefinitionCriterion.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger duration,
            String forumLink, @Optional String transactions) {
        transactions = optionalDefault(transactions, "[]");
        ProposalManager.defineVote(name, description, vote_start, duration, forumLink, transactions);
    }

    @External
    public void cancelVote(BigInteger vote_index) {
        ProposalManager.cancelVote(vote_index);
    }

    @External
    public void tryExecuteTransactions(String transactions) {
        ArbitraryCallManager.executeTransactions(transactions);
        Context.revert(successfulVoteExecutionRevertID);
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
    public BigInteger totalBoostedBaln(BigInteger block) {
        return ProposalManager.totalBoostedBaln(block);
    }

    @External(readonly = true)
    public BigInteger myVotingWeight(Address _address, BigInteger block) {
        return ProposalManager.myVotingWeight(_address, block);
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

    @External(readonly = true)
    public Map<String, Address> getAddresses() {
        return ContractManager.getAddresses();
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return ContractManager.getAddress(name);
    }

    @External(readonly = true)
    public Address getContractAddress(String contract) {
        return ContractManager.get(contract);
    }

    @External
    public void setContractAddresses() {
        onlyOwnerOrContract();
        ContractManager.setContractAddresses();
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwnerOrContract();
        ContractManager.setAddress(_contract);
    }

    @External
    public void setAdmins() {
        onlyOwnerOrContract();
        ContractManager.setAdmins();
    }

    @External
    public void setAdmin(Address contractAddress, Address admin) {
        onlyOwnerOrContract();
        Context.call(contractAddress, "setAdmin", admin);
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
    public void addExternalContract(String name, Address address) {
        onlyOwnerOrContract();
        ContractManager.addContract(name, address);
    }

    @External
    public void storeContract(String name, byte[] dataHash, String deploymentParams) {
        onlyOwnerOrContract();
        ContractManager.storeContract(name, dataHash, deploymentParams);
    }

    @External
    public void deployStoredContract(String name, byte[] contractData) {
        String msg = ContractManager.deployStoredContract(name, contractData);
        ContractUpdated(name, msg);
    }

    @External
    public void deployTo(Address targetContract, byte[] contractData, String deploymentParams) {
        onlyOwnerOrContract();
        ContractManager.updateContract(targetContract, contractData, deploymentParams);
    }

    @External
    public void deploy(byte[] contractData, String deploymentParams) {
        onlyOwnerOrContract();
        ContractManager.newContract(contractData, deploymentParams);
    }

    @External
    public void execute(String transactions) {
        onlyOwner();
        ArbitraryCallManager.executeTransactions(transactions);
    }

    @External
    public void enable() {
        EmergencyManager.authorizeEnableAndDisable();
        Context.require(!EmergencyManager.canOnlyDisable(Context.getCaller()),
                "This address does not have permission to enable balanced");
        EmergencyManager.enable();
    }

    @External
    public void disable() {
        EmergencyManager.authorizeEnableAndDisable();
        EmergencyManager.disable();
    }

    @External
    public void blacklist(String address) {
        onlyOwnerOrContract();
        EmergencyManager.blacklist(address);
    }

    @External
    public void removeBlacklist(String address) {
        onlyOwnerOrContract();
        EmergencyManager.removeBlacklist(address);
    }

    @External(readonly = true)
    public Map<String, Boolean> getBlacklist() {
        return EmergencyManager.getBlacklist();
    }

    @External(readonly = true)
    public boolean isBlacklisted(String address) {
        return EmergencyManager.isBlacklisted(address);
    }

    @External(readonly = true)
    public boolean getStatus() {
        return EmergencyManager.getStatus();
    }

    @External
    public void checkStatus(String address) {
        EmergencyManager.checkStatus(address);
    }

    @External
    public void addAuthorizedCallerShutdown(Address address, @Optional boolean disableOnly) {
        onlyOwnerOrContract();
        EmergencyManager.addAuthorizedCallerShutdown(address, disableOnly);
    }

    @External
    public void removeAuthorizedCallerShutdown(Address address) {
        onlyOwnerOrContract();
        EmergencyManager.removeAuthorizedCallerShutdown(address);
    }

    @External
    public void setShutdownPrivilegeTimeLock(BigInteger days) {
        onlyOwnerOrContract();
        EmergencyManager.setShutdownPrivilegeTimeLock(days);
    }

    @External(readonly = true)
    public BigInteger getShutdownPrivilegeTimeLock() {
        return EmergencyManager.getShutdownPrivilegeTimeLock();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getAuthorizedCallersShutdown() {
        return EmergencyManager.getShutdownCallers();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }

    // External shorthand calls, could be done by a set of transactions
    @External
    public void addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
            BigInteger _debtCeiling, BigInteger _liquidationRatio, BigInteger _liquidatorFee,
            BigInteger _daoFundFee) {
        onlyOwnerOrContract();
        _addCollateral(_token_address, _active, _peg, _lockingRatio, _debtCeiling, _liquidationRatio,
                _liquidatorFee, _daoFundFee);
    }

    @External
    public void delegate(String contract, PrepDelegations[] _delegations) {
        onlyOwnerOrContract();
        Context.call(ContractManager.getAddress(contract), "delegate", (Object) _delegations);
    }

    @External
    public void balwAdminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwnerOrContract();
        Context.call(ContractManager.getAddress(Names.WORKERTOKEN), "adminTransfer", _from, _to, _value, _data);
    }

    @EventLog(indexed = 1)
    public void ContractUpdated(String name, String error) {

    }

    public static void call(Address targetAddress, String method, Object... params) {
        Context.call(targetAddress, method, params);
    }

    public static void call(BigInteger icxValue, Address targetAddress, String method, Object... params) {
        Context.call(icxValue, targetAddress, method, params);
    }

    public static <T> T call(Class<T> returnType, Address targetAddress, String method, Object... params) {
        return Context.call(returnType, targetAddress, method, params);
    }

    public static BigInteger _getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp())
                .subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(MICRO_SECONDS_IN_A_DAY);
    }

    public static void _setTimeOffset(BigInteger offset) {
        onlyOwnerOrContract();
        timeOffset.set(offset);
        Context.call(ContractManager.getAddress(Names.LOANS), "setTimeOffset", offset);
        Context.call(ContractManager.getAddress(Names.REWARDS), "setTimeOffset", offset);
        Context.call(ContractManager.getAddress(Names.DEX), "setTimeOffset", offset);
        Context.call(ContractManager.getAddress(Names.DIVIDENDS), "setTimeOffset", offset);
    }

    public void _addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
            BigInteger _debtCeiling, BigInteger _liquidationRatio, BigInteger _liquidatorFee,
            BigInteger _daoFundFee) {
        Address loansAddress = ContractManager.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");

        Address balancedOracleAddress = ContractManager.get("balancedOracle");
        Context.call(balancedOracleAddress, "setPeg", symbol, _peg);
        BigInteger price = Context.call(BigInteger.class, balancedOracleAddress, "getPriceInLoop", symbol);
        Context.require(price.compareTo(BigInteger.ZERO) > 0,
                "Balanced oracle return a invalid icx price for " + symbol + "/" + _peg);

        Context.call(loansAddress, "setDebtCeiling", symbol, _debtCeiling);
        _setLockingRatio(symbol, _lockingRatio);
        _setLiquidationRatio(symbol, _liquidationRatio);
        _setLiquidatorFee(symbol, _liquidatorFee);
        _setDaoFundFee(symbol, _daoFundFee);
    }

    public void _setLiquidationRatio(String _symbol, BigInteger _value) {
        Context.call(ContractManager.get("loans"), "setLiquidationRatio", _symbol, _value);
    }

    public void _setLiquidatorFee(String _symbol, BigInteger _value) {
        Context.call(ContractManager.get("loans"), "setLiquidatorFee", _symbol, _value);
    }

    public void _setDaoFundFee(String _symbol, BigInteger _value) {
        Context.call(ContractManager.get("loans"), "setLiquidationDaoFundFee", _symbol, _value);
    }

    public void _setLockingRatio(String _symbol, BigInteger _value) {
        Context.call(ContractManager.get("loans"), "setLockingRatio", _symbol, _value);
    }

    private static Address getSystemScoreAddress() {
        byte[] rawAddress = new byte[Address.LENGTH];
        rawAddress[0] = 1;
        return new Address(rawAddress);
    }
}
