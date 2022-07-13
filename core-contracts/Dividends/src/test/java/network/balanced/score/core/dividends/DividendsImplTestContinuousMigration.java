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
import org.junit.jupiter.api.function.Executable;

import com.iconloop.score.test.Account;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DividendsImplTestContinuousMigration extends DividendsImplTestBase {

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

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
    }

    @Test
    void migrateDividendsBalnStakers() {
        // Arrange
        BigInteger day = getDay();
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        
        dividendScore.invoke(owner, "setContinuousDividendsDay", day.add(BigInteger.ONE));

        BigInteger expectedFees = BigInteger.TEN.pow(20);
        addBnusdFees(expectedFees);
        BigInteger stakerPercentage = getFeePercentage("baln_holders");
        BigInteger daofundPercentage = getFeePercentage("daofund");

        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        
        BigInteger staker1Balance = BigInteger.valueOf(150).multiply(ICX);
        dividendScore.invoke(balnScore, "updateBalnStake", staker1.getAddress(), BigInteger.ZERO, staker1Balance);

        BigInteger staker2Balance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);
        dividendScore.invoke(balnScore, "updateBalnStake", staker2.getAddress(), BigInteger.ZERO, totalStake);

        BigInteger expectedStaker1Fees = expectedStakingFees.multiply(staker1Balance).divide(totalStake);
        BigInteger expectedStaker2Fees = expectedStakingFees.multiply(staker2Balance).divide(totalStake);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");

        // Act
        mockStakeAt(staker1.getAddress(), day, staker1Balance);
        mockStakeAt(staker2.getAddress(), day, staker2Balance);
        mockStake(staker1.getAddress(), staker1Balance);
        mockStake(staker2.getAddress(), staker2Balance);
        mockTotalSupplyAt(day, totalStake);

        BigInteger expectedDaofundFees = expectedFees.multiply(daofundPercentage).divide(ICX);
        mockDaoFundTranfer(expectedDaofundFees);
        addBnusdFees(expectedFees);

        // Assert
        Map<String, BigInteger> expected_result_staker1 = new HashMap<>();
        expected_result_staker1.put(String.valueOf(bnUSDScore.getAddress()), expectedStaker1Fees);

        Map<String, BigInteger> expected_result_staker2 = new HashMap<>();
        expected_result_staker2.put(String.valueOf(bnUSDScore.getAddress()), expectedStaker2Fees);

        assertEquals(expected_result_staker1, dividendScore.call("getUnclaimedDividends", staker1.getAddress()));
        assertEquals(expected_result_staker2, dividendScore.call("getUnclaimedDividends", staker2.getAddress()));

        assertEquals(expected_result_staker1, dividendScore.call("getUserDividends", staker1.getAddress(), day.intValue(), day.intValue()+1));
        assertEquals(expected_result_staker2, dividendScore.call("getUserDividends", staker2.getAddress(), day.intValue(), day.intValue()+1));

        dividendScore.invoke(staker1, "accumulateDividends", staker1.getAddress(), day.intValue(), day.intValue()+1);
        dividendScore.invoke(staker2, "accumulateDividends", staker2.getAddress(), day.intValue(), day.intValue()+1);

        expected_result_staker1.put(String.valueOf(bnUSDScore.getAddress()), expectedStaker1Fees.multiply(BigInteger.TWO));
        expected_result_staker2.put(String.valueOf(bnUSDScore.getAddress()), expectedStaker2Fees.multiply(BigInteger.TWO));

        assertEquals(expected_result_staker1, dividendScore.call("getUnclaimedDividends", staker1.getAddress()));
        assertEquals(expected_result_staker2, dividendScore.call("getUnclaimedDividends", staker2.getAddress()));

        assertEquals(Map.of(), dividendScore.call("getUserDividends", staker1.getAddress(), day.intValue(), day.intValue()+1));
        assertEquals(Map.of(), dividendScore.call("getUserDividends", staker2.getAddress(), day.intValue(), day.intValue()+1));
    }

    @Test
    void migrateDividendsDaoFund() {
        // Arrange
        int day = getDay().intValue();
        dividendScore.invoke(owner, "setContinuousDividendsDay", getDay().add(BigInteger.ONE));
        dividendScore.invoke(balnScore, "updateBalnStake", owner.getAddress(), BigInteger.ZERO, BigInteger.valueOf(50));
        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger expectedContinuousFees = BigInteger.valueOf(15).pow(20);
        addBnusdFees(expectedFees);
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = expectedFees.multiply(daofundPercentage).divide(ICX);
        BigInteger expectedContinuousDaofundFees = expectedContinuousFees.multiply(daofundPercentage).divide(ICX);

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
    
        // Act
        mockDaoFundTranfer(expectedContinuousDaofundFees);
        addBnusdFees(expectedContinuousFees);
        
        mockDaoFundTranfer(expectedDaofundFees);
        dividendScore.invoke(owner, "transferDaofundDividends", day, day+1);

        // Assert
        contextMock.verify(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), expectedDaofundFees));
        Map<String, BigInteger> zeroDivsMap = new HashMap<>();
        assertEquals(zeroDivsMap, dividendScore.call("getDaoFundDividends", day, day+1));
    }

    @Test
    void distributeAfterMigration() {
        // Arrange
        dividendScore.invoke(owner, "setContinuousDividendsDay", getDay().add(BigInteger.ONE));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        BigInteger snapshotID = (BigInteger) dividendScore.call("getSnapshotId");
        // Act
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
        
        // Assert
        assertEquals(snapshotID, dividendScore.call("getSnapshotId"));       
    }

    private void mockStakeAt(Address user, BigInteger day, BigInteger stake) {
        contextMock.when(() -> Context.call(balnScore.getAddress(), "stakedBalanceOfAt", user, day)).thenReturn(stake);
    }

    private void mockStake(Address user, BigInteger stake) {
        contextMock.when(() -> Context.call(BigInteger.class, balnScore.getAddress(), "stakedBalanceOf", user)).thenReturn(stake);
    }

    private void mockTotalSupplyAt(BigInteger day, BigInteger supply) {
        contextMock.when(() -> Context.call(balnScore.getAddress(), "totalStakedBalanceOfAt", day)).thenReturn(supply);
    }

    private void mockDaoFundTranfer(BigInteger amount) {
        contextMock.when(() -> Context.call(bnUSDScore.getAddress(), "transfer", daoScore.getAddress(), amount)).thenReturn("Token Transferred");
    }
}