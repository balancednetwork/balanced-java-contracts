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

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import static network.balanced.score.core.governance.GovernanceConstants.*;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.core.governance.interfaces.*;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class GovernanceIntegrationTest implements ScoreIntegrationTest{
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient tester;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.deployBalanced();
        owner = balanced.ownerClient;

        KeyWallet testerWallet = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester = new BalancedClient(balanced, testerWallet);

        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setQuorum(BigInteger.ONE);
        increaseDay(1);
        owner.baln.toggleEnableSnapshot();

        tester.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(20), null, null);        

        increaseDay(1);
        syncDistributions();
        
        tester.rewards.claimRewards();

        BigInteger balance = tester.baln.balanceOf(tester.getAddress());
        tester.baln.stake(balance);
    }
  
    @Test
    void testName() {
        assertEquals("Balanced Governance", tester.governance.name());
    }

    @Test
    void executeVote() {
        BigInteger rebalancingThreshold = BigInteger.TEN;
        JsonObject setRebalancingThresholdParameters = new JsonObject()
            .add("_value", rebalancingThreshold.intValue());
        
        JsonArray setRebalancingThreshold = new JsonArray()
            .add("setRebalancingThreshold")
            .add(setRebalancingThresholdParameters);

        JsonArray actions = new JsonArray()
            .add(setRebalancingThreshold);

        BigInteger day = tester.governance.getDay();
        String name = "testVote";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        BigInteger snapshot = day.add(BigInteger.TWO);
        tester.governance.defineVote(name, "test", voteStart, snapshot, actions.toString());

        increaseDay(4);
        BigInteger id = tester.governance.getVoteIndex(name);
        tester.governance.castVote(id, true);

        increaseDay(2);

        tester.governance.evaluateVote(id);

        assertEquals(rebalancingThreshold, owner.rebalancing.getPriceChangeThreshold());
    }

    @Test
    void TryDefineVoteWithBadAction() {
        BigInteger rebalancingThreshold = BigInteger.TEN;
        JsonObject setRebalancingThresholdParameters = new JsonObject()
            .add("_value", rebalancingThreshold.intValue());
        
        JsonArray setRebalancingThreshold = new JsonArray()
            .add("NonExistingMethod")
            .add(setRebalancingThresholdParameters);

        JsonArray actions = new JsonArray()
            .add(setRebalancingThreshold);

        BigInteger day = tester.governance.getDay();
        String name = "testFailingVote";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        BigInteger snapshot = day.add(BigInteger.TWO);
        try {
            tester.governance.defineVote(name, "test", voteStart, snapshot, actions.toString());
            assertTrue(false);
        } catch (Exception e) {
            //success
        }
    }
    
    protected static void syncDistributions() {
        while (!checkDistributionsDone()) {
            Consumer<TransactionResult> distributeConsumer = result -> {};
            owner.rewards.distribute(distributeConsumer);
        }
    }
    
    protected static boolean checkDistributionsDone() {
        BigInteger day = owner.governance.getDay();
        Map<String, Object> status = owner.rewards.distStatus();
        if (hexObjectToInt(status.get("platform_day")) < day.intValue()) {
            return false;
        }

        Map<String, String> dataSourceStatus = (Map<String, String>) status.get("source_days");
        for (String sourceDay : dataSourceStatus.values()) {
            if (hexObjectToInt(sourceDay) < day.intValue()) {
                return false;
            }
        }

        return true;
    }

    private static int hexObjectToInt(Object hexNumber) {
        return Integer.parseInt(((String)hexNumber).substring(2), 16);
    }
    private static void increaseDay(int nrOfDays) {
        owner.governance.setTimeOffset(owner.governance.getTimeOffset().subtract(U_SECONDS_DAY.multiply(BigInteger.valueOf(nrOfDays))));
        owner.baln.setTimeOffset();
    }
}
