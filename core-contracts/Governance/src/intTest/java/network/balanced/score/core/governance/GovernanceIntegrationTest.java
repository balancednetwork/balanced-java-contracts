/*
 * Copyright (c) 2022-2023 Balanced.network.
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
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.governance.deploymentTester.DeploymentTester.name;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.newScoreClient;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient tester;

    private static final String deploymentTesterJar1 = "DeploymentTester-V1.jar";
    private static final String deploymentTesterJar2 = "DeploymentTester-V2.jar";
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

        tester.stakeDepositAndBorrow(BigInteger.TEN.pow(23), BigInteger.TEN.pow(20));

        balanced.increaseDay(1);
        balanced.syncDistributions();

        tester.rewards.claimRewards(null);

        BigInteger balance = tester.baln.balanceOf(tester.getAddress());

        final BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);
        long unlockTime =
                (System.currentTimeMillis() * 1000) + (WEEK_IN_MICRO_SECONDS.multiply(BigInteger.valueOf(4))).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        tester.baln.transfer(tester.boostedBaln._address(), balance.divide(BigInteger.TWO), data.getBytes());

        owner.governance.setShutdownPrivilegeTimeLock(BigInteger.TEN);
        owner.staking.setEmergencyManager(balanced.governance._address());
        owner.sicx.setEmergencyManager(balanced.governance._address());
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
                .add(createTransaction(balanced.rebalancing._address(), "setPriceDiffThreshold",
                        rebalancingThresholdParameter));

        BigInteger day = tester.governance.getDay();
        String name = "testVote1";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        String forumLink = "https://gov.balanced.network/";

        tester.governance.defineVote(name, "test", voteStart, duration, forumLink, actions.toString());

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
            tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, forumLink, actions.toString());
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
    void deployNewContract() throws IOException {
        // Arrange
        String deploymentValueParameter = "first deployment";
        byte[] contractData = getContractBytesFromResources(this.getClass(), deploymentTesterJar1);
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
    void updateContractChangeValue() throws IOException {
        // Arrange
        String deploymentValueParameter = "second deployment";
        Address contractAddress = owner.governance.getAddress(deploymentTesterName);
        String value = getValue(contractAddress);
        assertNotEquals(deploymentValueParameter, value);

        byte[] contractData = getContractBytesFromResources(this.getClass(), deploymentTesterJar1);
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
    void updateContractAndSetNewValue() throws IOException {
        // Arrange
        String deploymentValueParameter = "third deployment";
        String newSetterValue = "test new setter";
        Address contractAddress = owner.governance.getAddress(deploymentTesterName);

        byte[] contractData = getContractBytesFromResources(this.getClass(), deploymentTesterJar2);

        JsonArray deploymentParameters = new JsonArray()
                .add(createParameter(deploymentValueParameter));

        JsonArray deployToParameters = new JsonArray()
                .add(createParameter(contractAddress))
                .add(createParameter(getContractBytesFromResources(this.getClass(), deploymentTesterJar2)))
                .add(createParameter(deploymentParameters.toString()));

        JsonArray setNewValueParameter = new JsonArray()
                .add(createParameter(newSetterValue));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters))
                .add(createTransaction(new foundation.icon.jsonrpc.Address(contractAddress.toString()), "setValue2",
                        setNewValueParameter));

        // Act
        owner.governance.execute(actions.toString());

        // Assert
        assertEquals(newSetterValue, getValue2(contractAddress));
        assertEquals(deploymentValueParameter, getValue(contractAddress));
    }

    @Test
    @Order(99)
    void updateContractFromVote() throws IOException {
        // updating staking from vote
        // size of dex contract: 57,878 bytes
        // vote definition fee: fee=25907653000000000000 steps=2072612240 price=12500000000
        // vote execution fee: fee=25217133812500000000 steps=2017370705 price=12500000000
        changeOwner(owner.staking._address(), owner.governance._address());

        String updatedContract = "Staked ICX Manager";
        Address contractAddress = owner.governance.getAddress("Staked ICX Manager");

        byte[] stakingFileByte = getContractBytesFromResources(this.getClass(), "Staking-1.2.0-optimized.jar");

        JsonArray deployToParameters = new JsonArray()
                .add(createParameter(contractAddress))
                .add(createParameter(stakingFileByte))
                .add(createParameter("[]"));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters));

        BigInteger day = tester.governance.getDay();
        String name = "testUpdateContractVote";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));
        // changeOwner(owner.staking._address(), owner.governance._address());
        tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy",
                actions.toString());

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
    void updateSmallContractFromVote() throws IOException {
        // updating DeploymentTesterJar1 contract from vote
        // size of dex contract: 969 bytes
        // vote definition fee: fee=12754290212500000000 steps=1020343217 price=12500000000
        // vote execution fee: fee=12732669150000000000 steps=1018613532 price=12500000000

        String updateValueParameter = "update";
        String deploymentValueParameter = "first deployment";
        byte[] contractData = getContractBytesFromResources(this.getClass(), deploymentTesterJar1);
        JsonArray params = new JsonArray()
                .add(createParameter(deploymentValueParameter));
        owner.governance.deploy(contractData, params.toString());

        Address contractAddress = owner.governance.getAddress(deploymentTesterName);

        JsonArray deploymentParameters = new JsonArray()
                .add(createParameter(updateValueParameter));

        JsonArray deployToParameters = new JsonArray()
                .add(createParameter(contractAddress))
                .add(createParameter(getContractBytesFromResources(this.getClass(), deploymentTesterJar1)))
                .add(createParameter(deploymentParameters.toString()));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "deployTo", deployToParameters));

        BigInteger day = tester.governance.getDay();
        String name = "testUpdateContractVote2";
        BigInteger voteStart = day.add(BigInteger.valueOf(4));

        tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy",
                actions.toString());


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

    //     tester.governance.defineVote(name, "test", voteStart, BigInteger.TWO, "https://gov.balanced.network/dummy",
    //     actions.toString());

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

    @Test
    @Order(16)
    void blacklist() throws Throwable {
        // Arrange
        BalancedClient blacklistedUser1 = balanced.newClient();
        BalancedClient blacklistedUser2 = balanced.newClient();
        BalancedClient user3 = balanced.newClient();
        BigInteger loan = BigInteger.TEN.pow(20);
        blacklistedUser1.stakeDepositAndBorrow(BigInteger.TEN.pow(23), loan);
        blacklistedUser2.stakeDepositAndBorrow(BigInteger.TEN.pow(23), loan);
        user3.stakeDepositAndBorrow(BigInteger.TEN.pow(23), loan);

        // Act
        owner.governance.blacklist(blacklistedUser1.getAddress().toString());
        owner.governance.blacklist(blacklistedUser2.getAddress().toString());

        // Assert
        Executable nonAllowedTransfer1 = () -> blacklistedUser1.bnUSD.transfer(owner.getAddress(), BigInteger.ONE,
                null);
        Executable nonAllowedTransfer2 = () -> blacklistedUser2.bnUSD.transfer(owner.getAddress(), BigInteger.ONE,
                null);
        Executable nonAllowedBurn = () -> blacklistedUser1.loans.returnAsset("bnUSD", BigInteger.TEN.pow(18), "sICX");
        user3.bnUSD.transfer(owner.getAddress(), BigInteger.ONE, null);
        assertThrows(Exception.class, nonAllowedTransfer1);
        assertThrows(Exception.class, nonAllowedTransfer2);
        assertThrows(Exception.class, nonAllowedBurn);
    }

    @Test
    @Order(17)
    void emergency_disable_enable() throws Throwable {
        // Arrange
        BalancedClient user = balanced.newClient();
        BalancedClient trustedUser1 = balanced.newClient();
        BalancedClient trustedUser2 = balanced.newClient();

        BigInteger loan = BigInteger.TEN.pow(20);
        BigInteger collateral = BigInteger.TEN.pow(23);
        user.staking.stakeICX(collateral.multiply(BigInteger.TWO), null, null);
        user.depositAndBorrow(balanced.sicx._address(), collateral, loan);
        balanced.increaseDay(1);
        user.rewards.claimRewards(new String[]{"Loans"});

        owner.governance.addAuthorizedCallerShutdown(trustedUser1.getAddress());
        owner.governance.addAuthorizedCallerShutdown(trustedUser2.getAddress());

        // Act & Assert
        trustedUser1.governance.disable();
        Executable sameUserEnable = () -> trustedUser1.governance.enable();
        assertThrows(Exception.class, sameUserEnable);

        Executable bnUSDStatusTest = () -> owner.bnUSD.transfer(owner.getAddress(), BigInteger.ONE, null);
        Executable sICXStatusTest = () -> user.sicx.transfer(owner.getAddress(), BigInteger.ONE, null);
        Executable daofundStatusTest = () -> user.daofund.claimNetworkFees();
        Executable dexStatusTest = () -> user.dex._transfer(balanced.dex._address(),
                BigInteger.valueOf(200).multiply(BigInteger.TEN.pow(18)), null);
        Executable dividendsStatusTest = () -> user.dividends.distribute((tx) -> {
        });
        Executable loansStatusTest = () -> user.loans.returnAsset("bnUSD", BigInteger.ONE, "sICX");
        Executable rewardsStatusTest = () -> user.rewards.distribute((tx) -> {
        });
        Executable stakingStatusTest = () -> user.staking.stakeICX(collateral.multiply(BigInteger.TWO), null, null);
        Executable balnStatusTest = () -> user.baln.transfer(owner.getAddress(), BigInteger.ONE, null);
        assertThrows(Exception.class, bnUSDStatusTest);
        assertThrows(Exception.class, sICXStatusTest);
        assertThrows(Exception.class, daofundStatusTest);
        assertThrows(Exception.class, dexStatusTest);
        assertThrows(Exception.class, dividendsStatusTest);
        assertThrows(Exception.class, loansStatusTest);
        assertThrows(Exception.class, rewardsStatusTest);
        assertThrows(Exception.class, stakingStatusTest);
        assertThrows(Exception.class, balnStatusTest);

        trustedUser2.governance.enable();
        user.bnUSD.transfer(owner.getAddress(), BigInteger.ONE, null);
        Executable user1Disable = () -> trustedUser1.governance.disable();
        Executable user2Disable = () -> trustedUser2.governance.disable();
        assertThrows(Exception.class, user1Disable);
        assertThrows(Exception.class, user2Disable);

        assertDoesNotThrow(bnUSDStatusTest);
        assertDoesNotThrow(sICXStatusTest);
        assertDoesNotThrow(daofundStatusTest);
        assertDoesNotThrow(dexStatusTest);
        assertDoesNotThrow(dividendsStatusTest);
        assertDoesNotThrow(loansStatusTest);
        assertDoesNotThrow(rewardsStatusTest);
        assertDoesNotThrow(stakingStatusTest);
        assertDoesNotThrow(balnStatusTest);
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
