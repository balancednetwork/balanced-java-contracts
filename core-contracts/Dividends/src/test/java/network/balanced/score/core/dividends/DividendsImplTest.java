/*
 * Copyright (c) 2022 Balanced.network.
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
import static org.mockito.Mockito.*;

class DividendsImplTest extends DividendsImplTestBase {

    private BigInteger batchSize = BigInteger.TWO;
    @BeforeEach
    void setup() throws Exception {
        sm.getBlock().increase(2 * DAY);

        setupBase();
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDaofund", daoScore.getAddress());
        dividendScore.invoke(admin, "setBaln", balnScore.getAddress());

        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());
        dividendScore.invoke(admin, "addAcceptedTokens", bnUSDScore.getAddress());
        dividendScore.invoke(admin, "setDividendsBatchSize", batchSize);
        dividendScore.invoke(admin, "setDistributionActivationStatus", true);

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("getTimeOffset"))).thenReturn(BigInteger.TWO);

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        asset.put("bnUSD", String.valueOf(bnUSDScore.getAddress()));

        contextMock.when(getAssetTokens).thenReturn(asset);

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

        Executable contractCallCase3 = () -> transferDaofundDiv(day -1, day - 1);
        String expectedErrorMessageCase3 = "Reverted(0): Balanced Dividends: Start must not be greater than or equal to end.";
        expectErrorMessage(contractCallCase3, expectedErrorMessageCase3);

        Executable contractCallCase4 = () -> transferDaofundDiv(day - 1, day - 2);
        String expectedErrorMessageCase4 = "Reverted(0): Balanced Dividends: Start must not be greater than or equal to end.";
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

        Executable endAfterContinuous = () -> transferDaofundDiv(continuousDay-1, continuousDay+1);
        String expectedErrorMessageEndAfterContinuous = "Reverted(0): Balanced Dividends: Invalid value of end provided.";
        expectErrorMessage(endAfterContinuous, expectedErrorMessageEndAfterContinuous);

        Executable startAfterContinuous = () -> transferDaofundDiv(continuousDay, continuousDay+1);
        String expectedErrorMessageStartAfterContinuous = "Reverted(0): Balanced Dividends: Invalid value of start provided.";
        expectErrorMessage(startAfterContinuous, expectedErrorMessageStartAfterContinuous);
    }

    @Test
    void getBalance() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        contextMock.when(getAssetTokens).thenReturn(asset);
        contextMock.when(balanceOf).thenReturn(BigInteger.TEN.pow(4));

        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put("baln", BigInteger.TEN.pow(4));
        expectedResult.put("ICX", BigInteger.ZERO);

        assertEquals(expectedResult, dividendScore.call("getBalances"));
    }


    @Test
    void getDailyFees() {
        // Arrange
        BigInteger day = getDay();
        BigInteger expectedFees =  BigInteger.TWO.multiply(BigInteger.TEN.pow(19));
        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put(bnUSDScore.getAddress().toString(), expectedFees);

        // Act
        addBnusdFees(expectedFees);

        // Assert
        assertEquals(expectedResult, dividendScore.call("getDailyFees", day));
    }

    @Test
    void tokenFallback() {
        // Arrange
        BigInteger day = getDay();
        BigInteger expectedFeesBnusd =  BigInteger.valueOf(20).multiply(ICX);
        BigInteger expectedFeesBaln =  BigInteger.valueOf(30).multiply(ICX);
        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put(bnUSDScore.getAddress().toString(), expectedFeesBnusd);
        expectedResult.put(balnScore.getAddress().toString(), expectedFeesBaln);

        // Act
        dividendScore.invoke(bnUSDScore.getAccount(), "tokenFallback", bnUSDScore.getAddress(), expectedFeesBnusd, new byte[0]);
        // not yet added 
        dividendScore.invoke(balnScore, "tokenFallback", balnScore.getAddress(), expectedFeesBaln, new byte[0]);

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
        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), expectedDaofundFees)).thenReturn("Token Transferred");

        // Act
        dividendScore.invoke(owner, "transferDaofundDividends", day, day+1);

        // Assert
        contextMock.verify(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), expectedDaofundFees));
        Map<String, BigInteger> zeroDivsMap = new HashMap<>();
        assertEquals(zeroDivsMap, dividendScore.call("getDaoFundDividends", day, day+1));

    }

    @Test
    void claim() {
        // Arrange
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        int day = getDay().intValue();
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn("Token Transferred");

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()), any(BigInteger.class))).thenReturn("Token Transferred");

        // Act
        dividendScore.invoke(owner, "claim", day, day+1);

        // Assert
        contextMock.verify(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()), any(BigInteger.class)));
        Map<String, BigInteger> zeroDivsMap = new HashMap<>();
        assertEquals(zeroDivsMap, dividendScore.call("getUserDividends", owner.getAddress(), day, day+1));
    }

    @Test
    void getUserDividends() {
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        int day = getDay().intValue();
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);    

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), expectedStakingFees);

        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), day, day+1));
    }

    @Test
    void getUserDividendsOnlyToStaked() {
        // Arrange
        BigInteger day = getDay();
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", day.add(BigInteger.ONE));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        day = getDay();
        
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);

        BigInteger ownerStake = BigInteger.valueOf(100).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);
        BigInteger expectedOwnerFees = expectedStakingFees.multiply(ownerStake).divide(totalStake);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        // Act
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(ownerStake);
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(totalStake);

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));


        // Assert
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), expectedOwnerFees);

        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), day.intValue(), day.intValue()+1));
    }

    @Test
    void getUserDividendsOnlyToStaked_MultipleDays() {
        // Arrange
        BigInteger day = getDay();
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", day.add(BigInteger.ONE));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        day = getDay();
        
        BigInteger dayOneFees = BigInteger.TEN.pow(20);
        addBnusdFees(dayOneFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger ownerStake = BigInteger.valueOf(100).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(ownerStake);
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(totalStake);


        BigInteger expectedStakingFees = dayOneFees.multiply(stakerPercentage).divide(ICX);
        BigInteger expectedOwnerFees = expectedStakingFees.multiply(ownerStake).divide(totalStake);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger dayTwoFees = BigInteger.TEN.pow(19);
        addBnusdFees(dayTwoFees);
        expectedStakingFees = dayTwoFees.multiply(stakerPercentage).divide(ICX);
        expectedOwnerFees = expectedOwnerFees.add(expectedStakingFees.multiply(ownerStake).divide(totalStake));

        // Act
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
 
        // Assert
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), expectedOwnerFees);

        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), day.intValue(), day.intValue()+2));
    }
    @Test
    void getDaoFundDividends() {
        // Arrange
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
 
        int day = getDay().intValue();
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = expectedFees.multiply(daofundPercentage).divide(ICX);

        // Act
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
 
        // Assert       
        Map<String, BigInteger> result = new HashMap<>();
        result.put(bnUSDScore.getAddress().toString(), expectedDaofundFees);

        assertEquals(result, dividendScore.call("getDaoFundDividends", day, day+1));
    }

    @Test
    void getDaoFundDividends_MultipleDays() {
        // Arrange
        dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(4));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
 
        int day = getDay().intValue();
        BigInteger dayOneFees = BigInteger.TEN.pow(20);
        addBnusdFees(dayOneFees);
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = dayOneFees.multiply(daofundPercentage).divide(ICX);

        // Act
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger dayTwoFees = BigInteger.TEN.pow(21);
        addBnusdFees(dayTwoFees);
        expectedDaofundFees = expectedDaofundFees.add(dayTwoFees.multiply(daofundPercentage).divide(ICX));
 
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger dayThreeFees = BigInteger.TEN.pow(19);
        addBnusdFees(dayThreeFees);
        expectedDaofundFees = expectedDaofundFees.add(dayThreeFees.multiply(daofundPercentage).divide(ICX));
 
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        // Assert       
        Map<String, BigInteger> result = new HashMap<>();
        result.put(bnUSDScore.getAddress().toString(), expectedDaofundFees);

        assertEquals(result, dividendScore.call("getDaoFundDividends", day, day+3));
    }

    @Test
    void getDaoFundDividends_partialClaimedInTheFuture() {
        // Arrange
        dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(4));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
 
        int day = getDay().intValue();
        BigInteger dayOneFees = BigInteger.TEN.pow(20);
        addBnusdFees(dayOneFees);
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = dayOneFees.multiply(daofundPercentage).divide(ICX);

       
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger dayTwoFees = BigInteger.TEN.pow(21);
        addBnusdFees(dayTwoFees);
        expectedDaofundFees = expectedDaofundFees.add(dayTwoFees.multiply(daofundPercentage).divide(ICX));
 
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger dayThreeFees = BigInteger.TEN.pow(19);
        addBnusdFees(dayThreeFees);
        BigInteger expectedDayThreeDaofundFees = dayThreeFees.multiply(daofundPercentage).divide(ICX);
 
        // Act
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), expectedDayThreeDaofundFees)).thenReturn("Token Transferred");
        dividendScore.invoke(owner, "transferDaofundDividends", day+2, day+3);

        // Assert       
        Map<String, BigInteger> result = new HashMap<>();
        result.put(bnUSDScore.getAddress().toString(), expectedDaofundFees);

        assertEquals(result, dividendScore.call("getDaoFundDividends", day, day+3));
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
        
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistributionPercentage[] dist = new DistributionPercentage[]{new DistributionPercentage(), new DistributionPercentage(), new DistributionPercentage()};
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
        sm.getBlock().increase(-4*DAY);
    }

    @Test
    void delegate(){
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(eq(governanceScore.getAddress()), eq("getAddresses"))).thenReturn(Map.of("staking", stakingScore.getAddress()));
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn("Staking delegate called");
        dividendScore.invoke(governanceScore, "delegate", (Object) preps);
    }
}
