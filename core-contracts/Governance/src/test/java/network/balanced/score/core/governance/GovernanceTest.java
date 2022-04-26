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
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Context;
import score.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.core.governance.interfaces.*;

import static network.balanced.score.core.governance.GovernanceConstants.*;

public class GovernanceTest extends GovernanceTestBase {

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
        
        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(accountWithLowBalance.getAddress())).thenReturn(BigInteger.ZERO);
        
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

        BigInteger snapshotAfterStart = voteStart;
        expectedErrorMessage  = "The reference snapshot must be in the range: [current_day (" + day +"), start_day - 1 (" + voteStart.subtract(BigInteger.ONE) + ")].";
        Executable withSnapshotAfterStart = () -> governance.invoke(owner, "defineVote", name, description, voteStart, snapshotAfterStart, actions);
        expectErrorMessage(withSnapshotAfterStart, expectedErrorMessage);

        BigInteger balnVoteDefinitionCriterion = (BigInteger) governance.call("getBalnVoteDefinitionCriterion");
        expectedErrorMessage  = "User needs at least " + balnVoteDefinitionCriterion.divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.";
        Executable withToFewStakedBaln = () -> governance.invoke(accountWithLowBalance, "defineVote", name, description, voteStart, snapshot, actions);
        expectErrorMessage(withToFewStakedBaln, expectedErrorMessage);

        // Arrange 
        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);

        // Act & Assert
        expectedErrorMessage  = "Poll name " + name + " has already been used.";
        Executable withAlreadyUsedName = () -> governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);
        expectErrorMessage(withAlreadyUsedName, expectedErrorMessage);
    }

    @Test
    void cancelVote() {
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
    }
    

    // @Test
    // void castVote() {

    @Test
    void executeVote_enableDividends() {
        String actions = "[[\"enableDividends\", {}]]";
        executeVoteWithActions(actions);
        verify(dividends.mock).setDistributionActivationStatus(true);
    }

    @Test
    void executeVote_addNewDataSource() {
        JsonObject addNewDataSourceParameters = new JsonObject()
            .add("_data_source_name", "test")
            .add("_contract_address", "cx66d4d90f5f113eba575bf793570135f9b10cece1");
        
        JsonArray addNewDataSource = new JsonArray()
            .add("addNewDataSource")
            .add(addNewDataSourceParameters);

        JsonArray actions = new JsonArray()
            .add(addNewDataSource);

        executeVoteWithActions(actions.toString());
        verify(rewards.mock).addNewDataSource("test", Address.fromString("cx66d4d90f5f113eba575bf793570135f9b10cece1"));
    }

    // @Test
    // void executeVote_updateBalTokenDistPercentage() {
    //     DistributionPercentage[] distPercentages = GovernanceConstants.RECIPIENTS;
    //     JsonArray distribution = new JsonArray()
    //         .add(createJsonDistribtion("Loans",  BigInteger.valueOf(25).multiply(BigInteger.TEN.pow(16))))
    //         .add(createJsonDistribtion("sICX/ICX",  BigInteger.TEN.multiply(BigInteger.TEN.pow(16))))
    //         .add(createJsonDistribtion("Worker Tokens",  BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(16))))
    //         .add(createJsonDistribtion("Reserve Fund",  BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(16))))
    //         .add(createJsonDistribtion("DAOfund",  BigInteger.valueOf(40).multiply(BigInteger.TEN.pow(16))));

    //     JsonObject updateBalTokenDistPercentageParameter = new JsonObject()
    //         .add("_recipient_list", distribution);

    //     JsonArray updateBalTokenDistPercentage = new JsonArray()
    //         .add("updateBalTokenDistPercentage")
    //         .add(updateBalTokenDistPercentageParameter);

    //     JsonArray actions = new JsonArray()
    //         .add(updateBalTokenDistPercentage);

    //     executeVoteWithActions(actions.toString());
    //     verify(rewards.mock).updateBalTokenDistPercentage(distPercentages);
    // }

    @Test
    void vote_multiAction() {
        JsonArray enableDividends = new JsonArray()
            .add("enableDividends")
            .add(new JsonObject());

        JsonObject addNewDataSourceParameters = new JsonObject()
            .add("_data_source_name", "test")
            .add("_contract_address", "cx66d4d90f5f113eba575bf793570135f9b10cece1");
        
        JsonArray addNewDataSource = new JsonArray()
            .add("addNewDataSource")
            .add(addNewDataSourceParameters);

        JsonArray actions = new JsonArray()
            .add(addNewDataSource)
            .add(enableDividends);

        executeVoteWithActions(actions.toString());
        verify(dividends.mock).setDistributionActivationStatus(true);
        verify(rewards.mock).addNewDataSource("test", Address.fromString("cx66d4d90f5f113eba575bf793570135f9b10cece1"));

    }

    // @Test
    // void setupBalanced() {
    //     // Act
    //     governance.invoke(owner, "configureBalanced");

    //     // Assert
    //     verify(loans.mock).addAsset(sicx.getAddress(), true, true);
    //     verify(loans.mock).addAsset(bnUSD.getAddress(), true, false);
    //     verify(loans.mock).addAsset(baln.getAddress(), false, true);

    //     // Act
    //     governance.invoke(owner, "launchBalanced");

    //     // Assert
    //     verify(loans.mock).addAsset(sicx.getAddress(), true, true);
    //     verify(loans.mock).addAsset(bnUSD.getAddress(), true, false);
    //     verify(loans.mock).addAsset(baln.getAddress(), false, true);
    // }



}
