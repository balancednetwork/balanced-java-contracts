
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
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RewardsTestBase extends UnitTest {
    static final Long DAY = 43200L;
    static final BigInteger EXA = BigInteger.TEN.pow(18);

    static final ServiceManager sm = getServiceManager();
    static final Account owner = sm.createAccount();
    static final Account admin = sm.createAccount();

    DistributionPercentage loansDist = new DistributionPercentage();
    DistributionPercentage icxPoolDist = new DistributionPercentage();
    DistributionPercentage bwtDist = new DistributionPercentage();
    DistributionPercentage reserveDist = new DistributionPercentage();
    DistributionPercentage daoDist = new DistributionPercentage();

    int scoreCount = 0;
    final Account governance = Account.newScoreAccount(scoreCount++);
    final Account daoFund = Account.newScoreAccount(scoreCount++);
    final Account reserve = Account.newScoreAccount(scoreCount++);

    MockContract<DataSourceScoreInterface> dex;
    MockContract<DataSourceScoreInterface> loans;
    MockContract<IRC2MintableScoreInterface> baln;
    MockContract<IRC2MintableScoreInterface> bwt;
    MockContract<BoostedBalnScoreInterface> bBaln;
    Score rewardsScore;

    void setup() throws Exception {
        dex = new MockContract<>(DataSourceScoreInterface.class, sm, admin);
        loans = new MockContract<>(DataSourceScoreInterface.class, sm, admin);
        baln = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        bwt = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, admin);

        BigInteger startTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        sm.getBlock().increase(DAY);
        rewardsScore = sm.deploy(owner, RewardsImpl.class, governance.getAddress());

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());
        rewardsScore.invoke(admin, "setTimeOffset", startTime);

        rewardsScore.invoke(governance, "addNewDataSource", "sICX/ICX", dex.getAddress());
        rewardsScore.invoke(governance, "addNewDataSource", "Loans", loans.getAddress());

        Map<String, BigInteger> emptyDataSource = Map.of(
                "_balance", BigInteger.ZERO,
                "_totalSupply", BigInteger.ZERO
        );
        when(loans.mock.getBalanceAndSupply(any(String.class), any(Address.class))).thenReturn(emptyDataSource);
        when(dex.mock.getBalanceAndSupply(any(String.class), any(Address.class))).thenReturn(emptyDataSource);
        when(loans.mock.precompute(any(BigInteger.class), any(BigInteger.class))).thenReturn(true);
        when(dex.mock.precompute(any(BigInteger.class), any(BigInteger.class))).thenReturn(true);
        when(bBaln.mock.balanceOf(any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.ZERO);
        when(bBaln.mock.totalSupply(any(BigInteger.class))).thenReturn(BigInteger.ZERO);

        rewardsScore.invoke(admin, "setBaln", baln.getAddress());
        rewardsScore.invoke(admin, "setBwt", bwt.getAddress());
        rewardsScore.invoke(admin, "setDaofund", daoFund.getAddress());
        rewardsScore.invoke(admin, "setReserve", reserve.getAddress());
        rewardsScore.invoke(admin, "setBoostedBaln", bBaln.getAddress());

        rewardsScore.invoke(owner, "addDataProvider", loans.getAddress());
        rewardsScore.invoke(owner, "addDataProvider", dex.getAddress());

        setupDistributions();
        sm.getBlock().increase(DAY);
        syncDistributions();
    }

    private void setupDistributions() {
        loansDist.recipient_name = "Loans";
        loansDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        icxPoolDist.recipient_name = "sICX/ICX";
        icxPoolDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        bwtDist.recipient_name = "Worker Tokens";
        bwtDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        reserveDist.recipient_name = "Reserve Fund";
        reserveDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        daoDist.recipient_name = "DAOfund";
        daoDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist,
                bwtDist, reserveDist, daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);
    }

    void syncDistributions() {
        BigInteger currentDay = (BigInteger) rewardsScore.call("getDay");
        for (long i = 0; i < currentDay.intValue(); i++) {
            rewardsScore.invoke(admin, "distribute");
            rewardsScore.invoke(admin, "distribute");
        }
    }

    void mockBalanceAndSupply(MockContract<DataSourceScoreInterface> dataSource, String name, Address address,
                              BigInteger balance, BigInteger supply) {
        Map<String, BigInteger> balanceAndSupply = Map.of(
                "_balance", balance,
                "_totalSupply", supply
        );

        when(dataSource.mock.getBalanceAndSupply(name, address)).thenReturn(balanceAndSupply);
    }

    void verifyBalnReward(Address address, BigInteger expectedReward) {
        verify(baln.mock, times(1)).transfer(eq(address), argThat(reward -> {
            assertEquals(expectedReward.divide(BigInteger.TEN), reward.divide(BigInteger.TEN));
            return true;
        }), eq(new byte[0]));
    }

    BigInteger getOneDayRewards(Address address) {
        BigInteger rewardsPre = (BigInteger) rewardsScore.call("getBalnHolding", address);
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(admin, "distribute");
        BigInteger rewardsPost = (BigInteger) rewardsScore.call("getBalnHolding", address);
    
        return rewardsPost.subtract(rewardsPre);
    }

    void snapshotDistributionPercentage() {
        Object distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist,
                daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", distributionPercentages);
        sm.getBlock().increase(DAY);
    }
}
