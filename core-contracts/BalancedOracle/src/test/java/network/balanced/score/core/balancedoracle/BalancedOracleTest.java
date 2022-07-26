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

import static network.balanced.score.lib.utils.Math.exaPow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BalancedOracleTest extends BalancedOracleTestBase {
    @BeforeEach
    public void setupContract() throws Exception {
        setup();
        balancedOracle.invoke(governance, "setAdmin", governance.getAddress());
        balancedOracle.invoke(governance, "setDex", dex.getAddress());
        balancedOracle.invoke(governance, "setOracle", oracle.getAddress());
        balancedOracle.invoke(governance, "setStaking", staking.getAddress());
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
    void getUSDPriceInLoop() {
        // Arrange
        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        Map<String, Object> priceData = Map.of("rate", bnusdRate);
        when(oracle.mock.get_reference_data("USD", "ICX")).thenReturn(priceData);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "USD");
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "USD");

        // Assert
        assertEquals(bnusdRate, priceInLoop);
    }

    @Test
    void getBnUSDPriceInLoop_useBnUSD() {
        // Arrange
        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        Map<String, Object> priceData = Map.of("rate", bnusdRate);
        when(oracle.mock.get_reference_data("USD", "ICX")).thenReturn(priceData);

        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        // Assert
        assertEquals(bnusdRate, priceInLoop);
    }

    @Test
    void getDexPriceInLoop() {
        // Arrange
        String tokenSymbol = "BALN";
        BigInteger poolID = BigInteger.valueOf(3);
        balancedOracle.invoke(governance, "addDexPricedAsset", tokenSymbol, poolID);

        BigInteger bnusdRate = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        BigInteger balnPriceInBnusd = BigInteger.valueOf(20).multiply(BigInteger.TEN.pow(17));
        BigInteger expectedBalnpriceInLoop = balnPriceInBnusd.multiply(bnusdRate).divide(BigInteger.TEN.pow(18));

        Map<String, Object> priceData = Map.of("rate", bnusdRate);
        when(oracle.mock.get_reference_data("USD", "ICX")).thenReturn(priceData);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(balnPriceInBnusd);
        
        // Act
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "BALN");
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "BALN");

        // Assert
        assertEquals(expectedBalnpriceInLoop, priceInLoop);
    }

    @Test
    void EMA_OracleAsset() {
        // Arrange
        BigInteger alpha = ICX.divide(BigInteger.valueOf(DAY));
        BigInteger decay = ICX.subtract(alpha);
        balancedOracle.invoke(governance, "setOraclePriceEMADecay", alpha);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger EMA = rate1;

        int blockDiff = (int)DAY/4;
        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate2 = BigInteger.valueOf(8).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger factor = exaPow(decay, blockDiff);
        BigInteger priceDiff = rate1.subtract(EMA);
        EMA = rate1.subtract(priceDiff.multiply(factor).divide(ICX));

        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        factor = exaPow(decay, blockDiff);
        priceDiff = rate2.subtract(EMA);
        EMA = rate2.subtract(priceDiff.multiply(factor).divide(ICX));
        
        blockDiff = (int)DAY;
        sm.getBlock().increase(blockDiff-1);

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        factor = exaPow(decay, blockDiff);
        priceDiff = rate3.subtract(EMA);
        EMA = rate3.subtract(priceDiff.multiply(factor).divide(ICX));
        
        // Assert
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        assertEquals(EMA, priceInLoop);
    }

    @Test
    void EMA_DexAsset() {
        // Arrange
        String tokenSymbol = "BALN";
        BigInteger poolID = BigInteger.valueOf(3);
        balancedOracle.invoke(governance, "addDexPricedAsset", tokenSymbol, poolID);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(ICX);

        BigInteger alpha = ICX.divide(BigInteger.valueOf(DAY));
        BigInteger decay = ICX.subtract(alpha);
        balancedOracle.invoke(governance, "setDexPriceEMADecay", alpha);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockDexRate(poolID, rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger EMA = rate1;

        int blockDiff = (int)DAY/4;
        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate2 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockDexRate(poolID, rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        BigInteger factor = exaPow(decay, blockDiff);
        BigInteger priceDiff = rate1.subtract(EMA);
        EMA = rate1.subtract(priceDiff.multiply(factor).divide(ICX));
        
         blockDiff = (int)DAY/4;
        sm.getBlock().increase(blockDiff - 1);

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        mockDexRate(poolID, rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        factor = exaPow(decay, blockDiff);
        priceDiff = rate2.subtract(EMA);
        EMA = rate2.subtract(priceDiff.multiply(factor).divide(ICX));
        
        blockDiff = (int)DAY;
        sm.getBlock().increase(blockDiff-1);

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockDexRate(poolID, rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", tokenSymbol);
        factor = exaPow(decay, blockDiff);
        priceDiff = rate3.subtract(EMA);
        EMA = rate3.subtract(priceDiff.multiply(factor).divide(ICX));
        
        // Assert
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", tokenSymbol);

        assertEquals(EMA, priceInLoop);
    }

    private void mockRate(String symbol, BigInteger rate) {
        Map<String, Object> priceData = Map.of("rate", rate);
        when(oracle.mock.get_reference_data(symbol, "ICX")).thenReturn(priceData);
    }

    private void mockDexRate(BigInteger poolID, BigInteger rate) {
        mockRate("USD", ICX);
        when(dex.mock.getQuotePriceInBase(poolID)).thenReturn(rate);
    }
}