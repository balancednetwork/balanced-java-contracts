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

import network.balanced.score.lib.structs.PriceProtectionConfig;
import network.balanced.score.lib.structs.PriceProtectionParameter;

import network.balanced.score.lib.interfaces.BalancedOracleMessages;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BalancedOracleTestExternal extends BalancedOracleTestBase {
    String symbol = "hyUSDC";
    String externalPriceProxy = "avax/cx1";

    @BeforeEach
    public void setupContract() throws Exception {
        setup();
    }

    @Test
    void externalPriceProxy_noProtection() {
        // Arrange
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, BigInteger.ZERO, BigInteger.ZERO));
        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);
        verifyPrice(symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.multiply(BigInteger.TWO);
        timestamp = timestamp.add(BigInteger.ONE);
        updatePrice(externalPriceProxy, symbol, newRate, timestamp);

        // Assert
        verifyPrice(symbol, newRate, timestamp);
    }

    @Test
    void externalPriceProxy_oldTimestamp() {
        // Arrange
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, BigInteger.ZERO, BigInteger.ZERO));
        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.multiply(BigInteger.TWO);
        Executable updateWithOldTimestamp = () -> updatePrice(externalPriceProxy, symbol, newRate, timestamp);

        // Assert
        expectErrorMessage(updateWithOldTimestamp, "Price must be more recent than the current one");
    }

    @Test
    void externalPriceProxy_futureTimestamp() {
        // Arrange
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, BigInteger.ZERO, BigInteger.ZERO));
        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp()).add(MICRO_SECONDS_IN_A_DAY);

        // Act
        Executable updateWithFutureTimestamp = () -> updatePrice(externalPriceProxy, symbol, initialRate, timestamp);
        ;

        // Assert
        expectErrorMessage(updateWithFutureTimestamp, "Time cannot be in the future");
    }

    @Test
    void externalPriceProxy_increaseOnly() {
        // Arrange
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(true, BigInteger.ZERO, BigInteger.ZERO));
        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.add(BigInteger.ONE);
        timestamp = timestamp.add(BigInteger.ONE);
        updatePrice(externalPriceProxy, symbol, newRate, BigInteger.ONE);
        Executable decreasePrice = () -> updatePrice(externalPriceProxy, symbol, newRate.subtract(BigInteger.ONE),
                BigInteger.TWO);

        // Assert
        expectErrorMessage(decreasePrice, "Price of this asset can only increase");
        verifyPrice(symbol, newRate, timestamp);
    }

    @Test
    void externalPriceProxy_priceVolatilityProtection_fullTime() {
        // Arrange
        // Price can max change 10% per day
        BigInteger priceChangeInPoints = BigInteger.valueOf(1000); // 10%
        BigInteger priceChangeTimeWindowUs = MICRO_SECONDS_IN_A_DAY; // 1 Day
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, priceChangeInPoints, priceChangeTimeWindowUs));

        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.multiply(BigInteger.valueOf(11)).divide(BigInteger.TEN);
        // update 10% with very little time difference
        Executable fastPriceChange = () -> updatePrice(externalPriceProxy, symbol, newRate,
                BigInteger.ONE);
        expectErrorMessage(fastPriceChange, "Price of this asset has moved to fast");
        timestamp = MICRO_SECONDS_IN_A_DAY;
        updatePrice(externalPriceProxy, symbol, newRate, timestamp);

        // Assert
        verifyPrice(symbol, newRate, timestamp);
    }

    @Test
    void externalPriceProxy_priceVolatilityProtection_multipleTimeWindow() {
        // Arrange
        // Price can max change 10% per day
        BigInteger priceChangeInPoints = BigInteger.valueOf(1000); // 10%
        BigInteger priceChangeTimeWindowUs = MICRO_SECONDS_IN_A_DAY; // 1 Day
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, priceChangeInPoints, priceChangeTimeWindowUs));

        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.multiply(BigInteger.valueOf(13)).divide(BigInteger.TEN);
        // update 30% with only two day time difference
        Executable fastPriceChange = () -> updatePrice(externalPriceProxy, symbol, newRate,
                MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.TWO));
        expectErrorMessage(fastPriceChange, "Price of this asset has moved to fast");
        timestamp = MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(3));
        updatePrice(externalPriceProxy, symbol, newRate, timestamp);

        // Assert
        verifyPrice(symbol, newRate, timestamp);
    }

    @Test
    void externalPriceProxy_priceVolatilityProtection_partialTimeWindow() {
        // Arrange
        // Price can max change 10% per day
        BigInteger priceChangeInPoints = BigInteger.valueOf(1000); // 10%
        BigInteger priceChangeTimeWindowUs = MICRO_SECONDS_IN_A_DAY; // 1 Day
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, priceChangeInPoints, priceChangeTimeWindowUs));

        BigInteger initialRate = BigInteger.TEN.pow(18);
        BigInteger timestamp = BigInteger.ZERO;
        updatePrice(externalPriceProxy, symbol, initialRate, timestamp);

        // Act
        BigInteger newRate = initialRate.multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100));
        // update 5% with 1/4 day difference
        Executable fastPriceChange = () -> updatePrice(externalPriceProxy, symbol, newRate,
                MICRO_SECONDS_IN_A_DAY.divide(BigInteger.valueOf(4)));
        expectErrorMessage(fastPriceChange, "Price of this asset has moved to fast");
        timestamp = MICRO_SECONDS_IN_A_DAY.divide(BigInteger.TWO);
        updatePrice(externalPriceProxy, symbol, newRate, timestamp);

        // Assert
        verifyPrice(symbol, newRate, timestamp);
    }

    @Test
    void updatePermissions() {
        assertOnlyCallableBy(mockBalanced.xCall.getAddress(), balancedOracle, "handleCallMessage", "", new byte[0],
                (Object) new String[0]);
        balancedOracle.invoke(owner, "addExternalPriceProxy", symbol, externalPriceProxy,
                newPriceProtectionParameter(false, BigInteger.ZERO, BigInteger.ZERO));
        expectErrorMessage(() -> updatePrice("avax/wrongOracle", "hyUSDC", BigInteger.ZERO, BigInteger.ZERO),
                "is not allowed to update the price");
    }

    protected void updatePrice(String oracle, String symbol, BigInteger rate, BigInteger timestamp) {
        byte[] msg = BalancedOracleMessages.updatePriceData(symbol, rate, timestamp);
        balancedOracle.invoke(mockBalanced.xCall.account, "handleCallMessage", oracle, msg, (Object) new String[0]);
    }

    @SuppressWarnings("unchecked")
    protected void verifyPrice(String symbol, BigInteger rate, BigInteger timestamp) {
        Map<String, BigInteger> priceData = (Map<String, BigInteger>) balancedOracle.call("getPriceDataInUSD", symbol);

        assertEquals(timestamp, priceData.get("timestamp"));
        assertEquals(rate, priceData.get("rate"));
    }

    protected PriceProtectionParameter newPriceProtectionParameter(Boolean increaseOnly, BigInteger priceChangePoints,
            BigInteger priceChangeTimeWindowUs) {
        PriceProtectionParameter param = new PriceProtectionParameter();
        param.increaseOnly = increaseOnly;
        param.priceChangePoints = priceChangePoints;
        param.priceChangeTimeWindowUs = priceChangeTimeWindowUs;
        return param;
    }
}