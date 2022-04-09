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

import static network.balanced.score.lib.utils.Constants.U_SECONDS_DAY;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.Map;

import com.iconloop.score.test.Account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;

public class RewardsTestContinousRewards extends RewardsTestBase {

    @BeforeEach
    void setup() throws Exception {
        super.setup();
        rewardsScore.invoke(admin, "setContinuousRewardsDay", BigInteger.valueOf(-5));
    }

    @Test
    void getDataSourcesAt() {
        // Arrange
        sm.getBlock().increase(DAY);
        BigInteger previousDay = BigInteger.valueOf( (sm.getBlock().getHeight()/DAY) - 1);

        // Act
        Map<String, Map<String, Object>>  data = (Map<String, Map<String, Object>> ) rewardsScore.call("getDataSourcesAt", previousDay);

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        assertEquals(loansDist.dist_percent.multiply(emission).divide(EXA), data.get("Loans").get("total_dist"));
        assertEquals(icxPoolDist.dist_percent.multiply(emission).divide(EXA), data.get("sICX/ICX").get("total_dist"));

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
        rewardsScore.invoke(admin, "updateRewardsData", "Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeLoansInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        rewardsScore.invoke(admin, "updateRewardsData", "sICX/ICX", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeSwapInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), swapBalance, swapTotalSupply);
    
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(admin, "distribute");

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);
        BigInteger swapDistribution = icxPoolDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = loansDistribution.multiply(swapBalance).divide(swapTotalSupply);

        rewardsScore.invoke(account, "claimRewards");
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger diffInUSLoans = timeInUS.subtract(startTimeLoansInUS);
        BigInteger diffInUSSwap = timeInUS.subtract(startTimeSwapInUS);
        BigInteger expectedRewards = userLoansDistribution.multiply(diffInUSLoans).divide(BigInteger.valueOf(U_SECONDS_DAY));
        expectedRewards = expectedRewards.add(userSwapDistribution.multiply(diffInUSSwap).divide(BigInteger.valueOf(U_SECONDS_DAY)));

        verifyBalnReward(account.getAddress(), expectedRewards);
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
        user1Entry._balance = BigInteger.ZERO.multiply(EXA);
        user1Entry._user = account1.getAddress();
        RewardsDataEntry user2Entry = new RewardsDataEntry();;
        user2Entry._balance = BigInteger.ZERO.multiply(EXA);
        user2Entry._user = account2.getAddress();
        Object batch = new RewardsDataEntry[] {user1Entry, user2Entry};

        // Act
        rewardsScore.invoke(admin, "updateBatchRewardsData", name, totalSupply, batch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, totalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, totalSupply);
    
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(admin, "distribute");

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(totalSupply);
        
        rewardsScore.invoke(account1, "claimRewards");
        BigInteger user1TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user1DiffInUS = user1TimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(user1DiffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));

        loansDistribution.subtract(user1ExpectedRewards);
        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(totalSupply);
        rewardsScore.invoke(account2, "claimRewards");

        BigInteger user2TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user2DiffInUS = user2TimeInUS.subtract(startTimeInUS);
        BigInteger user2ExpectedRewards = user2Distribution.multiply(user1DiffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));

        verifyBalnReward(account1.getAddress(), user1ExpectedRewards);
        verifyBalnReward(account2.getAddress(), user2ExpectedRewards);
    }

    @Test
    void getBalnHolding() {
        // Arrange
        Account account = sm.createAccount();
        String name = "Loans";
        BigInteger initalBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger initalTotalSupply = BigInteger.TWO.multiply(EXA);
        BigInteger currentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger currentTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(admin, "updateRewardsData", name, initalTotalSupply, account.getAddress(), initalBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account.getAddress(), currentBalance, currentTotalSupply);
        
        BigInteger initalRewards = (BigInteger) rewardsScore.call("getBalnHolding", account.getAddress());
        assertEquals(BigInteger.ZERO, initalRewards);
        
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(admin, "distribute");

        // Assert
        BigInteger emission = (BigInteger)rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger userDistribution = loansDistribution.multiply(currentBalance).divide(currentTotalSupply);
        
        BigInteger rewards = (BigInteger) rewardsScore.call("getBalnHolding", account.getAddress());

        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
    
        BigInteger diffInUS = timeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = userDistribution.multiply(diffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));
        assertEquals(expectedRewards, rewards);
    }

    @Test
    void getBalnHoldings() {
        // Arrange
        Account account = sm.createAccount();
        
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger initalBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger previousBalance = BigInteger.TWO.multiply(EXA);

        rewardsScore.invoke(admin, "updateRewardsData", "Loans", totalSupply, account.getAddress(), initalBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        sm.getBlock().increase(DAY);

        // Act
        rewardsScore.invoke(admin, "updateRewardsData", "Loans", totalSupply, account.getAddress(),  previousBalance );
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());


        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger distribution = loansDistribution.multiply(previousBalance).divide(totalSupply);
        
        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = distribution.multiply(timeDiffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));

        Object users = new Address[] {account.getAddress()};
        Map<Address, BigInteger> rewards  = (Map<Address, BigInteger>) rewardsScore.call("getBalnHoldings", users);
        
        BigInteger reward = rewards.get(account.getAddress()).divide(EXA);
        assertEquals(expectedRewards.divide(EXA), reward);
    }

    @Test
    void getBalnHoldings_batch() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        
        String name = "Loans";
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger user1InitalBalance = BigInteger.ONE.multiply(EXA);
        BigInteger user2InitalBalance = BigInteger.TWO.multiply(EXA);
        BigInteger user1CurrentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.valueOf(4).multiply(EXA);

        RewardsDataEntry user1Entry= new RewardsDataEntry();
        RewardsDataEntry user2Entry = new RewardsDataEntry();
        user1Entry._balance = user1InitalBalance;
        user1Entry._user = account1.getAddress();
        user2Entry._balance = user2InitalBalance;
        user2Entry._user = account2.getAddress();

        Object initalBatch = new RewardsDataEntry[] {user1Entry, user2Entry};

        rewardsScore.invoke(admin, "updateBatchRewardsData", name, totalSupply, initalBatch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, totalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, totalSupply);

        // Act
        user1Entry._balance = user1CurrentBalance;
        user1Entry._user = account1.getAddress();

        user2Entry._balance = user2CurrentBalance;
        user2Entry._user = account2.getAddress();

        Object batch = new RewardsDataEntry[] {user1Entry, user2Entry};
        rewardsScore.invoke(admin, "updateBatchRewardsData", name, totalSupply, batch);
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = loansDist.dist_percent.multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(totalSupply);
        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(totalSupply);
        
        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(timeDiffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));
        BigInteger user2ExpectedRewards = user2Distribution.multiply(timeDiffInUS).divide(BigInteger.valueOf(U_SECONDS_DAY));

        Object users = new Address[] {account1.getAddress(), account2.getAddress()};
        Map<Address, BigInteger> rewards  = (Map<Address, BigInteger>) rewardsScore.call("getBalnHoldings", users);
        
        BigInteger user1Rewards = rewards.get(account1.getAddress()).divide(EXA);
        BigInteger user2Rewards = rewards.get(account1.getAddress()).divide(EXA);
        assertEquals(user1ExpectedRewards.divide(EXA), user1Rewards);
        assertEquals(user2ExpectedRewards.divide(EXA), user2Rewards);
    }
}


