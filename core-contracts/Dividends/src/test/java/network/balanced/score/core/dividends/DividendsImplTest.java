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
import network.balanced.score.lib.structs.PrepDelegations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DividendsImplTest extends DividendsImplTestBase {

    private BigInteger batchSize = BigInteger.TWO;

    @BeforeEach
    void setup() throws Exception {
        sm.getBlock().increase(2 * DAY);
        setupBase();
        dividendScore.invoke(governance.account, "setAdmin", admin.getAddress());

        dividendScore.invoke(admin, "addAcceptedTokens", bnusd.getAddress());
        dividendScore.invoke(admin, "setDividendsBatchSize", batchSize);
        dividendScore.invoke(admin, "setDistributionActivationStatus", true);

        when(dex.mock.getTimeOffset()).thenReturn(BigInteger.TWO);
        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(baln.getAddress()));
        asset.put("bnUSD", String.valueOf(bnusd.getAddress()));

        when(loans.mock.getAssetTokens()).thenReturn(asset);

        dividendScore.invoke(owner, "distribute");
    }

    private void transferDaofundDiv(int start, int end) {
        dividendScore.invoke(owner, "transferDaofundDividends", start, end);
    }

    @Test
    void checkStartEndDay() {
        sm.getBlock().increase(4 * DAY);

        int day = getDay().intValue();

        dividendScore.invoke(owner, "distribute");
        Executable contractCallCase1 = () -> transferDaofundDiv(-1, day);
        String expectedErrorMessageCase1 = "Reverted(0): Balanced Dividends: Invalid value of start provided.";
        expectErrorMessage(contractCallCase1, expectedErrorMessageCase1);

        Executable contractCallCase2 = () -> transferDaofundDiv(day + 1, day + 2);
        String expectedErrorMessageCase2 = "Reverted(0): Balanced Dividends: Invalid value of start provided.";
        expectErrorMessage(contractCallCase2, expectedErrorMessageCase2);

        Executable contractCallCase3 = () -> transferDaofundDiv(day - 1, day - 1);
        String expectedErrorMessageCase3 = "Reverted(0): Balanced Dividends: Start must not be greater than or equal " +
                "to end.";
        expectErrorMessage(contractCallCase3, expectedErrorMessageCase3);

        Executable contractCallCase4 = () -> transferDaofundDiv(day - 1, day - 2);
        String expectedErrorMessageCase4 = "Reverted(0): Balanced Dividends: Start must not be greater than or equal " +
                "to end.";
        expectErrorMessage(contractCallCase4, expectedErrorMessageCase4);

        Executable contractCallCase5 = () -> transferDaofundDiv(day - 1, -2);
        String expectedErrorMessageCase5 = "Reverted(0): Balanced Dividends: Invalid value of end provided.";
        expectErrorMessage(contractCallCase5, expectedErrorMessageCase5);

        Executable contractCallCase6 = () -> transferDaofundDiv(day - 4, day);
        String expectedErrorMessageCase6 = "Reverted(0): Balanced Dividends: Maximum allowed range is 2";
        expectErrorMessage(contractCallCase6, expectedErrorMessageCase6);

        dividendScore.invoke(owner, "setContinuousDividendsDay", BigInteger.valueOf(day + 1));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        int continuousDay = day + 1;

        Executable endAfterContinuous = () -> transferDaofundDiv(continuousDay - 1, continuousDay + 1);
        String expectedErrorMessageEndAfterContinuous = "Reverted(0): Balanced Dividends: Invalid value of end " +
                "provided.";
        expectErrorMessage(endAfterContinuous, expectedErrorMessageEndAfterContinuous);

        Executable startAfterContinuous = () -> transferDaofundDiv(continuousDay, continuousDay + 1);
        String expectedErrorMessageStartAfterContinuous = "Reverted(0): Balanced Dividends: Invalid value of start " +
                "provided.";
        expectErrorMessage(startAfterContinuous, expectedErrorMessageStartAfterContinuous);
    }

    @Test
    void getBalance() {
        dividendScore.invoke(governance.account, "setAdmin", admin.getAddress());

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(baln.getAddress()));

        when(loans.mock.getAssetTokens()).thenReturn(asset);
        when(baln.mock.balanceOf(any(Address.class))).thenReturn(BigInteger.TEN.pow(4));

        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put("baln", BigInteger.TEN.pow(4));
        expectedResult.put("ICX", BigInteger.ZERO);

        assertEquals(expectedResult, dividendScore.call("getBalances"));
    }


    @Test
    void getDailyFees() {
        // Arrange
        BigInteger day = getDay();
        BigInteger expectedFees = BigInteger.TWO.multiply(BigInteger.TEN.pow(19));
        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put(bnusd.getAddress().toString(), expectedFees);

        // Act
        addBnusdFees(expectedFees);

        // Assert
        assertEquals(expectedResult, dividendScore.call("getDailyFees", day));
    }

    @Test
    void tokenFallback() {
        // Arrange
        BigInteger day = getDay();
        BigInteger expectedFeesBnusd = BigInteger.valueOf(20).multiply(ICX);
        BigInteger expectedFeesBaln = BigInteger.valueOf(30).multiply(ICX);
        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put(bnusd.getAddress().toString(), expectedFeesBnusd);
        expectedResult.put(baln.getAddress().toString(), expectedFeesBaln);

        // Act
        dividendScore.invoke(bnusd.account, "tokenFallback", bnusd.getAddress(), expectedFeesBnusd,
                new byte[0]);
        // not yet added 
        dividendScore.invoke(baln.account, "tokenFallback", baln.getAddress(), expectedFeesBaln, new byte[0]);

        // Assert
        assertEquals(expectedResult, dividendScore.call("getDailyFees", day));
    }

    @Test
    void transferDaofundDividends() {
        // Arrange
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        int day = getDay().intValue();
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = expectedFees.multiply(daofundPercentage).divide(ICX);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        // Act
        dividendScore.invoke(owner, "transferDaofundDividends", day, day + 1);

        // Assert
        verify(bnusd.mock).transfer(daofund.getAddress(), expectedDaofundFees, null);
                
        Map<String, BigInteger> zeroDivsMap = new HashMap<>();
        assertEquals(zeroDivsMap, dividendScore.call("getDaoFundDividends", day, day + 1));

    }

    @Test
    void dividendsAt() {
        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("dividendsAt", BigInteger.valueOf(2)));
    }

    @Test
    void dividendsAt_snapshottedDistributions() {

        dividendScore.invoke(governance.account, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistributionPercentage[] dist = new DistributionPercentage[]{new DistributionPercentage(),
                new DistributionPercentage(), new DistributionPercentage()};
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        BigInteger secondChangeDay = (BigInteger) dividendScore.call("getDay");

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(8).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(1).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(1).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        BigInteger thirdChangeDay = (BigInteger) dividendScore.call("getDay");

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(3).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(3).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        Map<String, BigInteger> expectedOutput2 = new HashMap<>();
        expectedOutput2.put("daofund", BigInteger.valueOf(400000000000000000L));
        expectedOutput2.put("baln_holders", BigInteger.valueOf(200000000000000000L));
        expectedOutput2.put("loans", BigInteger.valueOf(400000000000000000L));
        assertEquals(expectedOutput2, dividendScore.call("dividendsAt", secondChangeDay));

        Map<String, BigInteger> expectedOutput3 = new HashMap<>();
        expectedOutput3.put("daofund", BigInteger.valueOf(800000000000000000L));
        expectedOutput3.put("baln_holders", BigInteger.valueOf(100000000000000000L));
        expectedOutput3.put("loans", BigInteger.valueOf(100000000000000000L));
        assertEquals(expectedOutput3, dividendScore.call("dividendsAt", thirdChangeDay));
        sm.getBlock().increase(-4 * DAY);
    }

    @Test
    void delegate() {
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        dividendScore.invoke(governance.account, "delegate", (Object) preps);

        verify(staking.mock).delegate(any());
    }
}
