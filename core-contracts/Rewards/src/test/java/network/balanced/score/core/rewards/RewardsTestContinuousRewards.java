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

import network.balanced.score.lib.structs.RewardsDataEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import score.Address;


import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.core.rewards.utils.RewardsConstants.WEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardsTestContinuousRewards extends RewardsTestBase {


    @BeforeEach
    void setup() throws Exception {
        super.setup();
    }

    @Test
    void distribute() {

        // Act
        sm.getBlock().increase(DAY);
        syncDistributions();
        int day = ((BigInteger)rewardsScore.call("getDay")).intValue();

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));

        verify(baln.mock, times(day-1)).transfer(bwt.getAddress(), bwtDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day-1)).transfer(daoFund.getAddress(), daoDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day-1)).transfer(reserve.getAddress(), reserveDist.dist_percent.multiply(emission).divide(EXA), new byte[0]);
    }

    @Test
    void claimRewards_updateRewardsData() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.ONE.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger swapBalance = BigInteger.TWO.multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeLoansInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        rewardsScore.invoke(dex.account, "updateRewardsData", "sICX/ICX", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeSwapInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), swapBalance, swapTotalSupply);
    
        sm.getBlock().increase(DAY);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);
        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = loansDistribution.multiply(swapBalance).divide(swapTotalSupply);

        claim(account);
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger diffInUSLoans = timeInUS.subtract(startTimeLoansInUS);
        BigInteger diffInUSSwap = timeInUS.subtract(startTimeSwapInUS);
        BigInteger expectedRewards = userLoansDistribution.multiply(diffInUSLoans).divide(MICRO_SECONDS_IN_A_DAY);
        expectedRewards = expectedRewards.add(userSwapDistribution.multiply(diffInUSSwap).divide(MICRO_SECONDS_IN_A_DAY));

        verifyBalnReward(account.getAddress(), expectedRewards);          
    }

    @Test
    void boostedRewards() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(200).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));
        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boost = loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedBalance = loansBalance.add(boost);

        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_maxBoost() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(800).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));
        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boostedBalance = loansBalance.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_switchBoost() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger swapBalance = BigInteger.valueOf(1500).multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.valueOf(2_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(200).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));
        mockBalanceAndSupply(dex, "sICX/ICX", EOA_ZERO, BigInteger.ZERO, swapTotalSupply.subtract(swapBalance));
        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        rewardsScore.invoke(dex.account, "updateRewardsData", "sICX/ICX",swapTotalSupply.subtract(swapBalance), account.getAddress(), BigInteger.ZERO);

        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), swapBalance, swapTotalSupply);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.add(userSwapDistribution).divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boostLoans= loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedLoansBalance = loansBalance.add(boostLoans);
        BigInteger boostedLoansSupply = loansTotalSupply.subtract(loansBalance).add(boostedLoansBalance);
        BigInteger boostedLoansDistribution = loansDistribution.multiply(boostedLoansBalance).divide(boostedLoansSupply);
        BigInteger boostedLoansRewards = getOneDayRewards(account.getAddress());

        assertEquals(boostedLoansRewards.divide(EXA), boostedLoansDistribution.add(userSwapDistribution).divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "sICX/ICX");

        // Assert
        BigInteger boostSwap = swapTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedSwapBalance = swapBalance.add(boostSwap);
        BigInteger boostedSwapSupply = swapTotalSupply.subtract(loansBalance).add(boostedSwapBalance);
        BigInteger boostedSwapDistribution = swapDistribution.multiply(boostedSwapBalance).divide(boostedSwapSupply);
        BigInteger boostedSwapRewards = getOneDayRewards(account.getAddress());

        assertEquals(boostedSwapRewards.divide(EXA), userLoansDistribution.add(boostedSwapDistribution).divide(EXA));
    }

    @Test
    void boostedRewards_updateBoostedBalance_claim() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(800).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));

        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boostedBalance = loansBalance.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(250).multiply(EXA);
        bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        claim(account);

        // Assert
        BigInteger boost = loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        boostedBalance = loansBalance.add(boost);
        boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_updateBoostedBalance_onBalanceUpdate() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(800).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));
        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boostedBalance = loansBalance.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(250).multiply(EXA);
        bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        rewardsScore.invoke(bBaln.account, "onBalanceUpdate", account.getAddress());

        // Assert
        BigInteger boost = loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        boostedBalance = loansBalance.add(boost);
        boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_updateRewardsData() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(800).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        mockBalanceAndSupply(loans, "Loans", EOA_ZERO, BigInteger.ZERO, loansTotalSupply.subtract(loansBalance));

        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance), account.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        rewardsScore.invoke(account, "boost", "Loans");

        // Assert
        BigInteger boostedBalance = loansBalance.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(250).multiply(EXA);
        bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);
        when(bBaln.mock.balanceOf(eq(account.getAddress()), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(bBalnSupply);
        
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply, account.getAddress(), loansBalance);
        loansBalance = BigInteger.valueOf(20000).multiply(EXA);
        loansTotalSupply = BigInteger.valueOf(1_019_000).multiply(EXA);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        // Assert
        BigInteger boost = loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        boostedBalance = loansBalance.add(boost);
        boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void claimRewards_updateBatchRewardsData() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        
        String name = "Loans";
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger user1CurrentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.TWO.multiply(EXA);

        RewardsDataEntry user1Entry= new RewardsDataEntry();
        user1Entry._balance = BigInteger.ZERO;
        user1Entry._user = account1.getAddress();
        RewardsDataEntry user2Entry = new RewardsDataEntry();
        user2Entry._balance = BigInteger.ZERO;
        user2Entry._user = account2.getAddress();
        Object batch = new RewardsDataEntry[] {user1Entry, user2Entry};

        // Act
        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, totalSupply, batch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, totalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, totalSupply);
    
        sm.getBlock().increase(DAY);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(totalSupply);
        
        claim(account1);
        BigInteger user1TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user1DiffInUS = user1TimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(user1DiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(totalSupply);
        
        claim(account2);
        BigInteger user2TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user2DiffInUS = user2TimeInUS.subtract(startTimeInUS);
        BigInteger user2ExpectedRewards = user2Distribution.multiply(user2DiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        verifyBalnReward(account1.getAddress(), user1ExpectedRewards);
        verifyBalnReward(account2.getAddress(), user2ExpectedRewards);
    }

    @Test
    void getBalnHolding() {
        // Arrange
        Account account = sm.createAccount();
        String name = "Loans";
        BigInteger initialBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger initialTotalSupply = BigInteger.TWO.multiply(EXA);
        BigInteger currentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger currentTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(loans.account, "updateRewardsData", name, initialTotalSupply, account.getAddress(), initialBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account.getAddress(), currentBalance, currentTotalSupply);
        
        BigInteger initialRewards = getBalnHoldings(account.getAddress());
        assertEquals(BigInteger.ZERO, initialRewards);
        
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(loans.account, "updateRewardsData", name, currentTotalSupply, account.getAddress(), currentBalance);
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userDistribution = loansDistribution.multiply(currentBalance).divide(currentTotalSupply);

        BigInteger rewards = getBalnHoldings(account.getAddress());
    
        BigInteger diffInUS = timeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = userDistribution.multiply(diffInUS).divide(MICRO_SECONDS_IN_A_DAY);
        assertEquals(expectedRewards.divide(BigInteger.TEN), rewards.divide(BigInteger.TEN));
    }

    @Test
    void claimRewards_withoutRewardsUpdate() {
        // Arrange
        Account account = sm.createAccount();
        Account account2 = sm.createAccount();
        String name = "Loans";
        BigInteger currentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger currentTotalSupply = BigInteger.TEN.multiply(EXA);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", BigInteger.TWO.multiply(EXA), account2.getAddress(), BigInteger.ZERO);

        mockBalanceAndSupply(loans, name, account.getAddress(), currentBalance, currentTotalSupply);  
        sm.getBlock().increase(DAY);
        BigInteger rewards = getBalnHoldings(account.getAddress());
        assertEquals(BigInteger.ZERO, rewards);

        // Act
        rewardsScore.invoke(account, "addUserDataSource", "Loans");
        rewards = getBalnHoldings(account.getAddress());
        assertTrue(rewards.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void closePostion_removesFromUserSources() {
        // Arrange
        Account account = sm.createAccount();
        String name = "Loans";
        BigInteger initialBalance = BigInteger.ONE.multiply(EXA);
        BigInteger initialTotalSupply = BigInteger.TWO.multiply(EXA);
        BigInteger currentTotalSupply = BigInteger.ONE.multiply(EXA);

        // Act
        assertTrue(!getUserSources(account.getAddress()).contains("Loans"));
        rewardsScore.invoke(loans.account, "updateRewardsData", name, initialTotalSupply, account.getAddress(), initialBalance);
        assertTrue(getUserSources(account.getAddress()).contains("Loans"));

        mockBalanceAndSupply(loans, name, account.getAddress(), BigInteger.ZERO, currentTotalSupply);
        claim(account);

        // Assert
        assertTrue(!getUserSources(account.getAddress()).contains("Loans"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalnHoldings() {
        // Arrange
        Account account = sm.createAccount();
        
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger initialBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger previousBalance = BigInteger.TWO.multiply(EXA);

        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", totalSupply, account.getAddress(), initialBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        sm.getBlock().increase(DAY);

        // Act
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", totalSupply, account.getAddress(),  previousBalance );
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger distribution = loansDistribution.multiply(previousBalance).divide(totalSupply);
        
        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        Object users = new Address[] {account.getAddress()};
        Map<String, BigInteger> rewards  = (Map<String, BigInteger>) rewardsScore.call("getBalnHoldings", users);
        
        BigInteger reward = rewards.get(account.getAddress().toString()).divide(BigInteger.TEN);
        assertEquals(expectedRewards.divide(BigInteger.TEN), reward);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalnHoldings_batch() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        
        String name = "Loans";
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger user1InitialBalance = BigInteger.ZERO;
        BigInteger user2InitialBalance = BigInteger.ZERO;
        BigInteger user1CurrentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.valueOf(4).multiply(EXA);

        RewardsDataEntry user1Entry= new RewardsDataEntry();
        RewardsDataEntry user2Entry = new RewardsDataEntry();
        user1Entry._balance = user1InitialBalance;
        user1Entry._user = account1.getAddress();
        user2Entry._balance = user2InitialBalance;
        user2Entry._user = account2.getAddress();

        Object initialBatch = new RewardsDataEntry[] {user1Entry, user2Entry};

        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, totalSupply, initialBatch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, totalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, totalSupply);

        // Act
        user1Entry._balance = user1CurrentBalance;
        user1Entry._user = account1.getAddress();
        user2Entry._balance = user2CurrentBalance;
        user2Entry._user = account2.getAddress();
        Object batch = new RewardsDataEntry[] {user1Entry, user2Entry};

        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, totalSupply, batch);
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(totalSupply);
        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(totalSupply);
        
        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);
        BigInteger user2ExpectedRewards = user2Distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        Object users = new Address[] {account1.getAddress(), account2.getAddress()};
        Map<String, BigInteger> rewards  = (Map<String, BigInteger>) rewardsScore.call("getBalnHoldings", users);
        
        BigInteger user1Rewards = rewards.get(account1.getAddress().toString()).divide(BigInteger.TEN);
        BigInteger user2Rewards = rewards.get(account2.getAddress().toString()).divide(BigInteger.TEN);
        assertEquals(user1ExpectedRewards.divide(BigInteger.TEN), user1Rewards);
        assertEquals(user2ExpectedRewards.divide(BigInteger.TEN), user2Rewards);
    }

    @Test
    void addNewDataSourceAndDelegate() {
        // Arrange
        Account account = sm.createAccount();
        String name = "newLP";
        BigInteger initialBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger initialTotalSupply = BigInteger.TWO.multiply(EXA);
        BigInteger currentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger currentTotalSupply = BigInteger.TEN.multiply(EXA);

        BigInteger finalBalance = BigInteger.TWO.multiply(EXA);
        BigInteger finalTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(account, "addNewDataSource", name, dex.getAddress(), dex.getAddress());
        rewardsScore.invoke(dex.account, "updateRewardsData", name, initialTotalSupply, account.getAddress(), initialBalance);

        BigInteger initialRewards = getBalnHoldings(account.getAddress());
        assertEquals(BigInteger.ZERO, initialRewards);
        
        sm.getBlock().increase(DAY);

        rewardsScore.invoke(dex.account, "updateRewardsData", name, currentTotalSupply, account.getAddress(), currentBalance);
        mockBalanceAndSupply(dex, name, account.getAddress(), finalBalance, finalTotalSupply);
        addDistribution(name); //20%
        
        initialRewards = getBalnHoldings(account.getAddress());
        assertEquals(BigInteger.ZERO, initialRewards);

        sm.getBlock().increase(DAY);

        // Assert
        BigInteger rewards = getBalnHoldings(account.getAddress());
        assertTrue(rewards.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void updateRewardsData_maliciousDataSource() {
        // Arrange
        Account account = sm.createAccount();
        String expectedErrorMessage;
        BigInteger loansBalance = BigInteger.valueOf(5).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);

        // Arrange
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply , account.getAddress(), loansBalance);
        Account account2 = sm.createAccount();
        BigInteger account2Balance = BigInteger.TEN.multiply(BigInteger.TEN.pow(24));

        sm.getBlock().increase(DAY);

        // Act & Assert
        expectedErrorMessage = "Reverted(0): "+ RewardsImpl.TAG + ": There are no rewards left to claim for Loans";
        Executable aboveTotalSupply = () -> 
            rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply, account2.getAddress(), account2Balance);
        expectErrorMessage(aboveTotalSupply, expectedErrorMessage);
    }

    @Test
    void claimRewards_maliciousBalanceAndSupply() {
        // Arrange
        Account user1 = sm.createAccount();
        Account user2 = sm.createAccount();
        BigInteger user1Balance = BigInteger.valueOf(5).multiply(EXA);
        BigInteger user2Balance = BigInteger.TEN.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", BigInteger.ZERO, user1.getAddress(), BigInteger.ZERO);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", user1Balance, user2.getAddress(), BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", user1.getAddress(), user1Balance, loansTotalSupply);
        mockBalanceAndSupply(loans, "Loans", user2.getAddress(), user2Balance, loansTotalSupply);
        
        sm.getBlock().increase(DAY * 4);
        
        // Assert
        claim(user2);
        String expectedErrorMessage = "Reverted(0): "+ RewardsImpl.TAG + ": There are no rewards left to claim for Loans";
        Executable aboveTotalSupply = () -> claim(user1);
        expectErrorMessage(aboveTotalSupply, expectedErrorMessage);
    }
}


