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

package network.balanced.score.core.governance.proposal;

import network.balanced.score.core.governance.utils.ContractManager;
import network.balanced.score.lib.utils.ArbitraryCallManager;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceImpl.*;
import static network.balanced.score.core.governance.utils.EventLogger.VoteCast;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;

public class ProposalManager {

    public static void defineVote(String name, String description, BigInteger vote_start, BigInteger duration,
                                  String forumLink, String transactions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");

        BigInteger snapshotBlock = BigInteger.valueOf(Context.getBlockHeight());

        Context.require(vote_start.compareTo(_getDay()) > 0, "Vote cannot start before the next day.");

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

        verifyTransactions(transactions);

        call(ContractManager.getAddress(Names.BNUSD), "govTransfer", Context.getCaller(),
                ContractManager.getAddress(Names.DAOFUND),
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
                transactions,
                bnusdVoteDefinitionFee.get()
        );
    }

    public static void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0,
                "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get().equals(ProposalStatus.STATUS[ProposalStatus.ACTIVE]), "Proposal can be " +
                "cancelled only from active status.");
        Context.require(Context.getCaller().equals(proposal.proposer.get()) ||
                Context.getCaller().equals(Context.getOwner()), "Only owner or proposer may call this method.");
        if (proposal.startDay.get().compareTo(_getDay()) <= 0) {
            Context.require(Context.getCaller().equals(Context.getOwner()), "Only owner can cancel a vote that has " +
                    "started.");
        }

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    public static List<Object> getProposals(BigInteger batch_size, BigInteger offset) {
        BigInteger start = BigInteger.ONE.max(offset);
        BigInteger end = batch_size.add(start).subtract(BigInteger.ONE).min(ProposalDB.getProposalCount());
        List<Object> proposals = new ArrayList<>();
        for (BigInteger i = start; i.compareTo(end) <= 0; i = i.add(BigInteger.ONE)) {
            proposals.add(checkVote(i));
        }

        return proposals;
    }

    public static void castVote(BigInteger vote_index, boolean vote) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require((vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        _getDay().compareTo(proposal.startDay.getOrDefault(BigInteger.ZERO)) >= 0 &&
                        _getDay().compareTo(proposal.endDay.getOrDefault(BigInteger.ZERO)) < 0) &&
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

    public static void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        vote_index.compareTo(ProposalDB.getProposalCount()) <= 0,
                TAG + ": There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endDay.get();
        String transactions = proposal.transactions.get();
        BigInteger majority = proposal.majority.get();

        Context.require(_getDay().compareTo(endSnap) >= 0, TAG + ": Voting period has not ended.");
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
        } else if (transactions.equals("[]")) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED]);
            refundVoteDefinitionFee(proposal);
            return;
        }

        try {
            ArbitraryCallManager.executeTransactions(transactions);
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.EXECUTED]);
        } catch (Exception e) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.FAILED_EXECUTION]);
        }

        refundVoteDefinitionFee(proposal);
    }

    public static Map<String, Object> checkVote(BigInteger _vote_index) {
        if (_vote_index.compareTo(BigInteger.ONE) < 0 ||
                _vote_index.compareTo(ProposalDB.getProposalCount()) > 0) {
            return Map.of();
        }

        ProposalDB proposal = new ProposalDB(_vote_index);
        BigInteger totalBoostedBaln;
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
        voteData.put("forum link", proposal.forumLink.getOrDefault(""));
        voteData.put("majority", proposal.majority.getOrDefault(BigInteger.ZERO));
        voteData.put("status", proposal.status.getOrDefault(""));
        voteData.put("vote snapshot", proposal.snapshotBlock.getOrDefault(BigInteger.ZERO));
        voteData.put("start day", proposal.startDay.getOrDefault(BigInteger.ZERO));
        voteData.put("end day", proposal.endDay.getOrDefault(BigInteger.ZERO));
        voteData.put("actions", proposal.transactions.getOrDefault(""));
        voteData.put("quorum", proposal.quorum.getOrDefault(BigInteger.ZERO));
        voteData.put("for", nrForVotes);
        voteData.put("against", nrAgainstVotes);
        voteData.put("for_voter_count", proposal.forVotersCount.getOrDefault(BigInteger.ZERO));
        voteData.put("against_voter_count", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO));
        voteData.put("fee_refund_status", proposal.feeRefunded.getOrDefault(false));

        return voteData;
    }

    public static Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
                "for_voters", proposal.forVotersCount.getOrDefault(BigInteger.ZERO),
                "against_voters", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO)
        );
    }

    public static Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
                "for", proposal.forVotesOfUser.getOrDefault(user, BigInteger.ZERO),
                "against", proposal.againstVotesOfUser.getOrDefault(user, BigInteger.ZERO)
        );
    }

    public static BigInteger totalBoostedBaln(BigInteger block) {
        return Context.call(BigInteger.class, ContractManager.getAddress(Names.BOOSTED_BALN), "totalSupplyAt", block);
    }

    public static BigInteger myVotingWeight(Address _address, BigInteger block) {
        return Context.call(BigInteger.class, ContractManager.getAddress(Names.BOOSTED_BALN), "balanceOfAt", _address
                , block);
    }

    private static void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }

        proposal.feeRefunded.set(true);
        call(ContractManager.getAddress(Names.BNUSD), "govTransfer", ContractManager.getAddress(Names.DAOFUND),
                proposal.proposer.get(), proposal.fee.get(), new byte[0]);
    }

    private static BigInteger totalBaln(BigInteger _day) {
        return call(BigInteger.class, ContractManager.getAddress(Names.BALN), "totalStakedBalanceOfAt", _day);
    }

    private static boolean checkBalnVoteCriterion(Address address, BigInteger block) {
        BigInteger boostedBalnTotal = Context.call(BigInteger.class, ContractManager.getAddress(Names.BOOSTED_BALN),
                "totalSupplyAt", block);
        BigInteger userBoostedBaln = myVotingWeight(address, block);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userBoostedBaln).divide(boostedBalnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private static void verifyTransactions(String transactions) {
        try {
            call(Context.getAddress(), "tryExecuteTransactions", transactions);
        } catch (score.UserRevertedException e) {
            Context.require(e.getCode() == successfulVoteExecutionRevertID, "Vote execution failed");
        }
    }
}
