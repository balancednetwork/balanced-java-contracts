/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import com.iconloop.score.test.Account;
import foundation.icon.xcall.NetworkAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DividendsImplTestContinuousDividends extends DividendsImplTestBase {

    private final BigInteger batchSize = BigInteger.TWO;


    @BeforeEach
    void setup() throws Exception {
        sm.getBlock().increase(2 * DAY);

        setupBase();
        dividendScore.invoke(governance.account, "addAcceptedTokens", bnUSD.getAddress());
        dividendScore.invoke(governance.account, "setDividendsBatchSize", batchSize);

        BigInteger day = getDay();
        dividendScore.invoke(governance.account, "setDividendsOnlyToStakedBalnDay", day.add(BigInteger.ONE));
        sm.getBlock().increase(DAY);
        dividendScore.invoke(owner, "distribute");
    }

    @Test
    void claimDividends_bbaln() {
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        String staker1String = NetworkAddress.valueOf(staker1.getAddress().toString(), NATIVE_NID).toString();
        String staker2String = NetworkAddress.valueOf(staker2.getAddress().toString(), NATIVE_NID).toString();

        when(baln.mock.stakedBalanceOf(staker1.getAddress())).thenReturn(BigInteger.ZERO);
        when(baln.mock.stakedBalanceOf(staker2.getAddress())).thenReturn(BigInteger.ZERO);

        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1Balance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2Balance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalStake = BigInteger.valueOf(200).multiply(ICX);

        mockBBalnBalanceOf(staker2.getAddress(), staker2Balance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1Balance);

        // Act
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker1String, staker1Balance);
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker2String, staker2Balance);

        BigInteger expectedFees = BigInteger.TEN.pow(20);
        BigInteger expectedStakingFees = expectedFees.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(expectedFees);
        BigInteger staker1ExpectedFees = expectedStakingFees.multiply(staker1Balance).divide(totalStake);
        BigInteger staker2ExpectedFees = expectedStakingFees.multiply(staker2Balance).divide(totalStake);

        // Assert
        mockStake(staker1.getAddress(), staker1Balance);
        mockStake(staker2.getAddress(), staker2Balance);
        mockBBalnBalanceOf(staker2.getAddress(), staker2Balance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1Balance);


        dividendScore.invoke(staker1, "claimDividends");
        dividendScore.invoke(staker2, "claimDividends");

        verify(bnUSD.mock).transfer(staker1.getAddress(), staker1ExpectedFees, new byte[0]);
        verify(bnUSD.mock).transfer(staker2.getAddress(), staker2ExpectedFees, new byte[0]);

        assertEquals(Map.of(bnUSD.getAddress().toString(), BigInteger.ZERO), dividendScore.call(
                "getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(bnUSD.getAddress().toString(), BigInteger.ZERO), dividendScore.call(
                "getUnclaimedDividends", staker2.getAddress()));
    }

    @Test
    void onKick_bbaln() {
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();

        when(baln.mock.stakedBalanceOf(staker1.getAddress())).thenReturn(BigInteger.ZERO);
        when(baln.mock.stakedBalanceOf(staker2.getAddress())).thenReturn(BigInteger.ZERO);

        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1BBalnBalance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2BBalnBalance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalSupply = BigInteger.valueOf(200).multiply(ICX);

        mockBBalnBalanceOf(staker2.getAddress(), staker2BBalnBalance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1BBalnBalance);
        String staker1String = NetworkAddress.valueOf(staker1.getAddress().toString(), NATIVE_NID).toString();
        String staker2String = NetworkAddress.valueOf(staker2.getAddress().toString(), NATIVE_NID).toString();

        // Act
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker1String, staker1BBalnBalance);
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker2String, staker2BBalnBalance);

        BigInteger loanAmount = BigInteger.TEN.pow(20);
        BigInteger dividendsForBBalnUser = loanAmount.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(loanAmount);

        BigInteger user1AccruedDividends = dividendsForBBalnUser.multiply(staker1BBalnBalance).divide(totalSupply);
        BigInteger user2AccruedDividends = dividendsForBBalnUser.multiply(staker2BBalnBalance).divide(totalSupply);

        // Assert
        mockStake(staker1.getAddress(), BigInteger.ZERO);
        mockStake(staker2.getAddress(), BigInteger.ZERO);
        mockBBalnBalanceOf(staker2.getAddress(), staker2BBalnBalance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1BBalnBalance);

        dividendScore.invoke(bBaln.account, "onKick", staker1String);

        dividendScore.invoke(staker1, "claimDividends");
        dividendScore.invoke(staker2, "claimDividends");


        verify(bnUSD.mock).transfer(staker1.getAddress(), user1AccruedDividends, new byte[0]);
        verify(bnUSD.mock).transfer(staker2.getAddress(), user2AccruedDividends, new byte[0]);

        assertEquals(Map.of(bnUSD.getAddress().toString(), BigInteger.ZERO), dividendScore.call(
                "getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(bnUSD.getAddress().toString(), BigInteger.ZERO), dividendScore.call(
                "getUnclaimedDividends", staker2.getAddress()));
    }

    @Test
    void onBalanceUpdate_bbaln() {
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        String staker1String = NetworkAddress.valueOf(staker1.getAddress().toString(), NATIVE_NID).toString();
        String staker2String = NetworkAddress.valueOf(staker2.getAddress().toString(), NATIVE_NID).toString();

        when(baln.mock.stakedBalanceOf(staker1.getAddress())).thenReturn(BigInteger.ZERO);
        when(baln.mock.stakedBalanceOf(staker2.getAddress())).thenReturn(BigInteger.ZERO);

        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1BBalnBalance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2BBalnBalance = BigInteger.valueOf(50).multiply(ICX);
        BigInteger totalSupply = BigInteger.valueOf(200).multiply(ICX);

        mockBBalnBalanceOf(staker2.getAddress(), staker2BBalnBalance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1BBalnBalance);

        // Act
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker1String, staker1BBalnBalance);
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker2String, staker2BBalnBalance);

        BigInteger loanAmount = BigInteger.TEN.pow(20);

        BigInteger dividendsForBBalnUser = loanAmount.multiply(stakerPercentage).divide(ICX);
        addBnusdFeesAndMockDaoFund(loanAmount);
        BigInteger staker1ExpectedFees = dividendsForBBalnUser.multiply(staker1BBalnBalance).divide(totalSupply);
        BigInteger staker2ExpectedFees = dividendsForBBalnUser.multiply(staker2BBalnBalance).divide(totalSupply);

        assertEquals(Map.of(bnUSD.getAddress().toString(), staker1ExpectedFees), dividendScore.call(
                "getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(bnUSD.getAddress().toString(), staker2ExpectedFees), dividendScore.call(
                "getUnclaimedDividends", staker2.getAddress()));
    }

    @Test
    void getUnclaimedDividends_bbaln() {

        // test unclaimed dividends after multiple tx.
        // Arrange
        Account staker1 = sm.createAccount();
        Account staker2 = sm.createAccount();
        String staker1String = NetworkAddress.valueOf(staker1.getAddress().toString(), NATIVE_NID).toString();
        String staker2String = NetworkAddress.valueOf(staker2.getAddress().toString(), NATIVE_NID).toString();

        when(baln.mock.stakedBalanceOf(staker1.getAddress())).thenReturn(BigInteger.ZERO);
        when(baln.mock.stakedBalanceOf(staker2.getAddress())).thenReturn(BigInteger.ZERO);

        BigInteger stakerPercentage = getFeePercentage("baln_holders");

        BigInteger staker1BBalnBalance = BigInteger.valueOf(150).multiply(ICX);
        BigInteger staker2BBalnBalance = BigInteger.valueOf(50).multiply(ICX);

        mockBBalnBalanceOf(staker2.getAddress(), staker2BBalnBalance);
        mockBBalnBalanceOf(staker1.getAddress(), staker1BBalnBalance);

        // Act
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker1String, staker1BBalnBalance);
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker2String, staker2BBalnBalance);

        BigInteger loanAmount = BigInteger.TEN.pow(20);
        addBnusdFeesAndMockDaoFund(loanAmount);

        // total supply of bbaln
        BigInteger totalSupply = staker1BBalnBalance.add(staker2BBalnBalance);

        // expected fees for bbaln category
        BigInteger expectedFees = loanAmount.multiply(stakerPercentage).divide(ICX);
        BigInteger weight = expectedFees.multiply(ICX).divide(totalSupply);

        // expected accrued dividends
        BigInteger staker1AccruedDividends = weight.multiply(staker1BBalnBalance).divide(ICX);
        BigInteger staker2AccruedDividends = weight.multiply(staker2BBalnBalance).divide(ICX);

        // getUnclaimedDividends of SCORE should be equal to the dividends of the user
        assertEquals(Map.of(bnUSD.getAddress().toString(), staker1AccruedDividends), dividendScore.call(
                "getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(bnUSD.getAddress().toString(), staker2AccruedDividends), dividendScore.call(
                "getUnclaimedDividends", staker2.getAddress()));

        // new bbaln balance of the users
        BigInteger newStaker1BBalnBalance = BigInteger.valueOf(350).multiply(ICX);
        BigInteger newStaker2BBalnBalance = BigInteger.valueOf(100).multiply(ICX);

        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker1String, newStaker1BBalnBalance);
        dividendScore.invoke(bBaln.account, "onBalanceUpdate", staker2String, newStaker2BBalnBalance);
        addBnusdFees(loanAmount);

        // new total supply for bbaln
        totalSupply = newStaker1BBalnBalance.add(newStaker2BBalnBalance);
        expectedFees = loanAmount.multiply(stakerPercentage).divide(ICX);
        BigInteger addedWeight = expectedFees.multiply(ICX).divide(totalSupply);

        // new accrued dividends for users
        BigInteger newStaker1AccruedDividends = addedWeight.multiply(newStaker1BBalnBalance).divide(ICX);
        BigInteger newStaker2AccruedDividends = addedWeight.multiply(newStaker2BBalnBalance).divide(ICX);

        assertEquals(Map.of(bnUSD.getAddress().toString(),
                staker1AccruedDividends.add(newStaker1AccruedDividends)), dividendScore.call(
                "getUnclaimedDividends", staker1.getAddress()));
        assertEquals(Map.of(bnUSD.getAddress().toString(),
                staker2AccruedDividends.add(newStaker2AccruedDividends)), dividendScore.call(
                "getUnclaimedDividends", staker2.getAddress()));
    }

    private void addBnusdFeesAndMockDaoFund(BigInteger amount) {
        BigInteger daofundPercentage = getFeePercentage("daofund");
        BigInteger expectedDaofundFees = amount.multiply(daofundPercentage).divide(ICX);
        addBnusdFees(amount);

        verify(bnUSD.mock, times(scoreCount)).transfer(daofund.getAddress(), expectedDaofundFees, new byte[0]);
    }

    private void mockStake(Address user, BigInteger stake) {
        when(baln.mock.stakedBalanceOf(user)).thenReturn(stake);
    }

    private void mockBBalnBalanceOf(Address user, BigInteger stake) {
        String networkAddress = NetworkAddress.valueOf(user.toString(), NATIVE_NID).toString();
        when(bBaln.mock.xBalanceOf(eq(networkAddress), any(BigInteger.class))).thenReturn(stake);
    }

}