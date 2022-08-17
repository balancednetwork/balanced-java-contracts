package network.balanced.score.core.governance.proposal;

import score.Address;
import score.Context;
import score.VarDB;
import score.DictDB;

import java.math.BigInteger;

public class ProposalDB {
    private static final String PREFIX = "ProposalDB_";

    public final DictDB<String, BigInteger> id;
    public final VarDB<BigInteger> proposalsCount;
    public final VarDB<Address> proposer;
    public final VarDB<BigInteger> quorum;
    public final VarDB<BigInteger> majority;
    public final VarDB<BigInteger> voteSnapshot;
    public final VarDB<BigInteger> startSnapshot;
    public final VarDB<BigInteger> endSnapshot;
    public final VarDB<String> transactions;
    public final VarDB<String> name;
    public final VarDB<String> description;
    public final VarDB<Boolean> active;
    public final DictDB<Address, BigInteger> forVotesOfUser;
    public final DictDB<Address, BigInteger> againstVotesOfUser;
    public final VarDB<BigInteger> totalForVotes;
    public final VarDB<BigInteger> forVotersCount;
    public final VarDB<BigInteger> againstVotersCount;
    public final VarDB<BigInteger> totalAgainstVotes;
    public final VarDB<String> status;
    public final VarDB<BigInteger> fee;
    public final VarDB<Boolean> feeRefunded;

    public ProposalDB(BigInteger varKey) {
        String key = PREFIX + varKey.toString();
        id = Context.newDictDB(PREFIX + "_id", BigInteger.class);
        proposalsCount = Context.newVarDB(PREFIX + "_proposals_count", BigInteger.class);
        proposer = Context.newVarDB(key + "_proposer", Address.class);
        quorum = Context.newVarDB(key + "_quorum", BigInteger.class);
        majority = Context.newVarDB(key + "_majority", BigInteger.class);
        voteSnapshot = Context.newVarDB(key + "_vote_snapshot", BigInteger.class);
        startSnapshot = Context.newVarDB(key + "_start_snapshot", BigInteger.class);
        endSnapshot = Context.newVarDB(key + "_end_snapshot", BigInteger.class);
        transactions = Context.newVarDB(key + "_actions", String.class);
        name = Context.newVarDB(key + "_name", String.class);
        description = Context.newVarDB(key + "_description", String.class);
        active = Context.newVarDB(key + "_active", Boolean.class);
        forVotesOfUser = Context.newDictDB(key + "_for_votes_of_user", BigInteger.class);
        againstVotesOfUser = Context.newDictDB(key + "_against_votes_of_user", BigInteger.class);
        totalForVotes = Context.newVarDB(key + "_total_for_votes", BigInteger.class);
        forVotersCount = Context.newVarDB(key + "_for_voters_count", BigInteger.class);
        againstVotersCount = Context.newVarDB(key + "_against_voters_count", BigInteger.class);
        totalAgainstVotes = Context.newVarDB(key + "_total_against_votes", BigInteger.class);
        status = Context.newVarDB(key + "_status", String.class);
        fee = Context.newVarDB(key + "_fee", BigInteger.class);
        feeRefunded = Context.newVarDB(key + "_fee_refunded", Boolean.class);
    }

    public static BigInteger getProposalId(String name) {
        ProposalDB db = new ProposalDB(BigInteger.ZERO);
        return db.id.getOrDefault(name, BigInteger.ZERO);
    }

    public static BigInteger getProposalCount() {
        ProposalDB db = new ProposalDB(BigInteger.ZERO);
        return db.proposalsCount.getOrDefault(BigInteger.ZERO);
    }

    public static ProposalDB createProposal(String name, 
                                 String description, 
                                 Address proposer,
                                 BigInteger quorum,
                                 BigInteger majority,
                                 BigInteger snapshot,
                                 BigInteger start,
                                 BigInteger end,
                                 String transactions,
                                 BigInteger fee) {
        BigInteger voteIndex = ProposalDB.getProposalCount().add(BigInteger.ONE);
        ProposalDB newProposal = new ProposalDB(voteIndex);

        newProposal.proposalsCount.set(voteIndex);
        newProposal.id.set(name, voteIndex);
        newProposal.proposer.set(proposer);
        newProposal.quorum.set(quorum);
        newProposal.majority.set(majority);
        newProposal.voteSnapshot.set(snapshot);
        newProposal.startSnapshot.set(start);
        newProposal.endSnapshot.set(end);
        newProposal.transactions.set(transactions);
        newProposal.name.set(name);
        newProposal.description.set(description);
        newProposal.status.set(ProposalStatus.STATUS[ProposalStatus.ACTIVE]);
        newProposal.active.set(true);
        newProposal.fee.set(fee);
        newProposal.feeRefunded.set(false);
        newProposal.forVotersCount.set(BigInteger.ZERO);
        newProposal.againstVotersCount.set(BigInteger.ZERO);

        return newProposal;
    }
}
