// /*
//  * Copyright (c) 2022-2022 Balanced.network.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package network.balanced.score.core.loans;

// import static  network.balanced.score.lib.test.integration.BalancedUtils.*;
// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;

// import network.balanced.score.lib.test.integration.Balanced;
// import network.balanced.score.lib.test.integration.BalancedClient;
// import score.Address;

// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Order;
// import org.junit.jupiter.api.Test;

// import java.math.BigInteger;
// import java.net.URL;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;

// import com.eclipsesource.json.JsonArray;

// import static network.balanced.score.lib.utils.Constants.POINTS;;

// class LoansIntegrationTestMigration extends LoansIntegrationTest {
//     static String loansPath;
//     private static List<Map<String, Object>> positions = new ArrayList<Map<String, Object>>();

//     private static BalancedClient testClient;

//     @BeforeAll
//     public static void contractSetup() throws Exception {
//         loansPath = System.getProperty("Loans");
//         URL url = LoansIntegrationTestMigration.class.getClassLoader().getResource("Loans-1.0.2-optimized.jar");
//         System.setProperty("Loans", url.getPath());

//         balanced = new Balanced();
//         balanced.setupBalanced();
//         owner = balanced.ownerClient;
//         reader = balanced.newClient(BigInteger.ZERO);

//         LoansIntegrationTest.setup();
//         testClient = balanced.newClient();
//     }


//     @Test
//     @Order(-10)
//     void setupPosition() {
//         BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
//         BigInteger loanAmountICX = BigInteger.TEN.pow(22);

//         testClient.stakeDepositAndBorrow(icxCollateral, loanAmountICX);
//     }

//     @Test
//     @Order(-9)
//     void migrate() {
//         for (Address address : balanced.balancedClients.keySet()) {
//             if (hasPosition(address.toString())) {
//                 positions.add(reader.loans.getAccountPositions(address.toString()));
//             }
//         }

//         byte[] bytes = getContractBytes(loansPath);
//         String governanceParam = new JsonArray()
//             .add(createParameter(balanced.governance._address()))
//             .toString();
//         owner.governance.deployTo(balanced.loans._address(), bytes, governanceParam);
//     }


//     @Test
//     @Order(-8)
//     void verifyPositions() {
//         assertTrue(positions.size() > 0);

//         for (Map<String, Object> pos : positions) {
//             String address = pos.get("address").toString();
//             assertEquals(pos, reader.loans.getAccountPositions(address.toString()));
//         }
//     }

//     private boolean hasPosition(String address) {
//         try {
//             return reader.loans.hasDebt(address.toString());
//         } catch (Exception e) {
//             return false;
//         }
//     }
// }
