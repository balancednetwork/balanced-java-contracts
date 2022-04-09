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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.security.AccessControlContext;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;


public class RewardsTest extends RewardsTestBase {

    @BeforeEach
    void setup() throws Exception{
        super.setup();
        rewardsScore.invoke(admin, "setContinuousRewardsDay", BigInteger.valueOf(10000));

    }

    @Test
    void name() {
        assertEquals("Balanced Rewards", rewardsScore.call("name"));
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(rewardsScore, governance, admin);
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(rewardsScore, governance, owner);
    }

    @Test
    void getDataSourceNames() {
        // Act 
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames");

        // Assert
        assertEquals(2, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));
    }

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
        BigInteger baseEmssion = BigInteger.TEN.pow(23);
        BigInteger percent = BigInteger.valueOf(10);
        BigInteger percent100 = percent.multiply(BigInteger.valueOf(100));
        BigInteger decay = percent100.subtract(BigInteger.valueOf(5));

        BigInteger expectedEmission = baseEmssion.multiply(decay).divide(percent100);
        BigInteger expectedEmissionDayAfter = expectedEmission.multiply(decay).divide(percent100);

         // Act & Assert 
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(61)));
        assertEquals(expectedEmissionDayAfter, rewardsScore.call("getEmission", BigInteger.valueOf(62)));
    }

    @Test
    void getEmission_after935Days() {
        // Arrange
        int day = 935;
        BigInteger minEmssion = BigInteger.valueOf(1250).multiply(ICX);

         // Act & Assert 
         assertNotEquals(minEmssion, rewardsScore.call("getEmission", BigInteger.valueOf(day-1)));
         assertEquals(minEmssion, rewardsScore.call("getEmission", BigInteger.valueOf(day)));
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

    @Test
    void distribute() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.ONE.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger swapBalance = BigInteger.TWO.multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.TEN.multiply(EXA);
        
        Map<Address, BigInteger> dataBatchLoans = Map.of(
            account.getAddress(), loansBalance
        );

        Map<Address, BigInteger> dataBatchSwap = Map.of(
            account.getAddress(), swapBalance
        );

        sm.getBlock().increase(DAY);
        int day = (int) (sm.getBlock().getHeight()/DAY) - 1;

        when(loans.mock.getTotalValue(eq("Loans"), any(Integer.class))).thenReturn(loansTotalSupply);
        when(dex.mock.getTotalValue(eq("sICX/ICX"), any(Integer.class))).thenReturn(swapTotalSupply);
        
        when(loans.mock.getDataBatch(eq("Loans"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchLoans);
        when(dex.mock.getDataBatch(eq("sICX/ICX"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchSwap);

        // Act
        //twice per datasource
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");

        // Assert
        Object users = new Address[] {account.getAddress()};
        Map<Address, BigInteger> rewards  = (Map<Address, BigInteger>) rewardsScore.call("getBalnHoldings", users);

        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);
        BigInteger userDistribution = userSwapDistribution.add(userLoansDistribution);

        assertEquals(userDistribution, rewards.get(account.getAddress()));           
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
        
        Map<Address, BigInteger> dataBatchLoans = Map.of(
            account.getAddress(), loansBalance
        );

        Map<Address, BigInteger> dataBatchSwap = Map.of(
            account.getAddress(), swapBalance
        );

        sm.getBlock().increase(DAY);
        int day = (int) (sm.getBlock().getHeight()/DAY) - 1;

        when(loans.mock.getTotalValue(eq("Loans"), any(Integer.class))).thenReturn(loansTotalSupply);
        when(dex.mock.getTotalValue(eq("sICX/ICX"), any(Integer.class))).thenReturn(swapTotalSupply);
        
        when(loans.mock.getDataBatch(eq("Loans"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchLoans);
        when(dex.mock.getDataBatch(eq("sICX/ICX"), eq(day), any(Integer.class), eq(0))).thenReturn(dataBatchSwap);

        // Act
        //twice per datasource
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");
        rewardsScore.invoke(admin, "distribute");

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


    @Test
    void distStatus() {
        // Arrange        
        sm.getBlock().increase(DAY);
        BigInteger day = BigInteger.valueOf((sm.getBlock().getHeight()/DAY));

        // Act
        rewardsScore.invoke(admin, "distribute");

        // Assert
        Map<String, Object> distStatus  = (Map<String, Object>) rewardsScore.call("distStatus");
        Map<String, BigInteger> sourceDays = (Map<String, BigInteger>)distStatus.get("source_days");
        BigInteger platformDay = (BigInteger) distStatus.get("platform_day");
        BigInteger daysSum = sourceDays.get("sICX/ICX").add(sourceDays.get("Loans"));

        assertEquals(day, platformDay);
        assertEquals(platformDay.multiply(BigInteger.TWO).subtract(BigInteger.ONE), daysSum);
    }
}


