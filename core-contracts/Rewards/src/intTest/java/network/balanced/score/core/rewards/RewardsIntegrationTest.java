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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
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

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[]{
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
    @Order(11)
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
    @Order(12)
    void verifyRewards_StakedLP() throws Exception {
        // Arrange
        BalancedClient borrower = balanced.newClient(BigInteger.TEN.pow(25));
        BalancedClient sicxBnusdLP1 = balanced.newClient();
        BalancedClient sicxBnusdLP2 = balanced.newClient();
        BalancedClient sicxBnusdLP3 = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);

        borrower.loans.depositAndBorrow(BigInteger.TEN.pow(24), "bnUSD", lpAmount.multiply(BigInteger.valueOf(3)),
                null, null);

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
    @Order(13)
    void boostRewards() throws Exception {
        // Arrange
        String sourceName = "sICX/ICX";
        BalancedClient icxSicxLp = balanced.newClient();
        BalancedClient icxSicxLpBoosted = balanced.newClient();

        BigInteger lockDays = BigInteger.valueOf(7).multiply(BigInteger.valueOf(3));
        BigInteger lpBalance = BigInteger.TEN.pow(22);
        BigInteger initialSupply = reader.dex.getBalanceAndSupply(sourceName, reader.getAddress()).get("_totalSupply");
        icxSicxLp.dex._transfer(balanced.dex._address(), lpBalance, null);
        icxSicxLpBoosted.dex._transfer(balanced.dex._address(), lpBalance, null);

        initialSupply = initialSupply.add(lpBalance).add(lpBalance);

        balanced.increaseDay(1);
        verifyRewards(icxSicxLp);
        verifyRewards(icxSicxLpBoosted);

        // Act
        BigInteger availableBalnBalance = reader.baln.balanceOf(icxSicxLpBoosted.getAddress());
        long unlockTime = (System.currentTimeMillis()*1000)+(MICRO_SECONDS_IN_A_DAY.multiply(lockDays)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        icxSicxLpBoosted.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());

        // Assert
        verifyRewards(icxSicxLpBoosted);

        Map<String, BigInteger> currentWorkingBalanceAndSupply = reader.rewards.getWorkingBalanceAndSupply(sourceName, icxSicxLpBoosted.getAddress());
        assertTrue(currentWorkingBalanceAndSupply.get("workingSupply").compareTo(initialSupply) > 0);
        assertTrue(currentWorkingBalanceAndSupply.get("workingBalance").compareTo(lpBalance) > 0);

        // Act
        owner.boostedBaln.setPenaltyAddress(balanced.daofund._address());
        icxSicxLpBoosted.boostedBaln.withdrawEarly();
        currentWorkingBalanceAndSupply = reader.rewards.getWorkingBalanceAndSupply(sourceName, icxSicxLpBoosted.getAddress());
        assertTrue(currentWorkingBalanceAndSupply.get("workingSupply").equals(initialSupply));
        assertTrue(currentWorkingBalanceAndSupply.get("workingBalance").equals(lpBalance));
    }

    // @Test
    // @Order(20)
    // void changeRewardsDistributions() {
    //     // Arrange
    //     balanced.increaseDay(1);
    //     owner.rewards.distribute((txr) -> {
    //     });

    //     BigInteger platformDay = hexObjectToBigInteger(reader.rewards.distStatus().get("platform_day"));
    //     BigInteger distributedDay = platformDay.subtract(BigInteger.ONE);
    //     BigInteger emission = reader.rewards.getEmission(distributedDay);
    //     Map<String, BigInteger> distributions = reader.rewards.recipientAt(distributedDay);
    //     BigInteger loansDist = distributions.get("Loans");
    //     BigInteger sicxDist = distributions.get("sICX/ICX");
    //     BigInteger expectedLoansMint = loansDist.multiply(emission).divide(EXA);
    //     BigInteger expectedSicxMint = sicxDist.multiply(emission).divide(EXA);

    //     // Assert
    //     BigInteger loansMint =
    //             hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("Loans").get("total_dist"));
    //     BigInteger sicxMint =
    //             hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("sICX/ICX").get("total_dist"
    //             ));
    //     assertEquals(expectedLoansMint, loansMint);
    //     assertEquals(expectedSicxMint, sicxMint);

    //     // Act
    //     BigInteger halfLoansDist = distributions.get("Loans").divide(BigInteger.TWO);
    //     DistributionPercentage[] recipients = new DistributionPercentage[]{
    //             createDistributionPercentage("Loans", halfLoansDist),
    //             createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(halfLoansDist)),
    //             createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")),
    //             createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")),
    //             createDistributionPercentage("DAOfund", distributions.get("DAOfund")),
    //             createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")),
    //             createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")),
    //             createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX"))
    //     };

    //     owner.governance.updateBalTokenDistPercentage(recipients);

    //     balanced.increaseDay(1);
    //     distributedDay = distributedDay.add(BigInteger.ONE);
    //     owner.rewards.distribute((txr) -> {
    //     });

    //     // Assert
    //     expectedLoansMint = loansDist.subtract(halfLoansDist).multiply(emission).divide(EXA);
    //     expectedSicxMint = sicxDist.add(halfLoansDist).multiply(emission).divide(EXA);

    //     loansMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("Loans").get(
    //             "total_dist"));
    //     sicxMint = hexObjectToBigInteger(reader.rewards.getDataSourcesAt(distributedDay).get("sICX/ICX").get(
    //             "total_dist"));
    //     assertEquals(expectedLoansMint, loansMint);
    //     assertEquals(expectedSicxMint, sicxMint);
    // }

    // @Test
    // @Order(21)
    // void removeRewardsDistributions() throws Exception {
    //     // Arrange
    //     BigInteger platformDay = hexObjectToBigInteger(reader.rewards.distStatus().get("platform_day"));
    //     BigInteger distributedDay = platformDay.subtract(BigInteger.ONE);
    //     Map<String, BigInteger> distributions = reader.rewards.recipientAt(distributedDay);
    //     BigInteger loansDist = distributions.get("Loans");
    //     BigInteger icxDist = distributions.get("sICX/ICX");

    //     BalancedClient loanTaker = balanced.newClient();
    //     BalancedClient icxSicxLP = balanced.newClient();
    //     BigInteger collateralAmount = BigInteger.TEN.pow(23);
    //     BigInteger loanAmount = BigInteger.TEN.pow(21);
    //     loanTaker.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);
    //     icxSicxLP.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);

    //     // Act
    //     DistributionPercentage[] recipients = new DistributionPercentage[]{
    //             createDistributionPercentage("Loans", BigInteger.ZERO),
    //             createDistributionPercentage("sICX/ICX", distributions.get("sICX/ICX").add(loansDist)),
    //             createDistributionPercentage("Worker Tokens", distributions.get("Worker Tokens")),
    //             createDistributionPercentage("Reserve Fund", distributions.get("Reserve Fund")),
    //             createDistributionPercentage("DAOfund", distributions.get("DAOfund")),
    //             createDistributionPercentage("sICX/bnUSD", distributions.get("sICX/bnUSD")),
    //             createDistributionPercentage("BALN/bnUSD", distributions.get("BALN/bnUSD")),
    //             createDistributionPercentage("BALN/sICX", distributions.get("BALN/sICX"))
    //     };

    //     owner.governance.updateBalTokenDistPercentage(recipients);

    //     verifyRewards(loanTaker);
    //     verifyRewards(icxSicxLP);

    //     // Assert
    //     balanced.increaseDay(1);
    //     owner.rewards.distribute((txr) -> {
    //     });
    //     verifyRewards(loanTaker);
    //     BigInteger rewardsPreChange = verifyRewards(icxSicxLP);

    //     balanced.increaseDay(1);
    //     owner.rewards.distribute((txr) -> {
    //     });
    //     verifyNoRewards(loanTaker);
    // }


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

    private BigInteger verifyRewards(BalancedClient client) {
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
        assertEquals(balancePostClaim, balancePreClaim);
    }

    private DistributionPercentage createDistributionPercentage(String name, BigInteger percentage) {
        DistributionPercentage recipient = new DistributionPercentage();
        recipient.recipient_name = name;
        recipient.dist_percent = percentage;
        return recipient;
    }

}
