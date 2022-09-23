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
import com.eclipsesource.json.JsonValue;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;
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
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Check.optionalDefault;
import static network.balanced.score.lib.utils.Math.pow;

public class GovernanceImpl {
    public final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public final VarDB<BigInteger> maxVoteDuration = Context.newVarDB(MAX_VOTE_DURATION, BigInteger.class);
    public final VarDB<BigInteger> minVoteDuration = Context.newVarDB(MIN_VOTE_DURATION, BigInteger.class);
    public final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);

    public GovernanceImpl() {
        if (launched.getOrDefault(null) == null) {
            launched.set(false);
        }

        if (maxVoteDuration.get() == null) {
            maxVoteDuration.set(BigInteger.valueOf(14));
            minVoteDuration.set(BigInteger.valueOf(1));
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Governance";
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime =
                BigInteger.valueOf(Context.getBlockTimestamp()).subtract(timeOffset.getOrDefault(BigInteger.ZERO));
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
    public void setVoteDurationLimits(BigInteger max, BigInteger min) {
        onlyOwner();
        _setVoteDurationLimits(max, min);
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
        onlyOwner();
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
    public void setDividendsOnlyToStakedBalnDay(BigInteger _day) {
        onlyOwner();
        Context.call(Addresses.get("dividends"), "setDividendsOnlyToStakedBalnDay", _day);
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setFeeProcessingInterval", _interval);
    }

    @External
    public void deleteRoute(Address _fromToken, Address _toToken) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "deleteRoute", _fromToken, _toToken);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setAcceptedDividendTokens", (Object) _tokens);
    }

    @External
    public void setRoute(Address _fromToken, Address _toToken, Address[] _path) {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "setRoute", _fromToken, _toToken, _path);
    }

    @External
    public void setQuorum(BigInteger quorum) {
        onlyOwner();
        _setQuorum(quorum);
    }

    @External(readonly = true)
    public BigInteger getQuorum() {
        return quorum.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setVoteDefinitionFee(BigInteger fee) {
        onlyOwner();
        _setVoteDefinitionFee(fee);
    }

    @External(readonly = true)
    public BigInteger getVoteDefinitionFee() {
        return bnusdVoteDefinitionFee.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setBalnVoteDefinitionCriterion(BigInteger percentage) {
        onlyOwner();
        _setBalnVoteDefinitionCriterion(percentage);
    }

    @External(readonly = true)
    public BigInteger getBalnVoteDefinitionCriterion() {
        return balnVoteDefinitionCriterion.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setAdmin(Address contractAddress, Address admin) {
        onlyOwner();
        Context.call(contractAddress, "setAdmin", admin);

    }

    @External
    public void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0,
                "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get().equals(ProposalStatus.STATUS[ProposalStatus.ACTIVE]), "Proposal can be " +
                "cancelled only from active status.");
        Context.require(Context.getCaller().equals(proposal.proposer.get()) ||
                        Context.getCaller().equals(Context.getOwner()),
                "Only owner or proposer may call this method.");
        if (proposal.startDay.get().compareTo(getDay()) <= 0) {
            Context.require(Context.getCaller().equals(Context.getOwner()),
                    "Only owner can cancel a vote that has started.");
        }

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger duration,
                           String forumLink, @Optional String actions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");

        BigInteger snapshotBlock = BigInteger.valueOf(Context.getBlockHeight());

        Context.require(vote_start.compareTo(getDay()) >= 0, "Vote cannot start before the current day.");

        actions = optionalDefault(actions, "[]");
        BigInteger voteIndex = ProposalDB.getProposalId(name);
        Context.require(forumLink.startsWith("https://gov.balanced.network/"), "Invalid forum link.");
        Context.require(duration.compareTo(maxVoteDuration.get()) <= 0, "Duration is above the maximum allowed " +
                "duration of " + maxVoteDuration.get());
        Context.require(duration.compareTo(minVoteDuration.get()) >= 0, "Duration is below the minimum allowed " +
                "duration of " + minVoteDuration.get());
        Context.require(voteIndex.equals(BigInteger.ZERO), "Poll name " + name + " has already been used.");
        Context.require(checkBalnVoteCriterion(Context.getCaller(), snapshotBlock),
                "User needs at least " + balnVoteDefinitionCriterion.get().divide(BigInteger.valueOf(100)) + "% of " +
                        "total boosted baln supply to define a vote.");
        verifyActions(actions);

        Context.call(Addresses.get("bnUSD"), "govTransfer", Context.getCaller(), Addresses.get("daofund"),
                bnusdVoteDefinitionFee.getOrDefault(BigInteger.ONE), new byte[0]);

        ProposalDB.createProposal(
                name,
                description,
                Context.getCaller(),
                quorum.get().multiply(EXA).divide(BigInteger.valueOf(100)),
                MAJORITY,
                snapshotBlock,
                vote_start,
                vote_start.add(duration),
                forumLink,
                actions,
                bnusdVoteDefinitionFee.get()
        );
    }

    @External
    public void tryExecuteActions(String actions) {
        JsonArray actionsParsed = Json.parse(actions).asArray();
        Context.require(actionsParsed.size() <= maxActions(), TAG + ": Only " + maxActions() + " actions are allowed");
        executeVoteActions(actions);
        Context.revert(successfulVoteExecutionRevertID);
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
                        getDay().compareTo(proposal.startDay.getOrDefault(BigInteger.ZERO)) >= 0 &&
                        getDay().compareTo(proposal.endDay.getOrDefault(BigInteger.ZERO)) < 0) &&
                        proposal.active.getOrDefault(false),
                TAG + " :This is not an active poll.");

        Address from = Context.getCaller();
        BigInteger snapshot = proposal.snapshotBlock.get();

        BigInteger totalVote = myVotingWeight(from, snapshot);

        Context.require(!totalVote.equals(BigInteger.ZERO), TAG + "Boosted Balanced tokens needed to cast the vote.");

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

    @External(readonly = true)
    public BigInteger totalBoostedBaln(BigInteger block) {
        return Context.call(BigInteger.class, Addresses.get("bBaln"), "totalSupplyAt", block);
    }

    @External
    public void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        vote_index.compareTo(ProposalDB.getProposalCount()) <= 0,
                TAG + ": There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endDay.get();
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
            executeVoteActions(actions);
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
        BigInteger totalBoostedBaln = BigInteger.ZERO;
        if (proposal.forumLink.get() == null) {
            totalBoostedBaln = totalBaln(proposal.snapshotBlock.getOrDefault(BigInteger.ZERO));
        } else {
            totalBoostedBaln = totalBoostedBaln(proposal.snapshotBlock.getOrDefault(BigInteger.ZERO));
        }

        BigInteger nrForVotes = BigInteger.ZERO;
        BigInteger nrAgainstVotes = BigInteger.ZERO;
        if (!totalBoostedBaln.equals(BigInteger.ZERO)) {
            nrForVotes = proposal.totalForVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBoostedBaln);
            nrAgainstVotes =
                    proposal.totalAgainstVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBoostedBaln);
        }

        Map<String, Object> voteData = new HashMap<>(16);

        voteData.put("id", _vote_index);
        voteData.put("name", proposal.name.getOrDefault(""));
        voteData.put("proposer", proposal.proposer.getOrDefault(EOA_ZERO));
        voteData.put("description", proposal.description.getOrDefault(""));
        voteData.put("majority", proposal.majority.getOrDefault(BigInteger.ZERO));
        voteData.put("status", proposal.status.getOrDefault(""));
        voteData.put("vote snapshot", proposal.snapshotBlock.getOrDefault(BigInteger.ZERO));
        voteData.put("start day", proposal.startDay.getOrDefault(BigInteger.ZERO));
        voteData.put("end day", proposal.endDay.getOrDefault(BigInteger.ZERO));
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
    public BigInteger myVotingWeight(Address _address, BigInteger block) {
        return Context.call(BigInteger.class, Addresses.get("bBaln"), "balanceOfAt", _address, block);
    }

    @External
    public void configureBalanced() {
        onlyOwner();
        for (Map<String, Object> asset : ASSETS) {
            Context.call(
                    Addresses.get("loans"),
                    "addAsset",
                    Addresses.get((String) asset.get("address")),
                    asset.get("active"),
                    asset.get("collateral")
            );
        }
    }

    @External
    public void launchBalanced() {
        onlyOwner();
        if (launched.get()) {
            return;
        }

        launched.set(true);

        BigInteger offset = DAY_ZERO.add(launchDay.getOrDefault(BigInteger.ZERO));
        BigInteger day = (BigInteger.valueOf(Context.getBlockTimestamp()).subtract(DAY_START)).divide(MICRO_SECONDS_IN_A_DAY).subtract(offset);
        launchDay.set(day);
        launchTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));

        BigInteger timeDelta = DAY_START.add(MICRO_SECONDS_IN_A_DAY.multiply(DAY_ZERO.add(launchDay.get()).subtract(BigInteger.ONE)));

        setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
            Context.call(Addresses.get("rewards"), "addNewDataSource", source.get("name"), Addresses.get(source.get(
                    "address")));
        }

        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) RECIPIENTS);

        balanceToggleStakingEnabled();
        Context.call(Addresses.get("loans"), "turnLoansOn");
        Context.call(Addresses.get("dex"), "turnDexOn");
        enableDividends();
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
        Context.call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount,
                Context.getAddress(), BigInteger.ZERO);

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
        onlyOwner();

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
    public void rebalancingSetBnusd(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), SETTERS.get("bnUSD"), _address);

    }

    @External
    public void rebalancingSetSicx(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), SETTERS.get("sicx"), _address);

    }

    @External
    public void rebalancingSetDex(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), SETTERS.get("dex"), _address);

    }

    @External
    public void rebalancingSetLoans(Address _address) {
        onlyOwner();
        Context.call(rebalancing.get(), SETTERS.get("loans"), _address);
    }

    @External
    public void setLoansRebalance(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("loans"), SETTERS.get("rebalancing"), _address);

    }

    @External
    public void setLoansDex(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("loans"), SETTERS.get("dex"), _address);
    }

    @External
    public void setRebalancing(Address _address) {
        onlyOwner();
        rebalancing.set(_address);
    }

    @External
    public void setRebalancingThreshold(BigInteger _value) {
        onlyOwner();
        _setRebalancingThreshold(_value);
    }

    @External
    public void setAddresses(BalancedAddresses _addresses) {
        onlyOwner();
        Addresses.setAddresses(_addresses);
    }

    @External(readonly = true)
    public Map<String, Address> getAddresses() {
        return Addresses.getAddresses();
    }

    @External
    public void setAdmins() {
        onlyOwner();
        Addresses.setAdmins();
    }

    @External
    public void setContractAddresses() {
        onlyOwner();
        Addresses.setContractAddresses();

    }

    @External
    public void toggleBalancedOn() {
        onlyOwner();
        Context.call(Addresses.get("loans"), "toggleLoansOn");
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
    public void addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
                              BigInteger _liquidationRatio, BigInteger _debtCeiling) {
        onlyOwner();
        _addCollateral(_token_address, _active, _peg, _lockingRatio, _liquidationRatio, _debtCeiling);
    }

    @External
    public void addDexPricedCollateral(Address _token_address, boolean _active, BigInteger _lockingRatio,
                                       BigInteger _liquidationRatio, BigInteger _debtCeiling) {
        onlyOwner();
        _addDexPricedCollateral(_token_address, _active, _lockingRatio, _liquidationRatio, _debtCeiling);
    }

    @External
    public void setDebtCeiling(String _symbol, BigInteger _debtCeiling) {
        onlyOwner();
        Address loansAddress = Addresses.get("loans");
        Context.call(loansAddress, "setDebtCeiling", _symbol, _debtCeiling);
    }

    @External
    public void setLockingRatio(String _symbol, BigInteger _value) {
        onlyOwner();
        _setLockingRatio(_symbol, _value);
    }

    @External
    public void setLiquidationRatio(String _symbol, BigInteger _value) {
        onlyOwner();
        _setLiquidationRatio(_symbol, _value);
    }

    @External
    public void toggleAssetActive(String _symbol) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "toggleAssetActive", _symbol);
    }

    @External
    public void setPeg(String _symbol, String _peg) {
        onlyOwner();
        Context.call(Addresses.get("balancedOracle"), "setPeg", _symbol, _peg);
    }

    @External
    public void addDexPricedAsset(String _symbol, BigInteger _limit) {
        onlyOwner();
        Context.call(Addresses.get("balancedOracle"), "addDexPricedAsset", _symbol, _limit);
    }

    @External
    public void removeDexPricedAsset(String _symbol) {
        onlyOwner();
        Context.call(Addresses.get("balancedOracle"), "removeDexPricedAsset", _symbol);
    }

    @External
    public void addNewDataSource(String _data_source_name, String _contract_address) {
        onlyOwner();
        _addNewDataSource(_data_source_name, _contract_address);
    }

    @External
    public void removeDataSource(String _data_source_name) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "removeDataSource", _data_source_name);
    }

    @External
    public void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        onlyOwner();
        _updateBalTokenDistPercentage(_recipient_list);
    }

    @External
    public void bonusDist(Address[] _addresses, BigInteger[] _amounts) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "bonusDist", (Object) _addresses, (Object) _amounts);
    }

    @External
    public void setDay(BigInteger _day) {
        onlyOwner();
        Context.call(Addresses.get("rewards"), "setDay", _day);
    }

    @External
    public void dexPermit(BigInteger _id, boolean _permission) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "permit", _id, _permission);
    }

    @External
    public void dexAddQuoteCoin(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "addQuoteCoin", _address);
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "setMarketName", _id, _name);
    }

    @External
    public void delegate(String contract, PrepDelegations[] _delegations) {
        onlyOwner();
        Context.call(Addresses.get(contract), "delegate", (Object) _delegations);
    }

    @External
    public void balwAdminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        onlyOwner();
        Context.call(Addresses.get("bwt"), "adminTransfer", _from, _to, _value, _data);
    }

    @External
    public void setbnUSD(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("baln"), SETTERS.get("bnUSD"), _address);
    }

    @External
    public void setDividends(Address _score) {
        onlyOwner();
        Context.call(Addresses.get("baln"), SETTERS.get("dividends"), _score);
    }

    @External
    public void balanceSetDex(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("baln"), SETTERS.get("dex"), _address);
    }

    @External
    public void balanceSetOracleName(String _name) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setOracleName", _name);
    }

    @External
    public void balanceSetMinInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setMinInterval", _interval);
    }

    @External
    public void balanceToggleStakingEnabled() {
        onlyOwner();
        Context.call(Addresses.get("baln"), "toggleStakingEnabled");
    }

    @External
    public void balanceSetMinimumStake(BigInteger _amount) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setMinimumStake", _amount);
    }

    @External
    public void balanceSetUnstakingPeriod(BigInteger _time) {
        onlyOwner();
        Context.call(Addresses.get("baln"), "setUnstakingPeriod", _time);
    }

    @External
    public void addAcceptedTokens(String _token) {
        onlyOwner();
        _addAcceptedTokens(_token);
    }

    @SuppressWarnings("unchecked")
    @External
    public void setAssetOracle(String _symbol, Address _address) {
        onlyOwner();
        Map<String, String> assetAddresses = (Map<String, String>) Context.call(Addresses.get("loans"),
                "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in " +
                "Balanced.");

        Address token = Address.fromString(assetAddresses.get(_symbol));
        Context.call(token, "setOracle", _address);
    }

    @SuppressWarnings("unchecked")
    @External
    public void setAssetOracleName(String _symbol, String _name) {
        onlyOwner();
        Map<String, String> assetAddresses = (Map<String, String>) Context.call(Addresses.get("loans"),
                "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in " +
                "Balanced.");

        Address token = Address.fromString(assetAddresses.get(_symbol));
        Context.call(token, "setOracleName", _name);
    }

    @SuppressWarnings("unchecked")
    @External
    public void setAssetMinInterval(String _symbol, BigInteger _interval) {
        onlyOwner();
        Map<String, String> assetAddresses = (Map<String, String>) Context.call(Addresses.get("loans"),
                "getAssetTokens");
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in " +
                "Balanced.");

        Address token = Address.fromString(assetAddresses.get(_symbol));
        Context.call(token, "setMinInterval", _interval);
    }

    @External
    public void bnUSDSetOracle(Address _address) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setOracle", _address);
    }

    @External
    public void bnUSDSetOracleName(String _name) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setOracleName", _name);
    }

    @External
    public void bnUSDSetMinInterval(BigInteger _interval) {
        onlyOwner();
        Context.call(Addresses.get("bnUSD"), "setMinInterval", _interval);
    }

    @External
    public void addUsersToActiveAddresses(BigInteger _poolId, Address[] _addressList) {
        onlyOwner();
        Context.call(Addresses.get("dex"), "addLpAddresses", _poolId, _addressList);
    }

    @External
    public void setRedemptionFee(BigInteger _fee) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setRedemptionFee", _fee);
    }

    @External
    public void setNewLoanMinimum(BigInteger _minimum) {
        onlyOwner();
        _setNewLoanMinimum(_minimum);
    }

    @External
    public void setMinMiningDebt(BigInteger _value) {
        onlyOwner();
        _setMinMiningDebt(_value);
    }

    @External
    public void setBatchSize(BigInteger _batch_size) {
        onlyOwner();
        _setBatchSize(_batch_size);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        onlyOwner();
        _setMaxRetirePercent(_value);
    }

    @External
    public void setRedeemBatchSize(BigInteger _value) {
        onlyOwner();
        Context.call(Addresses.get("loans"), "setRedeemBatchSize", _value.intValue());
    }

    @External
    public void addPoolOnStakedLp(BigInteger _id) {
        onlyOwner();
        Context.call(Addresses.get("stakedLp"), "addPool", _id);
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwner();
        Addresses.setAddress(_contract);
    }

    @External
    public void setRouter(Address _router) {
        onlyOwner();
        Addresses.router.set(_router);
    }

    @External
    public void enable_fee_handler() {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "enable");
    }

    @External
    public void disable_fee_handler() {
        onlyOwner();
        Context.call(Addresses.get("feehandler"), "disable");
    }

    @External
    public void reserveTransfer(Address _tokenAddress, Address _targetAddress, BigInteger _amount) {
        onlyOwner();
        Context.call(Addresses.get("reserve"), "transfer", _tokenAddress, _targetAddress, _amount);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }

    private void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }

        proposal.feeRefunded.set(true);
        Context.call(Addresses.get("bnUSD"), "govTransfer", Addresses.get("daofund"), proposal.proposer.get(),
                proposal.fee.get(), new byte[0]);
    }

    private boolean checkBalnVoteCriterion(Address address, BigInteger block) {
        BigInteger boostedBalnTotal = Context.call(BigInteger.class, Addresses.get("bBaln"), "totalSupplyAt", block);
        BigInteger userBoostedBaln = myVotingWeight(address, block);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userBoostedBaln).divide(boostedBalnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private BigInteger totalBaln(BigInteger _day) {
        if (_day.compareTo(getDay()) > 0) {
            return BigInteger.ZERO;
        }

        return Context.call(BigInteger.class, Addresses.get("baln"), "totalStakedBalanceOfAt", _day);
    }

    private void _refundVoteDefinitionFee(ProposalDB proposal) {
        Address daoFund = Addresses.get("daofund");
        Context.call(Addresses.get("bnUSD"), "govTransfer", daoFund, proposal.proposer.get(), proposal.fee.get(),
                new byte[0]);
        proposal.feeRefunded.set(true);
    }

    private void verifyActions(String actions) {
        try {
            Context.call(Context.getAddress(), "tryExecuteActions", actions);
        } catch (score.UserRevertedException e) {
            Context.require(e.getCode() == successfulVoteExecutionRevertID, "Vote execution failed");
        }
    }

    private void executeVoteActions(String actions) {
        JsonArray actionsList = Json.parse(actions).asArray();
        for (int i = 0; i < actionsList.size(); i++) {
            JsonValue action = actionsList.get(i);
            JsonArray parsedAction = action.asArray();

            String method = parsedAction.get(0).asString();
            JsonObject parameters = parsedAction.get(1).asObject();

            VoteActions.execute(this, method, parameters);
        }
    }

    public static void call(Address targetAddress, String method, Object... params) {
        Context.call(targetAddress, method, params);
    }

    public void daoDisburse(String _recipient, Disbursement[] _amounts) {
        Context.require(_amounts.length <= 3, "Cannot disburse more than 3 assets at a time.");
        Address recipient = Address.fromString(_recipient);
        Context.call(Addresses.get("daofund"), "disburse", recipient, (Object) _amounts);
    }

    public void enableDividends() {
        Context.call(Addresses.get("dividends"), "setDistributionActivationStatus", true);
    }

    public void _setLockingRatio(String _symbol, BigInteger _value) {
        Context.call(Addresses.get("loans"), "setLockingRatio", _symbol, _value);
    }

    public void setOriginationFee(BigInteger _fee) {
        Context.call(Addresses.get("loans"), "setOriginationFee", _fee);
    }

    public void _setLiquidationRatio(String _symbol, BigInteger _ratio) {
        Context.call(Addresses.get("loans"), "setLiquidationRatio", _symbol, _ratio);
    }

    public void setRetirementBonus(BigInteger _points) {
        Context.call(Addresses.get("loans"), "setRetirementBonus", _points);
    }

    public void setLiquidationReward(BigInteger _points) {
        Context.call(Addresses.get("loans"), "setLiquidationReward", _points);
    }

    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        Context.call(Addresses.get("dividends"), "setDividendsCategoryPercentage", (Object) _dist_list);
    }

    public void _setVoteDurationLimits(BigInteger min, BigInteger max) {
        Context.require(min.compareTo(BigInteger.ONE) >= 0, "Minimum vote duration has to be above 1");
        minVoteDuration.set(min);
        maxVoteDuration.set(max);
    }

    public void _setVoteDefinitionFee(BigInteger fee) {
        bnusdVoteDefinitionFee.set(fee);
    }

    public void _setQuorum(BigInteger quorum) {
        Context.require(quorum.compareTo(BigInteger.ZERO) > 0, "Quorum must be between 0 and 100.");
        Context.require(quorum.compareTo(BigInteger.valueOf(100)) < 0, "Quorum must be between 0 and 100.");

        this.quorum.set(quorum);
    }

    public void _setBalnVoteDefinitionCriterion(BigInteger percentage) {
        Context.require(percentage.compareTo(BigInteger.ZERO) >= 0, "Basis point must be between 0 and 10000.");
        Context.require(percentage.compareTo(BigInteger.valueOf(10000)) <= 0, "Basis point must be between 0 and " +
                "10000.");

        balnVoteDefinitionCriterion.set(percentage);
    }

    public void _addNewDataSource(String _data_source_name, String _contract_address) {
        Context.call(Addresses.get("rewards"), "addNewDataSource", _data_source_name,
                Address.fromString(_contract_address));
    }

    public void _addAcceptedTokens(String _token) {
        Address token = Address.fromString(_token);
        Context.call(Addresses.get("dividends"), "addAcceptedTokens", token);
    }

    public void _setMaxRetirePercent(BigInteger _value) {
        Context.call(Addresses.get("loans"), "setMaxRetirePercent", _value);
    }

    public void _setRebalancingThreshold(BigInteger _value) {
        Context.call(rebalancing.get(), "setPriceDiffThreshold", _value);
    }

    public void _setNewLoanMinimum(BigInteger _minimum) {
        Context.call(Addresses.get("loans"), "setNewLoanMinimum", _minimum);
    }


    public void _setMinMiningDebt(BigInteger _minimum) {
        Context.call(Addresses.get("loans"), "setMinMiningDebt", _minimum);
    }


    public void _setBatchSize(BigInteger _batch_size) {
        Context.call(Addresses.get("rewards"), "setBatchSize", _batch_size);
    }

    public void _updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        Context.call(Addresses.get("rewards"), "updateBalTokenDistPercentage", (Object) _recipient_list);
    }

    public void _addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
                               BigInteger _liquidationRatio, BigInteger _debtCeiling) {
        Address loansAddress = Addresses.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");

        Address balancedOracleAddress = Addresses.get("balancedOracle");
        Context.call(balancedOracleAddress, "setPeg", symbol, _peg);
        BigInteger price = Context.call(BigInteger.class, balancedOracleAddress, "getPriceInLoop", symbol);
        Context.require(price.compareTo(BigInteger.ZERO) > 0,
                "Balanced oracle return a invalid icx price for " + symbol + "/" + _peg);

        Context.call(loansAddress, "setDebtCeiling", symbol, _debtCeiling);
        _setLockingRatio(symbol, _lockingRatio);
        _setLiquidationRatio(symbol, _liquidationRatio);
    }

    public void _addDexPricedCollateral(Address _token_address, boolean _active, BigInteger _lockingRatio,
                                        BigInteger _liquidationRatio, BigInteger _debtCeiling) {
        Address loansAddress = Addresses.get("loans");
        Context.call(loansAddress, "addAsset", _token_address, _active, true);

        String symbol = Context.call(String.class, _token_address, "symbol");
        BigInteger poolId = Context.call(BigInteger.class, Addresses.get("dex"), "getPoolId", _token_address,
                Addresses.get("bnUSD"));

        Address balancedOracleAddress = Addresses.get("balancedOracle");
        Context.call(balancedOracleAddress, "addDexPricedAsset", symbol, poolId);
        BigInteger price = Context.call(BigInteger.class, balancedOracleAddress, "getPriceInLoop", symbol);
        Context.require(price.compareTo(BigInteger.ZERO) > 0,
                "Balanced oracle return a invalid icx price for " + symbol);

        Context.call(loansAddress, "setDebtCeiling", symbol, _debtCeiling);
        _setLockingRatio(symbol, _lockingRatio);
        _setLiquidationRatio(symbol, _liquidationRatio);
    }

    @EventLog(indexed = 2)
    public void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                         BigInteger total_against) {
    }
}
