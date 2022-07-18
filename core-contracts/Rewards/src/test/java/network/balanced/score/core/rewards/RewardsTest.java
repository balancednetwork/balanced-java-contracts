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

    // @SuppressWarnings("unchecked")
    // @Test
    // void getDataSources() {
    //     // Act 
    //     Map<String, Map<String, Object>> dataSources = (Map<String, Map<String, Object>>) rewardsScore.call("getDataSources");

    //     // Assert
    //     assertEquals(2, dataSources.size());
    //     assertTrue(dataSources.containsKey("Loans"));
    //     assertTrue(dataSources.containsKey("sICX/ICX"));
    // }

    @SuppressWarnings("unchecked")
    @Test
    void getDataSourceNames() {
        // Act 
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames", 0, 2);

        // Assert
        assertEquals(2, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));

        // Act 
        names = (List<String>) rewardsScore.call("getDataSourceNames", 0, 1);

        // Assert
        assertEquals(1, names.size());
        assertTrue(names.contains("sICX/ICX"));

        // Act 
        names = (List<String>) rewardsScore.call("getDataSourceNames", 1, 1);

        // Assert
        assertEquals(1, names.size());
        assertTrue(names.contains("Loans"));
    }

    // @SuppressWarnings("unchecked")
    // @Test
    // void getRecipients() {
    //     // Act 
    //     List<String> names = (List<String>) rewardsScore.call("getRecipients");

    //     // Assert
    //     assertEquals(5, names.size());
    //     assertTrue(names.contains("Loans"));
    //     assertTrue(names.contains("sICX/ICX"));
    //     assertTrue(names.contains("Worker Tokens"));
    //     assertTrue(names.contains("Reserve Fund"));
    //     assertTrue(names.contains("DAOfund"));
    // }

    @Test
    void updateBalTokenDistPercentage_nonExistingName() {
        // Arrange
        DistributionPercentage testDist = new DistributionPercentage();
        testDist.recipient_name = "test";
        testDist.dist_percent = BigInteger.TEN; 
        String expectedErrorMessage = "Reverted(0): BalancedRewards: Data source does not exist";

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

    // @Test
    // void getApy() {
    //     // Arrange
    //     Account account = sm.createAccount();

    //     BigInteger balnPrice = EXA;
    //     BigInteger loansValue = BigInteger.valueOf(1000).multiply(EXA);
    //     when(dex.mock.getBalnPrice()).thenReturn(balnPrice);
    //     when(loans.mock.getBnusdValue("Loans")).thenReturn(loansValue);
    
    //     BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.ONE);
    //     BigInteger year = BigInteger.valueOf(365);

    //     BigInteger loansYearlyEmission = year.multiply(emission).multiply(loansDist.dist_percent);
    //     BigInteger expectedAPY = loansYearlyEmission.multiply(balnPrice).divide(EXA.multiply(loansValue));

    //     //Act 
    //     BigInteger apy = (BigInteger) rewardsScore.call("getAPY", "Loans");
        
    //     // Assert
    //     assertEquals(expectedAPY, apy);
    // }


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


