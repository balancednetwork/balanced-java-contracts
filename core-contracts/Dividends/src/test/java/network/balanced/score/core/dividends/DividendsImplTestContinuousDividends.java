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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iconloop.score.test.Account;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DividendsImplTestContinuousDividends extends DividendsImplTestBase {

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

        BigInteger day = getDay();
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", day.add(BigInteger.ONE));
        dividendScore.invoke(owner, "setContinuousDividendsDay", day.add(BigInteger.ONE));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
    }

    @Test
    void updateBalnStake() {
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1Balance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2Balance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);

        BigInteger staker1ExpectedFees = BigInteger.ZERO;
        BigInteger staker2ExpectedFees = BigInteger.ZERO;

        // Act
        dividendScore.invoke(balnScore, "updateBalnStake", staker1.getAddress(), BigInteger.ZERO, staker1Balance);
        
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        staker1ExpectedFees = staker1ExpectedFees.add(expectedStakingFees);

        dividendScore.invoke(balnScore, "updateBalnStake", staker2.getAddress(), BigInteger.ZERO, totalStake);
        
        expectedFees = BigInteger.valueOf(30).pow(20);
        expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        staker1ExpectedFees = staker1ExpectedFees.add(expectedStakingFees.multiply(staker1Balance).divide(totalStake));
        staker2ExpectedFees = staker2ExpectedFees.add(expectedStakingFees.multiply(staker2Balance).divide(totalStake));

        BigInteger staker2Increase = BigInteger.valueOf(100).multiply(ICX);
        dividendScore.invoke(balnScore, "updateBalnStake", staker2.getAddress(), staker2Balance, totalStake.add(staker2Increase));
        staker2Balance = staker2Balance.add(staker2Increase);

        expectedFees = BigInteger.valueOf(10).pow(22);
        expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        staker1ExpectedFees = staker1ExpectedFees.add(expectedStakingFees.multiply(staker1Balance).divide(totalStake.add(staker2Increase)));
        staker2ExpectedFees = staker2ExpectedFees.add(expectedStakingFees.multiply(staker2Balance).divide(totalStake.add(staker2Increase)));


        dividendScore.invoke(balnScore, "updateBalnStake", staker1.getAddress(), staker1Balance, totalStake.add(staker2Increase).subtract(staker1Balance));
        staker1Balance = BigInteger.ZERO;

        expectedFees = BigInteger.TEN.pow(19);
        expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        staker2ExpectedFees = staker2ExpectedFees.add(expectedStakingFees);

        // Assert
        Map<String, BigInteger> expected_result_staker1 = new HashMap<>();
        expected_result_staker1.put(String.valueOf(bnUSDScore.getAddress()), staker1ExpectedFees);

        Map<String, BigInteger> expected_result_staker2 = new HashMap<>();
        expected_result_staker2.put(String.valueOf(bnUSDScore.getAddress()), staker2ExpectedFees);

        mockStake(staker1.getAddress(), staker1Balance);
        mockStake(staker2.getAddress(), staker2Balance);
        assertEquals(expected_result_staker1, dividendScore.call("getUnclaimedDividends", staker1.getAddress()));
        assertEquals(expected_result_staker2, dividendScore.call("getUnclaimedDividends", staker2.getAddress()));
    }

    @Test
    void claimDividends() {
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1Balance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2Balance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);

        // Act
        dividendScore.invoke(balnScore, "updateBalnStake", staker1.getAddress(), BigInteger.ZERO, staker1Balance);
        dividendScore.invoke(balnScore, "updateBalnStake", staker2.getAddress(), BigInteger.ZERO, totalStake);
        
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        BigInteger staker1ExpectedFees = expectedStakingFees.multiply(staker1Balance).divide(totalStake);
        BigInteger staker2ExpectedFees = expectedStakingFees.multiply(staker2Balance).divide(totalStake);

        // Assert
        mockStake(staker1.getAddress(), staker1Balance);
        mockStake(staker2.getAddress(), staker2Balance);

        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", staker1.getAddress(), staker1ExpectedFees)).thenReturn("Token Transferred");
        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", staker2.getAddress(), staker2ExpectedFees)).thenReturn("Token Transferred");
        dividendScore.invoke(staker1, "claimDividends");
        dividendScore.invoke(staker2, "claimDividends");
        contextMock.verify(() -> Context.call(bnUSDScore.getAddress(), "transfer", staker1.getAddress(), staker1ExpectedFees));
        contextMock.verify(() -> Context.call(bnUSDScore.getAddress(), "transfer", staker2.getAddress(), staker2ExpectedFees));

        assertEquals(Map.of(), dividendScore.call("getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(), dividendScore.call("getUnclaimedDividends", staker2.getAddress()));
    }

    private void addBnusdFeesAndMockDaoFund(BigInteger amount) {
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = amount.multiply(daofundPercentage).divide(ICX);
        mockDaoFundTranfer(expectedDaofundFees);
        addBnusdFees(amount);
    }

    private void mockStake(Address user, BigInteger stake) {
        contextMock.when(() -> Context.call(BigInteger.class, balnScore.getAddress(), "stakedBalanceOf", user)).thenReturn(stake);
    }

    private void mockDaoFundTranfer(BigInteger amount) {
        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), amount)).thenReturn("Token Transferred");
    }
}