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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;

import network.balanced.score.core.governance.proposal.ProposalStatus;
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.DistributionPercentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceConstants.EXA;
import static network.balanced.score.core.governance.utils.GovernanceConstants.TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GovernanceVotingTest extends GovernanceTestBase {

    @BeforeEach
    public void setup() throws Exception {
       super.setup();
    }

    @Test
    void defineVote() {
        // Arrange
        sm.getBlock().increase(DAY);
        Account accountWithLowBalance = sm.createAccount();
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        String actions = "[]";
        String expectedErrorMessage;
        
        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN.multiply(EXA));
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.TEN.multiply(EXA));
        when(baln.mock.stakedBalanceOf(accountWithLowBalance.getAddress())).thenReturn(BigInteger.ZERO);
        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(EXA));
        
        // Act & Assert
        String tooLongDescription = "T".repeat(501);
        expectedErrorMessage  = "Description must be less than or equal to 500 characters.";
        Executable withTooLongDescription = () -> governance.invoke(owner, "defineVote", name, tooLongDescription, voteStart, snapshot, actions);
        expectErrorMessage(withTooLongDescription, expectedErrorMessage);

        BigInteger voteStartBeforeToday = day.subtract(BigInteger.ONE);
        expectedErrorMessage  = "Vote cannot start at or before the current day.";
        Executable withVoteStartBeforeToday = () -> governance.invoke(owner, "defineVote", name, description, voteStartBeforeToday, snapshot, actions);
        expectErrorMessage(withVoteStartBeforeToday, expectedErrorMessage);

        BigInteger snapshotBeforeToday = day.subtract(BigInteger.ONE);
        expectedErrorMessage  = "The reference snapshot must be in the range: [current_day (" + day +"), start_day - 1 (" + voteStart.subtract(BigInteger.ONE) + ")].";
        Executable withSnapshotBeforeToday = () -> governance.invoke(owner, "defineVote", name, description, voteStart, snapshotBeforeToday, actions);
        expectErrorMessage(withSnapshotBeforeToday, expectedErrorMessage);

        expectedErrorMessage  = "The reference snapshot must be in the range: [current_day (" + day +"), start_day - 1 (" + voteStart.subtract(BigInteger.ONE) + ")].";
        Executable withSnapshotAfterStart = () -> governance.invoke(owner, "defineVote", name, description, voteStart, voteStart, actions);
        expectErrorMessage(withSnapshotAfterStart, expectedErrorMessage);

        BigInteger balnVoteDefinitionCriterion = (BigInteger) governance.call("getBalnVoteDefinitionCriterion");
        expectedErrorMessage  = "User needs at least " + balnVoteDefinitionCriterion.divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.";
        Executable withToFewStakedBaln = () -> governance.invoke(accountWithLowBalance, "defineVote", name, description, voteStart, snapshot, actions);
        expectErrorMessage(withToFewStakedBaln, expectedErrorMessage);

        String invalidActions = "[[\"" + governance.getAddress().toString() + "\", \"invalidAction\", [invalid]]]";
        expectedErrorMessage  = "";
        Executable withInvalidActions = () -> governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, invalidActions);
        expectErrorMessage(withInvalidActions, expectedErrorMessage);

        // Arrange 
        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);

        // Act & Assert
        expectedErrorMessage  = "Poll name " + name + " has already been used.";
        Executable withAlreadyUsedName = () -> governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);
        expectErrorMessage(withAlreadyUsedName, expectedErrorMessage);
        
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);
        Map<String, Object> vote = getVote(id);
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");

        verify(bnUSD.mock).govTransfer(owner.getAddress(), daofund.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.ACTIVE], vote.get("status"));
    }

    @Test
    void cancelVote_Owner() {
        // Arrange
        sm.getBlock().increase(DAY);
        Account proposer = sm.createAccount();
        Account nonProposer = sm.createAccount();
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        String actions = "[]";
        String expectedErrorMessage;

        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(proposer.getAddress())).thenReturn(BigInteger.TEN);
        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(EXA));

        governance.invoke(proposer, "defineVote", name, description, voteStart, snapshot, actions);
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        // Act & Assert
        BigInteger tooLowIndex = BigInteger.valueOf(-1);
        expectedErrorMessage  = "There is no proposal with index " + tooLowIndex;
        Executable withTooLowIndex = () -> governance.invoke(proposer, "cancelVote", tooLowIndex);
        expectErrorMessage(withTooLowIndex, expectedErrorMessage);

        BigInteger tooHighIndex = BigInteger.valueOf(100);
        expectedErrorMessage  = "There is no proposal with index " + tooHighIndex;
        Executable withTooHighIndex= () -> governance.invoke(proposer, "cancelVote", tooHighIndex);
        expectErrorMessage(withTooHighIndex, expectedErrorMessage);

        expectedErrorMessage = "Only owner or proposer may call this method.";
        Executable withWrongAccount= () -> governance.invoke(nonProposer, "cancelVote", id);
        expectErrorMessage(withWrongAccount, expectedErrorMessage);

        sm.getBlock().increase(DAY);
        sm.getBlock().increase(DAY);
        expectedErrorMessage  = "Only owner can cancel a vote that has started.";
        Executable withProposerAfterStart = () -> governance.invoke(proposer, "cancelVote", id);
        expectErrorMessage(withProposerAfterStart, expectedErrorMessage);

        governance.invoke(owner, "cancelVote", id);

        expectedErrorMessage  = "Proposal can be cancelled only from active status.";
        Executable withNonActiveStatus = () -> governance.invoke(owner, "cancelVote", id);
        expectErrorMessage(withNonActiveStatus, expectedErrorMessage);

        Map<String, Object> vote = getVote(id);
        
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
        verify(bnUSD.mock).govTransfer(daofund.getAddress(), proposer.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.CANCELLED], vote.get("status"));
        assertEquals(true, vote.get("fee_refund_status"));
    }

    @Test
    void cancelVote_Proposer() {
        // Arrange
        sm.getBlock().increase(DAY);
        Account proposer = sm.createAccount();
        Account nonProposer = sm.createAccount();
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        String actions = "[]";
        String expectedErrorMessage;

        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(proposer.getAddress())).thenReturn(BigInteger.TEN);
        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(EXA));

        governance.invoke(proposer, "defineVote", name, description, voteStart, snapshot, actions);
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        // Act & Assert
        governance.invoke(proposer, "cancelVote", id);

        Map<String, Object> vote = getVote(id);
        
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
        verify(bnUSD.mock).govTransfer(daofund.getAddress(), proposer.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.CANCELLED], vote.get("status"));
        assertEquals(true, vote.get("fee_refund_status"));
    }

    @Test
    void castVote_restrictions() {
        //Arrange
        Account zeroBalanceAccount = sm.createAccount();
        BigInteger id = defineTestVote();
        String expectedErrorMessage;
        Map<String, Object> vote = getVote(id);

        when(baln.mock.stakedBalanceOfAt(eq(owner.getAddress()), any(BigInteger.class))).thenReturn(BigInteger.valueOf(8));
        when(baln.mock.stakedBalanceOfAt(eq(zeroBalanceAccount.getAddress()), any(BigInteger.class))).thenReturn(BigInteger.ZERO);

        // Act & Assert
        expectedErrorMessage  = TAG + " :This is not an active poll.";
        Executable withVoteNotStarted = () -> governance.invoke(owner, "castVote", id, true);
        expectErrorMessage(withVoteNotStarted, expectedErrorMessage);

        BigInteger negativeId = BigInteger.valueOf(-1);
        expectedErrorMessage  = TAG + " :This is not an active poll.";
        Executable withNegativeId = () -> governance.invoke(owner, "castVote", negativeId, true);
        expectErrorMessage(withNegativeId, expectedErrorMessage);

        BigInteger nonExistingID = id.add(BigInteger.ONE);
        expectedErrorMessage  = TAG + " :This is not an active poll.";
        Executable withNonExistingID = () -> governance.invoke(owner, "castVote", nonExistingID, true);
        expectErrorMessage(withNonExistingID, expectedErrorMessage);
 
        //Arrange
        goToDay((BigInteger)vote.get("start day"));

        // Act & Assert
        expectedErrorMessage  = TAG + "Balanced tokens need to be staked to cast the vote.";
        Executable withNoStakedBaln = () -> governance.invoke(zeroBalanceAccount, "castVote", id, true);
        expectErrorMessage(withNoStakedBaln, expectedErrorMessage);


        //Arrange
        goToDay((BigInteger)vote.get("end day"));

        // Act & Assert
        expectedErrorMessage  = TAG + " :This is not an active poll.";
        Executable withVoteEnded = () -> governance.invoke(owner, "castVote", id, true);
        expectErrorMessage(withVoteEnded, expectedErrorMessage);
    }

    @Test
    void castVote() {
        //Arrange
        Account forVoter1 = sm.createAccount();
        Account forVoter2 = sm.createAccount();
        Account aginstVoter = sm.createAccount();
        Account swayedAgainstVoter = sm.createAccount();
        Account swayedForVoter = sm.createAccount();

        BigInteger forVoter1Balance = BigInteger.valueOf(3).multiply(EXA);
        BigInteger forVoter2Balance = BigInteger.TWO.multiply(EXA);
        BigInteger aginstVoterBalance = BigInteger.valueOf(3).multiply(EXA);
        BigInteger swayedAgainstVoterBalance = BigInteger.ONE.multiply(EXA);
        BigInteger swayedForVoterBalance = BigInteger.ONE.multiply(EXA);

        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger id = defineTestVote();
        String expectedErrorMessage;
        Map<String, Object> vote = getVote(id);

        when(baln.mock.totalSupply()).thenReturn(totalSupply);
        when(baln.mock.totalStakedBalanceOfAt( any(BigInteger.class))).thenReturn(totalSupply);
        when(baln.mock.stakedBalanceOfAt(eq(forVoter1.getAddress()), any(BigInteger.class))).thenReturn(forVoter1Balance);
        when(baln.mock.stakedBalanceOfAt(eq(forVoter2.getAddress()), any(BigInteger.class))).thenReturn(forVoter2Balance);
        when(baln.mock.stakedBalanceOfAt(eq(aginstVoter.getAddress()), any(BigInteger.class))).thenReturn(aginstVoterBalance);
        when(baln.mock.stakedBalanceOfAt(eq(swayedAgainstVoter.getAddress()), any(BigInteger.class))).thenReturn(swayedAgainstVoterBalance);
        when(baln.mock.stakedBalanceOfAt(eq(swayedForVoter.getAddress()), any(BigInteger.class))).thenReturn(swayedForVoterBalance);
         
        goToDay((BigInteger)vote.get("start day"));
      
        //Act
        governance.invoke(forVoter1, "castVote", id, true);
        governance.invoke(forVoter2, "castVote", id, true);
        governance.invoke(swayedAgainstVoter, "castVote", id, true);
       
        governance.invoke(aginstVoter, "castVote", id, false);
        governance.invoke(swayedForVoter, "castVote", id, false);
        
        governance.invoke(swayedAgainstVoter, "castVote", id, false);
        governance.invoke(swayedForVoter, "castVote", id, true);

        //Assert
        vote = getVote(id);
        Map<String, BigInteger> voterCount = (Map<String, BigInteger>) governance.call("getVotersCount", id);
        assertEquals(BigInteger.valueOf(3), vote.get("for_voter_count"));
        assertEquals(BigInteger.TWO, vote.get("against_voter_count"));
        assertEquals(BigInteger.valueOf(3), voterCount.get("for_voters"));
        assertEquals(BigInteger.TWO, voterCount.get("against_voters"));

        BigInteger forVotes = forVoter1Balance.add(forVoter2Balance).add(swayedForVoterBalance).multiply(EXA).divide(totalSupply);
        BigInteger againstvotes = aginstVoterBalance.add(swayedAgainstVoterBalance).multiply(EXA).divide(totalSupply);
        assertEquals(forVotes, vote.get("for"));
        assertEquals(againstvotes, vote.get("against"));

        Map<String, BigInteger> swayedAgainstVoterVotes = (Map<String, BigInteger>) governance.call("getVotesOfUser", id, swayedAgainstVoter.getAddress());
        Map<String, BigInteger> swayedForVoterVotes = (Map<String, BigInteger>) governance.call("getVotesOfUser", id, swayedForVoter.getAddress());

        assertEquals(BigInteger.ZERO, swayedAgainstVoterVotes.get("for"));
        assertEquals(swayedAgainstVoterBalance, swayedAgainstVoterVotes.get("against"));
        assertEquals(swayedForVoterBalance, swayedForVoterVotes.get("for"));
        assertEquals(BigInteger.ZERO, swayedForVoterVotes.get("against"));
    }

    @Test
    void evaluateVote_restrictions() {
        //Arrange
        BigInteger forVoters = BigInteger.valueOf(7).multiply(EXA);
        BigInteger againstVoters = BigInteger.valueOf(3).multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger voteIndex = createVoteWith("vote", totalSupply, forVoters, againstVoters);

        String expectedErrorMessage;

        // Act & Assert
        BigInteger negativeId = BigInteger.valueOf(-1);
        expectedErrorMessage  = TAG + ": There is no proposal with index " + negativeId;
        Executable withNegativeId = () -> governance.invoke(owner, "evaluateVote", negativeId);
        expectErrorMessage(withNegativeId, expectedErrorMessage);

        BigInteger toHighID = ((BigInteger) governance.call("getProposalCount")).add(BigInteger.ONE);
        expectedErrorMessage  = TAG + ": There is no proposal with index " + toHighID;
        Executable withToHighID = () -> governance.invoke(owner, "evaluateVote", toHighID);
        expectErrorMessage(withToHighID, expectedErrorMessage);

        expectedErrorMessage  = TAG + ": Voting period has not ended.";
        Executable withVoteNotEnded = () -> governance.invoke(owner, "evaluateVote", voteIndex);
        expectErrorMessage(withVoteNotEnded, expectedErrorMessage);

        // Act
        Map<String, Object> vote = getVote(voteIndex);
        goToDay((BigInteger)vote.get("end day"));
        governance.invoke(owner, "evaluateVote", voteIndex);
        vote = getVote(voteIndex);

        // Act & Assert
        expectedErrorMessage  =  TAG + ": This proposal is not active";
        Executable withEndedVote = () -> governance.invoke(owner, "evaluateVote", voteIndex);
        expectErrorMessage(withEndedVote, expectedErrorMessage);
    }

    @Test
    void evaluateVote_succeded() {
        // Arrange
        Account voteEvaluator = sm.createAccount();
        BigInteger forVoters = BigInteger.valueOf(7).multiply(EXA);
        BigInteger againstVoters = BigInteger.valueOf(3).multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger voteIndex = createVoteWith("vote", totalSupply, forVoters, againstVoters);

        // Act
        Map<String, Object> vote = getVote(voteIndex);
        goToDay((BigInteger)vote.get("end day"));
        governance.invoke(voteEvaluator, "evaluateVote", voteIndex);
        vote = getVote(voteIndex);

        // Assert
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
        verify(bnUSD.mock).govTransfer(daofund.getAddress(), owner.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED], vote.get("status"));
        assertEquals(true, vote.get("fee_refund_status"));
    }

    @Test
    void evaluateVote_executed() {
        // Arrange
        String actions = "[[\"" + daofund.getAddress().toString() +  "\", \"name\", []]]";
        
        // Act
        BigInteger voteIndex = executeVoteWithActions(actions);
        Map<String, Object> vote = getVote(voteIndex);

        // Assert
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
        verify(bnUSD.mock).govTransfer(daofund.getAddress(), owner.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.EXECUTED], vote.get("status"));
        assertEquals(true, vote.get("fee_refund_status"));
    }

    @Test
    void evaluateVote_defeated() {
         // Arrange
         BigInteger forVoters = BigInteger.valueOf(3).multiply(EXA);
         BigInteger againstVoters = BigInteger.valueOf(7).multiply(EXA);
         BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
         BigInteger voteIndex = createVoteWith("vote", totalSupply, forVoters, againstVoters);
 
         // Act
         Map<String, Object> vote = getVote(voteIndex);
         goToDay((BigInteger)vote.get("end day"));
         governance.invoke(owner, "evaluateVote", voteIndex);
         vote = getVote(voteIndex);
 
         // Assert
         BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
         verify(bnUSD.mock, never()).govTransfer(daofund.getAddress(), owner.getAddress(), voteDefinitionFee, new byte[0]);
         assertEquals(ProposalStatus.STATUS[ProposalStatus.DEFEATED], vote.get("status"));
         assertEquals(false, vote.get("fee_refund_status"));
    }

    @Test
    void evaluateVote_noQuorum() {
        // Arrange
        BigInteger forVoters = BigInteger.valueOf(2).multiply(EXA);
        BigInteger againstVoters = BigInteger.valueOf(1).multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        governance.invoke(owner, "setQuorum", BigInteger.valueOf(50));

        BigInteger voteIndex = createVoteWith("vote", totalSupply, forVoters, againstVoters);

        // Act
        Map<String, Object> vote = getVote(voteIndex);
        goToDay((BigInteger)vote.get("end day"));
        governance.invoke(owner, "evaluateVote", voteIndex);
        vote = getVote(voteIndex);

        // Assert
        BigInteger voteDefinitionFee = (BigInteger)governance.call("getVoteDefinitionFee");
        verify(bnUSD.mock, never()).govTransfer(daofund.getAddress(), owner.getAddress(), voteDefinitionFee, new byte[0]);
        assertEquals(ProposalStatus.STATUS[ProposalStatus.NO_QUORUM], vote.get("status"));
        assertEquals(false, vote.get("fee_refund_status"));
    }

    @Test
    void getProposals() {
        // Arrange
        String voteName1 = "test1";
        String voteName2 = "test2";
        String voteName3 = "test3";
        String voteName4 = "test4";
        defineTestVoteWithName(voteName1);
        
        defineTestVoteWithName(voteName2);
        createVoteWith(voteName3, BigInteger.TEN.multiply(EXA), BigInteger.valueOf(7).multiply(EXA), BigInteger.valueOf(3).multiply(EXA));
        defineTestVoteWithName(voteName4);
    

        // Act
        List<Map<String, Object>> votes = (List<Map<String, Object>>) governance.call("getProposals", BigInteger.valueOf(5), BigInteger.ZERO);

        // Assert
        assertEquals(4, votes.size());
        assertEquals(voteName1, votes.get(0).get("name"));
        assertEquals(voteName2, votes.get(1).get("name"));
        assertEquals(voteName3, votes.get(2).get("name"));
        assertEquals(voteName4, votes.get(3).get("name"));

        // Act
        votes = (List<Map<String, Object>>) governance.call("getProposals", BigInteger.valueOf(2), BigInteger.ZERO);

        // Assert
        assertEquals(2, votes.size());
        assertEquals(voteName1, votes.get(0).get("name"));
        assertEquals(voteName2, votes.get(1).get("name"));

        // Act
        votes = (List<Map<String, Object>>) governance.call("getProposals", BigInteger.valueOf(2), BigInteger.valueOf(2));

        // Assert
        assertEquals(2, votes.size());
        assertEquals(voteName2, votes.get(0).get("name"));
        assertEquals(voteName3, votes.get(1).get("name"));
    }

    @Test
    void executeVote_multipleActions() {
        // Arrange
        JsonArray addAcceptedTokensParameters = new JsonArray()
            .add(createParameter(sicx.getAddress()));

        JsonArray permitParameters = new JsonArray()
            .add(createParameter(BigInteger.ONE))
            .add(createParameter( true));

        JsonArray actions = new JsonArray()
            .add(createVoteAction(dividends.getAddress(), "addAcceptedTokens", addAcceptedTokensParameters))
            .add(createVoteAction(dex.getAddress(), "permit", permitParameters));

        // Act
        executeVoteWithActions(actions.toString());

        // Assert
        verify(dividends.mock, times(2)).addAcceptedTokens(sicx.getAddress());
        verify(dex.mock, times(2)).permit(BigInteger.ONE, true);
    }
}
