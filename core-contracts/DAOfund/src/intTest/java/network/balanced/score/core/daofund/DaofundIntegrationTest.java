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
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.test.integration.Balanced;
import network.balanced.score.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;


import static network.balanced.score.test.integration.ScoreIntegrationTest.*;
import network.balanced.score.lib.interfaces.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class DaofundIntegrationTest { 
    static Wallet tester;
    static Balanced balanced;

    @ScoreClient
    static DAOfund daofund;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("DAOfund", System.getProperty("python"));
        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.deployBalanced();

        daofund = new DAOfundScoreClient(balanced.daofund);
    };
  
    @Test
    void testName() throws Exception{
        assertEquals("Balanced DAOfund", daofund.name());

        balanced.daofund._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));

        assertEquals("Balanced DAOfund", daofund.name());
    }
}
