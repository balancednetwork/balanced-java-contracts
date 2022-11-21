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

package network.balanced.score.core.dividends;

import network.balanced.score.lib.structs.DistributionPercentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;

class DividendsImplTestSetup extends DividendsImplTestBase {
    @BeforeEach
    void setup() throws Exception {
        setupBase();
    }

    @Test
    void name() {
        String contractName = "Balanced Dividends";
        assertEquals(contractName, dividendScore.call("name"));
    }

    @Test
    void setDistributionActivationStatus() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDistributionActivationStatus", true);
        assertEquals(true, dividendScore.call("getDistributionActivationStatus"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(dividendScore, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(dividendScore, governanceScore, admin);
    }

    @Test
    void setAndGetLoans() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setLoans",
                loansScore.getAddress(), "getLoans");
    }

    @Test
    void setAndGetDao() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setDaofund",
                daoScore.getAddress(), "getDaofund");
    }

    @Test
    void setAndGetBaln() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setBaln",
                balnScore.getAddress(), "getBaln");
    }

    @Test
    void setAndGetDex() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setDex",
                dexScore.getAddress(), "getDex");
    }

    @Test
    void getAcceptedTokens() {
        List<Address> expected_list = new ArrayList<>();
        expected_list.add(balnScore.getAddress());
        expected_list.add(sICXScore.getAddress());

        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "addAcceptedTokens", balnScore.getAddress());
        dividendScore.invoke(admin, "addAcceptedTokens", sICXScore.getAddress());

        assertEquals(expected_list, dividendScore.call("getAcceptedTokens"));

        dividendScore.invoke(admin, "removeAcceptedTokens", balnScore.getAddress());
        expected_list.remove(balnScore.getAddress());

        assertEquals(expected_list, dividendScore.call("getAcceptedTokens"));
    }

    @Test
    void getDividendsCategories() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void removeDividendsCategory() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

//        add category
        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistributionPercentage[] dist = new DistributionPercentage[]{new DistributionPercentage(),
                new DistributionPercentage(), new DistributionPercentage()};
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));

//        remove category
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(6).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.ZERO;

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        dividendScore.invoke(admin, "removeDividendsCategory", "loans");

        expected_list.remove(expected_list.size() - 1);
        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void setGetDividendsBatchSize() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(2));

        assertEquals(BigInteger.valueOf(2), dividendScore.call("getDividendsBatchSize"));
    }

    @Test
    void getSnapshotId() {
        assertEquals(BigInteger.ONE, dividendScore.call("getSnapshotId"));
    }

    @Test
    void setGetDividendsOnlyToStakedBalnDay() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", BigInteger.TWO);
        assertEquals(BigInteger.TWO, dividendScore.call("getDividendsOnlyToStakedBalnDay"));
    }

    @Test
    void getTimeOffset() {
        dividendScore.invoke(owner, "distribute");
        assertEquals(BigInteger.ZERO, dividendScore.call("getTimeOffset"));
    }

    @Test
    void getDividendsPercentage() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        contextMock.when(() -> Context.call(eq(loansScore.getAddress()), eq("getDay"))).thenReturn(BigInteger.valueOf(1));

        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("getDividendsPercentage"));
    }

    @Test
    void getDayTest() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());

        BigInteger currentDay = (BigInteger) dividendScore.call("getDay");
        sm.getBlock().increase(4 * DAY);

        assertEquals(currentDay.add(BigInteger.valueOf(4)), dividendScore.call("getDay"));
        sm.getBlock().increase(-4 * DAY);
        assertEquals(currentDay, dividendScore.call("getDay"));
    }
}
