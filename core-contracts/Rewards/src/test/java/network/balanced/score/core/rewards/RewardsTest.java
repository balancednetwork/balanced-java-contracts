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
import network.balanced.score.lib.structs.DistributionPercentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RewardsTest extends RewardsTestBase {

    @BeforeEach
    void setup() throws Exception {
        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDataSources() {
        // Act
        Map<String, Map<String, Object>> dataSources = (Map<String, Map<String, Object>>) rewardsScore.call(
                "getDataSources");

        // Assert
        assertEquals(3, dataSources.size());
        assertTrue(dataSources.containsKey("Loans"));
        assertTrue(dataSources.containsKey("sICX/ICX"));
        assertTrue(dataSources.containsKey("sICX/bnUSD"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDataSourceNames() {
        // Act
        List<String> names = (List<String>) rewardsScore.call("getDataSourceNames");

        // Assert
        assertEquals(3, names.size());
        assertTrue(names.contains("Loans"));
        assertTrue(names.contains("sICX/ICX"));
        assertTrue(names.contains("sICX/bnUSD"));
    }

    @Test
    void getEmission_first60Days() {
        // Arrange
        BigInteger expectedEmission = BigInteger.TEN.pow(23);

        // Act & Assert
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.ONE));
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(60)));
    }

    @Test
    void getEmission_after60Days() {
        // Arrange
        BigInteger baseEmission = BigInteger.TEN.pow(23);
        BigInteger percent = BigInteger.valueOf(10);
        BigInteger percent100 = percent.multiply(BigInteger.valueOf(100));
        BigInteger decay = percent100.subtract(BigInteger.valueOf(5));

        BigInteger expectedEmission = baseEmission.multiply(decay).divide(percent100);
        BigInteger expectedEmissionDayAfter = expectedEmission.multiply(decay).divide(percent100);

        // Act & Assert
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(61)));
        assertEquals(expectedEmissionDayAfter, rewardsScore.call("getEmission", BigInteger.valueOf(62)));
    }

    @Test
    void getEmission_after935Days() {
        // Arrange
        int day = 935;
        BigInteger minEmission = BigInteger.valueOf(1250).multiply(ICX);

        // Act & Assert
        assertNotEquals(minEmission, rewardsScore.call("getEmission", BigInteger.valueOf(day - 1)));
        assertEquals(minEmission, rewardsScore.call("getEmission", BigInteger.valueOf(day)));
    }

    @Test
    void tokenFallback_baln() {
        // Arrange
        Account account = sm.createAccount();

        // Act & Assert
        rewardsScore.invoke(baln.account, "tokenFallback", account.getAddress(), BigInteger.TEN, new byte[0]);
    }

    @Test
    void tokenFallback_notBaln() {
        // Arrange
        Account account = sm.createAccount();
        String expectedErrorMessage = RewardsImpl.TAG + ": The Rewards SCORE can only accept BALN tokens";

        // Act & Assert
        Executable tokenFallbackBwt = () -> rewardsScore.invoke(bwt.account, "tokenFallback", account.getAddress(),
                BigInteger.TEN, new byte[0]);
        expectErrorMessage(tokenFallbackBwt, expectedErrorMessage);
    }
}



