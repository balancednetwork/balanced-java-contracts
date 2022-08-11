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

package network.balanced.score.core.rewards;

import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import static network.balanced.score.lib.utils.Constants.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RewardsIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
    }

    @Test
    @Order(10)
    void verifyRewards_Loans() throws Exception {
        // Arrange
        BalancedClient loanTaker1 = balanced.newClient();   
        BalancedClient loanTaker2 = balanced.newClient();   
        BalancedClient loanTaker3 = balanced.newClient();
        
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        // Act
        loanTaker1.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);
        loanTaker2.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);
        loanTaker3.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);

        // Assert
        verifyRewards(loanTaker1);
        verifyRewards(loanTaker2);
        verifyRewards(loanTaker3);

        // Act
        loanTaker1.bnUSD.transfer(loanTaker2.getAddress(), fee, null);

        loanTaker2.loans.returnAsset("bnUSD", loanAmount.add(fee), "sICX");
        loanTaker3.loans.returnAsset("bnUSD", loanAmount.divide(BigInteger.TWO), "sICX");
        loanTaker2.rewards.claimRewards();
        loanTaker3.rewards.claimRewards();

        // Assert
        verifyRewards(loanTaker1);
        verifyNoRewards(loanTaker2);
        verifyRewards(loanTaker3);

        // Act 
        loanTaker2.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);

        // Assert
        verifyRewards(loanTaker1);
        verifyRewards(loanTaker2);
        verifyRewards(loanTaker3);
    }

    @Test
    void verifyRewards_SICX() throws Exception {
        // Arrange
        BalancedClient icxSicxLp = balanced.newClient();   
        BalancedClient icxSicxLpLeaving = balanced.newClient();   
    
        // Act
        icxSicxLp.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);
        icxSicxLpLeaving.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);

        // Assert
        verifyRewards(icxSicxLp);
        verifyRewards(icxSicxLpLeaving);

        // Act
        icxSicxLpLeaving.dex.cancelSicxicxOrder();
        icxSicxLpLeaving.rewards.claimRewards();

        // Assert
        verifyRewards(icxSicxLp);
        verifyNoRewards(icxSicxLpLeaving);
    }

    @Test
    void verifyRewards_StakedLP() throws Exception {
        // Arrange
        BalancedClient borrower = balanced.newClient(BigInteger.TEN.pow(25));   
        BalancedClient sicxBnusdLP1 = balanced.newClient();   
        BalancedClient sicxBnusdLP2 = balanced.newClient();   
        BalancedClient sicxBnusdLP3 = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);

        borrower.loans.depositAndBorrow(BigInteger.TEN.pow(24), "bnUSD", lpAmount.multiply(BigInteger.valueOf(3)), null, null);

        borrower.bnUSD.transfer(sicxBnusdLP1.getAddress(), lpAmount, null);
        borrower.bnUSD.transfer(sicxBnusdLP2.getAddress(), lpAmount, null);
        borrower.bnUSD.transfer(sicxBnusdLP3.getAddress(), lpAmount, null);

        verifyNoRewards(sicxBnusdLP1);
        verifyNoRewards(sicxBnusdLP2);
        verifyNoRewards(sicxBnusdLP3);

        // Act
        joinsICXBnusdLP(sicxBnusdLP1, lpAmount, lpAmount);
        joinsICXBnusdLP(sicxBnusdLP2, lpAmount, lpAmount);
        joinsICXBnusdLP(sicxBnusdLP3, lpAmount, lpAmount);

        // Assert
        verifyNoRewards(sicxBnusdLP1);
        verifyNoRewards(sicxBnusdLP2);
        verifyNoRewards(sicxBnusdLP3);

        // Act
        stakeICXBnusdLP(sicxBnusdLP1);
        stakeICXBnusdLP(sicxBnusdLP2);
        stakeICXBnusdLP(sicxBnusdLP3);

        // Assert
        verifyRewards(sicxBnusdLP1);
        verifyRewards(sicxBnusdLP2);
        verifyRewards(sicxBnusdLP3);

        // Act
        unstakeICXBnusdLP(sicxBnusdLP2);
        unstakeICXBnusdLP(sicxBnusdLP3);
        sicxBnusdLP2.rewards.claimRewards();
        sicxBnusdLP3.rewards.claimRewards();

        // Assert
        verifyRewards(sicxBnusdLP1);
        verifyNoRewards(sicxBnusdLP2);
        verifyNoRewards(sicxBnusdLP3);

        // Act
        stakeICXBnusdLP(sicxBnusdLP2);
        leaveICXBnusdLP(sicxBnusdLP3);

        // Assert
        verifyRewards(sicxBnusdLP1);
        verifyRewards(sicxBnusdLP2);
        verifyNoRewards(sicxBnusdLP3);
    }

    @Test
    @Order(20)
    void changeRewardsDistributions() {
        // Arrange
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});

        BigInteger platformDay = hexObjectToBigInteger(reader.rewards.distStatus().get("platform_day"));
        BigInteger distributedDay = platformDay.subtract(BigInteger.ONE);
        BigInteger emission = reader.rewards.getEmission(distributedDay);
        Map<String, BigInteger> distributions = reader.rewards.recipientAt(distributedDay);
        BigInteger loansDist = distributions.get("Loans");
        BigInteger sicxDist = distributions.get("sICX/ICX");
        BigInteger expectedLoansMint = loansDist.multiply(emission).divide(EXA);
        BigInteger expectedSicxMint = sicxDist.multiply(emission).divide(EXA);

        // Assert
        BigInteger loansMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("Loans").get("total_dist"));
        BigInteger sicxMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("sICX/ICX").get("total_dist"));
        assertEquals(expectedLoansMint, loansMint);
        assertEquals(expectedSicxMint, sicxMint);

        // Act
        BigInteger halfLoansDist = distributions.get("Loans").divide(BigInteger.TWO);
        JsonArray recipients = new JsonArray()
            .add(createDistributionPercentage("Loans", halfLoansDist))
            .add(createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(halfLoansDist)))
            .add(createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")))
            .add(createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")))
            .add(createDistributionPercentage("DAOfund", distributions.get("DAOfund")))
            .add(createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")))
            .add(createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")))
            .add(createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX")));

        JsonArray updateBalTokenDistPercentage = new JsonArray()
            .add(createParameter("Struct[]", recipients));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.rewards._address(), "updateBalTokenDistPercentage", updateBalTokenDistPercentage));

        owner.governance.execute(actions.toString());

        balanced.increaseDay(1);
        distributedDay = distributedDay.add(BigInteger.ONE);
        owner.rewards.distribute((txr) -> {});

        // Assert
        expectedLoansMint = loansDist.subtract(halfLoansDist).multiply(emission).divide(EXA);
        expectedSicxMint = sicxDist.add(halfLoansDist).multiply(emission).divide(EXA);

        loansMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("Loans").get("total_dist"));
        sicxMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("sICX/ICX").get("total_dist"));
        assertEquals(expectedLoansMint, loansMint);
        assertEquals(expectedSicxMint, sicxMint);

    }

    @Test
    void removeRewardsDistributions() throws Exception {
        // Arrange
        BigInteger platformDay = hexObjectToBigInteger(reader.rewards.distStatus().get("platform_day"));
        BigInteger distributedDay = platformDay.subtract(BigInteger.ONE);
        Map<String, BigInteger> distributions = reader.rewards.recipientAt(distributedDay);
        BigInteger loansDist = distributions.get("Loans");
        BigInteger icxDist = distributions.get("sICX/ICX");

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient icxSicxLP = balanced.newClient();
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        loanTaker.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);
        icxSicxLP.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);
        
        // Act
        JsonArray recipients = new JsonArray()
            .add(createDistributionPercentage("Loans", BigInteger.ZERO))
            .add(createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(loansDist)))
            .add(createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")))
            .add(createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")))
            .add(createDistributionPercentage("DAOfund", distributions.get("DAOfund")))
            .add(createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")))
            .add(createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")))
            .add(createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX")));

        JsonArray updateBalTokenDistPercentage = new JsonArray()
            .add(createParameter("Struct[]", recipients));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.rewards._address(), "updateBalTokenDistPercentage", updateBalTokenDistPercentage));

        owner.governance.execute(actions.toString());

        verifyRewards(loanTaker);
        verifyRewards(icxSicxLP);

        // Assert
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        verifyRewards(loanTaker);
        BigInteger rewardsPreChange = verifyRewards(icxSicxLP);

        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        verifyNoRewards(loanTaker);
        BigInteger rewardsPostChange = verifyRewards(icxSicxLP);
        BigInteger increase = rewardsPostChange.multiply(EXA).divide(rewardsPreChange);
        BigInteger expectedIncrease = loansDist.add(icxDist).multiply(EXA).divide(icxDist);

        BigInteger increasePercent = increase.divide(BigInteger.TEN.pow(17));
        BigInteger expectedIncreasePercent = expectedIncrease.divide(BigInteger.TEN.pow(17));
        BigInteger diff = increasePercent.subtract(expectedIncreasePercent);

        assertTrue(diff.abs().compareTo(BigInteger.ONE) <= 0);
    }


    private void joinsICXBnusdLP(BalancedClient client, BigInteger icxAmount, BigInteger bnusdAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.bnUSD.transfer(balanced.dex._address(), bnusdAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(balanced.sicx._address(), balanced.bnusd._address(), sicxDeposit, bnusdAmount, true);
    }

    private void leaveICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = client.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.remove(icxBnusdPoolId, poolBalance, true);
    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }

    private void unstakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.stakedLp.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.stakedLp.unstake(icxBnusdPoolId, poolBalance);
    }

    private BigInteger verifyRewards(BalancedClient client)  {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);

        return balancePostClaim.subtract(balancePreClaim);
    }

    private void verifyNoRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }

    private JsonObject createDistributionPercentage(String name, BigInteger percentage) {
        JsonObject recipient = new JsonObject()
            .add("recipient_name", createParameter(name))
            .add("dist_percent", createParameter(percentage));

        return recipient;
    }

}
