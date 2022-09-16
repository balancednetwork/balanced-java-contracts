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

import network.balanced.score.lib.structs.PrepDelegations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

class DividendsImplTest extends DividendsImplTestBase {

    private final BigInteger batchSize = BigInteger.TWO;

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
    void claim() {
        dividendScore.invoke(owner, "setBBalnDay", BigInteger.valueOf(100L));

        // Arrange
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        int day = getDay().intValue();
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn("Token Transferred");

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class),
                any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"),
                any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class),
                any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class),
                any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class),
                any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()),
                any(BigInteger.class))).thenReturn("Token Transferred");

        // Act
        dividendScore.invoke(owner, "claimDividends");

        // Assert
        contextMock.verify(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()),
                any(BigInteger.class)));
        Map<String, BigInteger> zeroDivsMap = new HashMap<>();
        assertEquals(zeroDivsMap, dividendScore.call("getUnclaimedDividends", owner.getAddress()));
    }

    @Test
    void getUserDividendsOnlyToStaked() {
        dividendScore.invoke(owner, "setBBalnDay", BigInteger.valueOf(100L));

        // Arrange
        BigInteger day = getDay();
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", day.add(BigInteger.ONE));
        sm.getBlock().increase(DAY);

        day = getDay();

        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);
        dividendScore.invoke(balnScore, "updateBalnStake", owner.getAddress(), BigInteger.ZERO, totalStake);

        addBnusdFees(expectedFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);

        // Assert
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), expectedStakingFees);

        assertEquals(expected_result, dividendScore.call("getUnclaimedDividends", owner.getAddress()));
    }

    @Test
    void dividendsAt() {
        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("dividendsAt", BigInteger.valueOf(2)));
    }

    @Test
    void delegate() {
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking")).thenReturn(stakingScore.getAddress());
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn(
                "Staking delegate called");
        dividendScore.invoke(governanceScore, "delegate", (Object) preps);

        contextMock.verify(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking"));
        contextMock.verify(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any()));
    }

    @Test
    void getUserDividends() {
        dividendScore.invoke(owner, "setBBalnDay", BigInteger.valueOf(100L));

        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger stakeBalance = BigInteger.valueOf(200).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);

        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn("Token Transferred");
        mockStake(owner.getAddress(),stakeBalance);
        dividendScore.invoke(balnScore, "updateBalnStake", owner.getAddress(), BigInteger.ZERO, totalStake);

        addBnusdFees(expectedFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        BigInteger stakerExpectedFees = expectedStakingFees.multiply(stakeBalance).divide(totalStake);

        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), stakerExpectedFees);
        assertEquals(expected_result, dividendScore.call("getUnclaimedDividends", owner.getAddress()));
    }

    @Test
    void setBBalnDay() {
        dividendScore.invoke(owner, "setBBalnDay", BigInteger.TWO);
        assertEquals(BigInteger.TWO,dividendScore.call("getBBalnDay"));
    }

    @Test
    void setBBalnAddress() {
        dividendScore.invoke(owner, "setBBalnAddress", bBalnScore.getAddress());
        assertEquals(bBalnScore.getAddress(),dividendScore.call("getBBalnAddress"));
    }

    private void mockStake(Address user, BigInteger stake) {
        contextMock.when(() -> Context.call(BigInteger.class, balnScore.getAddress(), "stakedBalanceOf", user)).thenReturn(stake);
    }
}
