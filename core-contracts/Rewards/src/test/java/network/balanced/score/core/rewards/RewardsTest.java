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

import com.iconloop.score.test.Account;
import network.balanced.score.lib.structs.DistributionPercentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardsTest extends RewardsTestBase {

    @BeforeEach
    void setup() throws Exception{
        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDataSources() {
        // Act 
        Map<String, Map<String, Object>> dataSources = (Map<String, Map<String, Object>>) rewardsScore.call("getDataSources");

        // Assert
        assertEquals(2, dataSources.size());
        assertTrue(dataSources.containsKey("Loans"));
        assertTrue(dataSources.containsKey("sICX/ICX"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDataSourceNames() {
        // Act 
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames");

        // Assert
        assertEquals(2, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getRecipients() {
        // Act 
        List<String> names = (List<String>) rewardsScore.call("getRecipients");

        // Assert
        assertEquals(5, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));
        assertTrue(names.contains("Worker Tokens"));
        assertTrue(names.contains("Reserve Fund"));
        assertTrue(names.contains("DAOfund"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void removeDataSource() {
        // Arrange
        icxPoolDist.dist_percent = BigInteger.ZERO; //0%
        loansDist.dist_percent = loansDist.dist_percent.multiply(BigInteger.TWO); //40%

        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};
        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);
        rewardsScore.invoke(admin, "distribute");

        // Act 
        rewardsScore.invoke(governance, "removeDataSource", "sICX/ICX");

        // Assert
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames");

        assertEquals(1, names.size());
        assertTrue(names.contains("Loans"));
    }

    @Test
    void removeDataSource_nonEmptyDistribution() {
        // Arrange 
        String expectedErrorMessage = RewardsImpl.TAG + ": Data source rewards percentage must be set to 0 before removing.";

        // Act 
        Executable removeNonEmpty = () -> rewardsScore.invoke(governance, "removeDataSource", "sICX/ICX");

        // Assert
        expectErrorMessage(removeNonEmpty, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void removeDataSource_nonExisting() {
        // Act 
        rewardsScore.invoke(governance, "removeDataSource", "test");

        // Assert
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames");

        assertEquals(2, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));
    }

    @Test
    void removeDataSource_notGovernance() {
        // Arrange 
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + admin.getAddress() + " Authorized Caller: " + governance.getAddress();

        // Act 
        Executable removeAsAdmin = () ->rewardsScore.invoke(admin, "removeDataSource", "sICX/ICX");

        // Assert
        expectErrorMessage(removeAsAdmin, expectedErrorMessage);
    }

    @Test
    void updateBalTokenDistPercentage_nonExistingName() {
        // Arrange
        DistributionPercentage testDist = new DistributionPercentage();
        testDist.recipient_name = "test";
        testDist.dist_percent = BigInteger.TEN; 
        String expectedErrorMessage = RewardsImpl.TAG + ": Recipient test does not exist.";

        // Act
        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{testDist, icxPoolDist, bwtDist, reserveDist, daoDist};
        Executable updateWithWrongName = () -> rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);

        // Assert
        expectErrorMessage(updateWithWrongName, expectedErrorMessage);
    }

    @Test
    void updateBalTokenDistPercentage_wrongSum() {
        // Arrange 
        icxPoolDist.dist_percent = BigInteger.ZERO; //0%
        String expectedErrorMessage = RewardsImpl.TAG + ": Total percentage does not sum up to 100.";

        // Act 
        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};
        Executable updateWithWrongSum = () -> rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);

        // Assert
        expectErrorMessage(updateWithWrongSum, expectedErrorMessage);
    }

    @Test
    void updateBalTokenDistPercentage_wrongLength() {
        // Arrange 
        String expectedErrorMessage = RewardsImpl.TAG + ": Recipient lists lengths mismatched!";

        // Act 
        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, bwtDist, reserveDist, daoDist};
        Executable updateWithWrongLength = () -> rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);

        // Assert
        expectErrorMessage(updateWithWrongLength, expectedErrorMessage);
    }

    @Test
    void updateBalTokenDistPercentage_notGovernance() {
        // Arrange 
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + admin.getAddress() + " Authorized Caller: " + governance.getAddress();

        // Act 
        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};
        Executable updateAsAdmin = () -> rewardsScore.invoke(admin, "updateBalTokenDistPercentage", (Object) distributionPercentages);

        // Assert
        expectErrorMessage(updateAsAdmin, expectedErrorMessage);
    }
    
    @Test
    void getEmission_first60Days() {
        // Arrange
        BigInteger expectedEmission = BigInteger.TEN.pow(23);

        // Act & Assert 
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.ONE));
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(60)));
    }

    @Test
    void getEmission_after60Days() {
        // Arrange
        BigInteger baseEmission = BigInteger.TEN.pow(23);
        BigInteger percent = BigInteger.valueOf(10);
        BigInteger percent100 = percent.multiply(BigInteger.valueOf(100));
        BigInteger decay = percent100.subtract(BigInteger.valueOf(5));

        BigInteger expectedEmission = baseEmission.multiply(decay).divide(percent100);
        BigInteger expectedEmissionDayAfter = expectedEmission.multiply(decay).divide(percent100);

         // Act & Assert 
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(61)));
        assertEquals(expectedEmissionDayAfter, rewardsScore.call("getEmission", BigInteger.valueOf(62)));
    }

    @Test
    void getEmission_after935Days() {
        // Arrange
        int day = 935;
        BigInteger minEmission = BigInteger.valueOf(1250).multiply(ICX);

         // Act & Assert 
         assertNotEquals(minEmission, rewardsScore.call("getEmission", BigInteger.valueOf(day-1)));
         assertEquals(minEmission, rewardsScore.call("getEmission", BigInteger.valueOf(day)));
    }

    @Test
    void tokenFallback_baln() {
        // Arrange
        Account account = sm.createAccount();

        // Act & Assert
        rewardsScore.invoke(baln.account, "tokenFallback", account.getAddress(), BigInteger.TEN, new byte[0]);
    }

    @Test
    void tokenFallback_notBaln() {
        // Arrange
        Account account = sm.createAccount();
        String expectedErrorMessage = RewardsImpl.TAG + ": The Rewards SCORE can only accept BALN tokens";

        // Act & Assert
        Executable tokenFallbackBwt = () -> rewardsScore.invoke(bwt.account, "tokenFallback", account.getAddress(), BigInteger.TEN, new byte[0]);
        expectErrorMessage(tokenFallbackBwt, expectedErrorMessage);
    }

    @Test
    void getApy() {
        // Arrange
        Account account = sm.createAccount();

        BigInteger balnPrice = EXA;
        BigInteger loansValue = BigInteger.valueOf(1000).multiply(EXA);
        when(dex.mock.getBalnPrice()).thenReturn(balnPrice);
        when(loans.mock.getBnusdValue("Loans")).thenReturn(loansValue);
    
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.ONE);
        BigInteger year = BigInteger.valueOf(365);

        BigInteger loansYearlyEmission = year.multiply(emission).multiply(loansDist.dist_percent);
        BigInteger expectedAPY = loansYearlyEmission.multiply(balnPrice).divide(EXA.multiply(loansValue));

        //Act 
        BigInteger apy = (BigInteger) rewardsScore.call("getAPY", "Loans");
        
        // Assert
        assertEquals(expectedAPY, apy);
    }

    @SuppressWarnings("unchecked")
    @Test
    void distribute() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.ONE.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger swapBalance = BigInteger.TWO.multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.TEN.multiply(EXA);
        
        Map<String, BigInteger> dataBatchLoans = Map.of(
                account.getAddress().toString(), loansBalance
        );

        Map<String, BigInteger> dataBatchSwap = Map.of(
                account.getAddress().toString(), swapBalance
        );

        int day = ((BigInteger) rewardsScore.call("getDay")).intValue();
        sm.getBlock().increase(DAY);

        when(loans.mock.getTotalValue(eq("Loans"), any(BigInteger.class))).thenReturn(loansTotalSupply);
        when(dex.mock.getTotalValue(eq("sICX/ICX"), any(BigInteger.class))).thenReturn(swapTotalSupply);

        when(loans.mock.getDataBatch(eq("Loans"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchLoans);
        when(dex.mock.getDataBatch(eq("sICX/ICX"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchSwap);

        // Act
        syncDistributions();

        // Assert
        Object users = new Address[]{account.getAddress()};
        Map<String, BigInteger> rewards = (Map<String, BigInteger>) rewardsScore.call("getBalnHoldings", users);
        BigInteger userReward = (BigInteger) rewardsScore.call("getBalnHolding", account.getAddress());
        assertEquals(rewards.get(account.getAddress().toString()), userReward);

        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);
        BigInteger userDistribution = userSwapDistribution.add(userLoansDistribution);

        assertEquals(userDistribution, rewards.get(account.getAddress().toString()));
        verify(baln.mock, times(day)).transfer(bwt.getAddress(), bwtDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day)).transfer(daoFund.getAddress(), daoDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day)).transfer(reserve.getAddress(), reserveDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
    }

    @Test
    void claimRewards() {
        // Arrange
        Account account = sm.createAccount();
        Account emptyAccount = sm.createAccount();
        BigInteger loansBalance = BigInteger.ONE.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger swapBalance = BigInteger.TWO.multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.TEN.multiply(EXA);
        
        Map<String, BigInteger> dataBatchLoans = Map.of(
                account.getAddress().toString(), loansBalance
        );

        Map<String, BigInteger> dataBatchSwap = Map.of(
                account.getAddress().toString(), swapBalance
        );

        sm.getBlock().increase(DAY);
        int day = ((BigInteger)rewardsScore.call("getDay")).intValue() - 1;

        when(loans.mock.getTotalValue(eq("Loans"), any(BigInteger.class))).thenReturn(loansTotalSupply);
        when(dex.mock.getTotalValue(eq("sICX/ICX"), any(BigInteger.class))).thenReturn(swapTotalSupply);

        when(loans.mock.getDataBatch(eq("Loans"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchLoans);
        when(dex.mock.getDataBatch(eq("sICX/ICX"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchSwap);

        // Act
        syncDistributions();

        // Assert
        rewardsScore.invoke(account, "claimRewards");
        rewardsScore.invoke(emptyAccount, "claimRewards");

        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);
        BigInteger userDistribution = userSwapDistribution.add(userLoansDistribution);

        verify(baln.mock, never()).transfer(eq(emptyAccount.getAddress()), any(BigInteger.class), eq(new byte[0]));
        verifyBalnReward(account.getAddress(), userDistribution);        
    }

    @SuppressWarnings("unchecked")
    @Test
    void distStatus() {
        // Arrange        
        sm.getBlock().increase(DAY);
        BigInteger day = (BigInteger) rewardsScore.call("getDay");

        // TODO Distribute once and check the platform day is increased but not the source days and calling
        //  distribute again would bring all the days in sync
        // Act
        syncDistributions();

        // Assert
        Map<String, Object> distStatus = (Map<String, Object>) rewardsScore.call("distStatus");
        Map<String, BigInteger> sourceDays = (Map<String, BigInteger>)distStatus.get("source_days");
        BigInteger platformDay = (BigInteger) distStatus.get("platform_day");

        assertEquals(day, platformDay);
        assertEquals(sourceDays.get("sICX/ICX"), platformDay);
        assertEquals(sourceDays.get("Loans"), platformDay);
    }

    @SuppressWarnings("unchecked")
    @Test
    void recipientAt_multipleSnapshots() {
        // Arrange 
        BigInteger expectedLoansDist = loansDist.dist_percent.add(ICX.divide(BigInteger.TEN));//30%
        BigInteger expectedSwapDist = icxPoolDist.dist_percent.divide(BigInteger.TWO);//10%
        BigInteger originalLoansDist = loansDist.dist_percent;
        BigInteger originalSwapDist = icxPoolDist.dist_percent;
        //create a set of snapshot to be able to search for recipients
        snapshotDistributionPercentage();
        snapshotDistributionPercentage();
        snapshotDistributionPercentage();
        snapshotDistributionPercentage();
        
        loansDist.dist_percent = expectedLoansDist; 
        icxPoolDist.dist_percent = expectedSwapDist;
        // capture day, where the specific change took place
        BigInteger day = (BigInteger) rewardsScore.call("getDay");
        snapshotDistributionPercentage();

        // reset distribution percentages
        loansDist.dist_percent = originalLoansDist;
        icxPoolDist.dist_percent = originalSwapDist;

        snapshotDistributionPercentage();
        snapshotDistributionPercentage();
        snapshotDistributionPercentage();

        // Act
        Map<String, BigInteger> distributions = (Map<String, BigInteger>) rewardsScore.call("recipientAt", day);

        // Assert
        assertEquals(expectedLoansDist, distributions.get("Loans"));
        assertEquals(expectedSwapDist, distributions.get("sICX/ICX"));
        // TODO Check if the distribution percentages are back to original values
    }

    @SuppressWarnings("unchecked")
    @Test
    void recipientAt_withNewDataSourceInTheFuture() {
        // Arrange 
        sm.getBlock().increase(DAY);
        sm.getBlock().increase(DAY);
        DistributionPercentage testDist = new DistributionPercentage();
        testDist.recipient_name = "test";
        testDist.dist_percent = loansDist.dist_percent.divide(BigInteger.TWO);
        loansDist.dist_percent =  loansDist.dist_percent.divide(BigInteger.TWO);

        rewardsScore.invoke(governance, "addNewDataSource", testDist.recipient_name, loans.getAddress());

        // Act
        Object distributionPercentages = new DistributionPercentage[]{testDist, loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};
        BigInteger day = (BigInteger) rewardsScore.call("getDay");

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", distributionPercentages);
        Map<String, BigInteger> distributionsYesterday = (Map<String, BigInteger>) rewardsScore.call("recipientAt", day.subtract(BigInteger.ONE));
        Map<String, BigInteger> distributionsToday = (Map<String, BigInteger>) rewardsScore.call("recipientAt", day);

        // Assert
        assertFalse(distributionsYesterday.containsKey("test"));
        assertEquals(testDist.dist_percent, distributionsToday.get("test"));
    }

    @Test
    void recipientAt_dayLessThanZero() {
        // Arrange 
        String expectedErrorMessage = RewardsImpl.TAG + ": day:-1 must be equal to or greater then Zero";

        // Act
        Executable atDayLessThanZero = () ->  rewardsScore.invoke(admin, "recipientAt", BigInteger.valueOf(-1));

        // Assert
        expectErrorMessage(atDayLessThanZero, expectedErrorMessage);
    }
}


