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

package network.balanced.score.core.balancedoracle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.exaPow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class BalancedOracleTest extends BalancedOracleTestBase {
    @BeforeEach
    public void setupContract() throws Exception {
        setup();
        balancedOracle.invoke(governance, "setAdmin", governance.getAddress());
        balancedOracle.invoke(governance, "setDex", dex.getAddress());
        balancedOracle.invoke(governance, "setOracle", oracle.getAddress());
        balancedOracle.invoke(governance, "setStaking", staking.getAddress());
        balancedOracle.invoke(owner, "setLastUpdateThreshold", MICRO_SECONDS_IN_A_DAY);
    }

    @Test
    void getSicxPriceInLoop() {
        // Arrange
        BigInteger sicxRate = BigInteger.TEN.pow(18);
        when(staking.mock.getTodayRate()).thenReturn(sicxRate);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "sICX");
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "sICX");

        // Assert
        assertEquals(sicxRate, priceInLoop);
    }

    @Test
    void getsICXPriceInUSD() {
        // Arrange
        BigInteger sicxRateInLoop = BigInteger.TEN.pow(18);
        BigInteger icxRateInUSD = BigInteger.TEN.pow(17);
        BigInteger sicxRateInUSD = sicxRateInLoop.multiply(icxRateInUSD).divide(EXA);
        when(staking.mock.getTodayRate()).thenReturn(sicxRateInLoop);
        mockUSDRate("ICX", icxRateInUSD);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInUSD", "sICX");
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "sICX");

        // Assert
        assertEquals(sicxRateInUSD, priceInUSD);
    }

    @Test
    void getUSDPriceInUSD() {
        // Act
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "USD");

        // Assert
        assertEquals(EXA, priceInUSD);
    }

    @Test
    void getBTCPriceInUSD() {

        // Arrange
        BigInteger BTCUSDRate = BigInteger.valueOf(1000).multiply(EXA);
        mockUSDRate("BTC", BTCUSDRate);
        // Act
        balancedOracle.invoke(adminAccount, "getPriceInUSD", "BTC");
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "BTC");

        // Assert
        assertEquals(BTCUSDRate, priceInUSD);
    }

    @Test
    void getBnUSDPriceInLoop_useBnUSD() {
        // Arrange
        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", bnusdRate);
        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        // Assert
        assertEquals(bnusdRate, priceInLoop);
    }

    @Test
    void getDexPrice() {
        // Arrange
        String tokenSymbol = "BALN";
        BigInteger poolID = BigInteger.valueOf(3);
        when(dex.mock.getPoolBase(poolID)).thenReturn(baln.getAddress());

        balancedOracle.invoke(governance, "addDexPricedAsset", tokenSymbol, poolID);

        BigInteger icxRateInUSD = BigInteger.TEN.pow(17);
        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        BigInteger bnusdPriceInBaln = BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(17));
        BigInteger expectedBalnpriceInLoop = bnusdRate.multiply(BigInteger.TEN.pow(18)).divide(bnusdPriceInBaln);
        BigInteger expectedBalnpriceInDollars = expectedBalnpriceInLoop.multiply(icxRateInUSD).divide(EXA);

        mockRate("USD", bnusdRate);

        mockUSDRate("ICX", icxRateInUSD);

        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(bnusdPriceInBaln);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", tokenSymbol);
        BigInteger priceInDollars = (BigInteger) balancedOracle.call("getLastPriceInUSD", tokenSymbol);

        // Assert
        assertEquals(expectedBalnpriceInLoop, priceInLoop);
        assertEquals(expectedBalnpriceInDollars, priceInDollars);
    }

    @Test
    void getDexPriceInLoop_IUSDC() {
        // Arrange
        String tokenSymbol = "IUSDC";
        BigInteger poolID = BigInteger.valueOf(4);
        when(dex.mock.getPoolBase(poolID)).thenReturn(iusdc.getAddress());

        balancedOracle.invoke(governance, "addDexPricedAsset", tokenSymbol, poolID);

        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        BigInteger bnusdPriceIUSDC = BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(5));
        BigInteger expectedIUSDCpriceInLoop = bnusdRate.multiply(BigInteger.TEN.pow(6)).divide(bnusdPriceIUSDC);

        mockRate("USD", bnusdRate);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(bnusdPriceIUSDC);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", tokenSymbol);

        // Assert
        assertEquals(expectedIUSDCpriceInLoop, priceInLoop);
    }

    @Test
    void EMA_DexAsset() {
        // Arrange
        String tokenSymbol = "BALN";
        BigInteger poolID = BigInteger.valueOf(3);
        when(dex.mock.getPoolBase(poolID)).thenReturn(baln.getAddress());

        balancedOracle.invoke(governance, "addDexPricedAsset", tokenSymbol, poolID);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(ICX);

        BigInteger alpha = ICX.divide(BigInteger.valueOf(DAY));
        BigInteger decay = ICX.subtract(alpha);
        balancedOracle.invoke(governance, "setDexPriceEMADecay", alpha);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        BigInteger price1 = ICX.multiply(ICX).divide(rate1);
        mockDexRate(poolID, rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger EMA = price1;

        int blockDiff = (int) DAY / 4;
        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate2 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        BigInteger price2 = ICX.multiply(ICX).divide(rate2);
        mockDexRate(poolID, rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger factor = exaPow(decay, blockDiff);
        BigInteger priceDiff = price1.subtract(EMA);
        EMA = price1.subtract(priceDiff.multiply(factor).divide(ICX));

        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        BigInteger price3 = ICX.multiply(ICX).divide(rate3);
        mockDexRate(poolID, rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        factor = exaPow(decay, blockDiff);
        priceDiff = price2.subtract(EMA);
        EMA = price2.subtract(priceDiff.multiply(factor).divide(ICX));

        blockDiff = (int) DAY;
        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockDexRate(poolID, rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        factor = exaPow(decay, blockDiff);
        priceDiff = price3.subtract(EMA);
        EMA = price3.subtract(priceDiff.multiply(factor).divide(ICX));

        // Assert
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", tokenSymbol);

        assertEquals(EMA, priceInLoop);
    }

    @Test
    void testUpdateThreshold() {
        // Arrange
        BigInteger threshold = MICRO_SECONDS_IN_A_DAY;
        BigInteger rate = BigInteger.TEN;

        BigInteger baseUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger quoteUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold);
        mockRate("USD", rate, baseUpdateTime, quoteUpdateTime);

        // Act & Assert
        String expectedErrorMessage = "";
        Executable quoteAboveThreshold = () -> balancedOracle.call("getLastPriceInLoop", "USD");
        expectErrorMessage(quoteAboveThreshold, expectedErrorMessage);

        // Arrange
        baseUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold);
        quoteUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        mockRate("USD", rate, baseUpdateTime, quoteUpdateTime);

        // Act & Assert
        expectedErrorMessage = "";
        Executable baseAboveThreshold = () -> balancedOracle.call("getLastPriceInLoop", "USD");
        expectErrorMessage(baseAboveThreshold, expectedErrorMessage);

        // Act
        baseUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold).add(BigInteger.TEN);
        quoteUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold).add(BigInteger.TEN);
        mockRate("USD", rate, baseUpdateTime, quoteUpdateTime);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "USD");

        // Assert
        assertEquals(rate, priceInLoop);
    }

    private void mockRate(String symbol, BigInteger rate) {
        mockRate(symbol, rate, BigInteger.valueOf(sm.getBlock().getTimestamp()),
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
    }

    private void mockRate(String symbol, BigInteger rate, BigInteger baseUpdateTime, BigInteger quoteUpdateTime) {
        Map<String, Object> priceData = Map.of(
                "rate", rate,
                "last_update_base", baseUpdateTime,
                "last_update_quote", quoteUpdateTime
        );
        when(oracle.mock.get_reference_data(symbol, "ICX")).thenReturn(priceData);
    }

    private void mockUSDRate(String symbol, BigInteger rate) {
        mockUSDRate(symbol, rate, BigInteger.valueOf(sm.getBlock().getTimestamp()),
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
    }

    private void mockUSDRate(String symbol, BigInteger rate, BigInteger baseUpdateTime, BigInteger quoteUpdateTime) {
        Map<String, Object> priceData = Map.of(
                "rate", rate,
                "last_update_base", baseUpdateTime,
                "last_update_quote", quoteUpdateTime
        );
        when(oracle.mock.get_reference_data(symbol, "USD")).thenReturn(priceData);
    }

    private void mockDexRate(BigInteger poolID, BigInteger rate) {
        mockRate("USD", ICX);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(rate);
    }
}