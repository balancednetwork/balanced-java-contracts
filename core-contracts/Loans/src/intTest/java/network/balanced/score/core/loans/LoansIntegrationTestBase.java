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

package network.balanced.score.core.loans;

import network.balanced.score.lib.test.integration.Balanced;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Map;

class LoansIntegrationTestBase extends LoansIntegrationTest {
    @BeforeAll
    public static void contractSetup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);

        LoansIntegrationTest.setup();
    }

    @Test
    @Order(101)
    void checkBalances() throws Exception {
        BigInteger sICXBalance = BigInteger.ZERO;
        BigInteger iETHBalance = BigInteger.ZERO;
        BigInteger iBTCBalance = BigInteger.ZERO;
        BigInteger bnUSDBalance = BigInteger.ZERO;
        int i = 1;
        while (true) {
                score.Address address;
                try {
                        address = reader.loans.getPositionAddress(i);
                } catch (Exception e) {
                        break;
                }
                i++;

                Map<String, Object> position = reader.loans.getAccountPositions(address);
                Map<String, Map<String, Object>> assetsDetails = (Map<String, Map<String, Object>>) position.get("holdings");
                if (assetsDetails.containsKey("sICX")) {
                        sICXBalance = sICXBalance.add(hexObjectToBigInteger(assetsDetails.get("sICX").get("sICX")));
                        bnUSDBalance = bnUSDBalance.add(hexObjectToBigInteger(assetsDetails.get("sICX").get("bnUSD")));
                }

                if (assetsDetails.containsKey("iETH")) {
                        iETHBalance = iETHBalance.add(hexObjectToBigInteger(assetsDetails.get("iETH").get("iETH")));
                        bnUSDBalance = bnUSDBalance.add(hexObjectToBigInteger(assetsDetails.get("iETH").get("bnUSD")));
                }

                if (assetsDetails.containsKey("iBTC")) {
                        iBTCBalance = iBTCBalance.add(hexObjectToBigInteger(assetsDetails.get("iBTC").get("iBTC")));
                        bnUSDBalance = bnUSDBalance.add(hexObjectToBigInteger(assetsDetails.get("iBTC").get("bnUSD")));
                }
        }

        assertEquals(sICXBalance, reader.sicx.balanceOf(balanced.loans._address()));
        assertEquals(iETHBalance, reader.irc2(ethAddress).balanceOf(balanced.loans._address()));
        assertEquals(iBTCBalance, reader.irc2(btcAddress).balanceOf(balanced.loans._address()));

        BigInteger stabilityDebt = reader.sicx.balanceOf(balanced.stability._address());
        assertEquals(bnUSDBalance, reader.bnUSD.totalSupply().subtract(stabilityDebt));
    }
}
