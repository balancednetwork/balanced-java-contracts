/*
 * Copyright (c) 2022-2023 Balanced.network.
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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.weight.SourceWeightController.VOTE_POINTS;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// mimics dist percentages with votes
// runs all the same tests as RewardsTestRewards but after migration
class RewardsTestRewardsPostMigration extends RewardsTestRewards {

    BigInteger migrationDay;
    DistributionPercentage sICXbnUSDDist = new DistributionPercentage();
    Account user;

    @BeforeEach
    @Order(1)
    void setup() throws Exception {
        super.setup();
        sm.getBlock().increase(DAY * 10);
        syncDistributions();
        user = sm.createAccount();

        BigInteger day = (BigInteger) rewardsScore.call("getDay");
        migrationDay = day.add(BigInteger.valueOf(14));

        rewardsScore.invoke(owner, "setMigrateToVotingDay", migrationDay);

        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger unlockTime = currentTime.add(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(365)));
        when(bBaln.mock.lockedEnd(any(Address.class))).thenReturn(unlockTime);

        mockUserWeight(user, EXA);

        // Act
        vote(user, "sICX/ICX", VOTE_POINTS.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", VOTE_POINTS.divide(BigInteger.TWO));

        day = (BigInteger) rewardsScore.call("getDay");
        while (day.compareTo(migrationDay) <= 0) {
            sm.getBlock().increase(DAY);
            day = (BigInteger) rewardsScore.call("getDay");
        }

        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/ICX",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/bnUSD",
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
    }

    protected void setupDistributions() {
        rewardsScore.invoke(governance, "addNewDataSource", "sICX/bnUSD", dex.getAddress());

        loansDist.recipient_name = "Loans";
        loansDist.dist_percent = ICX.divide(BigInteger.valueOf(10)); //10%

        sICXbnUSDDist.recipient_name = "sICX/bnUSD";
        sICXbnUSDDist.dist_percent = BigInteger.valueOf(15).multiply(ICX).divide(BigInteger.valueOf(100)); //15%

        icxPoolDist.recipient_name = "sICX/ICX";
        icxPoolDist.dist_percent = BigInteger.valueOf(15).multiply(ICX).divide(BigInteger.valueOf(100)); //15%

        bwtDist.recipient_name = "Worker Tokens";
        bwtDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        reserveDist.recipient_name = "Reserve Fund";
        reserveDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        daoDist.recipient_name = "DAOfund";
        daoDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, sICXbnUSDDist,
                icxPoolDist,
                bwtDist, reserveDist, daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);
    }

    @Test
    void setPlatformDistPercentage() {
        // Arrange
        distribute();
        assertOnlyCallableByGovernance(rewardsScore, "setPlatformDistPercentage", "DAOfund",
                ICX.divide(BigInteger.TEN));

        // Act
        String expectedErrorMessage = "Reverted(0): Sum of distributions exceeds 100%";
        Executable overHundredPercent = () -> rewardsScore.invoke(governance, "setPlatformDistPercentage", "DAOfund",
                EXA);
        expectErrorMessage(overHundredPercent, expectedErrorMessage);
        // reduce by 10 %
        rewardsScore.invoke(governance, "setPlatformDistPercentage", "DAOfund", ICX.divide(BigInteger.TEN));
        BigInteger votingPercentage = BigInteger.valueOf(4).multiply(EXA).divide(BigInteger.TEN);

        // Assert
        Map<String, Map<String, BigInteger>> distData = (Map<String, Map<String, BigInteger>>) rewardsScore.call(
                "getDistributionPercentages");
        assertEquals(ICX.divide(BigInteger.TEN), distData.get("Base").get(daoDist.recipient_name));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(sICXbnUSDDist.recipient_name));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(icxPoolDist.recipient_name));

        sm.getBlock().increase(DAY);
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        rewardsScore.invoke(admin, "distribute");

        verify(baln.mock).transfer(daoFund.getAddress(),
                ICX.divide(BigInteger.TEN).multiply(emission).divide(EXA), new byte[0]);
    }

    @Test
    void setFixedSourcePercentage() {
        // Arrange
        BigInteger ICXFixed = BigInteger.valueOf(3).multiply(EXA).divide(BigInteger.valueOf(100)); // 3%
        BigInteger sICXBnusdFixed = BigInteger.valueOf(7).multiply(EXA).divide(BigInteger.valueOf(100)); // 7%
        BigInteger votingPercentage = BigInteger.valueOf(2).multiply(EXA).divide(BigInteger.TEN);
        assertOnlyCallableByGovernance(rewardsScore, "setFixedSourcePercentage", sICXbnUSDDist.recipient_name,
                sICXBnusdFixed);

        // Act
        String expectedErrorMessage = "Reverted(0): Sum of distributions exceeds 100%";
        Executable overHundredPercent = () -> rewardsScore.invoke(governance, "setFixedSourcePercentage",
                sICXbnUSDDist.recipient_name, EXA);
        expectErrorMessage(overHundredPercent, expectedErrorMessage);
        rewardsScore.invoke(governance, "setFixedSourcePercentage", sICXbnUSDDist.recipient_name, sICXBnusdFixed);
        rewardsScore.invoke(governance, "setFixedSourcePercentage", icxPoolDist.recipient_name, ICXFixed);

        // Assert
        Map<String, Map<String, BigInteger>> distData = (Map<String, Map<String, BigInteger>>) rewardsScore.call(
                "getDistributionPercentages");
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(sICXbnUSDDist.recipient_name));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(icxPoolDist.recipient_name));

        assertEquals(sICXBnusdFixed, distData.get("Fixed").get(sICXbnUSDDist.recipient_name));
        assertEquals(ICXFixed, distData.get("Fixed").get(icxPoolDist.recipient_name));
    }

    @Test
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
}