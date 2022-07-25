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

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;

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
    void timeWeightedAverage_matchingTimestamp() {
        // Arrange'
        BigInteger timespan = (BigInteger) balancedOracle.call("getOraclePriceTimespan");
        BigInteger timespanInBlocks = timespan.divide(MICRO_SECONDS_IN_A_SECOND).divide(BigInteger.TWO);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T1 = BigInteger.valueOf(sm.getBlock().getTimestamp());

        BigInteger timeDelta0 = timespanInBlocks.subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta0.intValue());

        BigInteger rate2 = BigInteger.valueOf(8).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T2 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight2  = T2.subtract(T1).multiply(rate1);

        sm.getBlock().increase(timespanInBlocks.divide(BigInteger.TWO).subtract(BigInteger.ONE).intValue());

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T3 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight3  = weight2.add(T3.subtract(T2).multiply(rate2));
        
        sm.getBlock().increase(timespanInBlocks.divide(BigInteger.TWO).subtract(BigInteger.ONE).intValue());

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T4 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight4  = weight3.add(T4.subtract(T3).multiply(rate3));

        // Assert
        BigInteger expectedPrice = weight4.subtract(weight2).divide(timespan);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        assertEquals(expectedPrice, priceInLoop);
    }

    @Test
    void timeWeightedAverage_recalculatedWeight() {
        // Arrange'
        BigInteger timespan = (BigInteger) balancedOracle.call("getOraclePriceTimespan");
        BigInteger timespanInBlocks = timespan.divide(MICRO_SECONDS_IN_A_SECOND).divide(BigInteger.TWO);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T1 = BigInteger.valueOf(sm.getBlock().getTimestamp());

        BigInteger timeDelta0 = timespanInBlocks.subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta0.intValue());

        BigInteger rate2 = BigInteger.valueOf(8).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T2 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight2 = T2.subtract(T1).multiply(rate1);

        BigInteger timeDelta1 = timespanInBlocks.subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta1.intValue());

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T3 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight3  = weight2.add(T3.subtract(T2).multiply(rate2));
        
        BigInteger timeDelta2 = timespanInBlocks.divide(BigInteger.valueOf(2)).subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta2.intValue());

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T4 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight4  = weight3.add(T4.subtract(T3).multiply(rate3));

        BigInteger timeDelta3 = timespanInBlocks.divide(BigInteger.valueOf(5)).subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta3.intValue());

        BigInteger rate5 = BigInteger.valueOf(12).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate5);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T5 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight5  = weight4.add(T5.subtract(T4).multiply(rate4));

        // Assert
        BigInteger endTime = T5.subtract(timespan);
        BigInteger calculatedWeight2 = weight2.add(rate2.multiply(endTime.subtract(T2)));
        BigInteger expectedPrice = weight5.subtract(calculatedWeight2).divide(timespan);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        assertEquals(expectedPrice, priceInLoop);
    }

    @Test
    void timeWeightedAverage_changeTimespan() {
        // Arrange'
        BigInteger timespan = (BigInteger) balancedOracle.call("getOraclePriceTimespan");
        BigInteger timespanInBlocks = timespan.divide(MICRO_SECONDS_IN_A_SECOND).divide(BigInteger.TWO);

        // Act
        BigInteger rate1 = BigInteger.valueOf(6).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate1);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T1 = BigInteger.valueOf(sm.getBlock().getTimestamp());

        BigInteger timeDelta0 = timespanInBlocks.subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta0.intValue());

        BigInteger rate2 = BigInteger.valueOf(8).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate2);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T2 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight2 = T2.subtract(T1).multiply(rate1);

        BigInteger timeDelta1 = timespanInBlocks.subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta1.intValue());

        BigInteger rate3 = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate3);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T3 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight3  = weight2.add(T3.subtract(T2).multiply(rate2));
        
        BigInteger timeDelta2 = timespanInBlocks.divide(BigInteger.valueOf(2)).subtract(BigInteger.ONE);
        sm.getBlock().increase(timeDelta2.intValue());

        BigInteger rate4 = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate4);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T4 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight4  = weight3.add(T4.subtract(T3).multiply(rate3));

        BigInteger timeDelta3 = timespanInBlocks.divide(BigInteger.valueOf(2)).subtract(BigInteger.TWO);
        sm.getBlock().increase(timeDelta3.intValue());

        BigInteger newTimespan = timespan.multiply(BigInteger.TWO);
        balancedOracle.invoke(governance, "setOrcalePriceTimespan", newTimespan);

        BigInteger rate5 = BigInteger.valueOf(12).multiply(BigInteger.TEN.pow(17));
        mockRate("USD", rate5);
        balancedOracle.invoke(adminAccount, "getPriceInLoop", "bnUSD");
        BigInteger T5 = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger weight5  = weight4.add(T5.subtract(T4).multiply(rate4));

        // Assert
        BigInteger expectedPrice = weight5.subtract(weight2).divide(newTimespan);
        BigInteger priceInLoop = (BigInteger) balancedOracle.call("getLastPriceInLoop", "bnUSD");

        assertEquals(expectedPrice, priceInLoop);
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


    private void mockRate(String symbol, BigInteger rate) {
        Map<String, Object> priceData = Map.of("rate", rate);
        when(oracle.mock.get_reference_data(symbol, "ICX")).thenReturn(priceData);
    }

}