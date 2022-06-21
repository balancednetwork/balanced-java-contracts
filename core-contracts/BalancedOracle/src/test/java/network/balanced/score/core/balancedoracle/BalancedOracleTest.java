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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.Disbursement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

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
        balancedOracle.invoke(owner, "addDexPricedAsset", tokenSymbol, poolID);

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
}