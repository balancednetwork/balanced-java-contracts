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

        owner.baln.toggleEnableSnapshot();

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });
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

        loanTaker2.loans.returnAsset("bnUSD", loanAmount.add(fee), true);
        loanTaker3.loans.returnAsset("bnUSD", loanAmount.divide(BigInteger.TWO), true);
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

    // @Test
    // void verifyRewards_SICX() {
      
    // }

    // @Test
    // void verifyRewards_StakedLP() {
      
    // }

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
        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans", halfLoansDist),
            createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(halfLoansDist)),
            createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")),
            createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")),
            createDistributionPercentage("DAOfund", distributions.get("DAOfund")),
            createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")),
            createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")),
            createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX"))
        };

        owner.governance.updateBalTokenDistPercentage(recipients);

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

        BalancedClient loanTaker = balanced.newClient();
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        loanTaker.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);
        // dex taker

        verifyRewards(loanTaker);
        
        // Act
        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans", BigInteger.ZERO),
            createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(loansDist)),
            createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")),
            createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")),
            createDistributionPercentage("DAOfund", distributions.get("DAOfund")),
            createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")),
            createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")),
            createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX"))
        };

        owner.governance.updateBalTokenDistPercentage(recipients);

        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});

        verifyRewards(loanTaker);
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        verifyNoRewards(loanTaker);
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

    private DistributionPercentage createDistributionPercentage(String name, BigInteger percentage) {
        DistributionPercentage recipient = new DistributionPercentage();
        recipient.recipient_name = name;
        recipient.dist_percent = percentage;
        return recipient;
    }

}
