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
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.getContractData;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
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

        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setQuorum(BigInteger.ONE);
        balanced.increaseDay(1);

        tester.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(20), null, null);

        balanced.increaseDay(1);
        balanced.syncDistributions();

        tester.rewards.claimRewards(null);

        BigInteger balance = tester.baln.balanceOf(tester.getAddress());

        final BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);
        long unlockTime =
                (System.currentTimeMillis() * 1000) + (WEEK_IN_MICRO_SECONDS.multiply(BigInteger.valueOf(4))).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        tester.baln.transfer(tester.boostedBaln._address(), balance.divide(BigInteger.TWO), data.getBytes());

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
        BigInteger duration = BigInteger.valueOf(5);

        JsonArray rebalancingThresholdParameter = new JsonArray()
            .add(createParameter(rebalancingThreshold));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.rebalancing._address(), "setPriceDiffThreshold", rebalancingThresholdParameter));

        BigInteger day = tester.governance.getDay();
        String name = "testVote1";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        String forumLink = "https://gov.balanced.network/";

        tester.governance.defineVote(name, "test", voteStart, duration, forumLink, actions.toString(), false);

        balanced.increaseDay(4);
        BigInteger id = tester.governance.getVoteIndex(name);
        tester.governance.castVote(id, true);

        balanced.increaseDay(duration.intValue());

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
        String forumLink = "https://gov.balanced.network/";
        try {
            tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, forumLink, actions.toString(), false);
            fail();
        } catch (Exception e) {
            //success
        }
    }

    void changeOwner(Address score, Address new_owner) {
        owner.systemScore.setScoreOwner(score, new_owner);
    }

    // @Test
    // void setNewOwner() throws Exception {
    //     // Arrange
    //     KeyWallet newOwnerWallet = createWalletWithBalance(BigInteger.TEN.pow(24));
    //     BalancedClient newOwner = new BalancedClient(balanced, newOwnerWallet);

    //     // Act
    //     changeOwner(owner.governance._address(), newOwner.getAddress());

    //     // Assert
    //     Address actualResult = owner.systemScore.getScoreOwner(owner.governance._address());
    //     assertEquals(newOwner.getAddress(), actualResult);
    // }

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

    @Test
    @Order(13)
    void updateContractFromVote(){
        // updating dex from vote
        // size of dex contract: 57,878 bytes
        // vote definition fee: fee=25907653000000000000 steps=2072612240 price=12500000000
        // vote execution fee: fee=25217133812500000000 steps=2017370705 price=12500000000
        changeOwner(owner.staking._address(), owner.governance._address());

        String updatedContract = "Staked ICX Manager";
        Address contractAddress = owner.governance.getAddress("Staked ICX Manager");

        JsonArray deploymentParameters = new JsonArray()
                .add(createParameter(owner.governance._address()));

        JsonArray deployToParameters = new JsonArray()
                .add(createParameter(contractAddress))
                .add(createParameter(getContractData("Staking")))
                .add(createParameter("[]"));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters));

        BigInteger day = tester.governance.getDay();
        String name = "testUpdateContractVote";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        // changeOwner(owner.staking._address(), owner.governance._address());
        tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy", actions.toString(), false);

        balanced.increaseDay(4);
        BigInteger id = tester.governance.getVoteIndex(name);
        tester.governance.castVote(id, true);

        balanced.increaseDay(2);
        tester.governance.evaluateVote(id);
        System.out.println(tester.staking.name());
        assertEquals(updatedContract, tester.staking.name());
    }

    //    updating loans vote definition fee: fee=29057727462500000000 steps=2324618197 price=12500000000
    //    updating loans vote execution fee: fee=28212425525000000000 steps=2256994042 price=12500000000
    //    updating dividends vote definition fee: fee=18598333262500000000 steps=1487866661 price=12500000000
    //    updating dividends vote execution fee: fee=18274508075000000000 steps=1461960646 price=12500000000
    //    updating staking vote definition fee: fee=23265180262500000000 steps=1861214421 price=12500000000
    //    updating staking vote execution fee: fee=22706577200000000000 steps=1816526176 price=12500000000


    @Test
    @Order(14)
    void updateSmallContractFromVote() {
        // updating DeploymentTesterJar1 contract from vote
        // size of dex contract: 969 bytes
        // vote definition fee: fee=12754290212500000000 steps=1020343217 price=12500000000
        // vote execution fee: fee=12732669150000000000 steps=1018613532 price=12500000000

        String updateValueParameter = "update";
        String deploymentValueParameter = "first deployment";
        byte[] contractData = getContractBytes(deploymentTesterJar1);
        JsonArray params = new JsonArray()
                .add(createParameter(deploymentValueParameter));
        owner.governance.deploy(contractData, params.toString());

        Address contractAddress = owner.governance.getAddress(deploymentTesterName);

        JsonArray deploymentParameters = new JsonArray()
                .add(createParameter(updateValueParameter));

        JsonArray deployToParameters = new JsonArray()
                .add(createParameter(contractAddress))
                .add(createParameter(getContractBytes(deploymentTesterJar1)))
                .add(createParameter(deploymentParameters.toString()));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters));

        BigInteger day = tester.governance.getDay();
        String name = "testUpdateContractVote2";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));

        tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy", actions.toString(), false);


        balanced.increaseDay(4);
        BigInteger id = tester.governance.getVoteIndex(name);
        tester.governance.castVote(id, true);

        balanced.increaseDay(2);
        tester.governance.evaluateVote(id);

        assertEquals(updateValueParameter, getValue(contractAddress));
    }

    // need to remove owner/contract lock on changeOwner to test this
    // @Test
    // @Order(15)
    // void updateSelfContractFromVote() {
    //     // updating governance from vote
    //     // size of governance contract: 54,962 bytes
    //     // vote definition fee: fee=25254928662500000000 steps=2020394293 price=12500000000
    //     // vote execution fee: fee=24599163162500000000 steps=1967933053 price=12500000000

    //     changeOwner(owner.governance._address(), owner.governance._address());

    //     String updatedContract = "Balanced Governance";
    //     Address contractAddress = owner.governance.getAddress("Balanced Governance");

    //     JsonArray deployToParameters = new JsonArray()
    //             .add(createParameter(contractAddress))
    //             .add(createParameter(getContractData("Governance")))
    //             .add(createParameter("[]"));

    //     JsonArray actions = new JsonArray()
    //             .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters));

    //     BigInteger day = tester.governance.getDay();
    //     String name = "testSelfUpdateContractVote";
    //     BigInteger voteStart = day.add(BigInteger.valueOf(4));

    //     tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy", actions.toString());

    //     // owner.governance.changeScoreOwner(owner.governance._address(), owner.getAddress());
    //     balanced.increaseDay(4);
    //     BigInteger id = tester.governance.getVoteIndex(name);
    //     tester.governance.castVote(id, true);

    //     balanced.increaseDay(2);

    //     changeOwner(owner.governance._address(), owner.governance._address());
    //     tester.governance.evaluateVote(id);
    //     System.out.println(tester.governance.name());
    //     assertEquals(updatedContract, tester.governance.name());
    // }

    private String getValue(Address address) {
        DefaultScoreClient client = newScoreClient(owner.wallet, address);
        return client._call(String.class, "getValue", Map.of());
    }

    private String getValue2(Address address) {
        DefaultScoreClient client = newScoreClient(owner.wallet, address);
        return client._call(String.class, "getValue2", Map.of());
    }
}
