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

import static network.balanced.score.core.governance.DeploymentTester.src.main.java.network.balanced.score.core.deploymenttester.DeploymentTester.name;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createTransaction;
import static network.balanced.score.lib.test.integration.BalancedUtils.getContractBytes;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.newScoreClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.Address;

class GovernanceIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient tester;

    private static final String deploymentTesterJar1 = System.getProperty("user.dir") + "/src/intTest/java/network/balanced/score/core/governance/DeploymentTester-V1.jar";
    private static final String deploymentTesterJar2 = System.getProperty("user.dir") + "/src/intTest/java/network/balanced/score/core/governance/DeploymentTester-V2.jar";
    private static final String deploymentTesterName = name;
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
    @Order(0)
    void testName() {
        assertEquals("Balanced Governance", tester.governance.name());
    }

    @Test
    @Order(2)
    void executeVote() {
        BigInteger rebalancingThreshold = BigInteger.TEN;
       
        JsonArray rebalancingThresholdParameter = new JsonArray()
            .add(createParameter(rebalancingThreshold));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.rebalancing._address(), "setPriceDiffThreshold", rebalancingThresholdParameter));

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
    @Order(3)
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

    @Test
    @Order(10)
    void deployNewContract() {
        // Arrange
        String deploymentValueParameter = "first deployment";
        byte[] contractData = getContractBytes(deploymentTesterJar1);
        JsonArray params = new JsonArray()
            .add(createParameter(deploymentValueParameter));
        
        // Act
        owner.governance.deploy(contractData, params.toString());

        // Assert
        Address contractAddress = owner.governance.getAddress(deploymentTesterName);
        String value = getValue(contractAddress);
        assertEquals(deploymentValueParameter, value);
        assertTrue(owner.governance.getAddresses().containsKey(deploymentTesterName));
    }

    @Test
    @Order(11)
    void updateContractChangeValue() {
        // Arrange
        String deploymentValueParameter = "second deployment";
        Address contractAddress = owner.governance.getAddress(deploymentTesterName);
        String value = getValue(contractAddress);
        assertNotEquals(deploymentValueParameter, value);

        byte[] contractData = getContractBytes(deploymentTesterJar1);
        JsonArray params = new JsonArray()
            .add(createParameter(deploymentValueParameter));
        
        // Act
        owner.governance.deployTo(contractAddress, contractData, params.toString());

        // Assert
        value = getValue(contractAddress);
        assertEquals(deploymentValueParameter, value);
    }

    @Test
    @Order(12)
    void updateContractAndSetNewValue() {
        // Arrange
        String deploymentValueParameter = "third deployment";
        String newSetterValue = "test new setter";
        Address contractAddress = owner.governance.getAddress(deploymentTesterName);

        byte[] contractData = getContractBytes(deploymentTesterJar2);

        JsonArray deploymentParameters = new JsonArray()
            .add(createParameter(deploymentValueParameter));

        JsonArray deployToParameters = new JsonArray()
            .add(createParameter(contractAddress))
            .add(createParameter(getContractBytes(deploymentTesterJar2)))
            .add(createParameter(deploymentParameters.toString()));

        JsonArray setNewValueParameter = new JsonArray()
            .add(createParameter(newSetterValue));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters))
            .add(createTransaction(new foundation.icon.jsonrpc.Address(contractAddress.toString()), "setValue2", setNewValueParameter));

        // Act
        owner.governance.execute(actions.toString());

        // Assert
        assertEquals(newSetterValue, getValue2(contractAddress));
        assertEquals(deploymentValueParameter, getValue(contractAddress));
    }

    private String getValue(Address address) {
        DefaultScoreClient client = newScoreClient(owner.wallet, address); 
        return client._call(String.class, "getValue", Map.of());
    }

    private String getValue2(Address address) {
        DefaultScoreClient client = newScoreClient(owner.wallet, address); 
        return client._call(String.class, "getValue2", Map.of());

    }
}
