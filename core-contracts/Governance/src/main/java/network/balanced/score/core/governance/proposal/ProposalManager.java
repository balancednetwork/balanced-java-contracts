package network.balanced.score.core.governance.proposal;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;

import network.balanced.score.core.governance.utils.AddressManager;
import network.balanced.score.core.governance.utils.ArbitraryCallManager;

import score.Address;
import score.Context;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceImpl.*;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;

public class ProposalManager {

    public static void defineVote(String name, String description, BigInteger vote_start, BigInteger snapshot, String actions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");
        Context.require(vote_start.compareTo(_getDay()) > 0, "Vote cannot start at or before the current day.");
        Context.require(_getDay().compareTo(snapshot) <= 0 &&
                        snapshot.compareTo(vote_start) < 0,
                "The reference snapshot must be in the range: [current_day (" + _getDay() + "), " +
                        "start_day - 1 (" + vote_start.subtract(BigInteger.ONE) + ")].");

        BigInteger voteIndex = ProposalDB.getProposalId(name);
        Context.require(voteIndex.equals(BigInteger.ZERO), "Poll name " + name + " has already been used.");
        Context.require(checkBalnVoteCriterion(Context.getCaller()), "User needs at least " + balnVoteDefinitionCriterion.get().divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.");
        verifyActions(actions);

        call(AddressManager.get("bnUSD"), "govTransfer", Context.getCaller(), AddressManager.get("daofund"), bnusdVoteDefinitionFee.getOrDefault(BigInteger.ONE), new byte[0]);

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

    public static void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0, "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get().equals(ProposalStatus.STATUS[ProposalStatus.ACTIVE]), "Proposal can be cancelled only from active status.");
        Context.require(Context.getCaller().equals(proposal.proposer.get()) ||
                        Context.getCaller().equals(Context.getOwner()),
                "Only owner or proposer may call this method.");
        if (proposal.startSnapshot.get().compareTo(_getDay()) <= 0) {
            Context.require(Context.getCaller().equals(Context.getOwner()),
                    "Only owner can cancel a vote that has started.");
        }

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    public static  List<Object> getProposals(BigInteger batch_size, BigInteger offset) {
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
                        _getDay().compareTo(proposal.startSnapshot.getOrDefault(BigInteger.ZERO)) >= 0 &&
                        _getDay().compareTo(proposal.endSnapshot.getOrDefault(BigInteger.ZERO)) < 0) &&
                        proposal.active.getOrDefault(false),
                TAG + " :This is not an active poll.");

        Address from = Context.getCaller();
        BigInteger snapshot = proposal.voteSnapshot.get();

        BigInteger totalVote = call(BigInteger.class, AddressManager.get("baln"), "stakedBalanceOfAt", from, snapshot);

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

        getEventLogger().VoteCast(proposal.name.get(), vote, from, totalVote, totalFor, totalAgainst);
    }

    public static  void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ZERO) > 0 &&
                        vote_index.compareTo(ProposalDB.getProposalCount()) <= 0,
                TAG + ": There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endSnapshot.get();
        String actions = proposal.actions.get();
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
        } else if (actions.equals("[]")) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED]);
            _refundVoteDefinitionFee(proposal);
            return;
        }

        try {
            ArbitraryCallManager.executeTransactions(actions);
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.EXECUTED]);
        } catch (Exception e) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.FAILED_EXECUTION]);
        }

        _refundVoteDefinitionFee(proposal);
    }

    public static Map<String, Object> checkVote(BigInteger _vote_index) {
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

    private static void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }

        proposal.feeRefunded.set(true);
        call(AddressManager.get("bnUSD"), "govTransfer", AddressManager.get("daofund"), proposal.proposer.get(), proposal.fee.get(), new byte[0]);
    }

    private static BigInteger totalBaln(BigInteger _day) {
        return call(BigInteger.class, AddressManager.get("baln"), "totalStakedBalanceOfAt", _day);
    }

    private static boolean checkBalnVoteCriterion(Address address) {
        BigInteger balnTotal = call(BigInteger.class, AddressManager.get("baln"), "totalSupply");
        BigInteger userStaked = call(BigInteger.class, AddressManager.get("baln"), "stakedBalanceOf", address);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userStaked).divide(balnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private static void _refundVoteDefinitionFee(ProposalDB proposal) {
        Address daoFund = AddressManager.get("daofund");
        call(AddressManager.get("bnUSD"), "govTransfer", daoFund, proposal.proposer.get(), proposal.fee.get(), new byte[0]);
        proposal.feeRefunded.set(true);
    }

    private static void verifyActions(String actions) {
        try {
            call(Context.getAddress(), "tryExecuteTransactions", actions);
        } catch (score.UserRevertedException e) {
            Context.require(e.getCode() == succsesfulVoteExecutionRevertID, "Vote execution failed");
        }
    }
}
