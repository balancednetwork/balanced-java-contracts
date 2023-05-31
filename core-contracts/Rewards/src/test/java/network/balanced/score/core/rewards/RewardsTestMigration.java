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

import static network.balanced.score.core.rewards.weight.SourceWeightController.VOTE_POINTS;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.iconloop.score.test.Account;

import network.balanced.score.lib.structs.DistributionPercentage;
import score.Address;

class RewardsTestMigration extends RewardsTestBase {

    BigInteger migrationDay;
    DistributionPercentage sICXbnUSDDist = new DistributionPercentage();

    @BeforeEach
    void setup() throws Exception {
            super.setup();
            sm.getBlock().increase(DAY *10);
            syncDistributions();
            BigInteger day = (BigInteger) rewardsScore.call("getDay");
            migrationDay = day.add(BigInteger.valueOf(14));

            rewardsScore.invoke(owner, "setMigrateToVotingDay", migrationDay);

            BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
            BigInteger unlockTime = currentTime.add(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(365)));
            when(bBaln.mock.lockedEnd(any(Address.class))).thenReturn(unlockTime);
    }

    protected void setupDistributions() {
        rewardsScore.invoke(governance, "addNewDataSource", "sICX/bnUSD", dex.getAddress());

        loansDist.recipient_name = "Loans";
        loansDist.dist_percent = ICX.divide(BigInteger.valueOf(10)); //10%

        sICXbnUSDDist.recipient_name = "sICX/bnUSD";
        sICXbnUSDDist.dist_percent = ICX.divide(BigInteger.valueOf(10)); //10%

        icxPoolDist.recipient_name = "sICX/ICX";
        icxPoolDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        bwtDist.recipient_name = "Worker Tokens";
        bwtDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        reserveDist.recipient_name = "Reserve Fund";
        reserveDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        daoDist.recipient_name = "DAOfund";
        daoDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, sICXbnUSDDist, icxPoolDist,
                bwtDist, reserveDist, daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);
    }

    @Test
    void migration() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);
        mockBalanceAndSupply(loans, "Loans", owner.getAddress(), BigInteger.ONE, BigInteger.ONE);
        mockBalanceAndSupply(dex, "sICX/ICX", owner.getAddress(), BigInteger.ONE, BigInteger.ONE);
        mockBalanceAndSupply(dex, "sICX/bnUSD", owner.getAddress(), BigInteger.ONE, BigInteger.ONE);

        BigInteger votingPercentage = BigInteger.valueOf(30).multiply(EXA).divide(BigInteger.valueOf(100));

        rewardsScore.invoke(loans.account, "updateRewardsData", "Loans", BigInteger.ZERO, owner.getAddress().toString(), BigInteger.ONE);
        rewardsScore.invoke(dex.account, "updateRewardsData", "sICX/ICX", BigInteger.ZERO, owner.getAddress().toString(), BigInteger.ONE);
        rewardsScore.invoke(dex.account, "updateRewardsData", "sICX/bnUSD", BigInteger.ZERO, owner.getAddress().toString(), BigInteger.ONE);

        // Act
        vote(user, "sICX/ICX", VOTE_POINTS.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", VOTE_POINTS.divide(BigInteger.TWO));

        BigInteger day  = (BigInteger) rewardsScore.call("getDay");
        while (day.compareTo(migrationDay) <= 0) {
            rewardsScore.invoke(owner, "claimRewards", getUserSources(owner.getAddress()));

            Map<String, Map<String, Object>> data = (Map<String, Map<String, Object>>) rewardsScore.call("getDataSourcesAt", day.subtract(BigInteger.ONE));
            BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));

            BigInteger expectedLoansDist = emission.multiply(loansDist.dist_percent).divide(EXA);
            BigInteger expectedICXDist = emission.multiply(icxPoolDist.dist_percent).divide(EXA);
            BigInteger expectedBnUSDDist = emission.multiply(sICXbnUSDDist.dist_percent).divide(EXA);

            BigInteger loansDist = (BigInteger) data.get("Loans").get("total_dist");
            BigInteger ICXDist = (BigInteger) data.get("sICX/ICX").get("total_dist");
            BigInteger bnUSDDist = (BigInteger) data.get("sICX/bnUSD").get("total_dist");

            assertEquals(expectedLoansDist, loansDist);
            assertEquals(expectedICXDist, ICXDist);
            assertEquals(expectedBnUSDDist, bnUSDDist);

            sm.getBlock().increase(DAY);
            day = (BigInteger) rewardsScore.call("getDay");
        }

        rewardsScore.invoke(owner, "claimRewards", getUserSources(owner.getAddress()));
        Map<String, Map<String, Object>> data = (Map<String, Map<String, Object>>) rewardsScore.call("getDataSourcesAt", day.subtract(BigInteger.ONE));
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));

        BigInteger expectedLoansDist = emission.multiply(loansDist.dist_percent).divide(EXA);
        BigInteger expectedICXDist = emission.multiply(votingPercentage.divide(BigInteger.TWO)).divide(EXA);
        BigInteger expectedBnUSDDist = emission.multiply(votingPercentage.divide(BigInteger.TWO)).divide(EXA);

        BigInteger loansDist = (BigInteger) data.get("Loans").get("total_dist");
        BigInteger ICXDist = (BigInteger) data.get("sICX/ICX").get("total_dist");
        BigInteger bnUSDDist = (BigInteger) data.get("sICX/bnUSD").get("total_dist");


        assertEquals(expectedLoansDist, loansDist);
        assertEquals(expectedICXDist, ICXDist);
        assertEquals(expectedBnUSDDist, bnUSDDist);
    }

    @Test
    void getDistPercentages() {
        // Arrange
        Account user = sm.createAccount();
        mockUserWeight(user, EXA);
        BigInteger votingPercentage = BigInteger.valueOf(30).multiply(EXA).divide(BigInteger.valueOf(100));

        // Act
        vote(user, "sICX/ICX", VOTE_POINTS.divide(BigInteger.TWO));
        vote(user, "sICX/bnUSD", VOTE_POINTS.divide(BigInteger.TWO));

        sm.getBlock().increase(WEEK_BLOCKS);
        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/ICX", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        rewardsScore.invoke(owner, "updateRelativeSourceWeight", "sICX/bnUSD", BigInteger.valueOf(sm.getBlock().getTimestamp()));

        // Assert
        Map<String, Map<String, BigInteger>> distData = (Map<String, Map<String, BigInteger>>) rewardsScore.call("getDistributionPercentages");

        assertEquals(bwtDist.dist_percent, distData.get("Base").get(bwtDist.recipient_name));
        assertEquals(daoDist.dist_percent, distData.get("Base").get(daoDist.recipient_name));
        assertEquals(reserveDist.dist_percent, distData.get("Base").get(reserveDist.recipient_name));

        assertEquals(BigInteger.ZERO, distData.get("Voting").get(loansDist.recipient_name));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(sICXbnUSDDist.recipient_name));
        assertEquals(votingPercentage.divide(BigInteger.TWO), distData.get("Voting").get(icxPoolDist.recipient_name));

        assertEquals(loansDist.dist_percent, distData.get("Fixed").get(loansDist.recipient_name));
    }

    @Test
    void setGetVotable() {
        assertTrue((boolean)rewardsScore.call("isVotable", "sICX/ICX"));
        assertOnlyCallableByGovernance(rewardsScore, "setVotable", "sICX/ICX", false);
        rewardsScore.invoke(governance, "setVotable", "sICX/ICX", false);
        assertTrue(!(boolean)rewardsScore.call("isVotable", "sICX/ICX"));
    }

    @Test
    void addDataSource() {
        // Arrange
        String newDataSource = "foo";
        int type = 1;
        // Act & Assert
        String expectedErrorMessage = "Reverted(0): There is no data source with the name foo";
        Executable nonExistingDataSource = () -> rewardsScore.invoke(governance, "addDataSource", newDataSource, 1, BigInteger.ZERO);
        expectErrorMessage(nonExistingDataSource, expectedErrorMessage);

        // Arrange
        rewardsScore.invoke(governance, "addNewDataSource", newDataSource, dex.getAddress());
        assertOnlyCallableByGovernance(rewardsScore, "addDataSource", newDataSource, type, BigInteger.ZERO);

        rewardsScore.invoke(governance, "addDataSource", newDataSource, 1, BigInteger.ZERO);

        // Assert
        assertEquals(type, rewardsScore.call("getSourceType", newDataSource));
    }

    @Test
    void addType() {
        assertOnlyCallableByGovernance(rewardsScore, "addType", "newType");
    }

    @Test
    void setGetTypeWeight() {
        int type = (int)rewardsScore.call("getSourceType", "sICX/ICX");
        rewardsScore.invoke(admin, "checkpoint");
        assertEquals(EXA ,rewardsScore.call("getCurrentTypeWeight", type));
        assertOnlyCallableByGovernance(rewardsScore, "changeTypeWeight", type, EXA.multiply(BigInteger.TWO));
        rewardsScore.invoke(governance, "changeTypeWeight", type, EXA.multiply(BigInteger.TWO));
        assertEquals(EXA.multiply(BigInteger.TWO) ,rewardsScore.call("getCurrentTypeWeight", type));
    }
}