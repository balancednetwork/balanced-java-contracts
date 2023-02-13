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

import static  network.balanced.score.lib.test.integration.BalancedUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import score.Address;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.JsonArray;

import static network.balanced.score.lib.utils.Constants.POINTS;;

class LoansIntegrationTestMigration extends LoansIntegrationTest {
    static String loansPath;
    private static List<Map<String, Object>> positions = new ArrayList<Map<String, Object>>();

    private static BalancedClient testClient;

    @BeforeAll
    public static void contractSetup() throws Exception {
        loansPath = System.getProperty("Loans");
        URL url = LoansIntegrationTestMigration.class.getClassLoader().getResource("Loans-0.1.0-optimized.jar");
        System.out.println(url.getPath());
        System.setProperty("Loans", url.getPath());

        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);

        LoansIntegrationTest.setup();
        testClient = balanced.newClient();
    }


    @Test
    @Order(101)
    void setupPosition() {
        BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);
        BigInteger loanAmountICX = BigInteger.TEN.pow(22);
        BigInteger loanAmountETH = BigInteger.TEN.pow(21);

        owner.irc2(ethAddress).mintTo(testClient.getAddress(), collateralETH, null);

        testClient.stakeDepositAndBorrow(icxCollateral, loanAmountICX);
        testClient.depositAndBorrow(ethAddress, collateralETH, loanAmountETH);
    }

    @Test
    @Order(102)
    void migrate() {
        for (Address address : balanced.balancedClients.keySet()) {
            if (hasPosition(address.toString())) {
                positions.add(reader.loans.getAccountPositions(address.toString()));
            }
        }

        byte[] bytes = getContractBytes(loansPath);
        String governanceParam = new JsonArray()
            .add(createParameter(balanced.governance._address()))
            .toString();
        owner.governance.deployTo(balanced.loans._address(), bytes, governanceParam);
    }


    @Test
    @Order(103)
    void verifyPositions() {
        for (Map<String, Object> pos : positions) {
            String address = pos.get("address").toString();
            assertEquals(pos, reader.loans.getAccountPositions(address.toString()));
        }

        assertTrue(positions.size() > 10);

        Map<String, Object> pos = reader.loans.getAccountPositions(testClient.getAddress().toString());
        BigInteger repaidLoanICX = BigInteger.TEN.pow(21);
        testClient.loans.returnAsset("bnUSD", repaidLoanICX, "sICX");

        BigInteger addedLoanETH = BigInteger.TEN.pow(21);
        testClient.loans.borrow("iETH", "bnUSD", addedLoanETH);
        Map<String, Object> newPos = reader.loans.getAccountPositions(testClient.getAddress().toString());
        Map<String, Map<String, String>> holdings = (Map<String, Map<String, String>>)pos.get("holdings");
        Map<String, Map<String, String>> newHoldings = (Map<String, Map<String, String>>)newPos.get("holdings");
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = addedLoanETH.multiply(feePercent).divide(POINTS);
        BigInteger debt = addedLoanETH.add(fee);

        System.out.println(holdings);
        assertEquals(hexObjectToBigInteger(holdings.get("sICX").get("bnUSD")).subtract(repaidLoanICX), hexObjectToBigInteger(newHoldings.get("sICX").get("bnUSD")));
        assertEquals(holdings.get("sICX").get("sICX"), newHoldings.get("sICX").get("sICX"));

        assertEquals(hexObjectToBigInteger(holdings.get("iETH").get("bnUSD")).add(debt), hexObjectToBigInteger(newHoldings.get("iETH").get("bnUSD")));
        assertEquals(holdings.get("iETH").get("iETH"), newHoldings.get("iETH").get("iETH"));
    }

    private boolean hasPosition(String address) {
        try {
            return reader.loans.hasDebt(address.toString());
        } catch (Exception e) {
            return false;
        }
    }
}
