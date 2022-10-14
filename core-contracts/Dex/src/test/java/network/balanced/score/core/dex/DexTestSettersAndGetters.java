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

package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;


public class DexTestSettersAndGetters extends DexTestBase {

    @BeforeEach
    public void configureContract() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        setupAddresses();
        super.setup();
    }

    @Test
    void testName() {
        assertEquals(dexScore.call("name"), "Balanced DEX");
    }

    @Test
    @SuppressWarnings("unchecked")
    void setGetFees() {
        // Arrange - fees to be set.
        BigInteger poolLpFee = BigInteger.valueOf(100);
        BigInteger poolBalnFee = BigInteger.valueOf(200);
        BigInteger icxConversionFee = BigInteger.valueOf(300);
        BigInteger icxBalnFee = BigInteger.valueOf(400);

        // Arrange - methods to be called and set specified fee.
        Map<String, BigInteger> fees = Map.of(
                "setPoolLpFee", poolLpFee,
                "setPoolBalnFee", poolBalnFee,
                "setIcxConversionFee", icxConversionFee,
                "setIcxBalnFee", icxBalnFee
        );

        // Arrange - expected result when retrieving fees".
        Map<String, BigInteger> expectedResult = Map.of(
                "icx_total", icxBalnFee.add(icxConversionFee),
                "pool_total", poolBalnFee.add(poolLpFee),
                "pool_lp_fee", poolLpFee,
                "pool_baln_fee", poolBalnFee,
                "icx_conversion_fee", icxConversionFee,
                "icx_baln_fee", icxBalnFee
        );
        Map<String, BigInteger> returnedFees;

        // Act & assert - set all fees and assert that all fee methods are only settable by governance.
        for (Map.Entry<String, BigInteger> fee : fees.entrySet()) {
            dexScore.invoke(governanceScore, fee.getKey(), fee.getValue());
            assertOnlyCallableByGovernance(dexScore, fee.getKey(), fee.getValue());
        }

        // Assert - retrieve all fees and check validity.
        returnedFees = (Map<String, BigInteger>) dexScore.call("getFees");
        assertEquals(expectedResult, returnedFees);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBalanceAndSupply_normalPool() {
        // Arrange - variables.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;
        BigInteger mockBalance = BigInteger.valueOf(100);
        BigInteger mockTotalSupply = BigInteger.valueOf(200);
        Map<String, BigInteger> expectedData = Map.of(
                "_balance", mockBalance,
                "_totalSupply", mockTotalSupply
        );

        // Arrange - Setup dex contract.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Arrange - Mock these calls to stakedLP contract.
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("balanceOf"),
                eq(ownerAccount.getAddress()), eq(poolId))).thenReturn(mockBalance);
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("totalStaked"), eq(poolId))).thenReturn(mockTotalSupply);

        // Assert.
        Map<String, BigInteger> returnedData = (Map<String, BigInteger>) dexScore.call("getBalanceAndSupply",
                poolName, ownerAccount.getAddress());
        assertEquals(expectedData, returnedData);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBalanceAndSupply_sicxIcxPool() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        String poolName = "sICX/ICX";

        Map<String, BigInteger> expectedData = Map.of(
                "_balance", icxValue,
                "_totalSupply", icxValue
        );

        // Act.
        supplyIcxLiquidity(supplier, icxValue);

        // Assert.
        Map<String, BigInteger> returnedData = (Map<String, BigInteger>) dexScore.call("getBalanceAndSupply",
                poolName, supplier.getAddress());
        assertEquals(expectedData, returnedData);
    }

    @Test
    void turnDexOnAndGetDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
        assertEquals(true, dexScore.call("getDexOn"));
        assertOnlyCallableByGovernance(dexScore, "turnDexOn");
    }

    @Test
    void addQuoteCoinAndCheckIfAllowed() {
        // Arrange.
        Address quoteCoin = Account.newScoreAccount(1).getAddress();

        // Act.
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteCoin);

        // Assert.
        Boolean quoteCoinAllowed = (Boolean) dexScore.call("isQuoteCoinAllowed", quoteCoin);
        assertEquals(true, quoteCoinAllowed);
        assertOnlyCallableByGovernance(dexScore, "addQuoteCoin", quoteCoin);
    }

    @Test
    void setGetTimeOffSet() {
        // Arrange.
        BigInteger timeOffset = BigInteger.valueOf(100);

        // Act.
        dexScore.invoke(governanceScore, "setTimeOffset", timeOffset);

        // Assert.
        BigInteger retrievedTimeOffset = (BigInteger) dexScore.call("getTimeOffset");
        assertEquals(timeOffset, retrievedTimeOffset);
        assertOnlyCallableByGovernance(dexScore, "setTimeOffset", timeOffset);
    }

    @Test
    void getIcxBalance() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);

        // Act.
        supplyIcxLiquidity(supplier, value);

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(IcxBalance, value);
    }

    @Test
    void getSicxEarnings() {
        // Supply liquidity to sicx/icx pool.
        // Swap some sicx to icx.
        // Get and verify earnings.
    }

    @Test
    void getDeposit() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);

        // Act.
        depositToken(depositor, bnusdScore, value);

        // Assert.
        BigInteger retrievedValue = (BigInteger) dexScore.call("getDeposit", bnusdScore.getAddress(),
                depositor.getAddress());
        assertEquals(value, retrievedValue);

    }

    @Test
    void getNonce() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedNonce = BigInteger.valueOf(3);

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedNonce = (BigInteger) dexScore.call("getNonce");
        assertEquals(expectedNonce, retrievedNonce);
    }

    @Test
    void getPoolId() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPoolValue = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedPoolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(),
                balnScore.getAddress());
        assertEquals(expectedPoolValue, retrievedPoolId);
    }

    @Test
    void lookupId() {
        // Arrange.
        String namedMarket = "sICX/ICX";
        BigInteger expectedId = BigInteger.ONE;

        // Assert.
        BigInteger retrievedId = (BigInteger) dexScore.call("lookupPid", namedMarket);
        assertEquals(expectedId, retrievedId);
    }

    @Test
    void getPoolTotal() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedBnusdValue = (BigInteger) dexScore.call("getPoolTotal", poolId, bnusdScore.getAddress());
        BigInteger retrievedBalnValue = (BigInteger) dexScore.call("getPoolTotal", poolId, balnScore.getAddress());
        assertEquals(bnusdValue, retrievedBnusdValue);
        assertEquals(balnValue, retrievedBalnValue);
    }

    @Test
    void balanceOf_normalPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger userLpTokenValue = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedUserLpTokenValue = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(),
                poolId);
        assertEquals(userLpTokenValue, retrievedUserLpTokenValue);
    }

    @Test
    void balanceOf_icxSicxPool() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        BigInteger poolId = BigInteger.ONE;

        // Act.
        supplyIcxLiquidity(ownerAccount, value);

        // Assert.
        BigInteger retrievedBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        assertEquals(value, retrievedBalance);
    }

    @Test
    void totalSupply_SicxIcxPool() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        BigInteger poolId = BigInteger.ONE;

        // Act.
        supplyIcxLiquidity(ownerAccount, value);

        // Assert.
        BigInteger totalIcxSupply = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(value, totalIcxSupply);
    }

    @Test
    void totalSupply_normalPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger totalLpTokens = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedTotalLpTokens = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(totalLpTokens, retrievedTotalLpTokens);
    }

    @Test
    void getPoolBaseAndGetPoolQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Address poolBase = (Address) dexScore.call("getPoolBase", BigInteger.TWO);
        Address poolQuote = (Address) dexScore.call("getPoolQuote", BigInteger.TWO);
        assertEquals(poolBase, bnusdScore.getAddress());
        assertEquals(poolQuote, balnScore.getAddress());
    }

    @Test
    void getQuotePriceInBase() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, balnValue);
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call("getQuotePriceInBase", poolId);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBasePriceInQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call("getBasePriceInQuote", poolId);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call("getPrice", poolId);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBalnPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call("getBalnPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getSicxBnusdPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, sicxValue);

        // Act.
        supplyLiquidity(ownerAccount, sicxScore, bnusdScore, sicxValue, bnusdValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call("getSicxBnusdPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBnusdValue_sicxIcxPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger icxValue = BigInteger.valueOf(10).multiply(EXA);
        BigInteger sicxIcxConversionRate = BigInteger.valueOf(10).multiply(EXA);
        String poolName = "sICX/ICX";
        BigInteger expectedPoolValue =
                (computePrice(bnusdValue, sicxValue).multiply(icxValue)).divide(sicxIcxConversionRate);
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();

        // Act.
        supplyLiquidity(ownerAccount, sicxScore, bnusdScore, sicxValue, bnusdValue, false);
        supplyIcxLiquidity(ownerAccount, icxValue);

        // Assert.
        BigInteger poolValue = (BigInteger) dexScore.call("getBnusdValue", poolName);
        assertEquals(expectedPoolValue, poolValue);
    }

    // @Test
    // void getBnusdValue_sicxIsQuote() {
    //     // Arrange.
    //     BigInteger balnValue = BigInteger.valueOf(195).multiply(EXA);
    //     BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
    //     String poolName = "bnUSD/sICX";
    //     BigInteger poolId = BigInteger.TWO;
    //     BigInteger sicxBnusdPrice = BigInteger.valueOf(10).multiply(EXA);
    //     BigInteger expectedValue = (sicxValue.multiply(BigInteger.TWO).multiply(sicxBnusdPrice)).divide(EXA);
    //     doReturn(sicxBnusdPrice).when(dexScoreSpy).getSicxBnusdPrice();

    //     // Act. Why can I not supply with sicx as quote currency? Fails.
    //     dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
    //     supplyLiquidity(ownerAccount, bnusdScore, sicxScore, balnValue, sicxValue, false);

    //     // Assert.
    //     //BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", poolName);
    //     //assertEquals(expectedValue, poolValue);
    // }

    @Test
    void getBnusdValue_bnusdIsQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedValue = BigInteger.valueOf(195).multiply(EXA).multiply(BigInteger.TWO);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, balnScore, bnusdScore, balnValue, bnusdValue, false);

        // Assert.
        BigInteger poolValue = (BigInteger) dexScore.call("getBnusdValue", poolName);
        assertEquals(expectedValue, poolValue);
    }

    @Test
    void getBnusdValue_QuoteNotSupported() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, balnValue, bnusdValue, false);

        // Assert
        BigInteger poolValue = (BigInteger) dexScore.call("getBnusdValue", "bnUSD/BALN");
        assertEquals(BigInteger.ZERO, poolValue);
    }

    @Test
    void getPoolName() {
        // Arrange.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Assert.
        String retrievedPoolName = (String) dexScore.call("getPoolName", poolId);
        assertEquals(poolName, retrievedPoolName);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPoolStats_notSicxIcxPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;
        BigInteger tokenDecimals = BigInteger.valueOf(18);
        BigInteger minQuote = BigInteger.ZERO;
        BigInteger totalLpTokens = new BigInteger("261247009552262626468");

        Map<String, Object> expectedPoolStats = Map.of(
                "base", bnusdValue,
                "quote", balnValue,
                "base_token", bnusdScore.getAddress(),
                "quote_token", balnScore.getAddress(),
                "total_supply", totalLpTokens,
                "price", expectedPrice,
                "name", poolName,
                "base_decimals", tokenDecimals,
                "quote_decimals", tokenDecimals,
                "min_quote", minQuote
        );

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        assertEquals(expectedPoolStats, poolStats);
    }

    @Test
    void getTotalDexAddresses() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger poolId = BigInteger.TWO;

        // Act.
        supplyLiquidity(governanceScore, bnusdScore, balnScore, bnusdValue, balnValue, false);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert
        BigInteger totalDexAddresses = (BigInteger) dexScore.call("totalDexAddresses", BigInteger.TWO);
        assertEquals(BigInteger.TWO, totalDexAddresses);
    }

    @Test
    void permit_OnlyGovernance() {
        // Arrange.
        BigInteger poolId = BigInteger.ONE;
        Boolean permission = true;

        // Assert.
        assertOnlyCallableByGovernance(dexScore, "permit", poolId, permission);
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}