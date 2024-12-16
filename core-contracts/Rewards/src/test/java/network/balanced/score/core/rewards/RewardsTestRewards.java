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
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.structs.RewardsDataEntryOld;
import network.balanced.score.lib.utils.Names;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.utils.RewardsConstants.WEIGHT;
import static network.balanced.score.core.rewards.weight.SourceWeightController.VOTE_POINTS;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RewardsTestRewards extends RewardsTestBase {
    @BeforeEach
    void setup() throws Exception {
        super.setup();
    }

    @Test
    void distribute() {
        // Act
        sm.getBlock().increase(DAY);
        syncDistributions();
        int day = ((BigInteger) rewardsScore.call("getDay")).intValue();

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));

        verify(baln.mock, times(day)).transfer(bwt.getAddress(),
            defaultPlatformDist.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day)).transfer(daoFund.getAddress(),
            defaultPlatformDist.multiply(emission).divide(EXA), new byte[0]);
        verify(baln.mock, times(day)).transfer(reserve.getAddress(),
            defaultPlatformDist.multiply(emission).divide(EXA), new byte[0]);
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
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), swapBalance, swapTotalSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance),
                account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeLoansInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        rewardsScore.invoke(dex.account, "updateRewardsData", "sICX/ICX", swapTotalSupply.subtract(swapBalance),
                account.getAddress(), BigInteger.ZERO);
        BigInteger startTimeSwapInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        sm.getBlock().increase(DAY*2);

        // Assert
        System.out.println( getVotePercentage("Loans"));
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);
        BigInteger swapDistribution = getVotePercentage("sICX/ICX").multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);

        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger diffInUSLoans = timeInUS.subtract(startTimeLoansInUS);
        BigInteger diffInUSSwap = timeInUS.subtract(startTimeSwapInUS);
        BigInteger expectedRewards = userLoansDistribution.multiply(diffInUSLoans).divide(MICRO_SECONDS_IN_A_DAY);
        expectedRewards =
                expectedRewards.add(userSwapDistribution.multiply(diffInUSSwap).divide(MICRO_SECONDS_IN_A_DAY));

        verifyBalnReward(account.getAddress(), expectedRewards);
    }

    @Test
    void claimRewards_updateBalanceAndSupply() {
        // Arrange
        Account account = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger loansBalance = BigInteger.ONE.multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger swapBalance = BigInteger.TWO.multiply(EXA);
        BigInteger swapTotalSupply = BigInteger.TEN.multiply(EXA);

        // Act
        rewardsScore.invoke(loans.account, "updateBalanceAndSupply", "Loans", loansTotalSupply.subtract(loansBalance),
                supplyAccount.getAddress().toString(), loansTotalSupply.subtract(loansBalance));
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", swapTotalSupply.subtract(swapBalance),
                supplyAccount.getAddress().toString(), swapTotalSupply.subtract(swapBalance));

        rewardsScore.invoke(loans.account, "updateBalanceAndSupply", "Loans", loansTotalSupply,
                account.getAddress().toString(), loansBalance);
        BigInteger startTimeLoansInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", swapTotalSupply,
                account.getAddress().toString(), swapBalance);
        BigInteger startTimeSwapInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), swapBalance, swapTotalSupply);

        sm.getBlock().increase(DAY*2);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);
        BigInteger swapDistribution = getVotePercentage("sICX/ICX").multiply(emission).divide(EXA);
        BigInteger userSwapDistribution = swapDistribution.multiply(swapBalance).divide(swapTotalSupply);

        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger diffInUSLoans = timeInUS.subtract(startTimeLoansInUS);
        BigInteger diffInUSSwap = timeInUS.subtract(startTimeSwapInUS);
        BigInteger expectedRewards = userLoansDistribution.multiply(diffInUSLoans).divide(MICRO_SECONDS_IN_A_DAY);
        expectedRewards =
                expectedRewards.add(userSwapDistribution.multiply(diffInUSSwap).divide(MICRO_SECONDS_IN_A_DAY));

        verifyBalnReward(account.getAddress(), expectedRewards);
    }

    @Test
    void boostedRewards() {
        // Arrange
        Account account = sm.createAccount();
        String accountNetworkAddress = new NetworkAddress(NATIVE_NID, account.getAddress()).toString();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger setupBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger initialSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger currentSupply = initialSupply.add(loansBalance).add(setupBalance);
        BigInteger bBalnBalance = BigInteger.valueOf(100).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(100_000).multiply(EXA);

        // Act
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, currentSupply.subtract(setupBalance));
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", initialSupply, account.getAddress(),
                BigInteger.ZERO);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(currentSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "boost", getUserSources(account.getAddress()));

        // Assert
        BigInteger boost =
                currentSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedBalance = loansBalance.add(boost);

        BigInteger boostedSupply = currentSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_maxBoost() {
        // Arrange
        Account account = sm.createAccount();
        String accountNetworkAddress = new NetworkAddress(NATIVE_NID, account.getAddress()).toString();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger initialSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger currentSupply = initialSupply.add(loansBalance);
        BigInteger bBalnSupply = BigInteger.valueOf(40000).multiply(EXA);
        BigInteger bBalnBalance = loansBalance.multiply(bBalnSupply).divide(currentSupply);

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(BigInteger.ZERO);

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, currentSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", initialSupply, account.getAddress(),
                BigInteger.ZERO);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(currentSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "boost", getUserSources(account.getAddress()));

        // Assert
        BigInteger boostedBalance = loansBalance.multiply(EXA).divide(WEIGHT);

        BigInteger boostedSupply = currentSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }


    @Test
    void boostedRewards_updateBoostedBalance_claim() {
        // Arrange
        Account account = sm.createAccount();
        String accountNetworkAddress = new NetworkAddress(NATIVE_NID, account.getAddress()).toString();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(177).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(BigInteger.ZERO);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);

        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance),
                account.getAddress(), BigInteger.ZERO);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "boost", getUserSources(account.getAddress()));

        // Assert
        BigInteger boost =
                loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedBalance = loansBalance.add(boost);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(250).multiply(EXA);
        bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));

        // Assert
        boost = loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
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
        String accountNetworkAddress = new NetworkAddress(NATIVE_NID, account.getAddress()).toString();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(800).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance),
                account.getAddress(), BigInteger.ZERO);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "boost", getUserSources(account.getAddress()));

        // Assert
        // max boost
        BigInteger initialBoostedBalance = loansBalance.multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(initialBoostedBalance);
        userLoansDistribution = loansDistribution.multiply(initialBoostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(100).multiply(EXA);
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        rewardsScore.invoke(bBaln.account, "onBalanceUpdate", accountNetworkAddress, bBalnBalance);

        // Assert
        BigInteger boost =
                loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedBalance = loansBalance.add(boost);
        boostedSupply = boostedSupply.subtract(initialBoostedBalance).add(boostedBalance);
        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);

        boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));
    }

    @Test
    void boostedRewards_updateRewardsData() {
        // Arrange
        Account account = sm.createAccount();
        String accountNetworkAddress = new NetworkAddress(NATIVE_NID, account.getAddress()).toString();
        BigInteger loansBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loansTotalSupply = BigInteger.valueOf(1_000_000).multiply(EXA);
        BigInteger bBalnBalance = BigInteger.valueOf(200).multiply(EXA);
        BigInteger bBalnSupply = BigInteger.valueOf(500_000).multiply(EXA);

        // Act
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), loansBalance, loansTotalSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", loansTotalSupply.subtract(loansBalance),
                account.getAddress(), BigInteger.ZERO);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userLoansDistribution = loansDistribution.multiply(loansBalance).divide(loansTotalSupply);

        BigInteger unBoostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(unBoostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);
        when(bBaln.mock.totalSupply(BigInteger.ZERO)).thenReturn(bBalnSupply);
        rewardsScore.invoke(account, "boost", getUserSources(account.getAddress()));

        // Assert
        BigInteger boost =
                loansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        BigInteger boostedBalance = loansBalance.add(boost);
        BigInteger boostedSupply = loansTotalSupply.subtract(loansBalance).add(boostedBalance);

        userLoansDistribution = loansDistribution.multiply(boostedBalance).divide(boostedSupply);
        BigInteger boostedRewards = getOneDayRewards(account.getAddress());
        assertEquals(boostedRewards.divide(EXA), userLoansDistribution.divide(EXA));

        // Act
        bBalnBalance = BigInteger.valueOf(250).multiply(EXA);
        when(bBaln.mock.xBalanceOf(eq(accountNetworkAddress), any(BigInteger.class))).thenReturn(bBalnBalance);

        BigInteger newLoansBalance = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger newLoansTotalSupply = loansTotalSupply.add(newLoansBalance).subtract(loansBalance);
        mockBalanceAndSupply(loans, "Loans", account.getAddress(), newLoansBalance, newLoansTotalSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", newLoansTotalSupply, account.getAddress(),
                newLoansBalance);

        // Assert
        boost = newLoansTotalSupply.multiply(bBalnBalance).divide(bBalnSupply).multiply(EXA.subtract(WEIGHT)).divide(WEIGHT);
        boostedBalance = newLoansBalance.add(boost);
        boostedSupply = newLoansTotalSupply.subtract(newLoansBalance).add(boostedBalance);
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
        BigInteger initialTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger user1CurrentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger currentTotalSupply = initialTotalSupply.add(user1CurrentBalance).add(user2CurrentBalance);

        RewardsDataEntryOld user1Entry = new RewardsDataEntryOld();
        user1Entry._balance = BigInteger.ZERO;
        user1Entry._user = account1.getAddress();
        RewardsDataEntryOld user2Entry = new RewardsDataEntryOld();
        user2Entry._balance = BigInteger.ZERO;
        user2Entry._user = account2.getAddress();
        Object batch = new RewardsDataEntryOld[]{user1Entry, user2Entry};

        // Act
        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, currentTotalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, currentTotalSupply);
        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, initialTotalSupply, batch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        sm.getBlock().increase(DAY);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(currentTotalSupply);

        rewardsScore.invoke(account1, "claimRewards", getUserSources(account1.getAddress()));
        BigInteger user1TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user1DiffInUS = user1TimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(user1DiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(currentTotalSupply);

        rewardsScore.invoke(account2, "claimRewards", getUserSources(account1.getAddress()));
        BigInteger user2TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user2DiffInUS = user2TimeInUS.subtract(startTimeInUS);
        BigInteger user2ExpectedRewards = user2Distribution.multiply(user2DiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        verifyBalnReward(account1.getAddress(), user1ExpectedRewards);
        verifyBalnReward(account2.getAddress(), user2ExpectedRewards);
    }

    @Test
    void claimRewards_updateBalanceAndSupplyBatch() {
        // Arrange
        Account supplyAccount = sm.createAccount();
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();

        String name = "Loans";
        BigInteger initialTotalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger user1CurrentBalance = BigInteger.ONE.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger currentTotalSupply = initialTotalSupply.add(user1CurrentBalance).add(user2CurrentBalance);

        RewardsDataEntry user1Entry = new RewardsDataEntry();
        user1Entry._balance =user1CurrentBalance;
        user1Entry._user = NetworkAddress.valueOf(account1.getAddress().toString(), NATIVE_NID).toString();
        RewardsDataEntry user2Entry = new RewardsDataEntry();
        user2Entry._balance = user2CurrentBalance;
        user2Entry._user = NetworkAddress.valueOf(account2.getAddress().toString(), NATIVE_NID).toString();
        Object batch = new RewardsDataEntry[]{user1Entry, user2Entry};

        rewardsScore.invoke(loans.account, "updateBalanceAndSupply", "Loans", initialTotalSupply,
                NetworkAddress.valueOf(supplyAccount.getAddress().toString(), NATIVE_NID).toString(), initialTotalSupply);

        // Act
        rewardsScore.invoke(loans.account, "updateBalanceAndSupplyBatch", name, currentTotalSupply, batch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, currentTotalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, currentTotalSupply);

        sm.getBlock().increase(DAY);

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(currentTotalSupply);

        rewardsScore.invoke(account1, "claimRewards", getUserSources(account1.getAddress()));
        BigInteger user1TimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger user1DiffInUS = user1TimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(user1DiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(currentTotalSupply);

        rewardsScore.invoke(account2, "claimRewards", getUserSources(account1.getAddress()));
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
        BigInteger currentTotalSupply = BigInteger.valueOf(3).multiply(EXA);

        // Act
        mockBalanceAndSupply(loans, name, account.getAddress(), currentBalance, currentTotalSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", name, initialTotalSupply, account.getAddress(),
                initialBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());


        BigInteger initialRewards = (BigInteger) rewardsScore.call("getBalnHolding", NetworkAddress.valueOf(account.getAddress().toString(), NATIVE_NID).toString());
        assertEquals(BigInteger.ZERO, initialRewards);

        sm.getBlock().increase(DAY*3);
        rewardsScore.invoke(owner, "distribute");
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger userDistribution = loansDistribution.multiply(currentBalance).divide(currentTotalSupply);

        BigInteger rewards = (BigInteger) rewardsScore.call("getBalnHolding", NetworkAddress.valueOf(account.getAddress().toString(), NATIVE_NID).toString());

        BigInteger diffInUS = timeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = userDistribution.multiply(diffInUS).divide(MICRO_SECONDS_IN_A_DAY);
        assertEquals(expectedRewards.divide(BigInteger.TEN), rewards.divide(BigInteger.TEN));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalnHoldings() {
        // Arrange
        Account account = sm.createAccount();
        String addressNetworkAddress = NetworkAddress.valueOf(account.getAddress().toString(), NATIVE_NID).toString();

        BigInteger initialBalance = BigInteger.ZERO.multiply(EXA);
        BigInteger initialSupply = BigInteger.TEN.multiply(EXA);

        BigInteger currentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger currentSupply = initialSupply.add(currentBalance);

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), currentBalance, currentSupply);
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", initialSupply, account.getAddress(),
                initialBalance);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        sm.getBlock().increase(DAY);

        // Act
        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", currentSupply, account.getAddress(),
                currentBalance);
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger distribution = loansDistribution.multiply(currentBalance).divide(currentSupply);

        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger expectedRewards = distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        Object users = new String[]{addressNetworkAddress};
        Map<String, BigInteger> rewards = (Map<String, BigInteger>) rewardsScore.call("getBalnHoldings", users);

        BigInteger reward = rewards.get(addressNetworkAddress).divide(BigInteger.TEN);
        assertEquals(expectedRewards.divide(BigInteger.TEN), reward);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalnHoldings_batch() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();

        String name = "Loans";
        BigInteger user1InitialBalance = BigInteger.ZERO;
        BigInteger user2InitialBalance = BigInteger.ZERO;
        BigInteger initialSupply = BigInteger.ZERO;
        BigInteger user1CurrentBalance = BigInteger.TWO.multiply(EXA);
        BigInteger user2CurrentBalance = BigInteger.valueOf(4).multiply(EXA);
        BigInteger currentTotalSupply = user1CurrentBalance.add(user2CurrentBalance);

        RewardsDataEntryOld user1Entry = new RewardsDataEntryOld();
        RewardsDataEntryOld user2Entry = new RewardsDataEntryOld();
        user1Entry._balance = user1InitialBalance;
        user1Entry._user = account1.getAddress();
        user2Entry._balance = user2InitialBalance;
        user2Entry._user = account2.getAddress();

        Object initialBatch = new RewardsDataEntryOld[]{user1Entry, user2Entry};

        mockBalanceAndSupply(loans, name, account1.getAddress(), user1CurrentBalance, currentTotalSupply);
        mockBalanceAndSupply(loans, name, account2.getAddress(), user2CurrentBalance, currentTotalSupply);
        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, initialSupply, initialBatch);
        BigInteger startTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());


        // Act
        user1Entry._balance = user1CurrentBalance;
        user1Entry._user = account1.getAddress();
        user2Entry._balance = user2CurrentBalance;
        user2Entry._user = account2.getAddress();
        Object batch = new RewardsDataEntryOld[]{user1Entry, user2Entry};

        rewardsScore.invoke(loans.account, "updateBatchRewardsData", name, currentTotalSupply, batch);
        BigInteger endTimeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());

        // Assert
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger loansDistribution = getVotePercentage("Loans").multiply(emission).divide(EXA);
        BigInteger user1Distribution = loansDistribution.multiply(user1CurrentBalance).divide(currentTotalSupply);
        BigInteger user2Distribution = loansDistribution.multiply(user2CurrentBalance).divide(currentTotalSupply);

        BigInteger timeDiffInUS = endTimeInUS.subtract(startTimeInUS);
        BigInteger user1ExpectedRewards = user1Distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);
        BigInteger user2ExpectedRewards = user2Distribution.multiply(timeDiffInUS).divide(MICRO_SECONDS_IN_A_DAY);

        Object users = new String[]{NetworkAddress.valueOf(account1.getAddress().toString(), NATIVE_NID).toString(), NetworkAddress.valueOf(account2.getAddress().toString(), NATIVE_NID).toString()};
        Map<String, BigInteger> rewards = (Map<String, BigInteger>) rewardsScore.call("getBalnHoldings", users);

        BigInteger user1Rewards = rewards.get(NetworkAddress.valueOf(account1.getAddress().toString(), NATIVE_NID).toString()).divide(BigInteger.TEN);
        BigInteger user2Rewards = rewards.get(NetworkAddress.valueOf(account2.getAddress().toString(), NATIVE_NID).toString()).divide(BigInteger.TEN);
        assertEquals(user1ExpectedRewards.divide(BigInteger.TEN), user1Rewards);
        assertEquals(user2ExpectedRewards.divide(BigInteger.TEN), user2Rewards);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setPlatformDistPercentage() {
        // Arrange
        distribute();
        // assertOnlyCallableByGovernance(rewardsScore, "setPlatformDistPercentage", Names.DAOFUND,
        //         ICX.divide(BigInteger.TEN));

        // Act
        String expectedErrorMessage = "Reverted(0): Sum of distributions exceeds 100%";
        Executable overHundredPercent = () -> rewardsScore.invoke(owner, "setPlatformDistPercentage", Names.DAOFUND,
                EXA);
        expectErrorMessage(overHundredPercent, expectedErrorMessage);
        // reduce by 10 %
        rewardsScore.invoke(owner, "setPlatformDistPercentage", Names.DAOFUND, ICX.divide(BigInteger.TEN));
        BigInteger votingPercentage = BigInteger.valueOf(4).multiply(EXA).divide(BigInteger.TEN);

        // Assert
        Map<String, Map<String, BigInteger>> distData = (Map<String, Map<String, BigInteger>>) rewardsScore.call(
                "getDistributionPercentages");
        assertEquals(ICX.divide(BigInteger.TEN), distData.get("Base").get(Names.DAOFUND));
        assertEquals(votingPercentage.divide(BigInteger.TWO), getVotePercentage("sICX/bnUSD"));
        assertEquals(votingPercentage.divide(BigInteger.TWO), getVotePercentage("sICX/ICX"));

        sm.getBlock().increase(DAY);
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        rewardsScore.invoke(owner, "distribute");

        verify(baln.mock).transfer(daoFund.getAddress(),
                ICX.divide(BigInteger.TEN).multiply(emission).divide(EXA), new byte[0]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setFixedSourcePercentage() {
        // Arrange
        BigInteger ICXFixed = BigInteger.valueOf(3).multiply(EXA).divide(BigInteger.valueOf(100)); // 3%
        BigInteger sICXBnusdFixed = BigInteger.valueOf(7).multiply(EXA).divide(BigInteger.valueOf(100)); // 7%
        BigInteger votingPercentage = BigInteger.valueOf(2).multiply(EXA).divide(BigInteger.TEN);
        // assertOnlyCallableBy(rewardsScore, "setFixedSourcePercentage", "sICX/bnUSD",
        //         sICXBnusdFixed);

        // Act
        String expectedErrorMessage = "Reverted(0): Sum of distributions exceeds 100%";
        Executable overHundredPercent = () -> rewardsScore.invoke(owner, "setFixedSourcePercentage",
        "sICX/bnUSD", EXA);
        expectErrorMessage(overHundredPercent, expectedErrorMessage);
        rewardsScore.invoke(owner, "setFixedSourcePercentage", "sICX/bnUSD", sICXBnusdFixed);
        rewardsScore.invoke(owner, "setFixedSourcePercentage", "sICX/ICX", ICXFixed);

        // Assert
        Map<String, Map<String, BigInteger>> distData = (Map<String, Map<String, BigInteger>>) rewardsScore.call(
                "getDistributionPercentages");
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get("sICX/bnUSD"));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get("sICX/bnUSD"));

        assertEquals(sICXBnusdFixed, distData.get("Fixed").get("sICX/bnUSD"));
        assertEquals(ICXFixed, distData.get("Fixed").get("sICX/ICX"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getUserData() {
        // Assert
        Map<String, Map<String, BigInteger>> data = (Map<String, Map<String, BigInteger>>) rewardsScore.call(
                "getUserVoteData", user.getAddress());
        assertTrue(data.containsKey("sICX/ICX"));
        assertTrue(data.containsKey("sICX/bnUSD"));
        assertEquals(VOTE_POINTS.divide(BigInteger.TWO), data.get("sICX/ICX").get("power"));
        assertEquals(VOTE_POINTS.divide(BigInteger.TWO), data.get("sICX/bnUSD").get("power"));

        // Act
        sm.getBlock().increase(DAY * 10);
        vote(user, "sICX/ICX", BigInteger.ZERO);
        vote(user, "sICX/bnUSD", VOTE_POINTS);
        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/bnUSD",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        data = (Map<String, Map<String, BigInteger>>) rewardsScore.call("getUserVoteData", user.getAddress());
        assertTrue(data.containsKey("sICX/ICX"));
        assertTrue(data.containsKey("sICX/bnUSD"));
        assertEquals(BigInteger.ZERO, data.get("sICX/ICX").get("power"));
        assertEquals(VOTE_POINTS, data.get("sICX/bnUSD").get("power"));

        sm.getBlock().increase(DAY * 10);

        data = (Map<String, Map<String, BigInteger>>) rewardsScore.call("getUserVoteData", user.getAddress());
        assertTrue(!data.containsKey("sICX/ICX"));
        assertTrue(data.containsKey("sICX/bnUSD"));
        assertEquals(VOTE_POINTS, data.get("sICX/bnUSD").get("power"));
    }

    @Test
    void setGetVotable() {
        assertTrue((boolean)rewardsScore.call("isVotable", "sICX/ICX"));
        // assertOnlyCallableByGovernance(rewardsScore, "setVotable", "sICX/ICX", false);
        rewardsScore.invoke(owner, "setVotable", "sICX/ICX", false);
        assertTrue(!(boolean)rewardsScore.call("isVotable", "sICX/ICX"));
    }

    @Test
    void setGetTypeWeight() {
        int type = (int)rewardsScore.call("getSourceType", "sICX/ICX");
        rewardsScore.invoke(owner, "checkpoint");
        assertEquals(EXA ,rewardsScore.call("getCurrentTypeWeight", type));
        rewardsScore.invoke(owner, "changeTypeWeight", type, EXA.multiply(BigInteger.TWO));
        assertEquals(EXA.multiply(BigInteger.TWO) ,rewardsScore.call("getCurrentTypeWeight", type));
    }
}