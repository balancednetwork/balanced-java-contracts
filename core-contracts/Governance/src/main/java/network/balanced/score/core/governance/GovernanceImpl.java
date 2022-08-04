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
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;

import network.balanced.score.lib.interfaces.Governance;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Check.onlyOwnerOrContract;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.optionalDefault;
import static network.balanced.score.lib.utils.Math.pow;

public class GovernanceImpl implements Governance {
    public final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public final VarDB<BigInteger> voteDuration = Context.newVarDB(VOTE_DURATION, BigInteger.class);
    public final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);

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
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
                "for_voters", proposal.forVotersCount.getOrDefault(BigInteger.ZERO),
                "against_voters", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO)
        );
    }

    @External(readonly = true)
    public Address getContractAddress(String contract) {
        return Addresses.get(contract);
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
        timeOffset.set(offset);
        Context.call(Addresses.get("loans"), "setTimeOffset", offset);
        Context.call(Addresses.get("rewards"), "setTimeOffset", offset);
        Context.call(Addresses.get("dex"), "setTimeOffset", offset);
        Context.call(Addresses.get("dividends"), "setTimeOffset", offset);
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

        this.quorum.set(quorum);
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
    public void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0, "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get().equals(ProposalStatus.STATUS[ProposalStatus.ACTIVE]), "Proposal can be cancelled only from active status.");
        Context.require(Context.getCaller().equals(proposal.proposer.get()) ||
                        Context.getCaller().equals(Context.getOwner()),
                "Only owner or proposer may call this method.");
        if (proposal.startSnapshot.get().compareTo(getDay()) <= 0) {
            Context.require(Context.getCaller().equals(Context.getOwner()),
                    "Only owner can cancel a vote that has started.");
        }

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger snapshot, @Optional String actions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");
        Context.require(vote_start.compareTo(getDay()) > 0, "Vote cannot start at or before the current day.");
        Context.require(getDay().compareTo(snapshot) <= 0 &&
                        snapshot.compareTo(vote_start) < 0,
                "The reference snapshot must be in the range: [current_day (" + getDay() + "), " +
                        "start_day - 1 (" + vote_start.subtract(BigInteger.ONE) + ")].");

        actions = optionalDefault(actions, "[]");
        BigInteger voteIndex = ProposalDB.getProposalId(name);
        Context.require(voteIndex.equals(BigInteger.ZERO), "Poll name " + name + " has already been used.");
        Context.require(checkBalnVoteCriterion(Context.getCaller()), "User needs at least " + balnVoteDefinitionCriterion.get().divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.");
        verifyActions(actions);

        Context.call(Addresses.get("bnUSD"), "govTransfer", Context.getCaller(), Addresses.get("daofund"), bnusdVoteDefinitionFee.getOrDefault(BigInteger.ONE), new byte[0]);

        ProposalDB.createProposal(
                name,
                description,
                Context.getCaller(),
                quorum.get().multiply(EXA).divide(BigInteger.valueOf(100)),
                MAJORITY,
                snapshot,
                vote_start,
                vote_start.add(voteDuration.get()),
                actions,
                bnusdVoteDefinitionFee.get()
        );
    }

    @External
    public void tryExecuteActions(String actions) {
        JsonArray actionsParsed = Json.parse(actions).asArray();
        Context.require(actionsParsed.size() <= maxActions(), TAG + ": Only " + maxActions() + " actions are allowed");
        executeActions(actions);
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

        BigInteger start = BigInteger.ONE.max(offset);
        BigInteger end = batch_size.add(start).subtract(BigInteger.ONE).min(getProposalCount());
        List<Object> proposals = new ArrayList<>();
        for (BigInteger i = start; i.compareTo(end) <= 0; i = i.add(BigInteger.ONE)) {
            proposals.add(checkVote(i));
        }

        return proposals;
    }

    @External
    public void castVote(BigInteger vote_index, boolean vote) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require((vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        getDay().compareTo(proposal.startSnapshot.getOrDefault(BigInteger.ZERO)) >= 0 &&
                        getDay().compareTo(proposal.endSnapshot.getOrDefault(BigInteger.ZERO)) < 0) &&
                        proposal.active.getOrDefault(false),
                TAG + " :This is not an active poll.");

        Address from = Context.getCaller();
        BigInteger snapshot = proposal.voteSnapshot.get();

        BigInteger totalVote = Context.call(BigInteger.class, Addresses.get("baln"), "stakedBalanceOfAt", from, snapshot);

        Context.require(!totalVote.equals(BigInteger.ZERO), TAG + "Balanced tokens need to be staked to cast the vote.");

        BigInteger userForVotes = proposal.forVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger userAgainstVotes = proposal.againstVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger totalForVotes = proposal.totalForVotes.getOrDefault(BigInteger.ZERO);
        BigInteger totalAgainstVotes = proposal.totalAgainstVotes.getOrDefault(BigInteger.ZERO);
        BigInteger totalForVotersCount = proposal.forVotersCount.getOrDefault(BigInteger.ZERO);
        BigInteger totalForAgainstVotersCount = proposal.againstVotersCount.getOrDefault(BigInteger.ZERO);
        BigInteger totalFor;
        BigInteger totalAgainst;
        boolean isFirstTimeVote = userForVotes.signum() == 0 && userAgainstVotes.signum() == 0;

        if (vote) {
            proposal.forVotesOfUser.set(from, totalVote);
            proposal.againstVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath

            totalFor = totalForVotes.add(totalVote).subtract(userForVotes);
            totalAgainst = totalAgainstVotes.subtract(userAgainstVotes);
            if (isFirstTimeVote) {
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            } else if (userAgainstVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.subtract(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            }
        } else {
            proposal.againstVotesOfUser.set(from, totalVote);
            proposal.forVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath
            totalFor = totalForVotes.subtract(userForVotes);
            totalAgainst = totalAgainstVotes.add(totalVote).subtract(userAgainstVotes);

            if (isFirstTimeVote) {
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
            } else if (userForVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.subtract(BigInteger.ONE));
            }
        }

        proposal.totalForVotes.set(totalFor);
        proposal.totalAgainstVotes.set(totalAgainst);

        VoteCast(proposal.name.get(), vote, from, totalVote, totalFor, totalAgainst);
    }

    @External
    public void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        vote_index.compareTo(ProposalDB.getProposalCount()) <= 0,
                TAG + ": There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endSnapshot.get();
        String actions = proposal.actions.get();
        BigInteger majority = proposal.majority.get();

        Context.require(getDay().compareTo(endSnap) >= 0, TAG + ": Voting period has not ended.");
        Context.require(proposal.active.get(), TAG + ": This proposal is not active");


        Map<String, Object> result = checkVote(vote_index);
        proposal.active.set(false);

        BigInteger forVotes = (BigInteger) result.get("for");
        BigInteger againstVotes = (BigInteger) result.get("against");
        if (forVotes.add(againstVotes).compareTo(proposal.quorum.get()) < 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.NO_QUORUM]);
            return;
        }

        //TODO SafeMath
        BigInteger percentageFor = EXA.subtract(majority).multiply(forVotes);
        BigInteger percentageAgainst = majority.multiply(againstVotes);
        if (percentageFor.compareTo(percentageAgainst) <= 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.DEFEATED]);
            return;
        } else if (actions.equals("[]")) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED]);
            _refundVoteDefinitionFee(proposal);
            return;
        }

        try {
            executeActions(actions);
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.EXECUTED]);
        } catch (Exception e) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.FAILED_EXECUTION]);
        }

        _refundVoteDefinitionFee(proposal);
    }

    @External(readonly = true)
    public BigInteger getVoteIndex(String _name) {
        return ProposalDB.getProposalId(_name);
    }

    @External(readonly = true)
    public Map<String, Object> checkVote(BigInteger _vote_index) {
        if (_vote_index.compareTo(BigInteger.ONE) < 0 ||
                _vote_index.compareTo(ProposalDB.getProposalCount()) > 0) {
            return Map.of();
        }

        ProposalDB proposal = new ProposalDB(_vote_index);
        BigInteger totalBaln = totalBaln(proposal.voteSnapshot.getOrDefault(BigInteger.ZERO));

        BigInteger nrForVotes = BigInteger.ZERO;
        BigInteger nrAgainstVotes = BigInteger.ZERO;
        if (!totalBaln.equals(BigInteger.ZERO)) {
            nrForVotes = proposal.totalForVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBaln);
            nrAgainstVotes = proposal.totalAgainstVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBaln);
        }

        Map<String, Object> voteData = new HashMap<>(16);

        voteData.put("id", _vote_index);
        voteData.put("name", proposal.name.getOrDefault(""));
        voteData.put("proposer", proposal.proposer.getOrDefault(EOA_ZERO));
        voteData.put("description", proposal.description.getOrDefault(""));
        voteData.put("majority", proposal.majority.getOrDefault(BigInteger.ZERO));
        voteData.put("status", proposal.status.getOrDefault(""));
        voteData.put("vote snapshot", proposal.voteSnapshot.getOrDefault(BigInteger.ZERO));
        voteData.put("start day", proposal.startSnapshot.getOrDefault(BigInteger.ZERO));
        voteData.put("end day", proposal.endSnapshot.getOrDefault(BigInteger.ZERO));
        voteData.put("actions", proposal.actions.getOrDefault(""));
        voteData.put("quorum", proposal.quorum.getOrDefault(BigInteger.ZERO));
        voteData.put("for", nrForVotes);
        voteData.put("against", nrAgainstVotes);
        voteData.put("for_voter_count", proposal.forVotersCount.getOrDefault(BigInteger.ZERO));
        voteData.put("against_voter_count", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO));
        voteData.put("fee_refund_status", proposal.feeRefunded.getOrDefault(false));

        return voteData;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
                "for", proposal.forVotesOfUser.getOrDefault(user, BigInteger.ZERO),
                "against", proposal.againstVotesOfUser.getOrDefault(user, BigInteger.ZERO)
        );
    }

    @External(readonly = true)
    public BigInteger myVotingWeight(Address _address, BigInteger _day) {
        return Context.call(BigInteger.class, Addresses.get("baln"), "stakedBalanceOfAt", _address, _day);
    }

    @External
    public void configureBalanced() {
        onlyOwner();
        for (Map<String, Object> asset : ASSETS) {
            Address tokenAddress = Addresses.get((String) asset.get("address"));
            Context.call(
                    Addresses.get("loans"),
                    "addAsset",
                    tokenAddress,
                    asset.get("active"),
                    asset.get("collateral")
            );
            Context.call(Addresses.get("dividends"), "addAcceptedTokens", tokenAddress);
        }

        Address[] acceptedFeeTokens = new Address[]{
            Addresses.get("sicx"), 
            Addresses.get("bnUSD"), 
            Addresses.get("baln")
        };

        Context.call(Addresses.get("feehandler"), "setAcceptedDividendTokens", (Object) acceptedFeeTokens);
    }

    @External
    public void launchBalanced() {
        onlyOwner();
        if (launched.get()) {
            return;
        }

        launched.set(true);

        BigInteger day = getDay();
        launchDay.set(day);
        BigInteger timeDelta = BigInteger.valueOf(Context.getBlockTimestamp()).add(getTimeOffset());

        launchTime.set(timeDelta);
        setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
            Context.call(Addresses.get("rewards"), "addNewDataSource", source.get("name"), Addresses.get(source.get("address")));
        }

        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) RECIPIENTS);
    }

    @External
    @Payable
    public void createBnusdMarket() {
        onlyOwner();

        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = Addresses.get("dex");
        Address sICXAddress = Addresses.get("sicx");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address stakingAddress = Addresses.get("staking");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        BigInteger price = Context.call(BigInteger.class, bnUSDAddress, "priceInLoop");
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        Context.call(value.divide(BigInteger.valueOf(7)), stakingAddress, "stakeICX", Context.getAddress(),
                new byte[0]);
        Context.call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount, Context.getAddress(), BigInteger.ZERO);

        BigInteger bnUSDValue = Context.call(BigInteger.class, bnUSDAddress, "balanceOf", Context.getAddress());
        BigInteger sICXValue = Context.call(BigInteger.class, sICXAddress, "balanceOf", Context.getAddress());


        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(bnUSDAddress, "transfer", dexAddress, bnUSDValue, depositData.toString().getBytes());
        Context.call(sICXAddress, "transfer", dexAddress, sICXValue, depositData.toString().getBytes());

        Context.call(dexAddress, "add", sICXAddress, bnUSDAddress, sICXValue, bnUSDValue, false);
        String name = "sICX/bnUSD";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", sICXAddress, bnUSDAddress);
        Context.call(dexAddress, "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);
        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(225).multiply(pow(BigInteger.TEN, 15))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        onlyOwner();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        Context.call(rewardsAddress, "claimRewards");
        Context.call(loansAddress, "depositAndBorrow", "bnUSD", _bnUSD_amount, Context.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(bnUSDAddress, "transfer", dexAddress, _bnUSD_amount, depositData.toString().getBytes());
        Context.call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        Context.call(dexAddress, "add", balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount, false);
        String name = "BALN/bnUSD";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", balnAddress, bnUSDAddress);
        Context.call(dexAddress, "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        Context.call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void createBalnSicxMarket(BigInteger _sicx_amount, BigInteger _baln_amount) {
        onlyOwnerOrContract();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address sICXAddress = Addresses.get("sicx");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");

        Context.call(rewardsAddress, "claimRewards");

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        Context.call(sICXAddress, "transfer", dexAddress, _sicx_amount, depositData.toString().getBytes());
        Context.call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        Context.call(dexAddress, "add", balnAddress, sICXAddress, _baln_amount, _sicx_amount, false);
        String name = "BALN/sICX";
        BigInteger pid = Context.call(BigInteger.class, dexAddress, "getPoolId", balnAddress, sICXAddress);
        Context.call(dexAddress, "setMarketName", pid, name);

        Context.call(rewardsAddress, "addNewDataSource", name, dexAddress);
        Context.call(stakedLpAddress, "addPool", pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/sICX", BigInteger.valueOf(10).multiply(pow(BigInteger.TEN, 16)))
        };

        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }

    @External
    public void setAddresses(BalancedAddresses _addresses) {
        onlyOwnerOrContract();
        Addresses.setAddresses(_addresses);
    }

    @External(readonly = true)
    public Map<String, Address> getAddresses() {
        return Addresses.getAddresses();
    }

    @External
    public void setAdmins() {
        onlyOwnerOrContract();
        Addresses.setAdmins();
    }

    @External
    public void setContractAddresses() {
        onlyOwnerOrContract();
        Addresses.setContractAddresses();
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
        Address loansAddress = Addresses.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");

        Address balancedOraclAddress = Addresses.get("balancedOracle");
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
        Address loansAddress = Addresses.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");
        BigInteger poolId = Context.call(BigInteger.class, Addresses.get("dex"), "getPoolId", _token_address, Addresses.get("bnUSD"));
        
        Address balancedOraclAddress = Addresses.get("balancedOracle");
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
        Context.call(Addresses.get(contract), "delegate", (Object) _delegations);
    }

    @External
    public void balwAdminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwnerOrContract();
        Context.call(Addresses.get("bwt"), "adminTransfer", _from, _to, _value, _data);
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwnerOrContract();
        Addresses.setAddress(_contract);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }

    @External
    public void callActions(String actions) {
        onlyOwner();
        executeActions(actions);
    }

    private void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }

        proposal.feeRefunded.set(true);
        Context.call(Addresses.get("bnUSD"), "govTransfer", Addresses.get("daofund"), proposal.proposer.get(), proposal.fee.get(), new byte[0]);
    }

    private BigInteger totalBaln(BigInteger _day) {
        return Context.call(BigInteger.class, Addresses.get("baln"), "totalStakedBalanceOfAt", _day);
    }

    private boolean checkBalnVoteCriterion(Address address) {
        BigInteger balnTotal = Context.call(BigInteger.class, Addresses.get("baln"), "totalSupply");
        BigInteger userStaked = Context.call(BigInteger.class, Addresses.get("baln"), "stakedBalanceOf", address);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userStaked).divide(balnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private void _refundVoteDefinitionFee(ProposalDB proposal) {
        Address daoFund = Addresses.get("daofund");
        Context.call(Addresses.get("bnUSD"), "govTransfer", daoFund, proposal.proposer.get(), proposal.fee.get(), new byte[0]);
        proposal.feeRefunded.set(true);
    }

    private void verifyActions(String actions) {
        try {
            Context.call(Context.getAddress(), "tryExecuteActions", actions);
        } catch (score.UserRevertedException e) {
            Context.require(e.getCode() == succsesfulVoteExecutionRevertID, "Vote execution failed");
        }
    }

    private void executeActions(String actions) {
        JsonArray actionsList = Json.parse(actions).asArray();
        for (int i = 0; i < actionsList.size(); i++) {
            JsonArray action = actionsList.get(i).asArray();
            VoteActions.executeAction(action);
        }
    }

    public static void call(Address targetAddress, String method, Object... params) {
        Context.call(targetAddress, method, params);
    }

    @EventLog(indexed = 2)
    public void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                   BigInteger total_against) {
    }
}
