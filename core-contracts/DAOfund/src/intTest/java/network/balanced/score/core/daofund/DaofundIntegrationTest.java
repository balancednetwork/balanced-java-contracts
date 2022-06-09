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

package network.balanced.score.core.daofund;

import foundation.icon.icx.Wallet;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.DAOfund;
import network.balanced.score.lib.interfaces.DAOfundScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;


class DaofundIntegrationTest {
    private static Wallet tester;
    private static Balanced balanced;

    @ScoreClient
    private static DAOfund daofund;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("DAOfund", System.getProperty("python"));
        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.deployBalanced();

        daofund = new DAOfundScoreClient(balanced.daofund);
    }
  
    @Test
    void testName() {
        assertEquals("Balanced DAOfund", daofund.name());

        balanced.daofund._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));

        assertEquals("Balanced DAOfund", daofund.name());
    }
}
