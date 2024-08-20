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

package network.balanced.score.core.balancedoracle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class BalancedOracleTest extends BalancedOracleTestBase {
    @BeforeEach
    public void setupContract() throws Exception {
        setup();
        balancedOracle.invoke(owner, "setLastUpdateThreshold", MICRO_SECONDS_IN_A_DAY);
    }

    @Test
    void getSicxPriceInLoop() {
        // Arrange
        BigInteger sicxRate = BigInteger.TEN.pow(18);
        when(staking.mock.getTodayRate()).thenReturn(sicxRate);

        // Act
        balancedOracle.invoke(owner, "getPriceInLoop", "sICX");
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
        balancedOracle.invoke(owner, "getPriceInUSD", "sICX");
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
        balancedOracle.invoke(owner, "getPriceInUSD", "BTC");
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "BTC");

        // Assert
        assertEquals(BTCUSDRate, priceInUSD);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPriceDataInUSD() {
        // Arrange
        BigInteger BTCUSDRate = BigInteger.valueOf(1000).multiply(EXA);
        mockUSDRate("BTC", BTCUSDRate);
        // round to seconds for pyth
        BigInteger time = BigInteger.valueOf(sm.getBlock().getTimestamp()).divide(MICRO_SECONDS_IN_A_SECOND)
                .multiply(MICRO_SECONDS_IN_A_SECOND);

        // Act
        Map<String, BigInteger> priceInUSD = (Map<String, BigInteger>) balancedOracle.call("getPriceDataInUSD", "BTC");

        // Assert
        assertEquals(BTCUSDRate, priceInUSD.get("rate"));
        assertEquals(time, priceInUSD.get("timestamp"));
    }

    @Test
    void getBnUSDPriceInLoop_useBnUSD() {
        // Act
        BigInteger res = (BigInteger) balancedOracle.call("getLastPriceInUSD", "bnUSD");

        // Assert
        assertEquals(EXA, res);
    }

    @Test
    void testUpdateThreshold() {
        // Arrange
        BigInteger threshold = MICRO_SECONDS_IN_A_DAY;
        BigInteger rate = BigInteger.TEN;

        BigInteger baseUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold);
        BigInteger quoteUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        mockUSDRate("BTC", rate, baseUpdateTime, quoteUpdateTime);

        // Act & Assert
        String expectedErrorMessage = "";
        Executable baseAboveThreshold = () -> balancedOracle.call("getLastPriceInUSD", "BTC");
        expectErrorMessage(baseAboveThreshold, expectedErrorMessage);

        // Act
        baseUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold)
                .add(BigInteger.TEN.multiply(MICRO_SECONDS_IN_A_SECOND));
        quoteUpdateTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(threshold)
                .add(BigInteger.TEN.multiply(MICRO_SECONDS_IN_A_SECOND));
        mockUSDRate("BTC", rate, baseUpdateTime, quoteUpdateTime);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInUSD", "BTC");

        // Assert
        assertEquals(rate, priceInLoop);
    }

    @Test
    void getPriceInUSD_BandOnly() {

        // Arrange
        BigInteger ETHUSDRate = BigInteger.valueOf(1000).multiply(EXA);
        mockUSDRate("ETH", ETHUSDRate);
        balancedOracle.invoke(owner, "configureBandPrice", "ETH");

        // Act
        balancedOracle.invoke(owner, "getPriceInUSD", "ETH");
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "ETH");

        // Assert
        assertEquals(ETHUSDRate, priceInUSD);
    }


    @Test
    void getPriceInUSD_BandAndPyth() {

        // Arrange
        BigInteger ETHUSDRate_pyth = BigInteger.valueOf(1002).multiply(EXA);
        BigInteger ETHUSDRate_band = BigInteger.valueOf(1001).multiply(EXA);
        mockBandUSDRate("ETH", ETHUSDRate_band);
        mockPythUSDRate("ETH", ETHUSDRate_pyth);
        balancedOracle.invoke(owner, "configurePythPriceId", "ETH", "ETH".getBytes());
        balancedOracle.invoke(owner, "configureBandPrice", "ETH");

        // Act
        balancedOracle.invoke(owner, "getPriceInUSD", "ETH");
        BigInteger priceInUSD = (BigInteger) balancedOracle.call("getLastPriceInUSD", "ETH");

        // Assert
        assertEquals(ETHUSDRate_pyth, priceInUSD);
    }

    @Test
    void getPriceInUSD_InvalidPriceDiff() {
        // Arrange
        BigInteger ETHUSDRate_pyth = BigInteger.valueOf(1011).multiply(EXA);
        BigInteger ETHUSDRate_band = BigInteger.valueOf(1000).multiply(EXA);
        mockBandUSDRate("ETH", ETHUSDRate_band);
        mockPythUSDRate("ETH", ETHUSDRate_pyth);
        balancedOracle.invoke(owner, "setPriceDiffThreshold", BigInteger.valueOf(100)); // 1%
        balancedOracle.invoke(owner, "configurePythPriceId", "ETH", "ETH".getBytes());
        balancedOracle.invoke(owner, "configureBandPrice", "ETH");

        // Act
        String expectedErrorMessage = "Difference between Pyth and Band is bigger than threshold";
        Executable baseAboveThreshold = () -> balancedOracle.call("getLastPriceInUSD", "ETH");

        // Assert
        expectErrorMessage(baseAboveThreshold, expectedErrorMessage);
    }

    private void mockUSDRate(String symbol, BigInteger rate) {
        mockUSDRate(symbol, rate, BigInteger.valueOf(sm.getBlock().getTimestamp()),
                BigInteger.valueOf(sm.getBlock().getTimestamp()));
    }

    private void mockUSDRate(String symbol, BigInteger rate, BigInteger baseUpdateTime, BigInteger quoteUpdateTime) {
        Map<String, Object> priceData = Map.of(
                "rate", rate,
                "last_update_base", baseUpdateTime,
                "last_update_quote", quoteUpdateTime);

        Map<String, BigInteger> pythData = Map.of(
                "price", rate,
                "expo", BigInteger.valueOf(-18),
                "publishTime", baseUpdateTime.divide(MICRO_SECONDS_IN_A_SECOND));
        when(oracle.mock.get_reference_data(symbol, "USD")).thenReturn(priceData);
        when(pyth.mock.getPrice(symbol.getBytes())).thenReturn(pythData);
    }

    private void mockBandUSDRate(String symbol, BigInteger rate) {
        Map<String, Object> priceData = Map.of(
                "rate", rate,
                "last_update_base", BigInteger.valueOf(sm.getBlock().getTimestamp()),
                "last_update_quote", BigInteger.valueOf(sm.getBlock().getTimestamp()));
        when(oracle.mock.get_reference_data(symbol, "USD")).thenReturn(priceData);
    }

    private void mockPythUSDRate(String symbol, BigInteger rate) {
        Map<String, BigInteger> pythData = Map.of(
            "price", rate,
            "expo", BigInteger.valueOf(-18),
            "publishTime", BigInteger.valueOf(sm.getBlock().getTimestamp()).divide(MICRO_SECONDS_IN_A_SECOND));
        when(pyth.mock.getPrice(symbol.getBytes())).thenReturn(pythData);
    }


}