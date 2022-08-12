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

package network.balanced.score.core.rebalancing;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class RebalancingTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account adminAccount = sm.createAccount();

    int scoreCount = 0;

    private Score rebalancingScore;
    private static final BigInteger FEE = BigInteger.valueOf(997);
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    protected MockBalanced mockBalanced;
    protected MockContract<Governance> governance;
    protected MockContract<Loans> loans;
    protected MockContract<Dex> dex;
    protected MockContract<Staking> staking;
    protected MockContract<Sicx> sicx;
    protected MockContract<BalancedDollar> bnUSD; 
    protected MockContract<BalancedOracle> balancedOracle;

    @BeforeEach
    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        loans  = mockBalanced.loans ;
        dex = mockBalanced.dex;
        staking = mockBalanced.staking;
        sicx = mockBalanced.sicx;
        bnUSD = mockBalanced.bnUSD;
        balancedOracle = mockBalanced.balancedOracle;
        rebalancingScore = sm.deploy(owner, RebalancingImpl.class, governance.getAddress());
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(rebalancingScore, governance.account, adminAccount);
    }

    @Test
    void setAndGetPriceDiffThreshold() {
        BigInteger threshold = BigInteger.TEN;

        // Sender not governance
        Account nonGovernance = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() + " Authorized " +
                        "Caller: " + governance.getAddress();
        Executable setThresholdNotFromGovernance = () -> rebalancingScore.invoke(nonGovernance,
                "setPriceDiffThreshold", threshold);
        expectErrorMessage(setThresholdNotFromGovernance, expectedErrorMessage);

        rebalancingScore.invoke(governance.account, "setPriceDiffThreshold", threshold);
        assertEquals(threshold, rebalancingScore.call("getPriceChangeThreshold"));
    }

    public static Stream<BigInteger> provideDifferentThresholds() {
        return Stream.of(BigInteger.TEN.pow(17), BigInteger.TEN.pow(16));
    }

    @Nested
    @DisplayName("Test Rebalancing in different condition with threshold")
    class TestRebalancingStatus {

        private final BigInteger sicxLiquidity = BigInteger.valueOf(300_000).multiply(ICX);
        private final BigInteger bnusdLiquidity = BigInteger.valueOf(150_000).multiply(ICX);
        private final BigInteger dexPriceOfBnusdInSicx = sicxLiquidity.multiply(ICX).divide(bnusdLiquidity);
        private final BigInteger sicxPriceInIcx = ICX;

        @BeforeEach
        void configureRebalancing() {
            rebalancingScore.invoke(governance.account, "setAdmin", adminAccount.getAddress());

            Map<String, Object> poolStats = new HashMap<>();
            poolStats.put("base", sicxLiquidity);
            poolStats.put("quote", bnusdLiquidity);

            when(sicx.mock.symbol()).thenReturn("sICX");
            when(dex.mock.getPoolId(sicx.getAddress(), bnUSD.getAddress())).thenReturn(BigInteger.TWO);
            when(dex.mock.getPoolStats(BigInteger.TWO)).thenReturn(poolStats);
            when(balancedOracle.mock.getPriceInLoop("sICX")).thenReturn(sicxPriceInIcx);
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceWithinThreshold(BigInteger threshold) {
            //Case 1: Dex price equal to actual price (within threshold limit)
            // Manipulating actual price with bnusd/sicx price as icx/sicx price is 1:1
            // Increase price within threshold range
            rebalancingScore.invoke(governance.account, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(dexPriceOfBnusdInSicx).divide(ICX).divide(BigInteger.TEN);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.add(additionalPrice);
            when(balancedOracle.mock.getPriceInLoop("USD")).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(ICX).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance", sicx.getAddress());
            verify(loans.mock, never()).raisePrice(any(String.class), any(BigInteger.class));
            verify(loans.mock, never()).lowerPrice(any(String.class), any(BigInteger.class));

            // Decrease price within threshold range
            bnUSDPriceInIcx = dexPriceOfBnusdInSicx.subtract(additionalPrice);
            when(balancedOracle.mock.getPriceInLoop("USD")).thenReturn(bnUSDPriceInIcx);
            expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(ICX).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance", sicx.getAddress());
            verify(loans.mock, never()).raisePrice(any(String.class), any(BigInteger.class));
            verify(loans.mock, never()).lowerPrice(any(String.class), any(BigInteger.class));

            // Exactly equal price
            bnUSDPriceInIcx = dexPriceOfBnusdInSicx;
            when(balancedOracle.mock.getPriceInLoop("USD")).thenReturn(bnUSDPriceInIcx);
            expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(ICX).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance", sicx.getAddress());
            verify(loans.mock, never()).raisePrice(any(String.class), any(BigInteger.class));
            verify(loans.mock, never()).lowerPrice(any(String.class), any(BigInteger.class));
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceGreaterThanThreshold(BigInteger threshold) {
            // Case 2: Dex price more than actual price.
            rebalancingScore.invoke(governance.account, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(BigInteger.TWO).multiply(dexPriceOfBnusdInSicx).divide(ICX);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.add(additionalPrice);
            when(balancedOracle.mock.getPriceInLoop("USD")).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(ICX).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance", sicx.getAddress());
            verify(loans.mock).raisePrice(any(String.class), any(BigInteger.class));
            verify(loans.mock, never()).lowerPrice(any(String.class), any(BigInteger.class));
            
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceLessThanThreshold(BigInteger threshold) {
            // Case 3: Dex price less than actual price.
            rebalancingScore.invoke(governance.account, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(BigInteger.TWO).multiply(dexPriceOfBnusdInSicx).divide(ICX);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.subtract(additionalPrice);
            when(balancedOracle.mock.getPriceInLoop("USD")).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(ICX).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance", sicx.getAddress());
            verify(loans.mock, never()).raisePrice(any(String.class), any(BigInteger.class));
            verify(loans.mock).lowerPrice(any(String.class), any(BigInteger.class));
        }

        private BigInteger calculateOutputAmount(BigInteger fromTokenLiquidity, BigInteger toTokenLiquidity,
                                                 BigInteger tokensToSell) {
            return FEE.multiply(toTokenLiquidity).multiply(tokensToSell).divide(THOUSAND).divide(fromTokenLiquidity.add(FEE.multiply(tokensToSell).divide(THOUSAND)));
        }

        private void assertRebalancingStatus(BigInteger expectedBnusdPriceInSicx, BigInteger sicxLiquidity,
                                             BigInteger bnusdLiquidity, BigInteger threshold) {
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) rebalancingScore.call("getRebalancingStatusFor", sicx.getAddress());

            boolean forward = (boolean) results.get(0);
            boolean reverse = (boolean) results.get(2);
            BigInteger tokenAmount = (BigInteger) results.get(1);

            BigInteger newSicxLiquidity;
            BigInteger newBnusdLiquidity;

            assertFalse(forward && reverse);

            if (forward) {
                assertTrue(tokenAmount.compareTo(BigInteger.ZERO) > 0);
                newSicxLiquidity = sicxLiquidity.add(tokenAmount);
                BigInteger bnUsdReceivedAfterTrade = calculateOutputAmount(sicxLiquidity, bnusdLiquidity, tokenAmount);
                newBnusdLiquidity = bnusdLiquidity.subtract(bnUsdReceivedAfterTrade);
            } else if (reverse) {
                assertTrue(tokenAmount.compareTo(BigInteger.ZERO) > 0);
                newBnusdLiquidity = bnusdLiquidity.add(tokenAmount);
                BigInteger sicxReceivedAfterTrade = calculateOutputAmount(bnusdLiquidity, sicxLiquidity, tokenAmount);
                newSicxLiquidity = sicxLiquidity.subtract(sicxReceivedAfterTrade);
            } else {
                newSicxLiquidity = sicxLiquidity;
                newBnusdLiquidity = bnusdLiquidity;
                assertEquals(BigInteger.ZERO, tokenAmount);
            }
            BigInteger newBnusdPriceInSicx = newSicxLiquidity.multiply(ICX).divide(newBnusdLiquidity);
            BigInteger priceDifferencePercentage =
                    expectedBnusdPriceInSicx.subtract(newBnusdPriceInSicx).multiply(ICX).divide(expectedBnusdPriceInSicx);

            assertTrue(priceDifferencePercentage.abs().compareTo(threshold) <= 0);
        }
    }
}
