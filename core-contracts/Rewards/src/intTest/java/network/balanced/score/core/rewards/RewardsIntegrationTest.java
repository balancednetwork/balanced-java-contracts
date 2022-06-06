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

import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Balanced;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RewardsIntegrationTest {
    private static Balanced balanced;

    @ScoreClient
    private static Governance governance;

    @ScoreClient
    private static Rewards rewards;

    @ScoreClient
    private static Loans loans;

    @ScoreClient
    private static BalancedDollar bnUSD;

    @ScoreClient
    private static Baln baln;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.deployBalanced();

        governance = new GovernanceScoreClient(balanced.governance);
        rewards = new RewardsScoreClient(balanced.rewards);
        loans = new LoansScoreClient(balanced.loans);
        bnUSD = new BalancedDollarScoreClient(balanced.bnusd);
        baln = new BalnScoreClient(balanced.baln);
    }

    @Test
    void testName() {
        assertEquals("Balanced Rewards", rewards.name());
    }

    @Test
    void getEmission() {
        BigInteger dayAfterRewardsDecay = BigInteger.valueOf(70);
        BigInteger emission = rewards.getEmission(BigInteger.ONE);
        BigInteger emissionAfterDecay = rewards.getEmission(dayAfterRewardsDecay);

        assertEquals(BigInteger.TEN.pow(23), emission);
        assertTrue(BigInteger.TEN.pow(23).compareTo(emissionAfterDecay) > 0);
    }

    @Test
    void testClaimAndDistribute() {        
        ((LoansScoreClient)loans).depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(20), null, null);   
        
        balanced.distributeRewards();
        balanced.distributeRewards();
        balanced.distributeRewards();


        rewards.claimRewards();
    
    }
}
