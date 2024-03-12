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
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

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
        loanTaker1.stakeDepositAndBorrow(collateralAmount, loanAmount);
        loanTaker2.stakeDepositAndBorrow(collateralAmount, loanAmount);
        loanTaker3.stakeDepositAndBorrow(collateralAmount, loanAmount);

        // Assert
        verifyRewards(loanTaker1);
        verifyRewards(loanTaker2);
        verifyRewards(loanTaker3);

        // Act
        loanTaker1.bnUSD.transfer(loanTaker2.getAddress(), fee, null);

        loanTaker2.loans.returnAsset("bnUSD", loanAmount.add(fee), "sICX");
        loanTaker3.loans.returnAsset("bnUSD", loanAmount.divide(BigInteger.TWO), "sICX");
        loanTaker2.rewards.claimRewards(reader.rewards.getUserSources(loanTaker2.getAddress().toString()));
        loanTaker3.rewards.claimRewards(reader.rewards.getUserSources(loanTaker3.getAddress().toString()));

        // Assert
        verifyRewards(loanTaker1);
        verifyNoRewards(loanTaker2);
        verifyRewards(loanTaker3);

        // Act
        loanTaker2.loans.borrow("sICX", "bnUSD", loanAmount);

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
        icxSicxLpLeaving.rewards.claimRewards(reader.rewards.getUserSources(icxSicxLpLeaving.getAddress().toString()));

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

        borrower.stakeDepositAndBorrow(BigInteger.TEN.pow(24), lpAmount.multiply(BigInteger.valueOf(3)));

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
        sicxBnusdLP2.rewards.claimRewards(reader.rewards.getUserSources(sicxBnusdLP2.getAddress().toString()));
        sicxBnusdLP3.rewards.claimRewards(reader.rewards.getUserSources(sicxBnusdLP3.getAddress().toString()));

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
        BigInteger initialSupply = reader.dex.getBalanceAndSupply(sourceName, reader.getAddress().toString())
                .get("_totalSupply");
        icxSicxLp.dex._transfer(balanced.dex._address(), lpBalance, null);
        icxSicxLpBoosted.dex._transfer(balanced.dex._address(), lpBalance, null);

        initialSupply = initialSupply.add(lpBalance).add(lpBalance);

        balanced.increaseDay(1);
        verifyRewards(icxSicxLp);
        verifyRewards(icxSicxLpBoosted);

        // Act
        BigInteger availableBalnBalance = reader.baln.balanceOf(icxSicxLpBoosted.getAddress());
        long unlockTime = (System.currentTimeMillis() * 1000) + (MICRO_SECONDS_IN_A_DAY.multiply(lockDays)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        icxSicxLpBoosted.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());

        // Assert
        verifyRewards(icxSicxLpBoosted);

        Map<String, BigInteger> currentWorkingBalanceAndSupply = reader.rewards.getWorkingBalanceAndSupply(sourceName,
                icxSicxLpBoosted.getAddress().toString());
        assertTrue(currentWorkingBalanceAndSupply.get("workingSupply").compareTo(initialSupply) > 0);
        assertTrue(currentWorkingBalanceAndSupply.get("workingBalance").compareTo(lpBalance) > 0);

        Map<String, Map<String, BigInteger>> boostData = reader.rewards.getBoostData(
                icxSicxLpBoosted.getAddress().toString(),
                new String[] { "sICX/ICX", "Loans" });
        assertEquals(currentWorkingBalanceAndSupply.get("workingSupply"), boostData.get("sICX/ICX").get(
                "workingSupply"));
        assertEquals(currentWorkingBalanceAndSupply.get("workingBalance"), boostData.get("sICX/ICX").get(
                "workingBalance"));
        assertEquals(initialSupply, boostData.get("sICX/ICX").get("supply"));
        assertEquals(lpBalance, boostData.get("sICX/ICX").get("balance"));

        // Act
        owner.governance.execute(createSingleTransaction(balanced.bBaln._address(), "setPenaltyAddress",
                new JsonArray().add(createParameter(balanced.daofund._address()))).toString());
        icxSicxLpBoosted.boostedBaln.withdrawEarly();

        // Assert
        currentWorkingBalanceAndSupply = reader.rewards.getWorkingBalanceAndSupply(sourceName,
                icxSicxLpBoosted.getAddress().toString());
        assertEquals(currentWorkingBalanceAndSupply.get("workingSupply"), initialSupply);
        assertEquals(currentWorkingBalanceAndSupply.get("workingBalance"), lpBalance);
    }

    @Test
    @Order(20)
    void changeRewardsDistributions() throws Exception {
        // Arrange
        BigInteger emission = reader.rewards.getEmission(BigInteger.ZERO);
        Map<String, Map<String, BigInteger>> distributions = reader.rewards.getDistributionPercentages();
        BigInteger loansDist = distributions.get("Fixed").get("Loans");
        BigInteger sicxDist = distributions.get("Fixed").get("sICX/ICX");
        BigInteger expectedLoansMint = loansDist.multiply(emission).divide(EXA);
        BigInteger expectedSicxMint = sicxDist.multiply(emission).divide(EXA);

        // Assert
        BigInteger loansMint = hexObjectToBigInteger(reader.rewards.getSourceData("Loans").get("total_dist"));
        BigInteger sicxMint = hexObjectToBigInteger(reader.rewards.getSourceData("sICX/ICX").get("total_dist"));
        assertEquals(expectedLoansMint, loansMint);
        assertEquals(expectedSicxMint, sicxMint);

        // Act
        BigInteger halfLoansDist = loansDist.divide(BigInteger.TWO);
        JsonArray updateLoansPercentage = new JsonArray()
                .add(createParameter("Loans"))
                .add(createParameter(halfLoansDist));
        JsonArray updateSICXICXPercentage = new JsonArray()
                .add(createParameter("sICX/ICX"))
                .add(createParameter(sicxDist.add(halfLoansDist)));
        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateLoansPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateSICXICXPercentage));

        owner.governance.execute(actions.toString());

        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {
        });

        // Assert
        expectedLoansMint = halfLoansDist.multiply(emission).divide(EXA);
        expectedSicxMint = sicxDist.add(halfLoansDist).multiply(emission).divide(EXA);

        loansMint = hexObjectToBigInteger(reader.rewards.getSourceData("Loans").get("total_dist"));
        sicxMint = hexObjectToBigInteger(reader.rewards.getSourceData("sICX/ICX").get("total_dist"));
        assertEquals(expectedLoansMint, loansMint);
        assertEquals(expectedSicxMint, sicxMint);

    }

    @Test
    @Order(21)
    void removeRewardsDistributions() throws Exception {
        // Arrange
        Map<String, Map<String, BigInteger>> distributions = reader.rewards.getDistributionPercentages();
        BigInteger loansDist = distributions.get("Fixed").get("Loans");
        BigInteger sicxDist = distributions.get("Fixed").get("sICX/ICX");

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient icxSicxLP = balanced.newClient();
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        loanTaker.stakeDepositAndBorrow(collateralAmount, loanAmount);
        icxSicxLP.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);

        // Act
        JsonArray updateLoansPercentage = new JsonArray()
                .add(createParameter("Loans"))
                .add(createParameter(BigInteger.ZERO));
        JsonArray updateSICXICXPercentage = new JsonArray()
                .add(createParameter("sICX/ICX"))
                .add(createParameter(sicxDist.add(loansDist)));
        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateLoansPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateSICXICXPercentage));

        owner.governance.execute(actions.toString());

        verifyRewards(loanTaker);
        verifyRewards(icxSicxLP);

        // Assert
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {
        });

        verifyRewards(loanTaker);
        verifyRewards(icxSicxLP);

        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {
        });
        verifyNoRewards(loanTaker);

        // reset
        updateLoansPercentage = new JsonArray()
                .add(createParameter("Loans"))
                .add(createParameter(BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(16))));
        updateSICXICXPercentage = new JsonArray()
                .add(createParameter("sICX/ICX"))
                .add(createParameter(BigInteger.TEN.pow(17)));
        actions = new JsonArray()
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateSICXICXPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateLoansPercentage));

        owner.governance.execute(actions.toString());
    }

    @Test
    @Order(30)
    void voting() throws Exception {
        // Arrange
        BalancedClient borrower = balanced.newClient(BigInteger.TEN.pow(25));
        BalancedClient sicxBnusdLP = balanced.newClient();
        BigInteger lpAmount = BigInteger.TEN.pow(22);

        borrower.stakeDepositAndBorrow(BigInteger.TEN.pow(24), lpAmount);
        borrower.bnUSD.transfer(sicxBnusdLP.getAddress(), lpAmount, null);
        joinsICXBnusdLP(sicxBnusdLP, lpAmount, lpAmount);
        stakeICXBnusdLP(sicxBnusdLP);
        balanced.increaseDay(1);

        // Act
        JsonArray updateSICXBnUSDPercentage = new JsonArray()
                .add(createParameter("sICX/bnUSD"))
                .add(createParameter(BigInteger.ZERO));
        JsonArray updateSICXICXPercentage = new JsonArray()
                .add(createParameter("sICX/ICX"))
                .add(createParameter(BigInteger.ZERO));
        JsonArray updateBalnBnUSDPercentage = new JsonArray()
                .add(createParameter("BALN/bnUSD"))
                .add(createParameter(BigInteger.ZERO));
        JsonArray updateBalnSICXPercentage = new JsonArray()
                .add(createParameter("BALN/sICX"))
                .add(createParameter(BigInteger.ZERO));
        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateSICXBnUSDPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateSICXICXPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateBalnBnUSDPercentage))
                .add(createTransaction(balanced.rewards._address(), "setFixedSourcePercentage",
                        updateBalnSICXPercentage));
        owner.governance.execute(actions.toString());

        // Assert
        verifyRewards(borrower);
        verifyRewards(sicxBnusdLP);

        balanced.increaseDay(1);
        verifyRewards(borrower);
        verifyNoRewards(sicxBnusdLP);

        // Arrange
        BigInteger availableBalnBalance = reader.baln.balanceOf(sicxBnusdLP.getAddress());
        long unlockTime = (System.currentTimeMillis() * 1000)
                + (MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(400))).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        sicxBnusdLP.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());
        availableBalnBalance = reader.baln.balanceOf(borrower.getAddress());
        borrower.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());

        // Act & Assert
        // Simple vote test we can't test further due to time constraints
        sicxBnusdLP.rewards.voteForSource("BALN/bnUSD", BigInteger.valueOf(5000));
        sicxBnusdLP.rewards.voteForSource("BALN/sICX", BigInteger.valueOf(5000));
        borrower.rewards.voteForSource("BALN/sICX", BigInteger.valueOf(10000));

        assertThrows(UserRevertedException.class,
                () -> sicxBnusdLP.rewards.voteForSource("BALN/bnUSD", BigInteger.valueOf(2000)));

        assertThrows(UserRevertedException.class,
                () -> borrower.rewards.voteForSource("BALN/bnUSD", BigInteger.valueOf(1)));
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

    private BigInteger verifyRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards(client.rewards.getUserSources(client.getAddress().toString()));
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);

        return balancePostClaim.subtract(balancePreClaim);
    }

    private void verifyNoRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards(client.rewards.getUserSources(client.getAddress().toString()));
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertEquals(balancePostClaim, balancePreClaim);
    }

    private JsonObject createDistributionPercentage(String name, BigInteger percentage) {
        JsonObject recipient = new JsonObject()
                .add("recipient_name", createParameter(name))
                .add("dist_percent", createParameter(percentage));

        return recipient;
    }

}
