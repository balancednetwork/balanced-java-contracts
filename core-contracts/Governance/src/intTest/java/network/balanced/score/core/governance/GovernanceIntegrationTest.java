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
import foundation.icon.icx.KeyWallet;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.Address;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient tester;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;

        KeyWallet testerWallet = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester = new BalancedClient(balanced, testerWallet);

        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setQuorum(BigInteger.ONE);
        balanced.increaseDay(1);

        tester.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(20), null, null);

        balanced.increaseDay(1);
        balanced.syncDistributions();

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
       
        JsonArray rebalancingThresholdParameter = new JsonArray()
            .add(createParameter(rebalancingThreshold));

        JsonArray actions = new JsonArray()
            .add(createVoteAction(balanced.rebalancing._address(), "setPriceDiffThreshold", rebalancingThresholdParameter));

        BigInteger day = tester.governance.getDay();
        String name = "testVote";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        BigInteger snapshot = day.add(BigInteger.TWO);
        tester.governance.defineVote(name, "test", voteStart, snapshot, actions.toString());
        assertNotEquals(rebalancingThreshold, owner.rebalancing.getPriceChangeThreshold());

        balanced.increaseDay(4);
        BigInteger id = tester.governance.getVoteIndex(name);
        tester.governance.castVote(id, true);

        balanced.increaseDay(2);

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
            fail();
        } catch (Exception e) {
            //success
        }
    }


    public static JsonObject createJsonDistribtion(String name, BigInteger dist) {
        return new JsonObject()
            .add("recipient_name", name)
            .add("dist_percent", dist.toString());
    }
    
    public static JsonObject createJsonDisbusment(String token, BigInteger amount) {
        return new JsonObject()
            .add("address", token)
            .add("amount", amount.intValue());
    }

    public static JsonObject createParameter(String value) {
        return new JsonObject()
            .add("type", "String")
            .add("value", value);
    }

    public static JsonObject createParameter(Address value) {
        return new JsonObject()
            .add("type", "Address")
            .add("value", value.toString());
    }

    public static JsonObject createParameter(BigInteger value) {
        return new JsonObject()
            .add("type", "int")
            .add("value", value.intValue());
    }

    public static JsonObject createParameter(Boolean value) {
        return new JsonObject()
            .add("type", "boolean")
            .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonObject value) {
        return new JsonObject()
            .add("type", type)
            .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonArray value) {
        return new JsonObject()
            .add("type", type)
            .add("value", value);
    }

    public static JsonArray createVoteAction(Address address, String method, JsonArray parameters) {
        return new JsonArray()
            .add(address.toString())
            .add(method)
            .add(parameters);
    }
}
