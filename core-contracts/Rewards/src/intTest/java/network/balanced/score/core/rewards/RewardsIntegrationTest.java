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

import foundation.icon.icx.Wallet;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.RewardsScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RewardsIntegrationTest {
    private static Wallet tester;
    private static Balanced balanced;

    @ScoreClient
    private static Rewards rewards;

    @BeforeAll
    static void setup() throws Exception {
        // System.setProperty("Rewards", System.getProperty("python"));
        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.deployBalanced();

        rewards = new RewardsScoreClient(balanced.rewards);
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

    // @Test 
    // void syncDistributions() {
    //     Map<String, Object> distStatus;
    //     while (true) {
    //         distStatus = rewards.distStatus();

    //         BigInteger platform_day = new BigInteger(((String)distStatus.get("platform_day")).substring(2), 16);
    //         Map<String, String> sourceDays = (Map<String, String>)distStatus.get("source_days");
    //         boolean allSourcesUpToDate = true;
    //         for(String hexDay : sourceDays.values()) {
    //             BigInteger day = new BigInteger(hexDay.substring(2), 16);
    //             if (day.compareTo(platform_day) < 0) {
    //                 balanced.distributeRewards();
    //                 allSourcesUpToDate = false;
    //             }
    //         }

    //         if (allSourcesUpToDate) {
    //             break;
    //         }
    //     }

    // }

}
