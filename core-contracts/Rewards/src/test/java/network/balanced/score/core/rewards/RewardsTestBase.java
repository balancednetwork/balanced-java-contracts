
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
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.test.mock.MockBalanced;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.weight.SourceWeightController.VOTE_POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RewardsTestBase extends UnitTest {
    static final Long DAY = 43200L;
    static final Long WEEK_BLOCKS = 7*DAY;

    static final BigInteger EXA = BigInteger.TEN.pow(18);

    static final ServiceManager sm = getServiceManager();
    static final Account owner = sm.createAccount();

    BigInteger defaultPlatformDist = ICX.divide(BigInteger.valueOf(5));

    int scoreCount = 0;
    Account governance;
    Account daoFund;
    Account reserve;
    Account user;
    MockBalanced mockBalanced;

    MockContract<Dex> dex;
    MockContract<Loans> loans;
    MockContract<BalancedToken> baln;
    MockContract<WorkerToken> bwt;
    MockContract<BoostedBaln> bBaln;

    Score rewardsScore;

    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance.account;
        dex = mockBalanced.dex;
        loans = mockBalanced.loans;
        baln = mockBalanced.baln;
        bwt = mockBalanced.bwt;
        bBaln = mockBalanced.bBaln;
        daoFund = mockBalanced.daofund.account;
        reserve = mockBalanced.reserve.account;

        user = sm.createAccount();

        BigInteger startTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        // One vote period before being able to start voting
        sm.getBlock().increase(DAY*10);

        rewardsScore = sm.deploy(owner, RewardsImpl.class, governance.getAddress());
        setupDistributions();
        rewardsScore.invoke(owner, "setTimeOffset", startTime);
        rewardsScore.invoke(owner, "addDataProvider", loans.getAddress());
        rewardsScore.invoke(owner, "addDataProvider", dex.getAddress());

        rewardsScore.invoke(owner, "createDataSource", "sICX/ICX", dex.getAddress(), 0);
        rewardsScore.invoke(owner, "createDataSource", "sICX/bnUSD", dex.getAddress(), 0);
        rewardsScore.invoke(owner, "createDataSource", "Loans", loans.getAddress(), 0);

        Map<String, BigInteger> emptyDataSource = Map.of(
                "_balance", BigInteger.ZERO,
                "_totalSupply", BigInteger.ZERO
        );
        when(loans.mock.getBalanceAndSupply(any(String.class), any(String.class))).thenReturn(emptyDataSource);
        when(dex.mock.getBalanceAndSupply(any(String.class), any(String.class))).thenReturn(emptyDataSource);
        when(bBaln.mock.balanceOf(any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.ZERO);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(BigInteger.ZERO);


        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger unlockTime = currentTime.add(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(365)));
        when(bBaln.mock.lockedEnd(any(Address.class))).thenReturn(unlockTime);
        sm.getBlock().increase(DAY);

        mockUserWeight(user, EXA);


        vote(user, "sICX/ICX", VOTE_POINTS.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", VOTE_POINTS.divide(BigInteger.TWO));
        rewardsScore.invoke(owner, "setFixedSourcePercentage", "Loans", EXA.divide(BigInteger.TEN));

        // One vote period to apply votes
        sm.getBlock().increase(DAY*10);

        BigInteger day = (BigInteger) rewardsScore.call("getDay");

        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/ICX",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/bnUSD",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
    }

    protected void setupDistributions() {
        rewardsScore.invoke(owner, "setPlatformDistPercentage", Names.DAOFUND, defaultPlatformDist);
        rewardsScore.invoke(owner, "setPlatformDistPercentage", Names.WORKERTOKEN, defaultPlatformDist);
        rewardsScore.invoke(owner, "setPlatformDistPercentage", Names.RESERVE, defaultPlatformDist);
    }

    void syncDistributions() {
        BigInteger currentDay = (BigInteger) rewardsScore.call("getDay");
        for (long i = 0; i < currentDay.intValue(); i++) {
            rewardsScore.invoke(owner, "distribute");
        }
    }

    void mockBalanceAndSupply(MockContract<? extends DataSource> dataSource, String name, Address address,
                              BigInteger balance, BigInteger supply) {
        Map<String, BigInteger> balanceAndSupply = Map.of(
                "_balance", balance,
                "_totalSupply", supply
        );

        when(dataSource.mock.getBalanceAndSupply(name, address.toString())).thenReturn(balanceAndSupply);
    }

    void verifyBalnReward(Address address, BigInteger expectedReward) {
        verify(baln.mock, times(1)).transfer(eq(address), argThat(reward -> {
            assertEquals(expectedReward.divide(BigInteger.valueOf(100)), reward.divide(BigInteger.valueOf(100)));
            return true;
        }), eq(new byte[0]));
    }

    BigInteger getOneDayRewards(Address address) {
        BigInteger rewardsPre = (BigInteger) rewardsScore.call("getBalnHolding", address.toString());
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(owner, "distribute");
        BigInteger rewardsPost = (BigInteger) rewardsScore.call("getBalnHolding", address.toString());

        return rewardsPost.subtract(rewardsPre);
    }

    Object getUserSources(Address address) {
        return rewardsScore.call("getUserSources", address.toString());
    }

    void vote(Account user, String name, BigInteger weight) {
        rewardsScore.invoke(user, "voteForSource", name, weight);
    }

    void mockUserWeight(Account user, BigInteger weight) {
        when(bBaln.mock.getLastUserSlope(user.getAddress())).thenReturn(weight);
    }

    @SuppressWarnings("unchecked")
    BigInteger getVotePercentage(String name){
        Map<String, Map<String, BigInteger>> data = (Map<String, Map<String, BigInteger>>) rewardsScore.call("getDistributionPercentages");
        return data.get("Voting").get(name).add(data.get("Fixed").getOrDefault(name, BigInteger.ZERO));
    }
}
