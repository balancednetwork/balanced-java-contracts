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

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;

public class ProposalDB {
    private static final String PREFIX = "ProposalDB_";

    public final DictDB<String, BigInteger> id;
    public final VarDB<BigInteger> proposalsCount;
    public final VarDB<Address> proposer;
    public final VarDB<BigInteger> quorum;
    public final VarDB<BigInteger> majority;
    public final VarDB<BigInteger> snapshotBlock;
    public final VarDB<BigInteger> startDay;
    public final VarDB<BigInteger> endDay;
    public final VarDB<String> forumLink;
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
        snapshotBlock = Context.newVarDB(key + "_vote_snapshot", BigInteger.class);
        startDay = Context.newVarDB(key + "_start_snapshot", BigInteger.class);
        endDay = Context.newVarDB(key + "_end_snapshot", BigInteger.class);
        forumLink = Context.newVarDB(key + "_forum_link", String.class);
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
                                            String link,
                                            String transactions,
                                            BigInteger fee) {
        BigInteger voteIndex = ProposalDB.getProposalCount().add(BigInteger.ONE);
        ProposalDB newProposal = new ProposalDB(voteIndex);

        newProposal.proposalsCount.set(voteIndex);
        newProposal.id.set(name, voteIndex);
        newProposal.proposer.set(proposer);
        newProposal.quorum.set(quorum);
        newProposal.majority.set(majority);
        newProposal.snapshotBlock.set(snapshot);
        newProposal.startDay.set(start);
        newProposal.endDay.set(end);
        newProposal.forumLink.set(link);
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
