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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static network.balanced.score.core.rebalancing.Constants.EXA;
import static network.balanced.score.core.rebalancing.Constants.SICX_BNUSD_POOL_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;


public class RebalancingTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account dexScore = Account.newScoreAccount(scoreCount++);
    private final Account loansScore = Account.newScoreAccount(scoreCount++);
    private final Account bnUSDScore = Account.newScoreAccount(scoreCount++);
    private final Account sicxScore = Account.newScoreAccount(scoreCount++);

    private Score rebalancingScore;
    private static final BigInteger FEE = BigInteger.valueOf(997);
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    private void expectErrorMessage(Executable _contractCall, String _expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, _contractCall);
        assertEquals(_expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    public void setup() throws Exception {
        rebalancingScore = sm.deploy(owner, Rebalancing.class, governanceScore.getAddress());
        contextMock.when(() -> Context.call(eq(loansScore.getAddress()), eq("raisePrice"), any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(loansScore.getAddress()), eq("lowerPrice"), any(BigInteger.class))).thenReturn(null);
    }

    @Test
    void setAndGetAdmin() {
        String expectedErrorMessage = "Rebalancing: Sender not governance contract";
        Executable setAdminNotFromGovernance = () -> rebalancingScore.invoke(sm.createAccount(), "setAdmin",
                adminAccount.getAddress());
        expectErrorMessage(setAdminNotFromGovernance, expectedErrorMessage);

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        assertEquals(adminAccount.getAddress(), rebalancingScore.call("getAdmin"));
    }

    @Test
    void setAndGetGovernance() {
        assertEquals(governanceScore.getAddress(), rebalancingScore.call("getGovernance"));

        Account newGovernance = Account.newScoreAccount(scoreCount++);
        rebalancingScore.invoke(owner, "setGovernance", newGovernance.getAddress());
        assertEquals(newGovernance.getAddress(), rebalancingScore.call("getGovernance"));

        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + sender.getAddress() + "Owner=" + owner.getAddress();
        Executable setGovernanceNotFromOwner = () -> rebalancingScore.invoke(sender, "setGovernance",
                governanceScore.getAddress());
        expectErrorMessage(setGovernanceNotFromOwner, expectedErrorMessage);
    }

    @Test
    void setAndGetDex() {
        testContractSettersAndGetters("setDex", dexScore, "getDex");
    }

    @Test
    void setAndGetSICX() {
        testContractSettersAndGetters("setSicx", sicxScore, "getSicx");
    }

    @Test
    void setAndGetBnusd() {
        testContractSettersAndGetters("setBnusd", bnUSDScore, "getBnusd");
    }

    @Test
    void setAndGetLoans() {
        testContractSettersAndGetters("setLoans", loansScore, "getLoans");
    }

    private void testContractSettersAndGetters(String setterMethod, Account scoreAddress, String getterMethod) {
        String expectedErrorMessage = "Rebalancing: Admin address not set";
        Executable setScoreWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, setterMethod,
                scoreAddress.getAddress());
        expectErrorMessage(setScoreWithoutAdmin, expectedErrorMessage);

        setAndGetAdmin();

        expectedErrorMessage = "Rebalancing: Sender not admin";
        Executable setScoreNotFromAdmin = () -> rebalancingScore.invoke(sm.createAccount(), setterMethod,
                scoreAddress.getAddress());
        expectErrorMessage(setScoreNotFromAdmin, expectedErrorMessage);

        expectedErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";
        Executable setScoreToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, setterMethod,
                sm.createAccount().getAddress());
        expectErrorMessage(setScoreToInvalidAddress, expectedErrorMessage);

        rebalancingScore.invoke(adminAccount, setterMethod, scoreAddress.getAddress());
        assertEquals(scoreAddress.getAddress(), rebalancingScore.call(getterMethod));
    }

    @Test
    void setAndGetPriceDiffThreshold() {
        BigInteger threshold = BigInteger.TEN;

        // Sender not governance
        String expectedErrorMessage = "Rebalancing: Sender not governance contract";
        Executable setThresholdNotFromGovernance = () -> rebalancingScore.invoke(sm.createAccount(),
                "setPriceDiffThreshold", threshold);
        expectErrorMessage(setThresholdNotFromGovernance, expectedErrorMessage);

        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", threshold);
        assertEquals(threshold, rebalancingScore.call("getPriceChangeThreshold"));
    }

    public static Stream<BigInteger> provideDifferentThresholds() {
        return Stream.of(BigInteger.TEN.pow(17), BigInteger.TEN.pow(16));
    }

    @Nested
    @DisplayName("Test Rebalancing in different condition with threshold")
    class TestRebalancingStatus {

        private final BigInteger sicxLiquidity = BigInteger.valueOf(300_000).multiply(EXA);
        private final BigInteger bnusdLiquidity = BigInteger.valueOf(150_000).multiply(EXA);
        private final BigInteger dexPriceOfBnusdInSicx = sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
        private final BigInteger sicxPriceInIcx = EXA;

        Verification getBnusdPrice = () -> Context.call(bnUSDScore.getAddress(), "lastPriceInLoop");

        @BeforeEach
        void configureRebalancing() {
            setAndGetAdmin();
            rebalancingScore.invoke(adminAccount, "setBnusd", bnUSDScore.getAddress());
            rebalancingScore.invoke(adminAccount, "setDex", dexScore.getAddress());
            rebalancingScore.invoke(adminAccount, "setSicx", sicxScore.getAddress());
            rebalancingScore.invoke(adminAccount, "setLoans", loansScore.getAddress());

            Map<String, Object> poolStats = new HashMap<>();
            poolStats.put("base", sicxLiquidity);
            poolStats.put("quote", bnusdLiquidity);
            contextMock.when(() -> Context.call(dexScore.getAddress(), "getPoolStats", SICX_BNUSD_POOL_ID)).thenReturn(poolStats);

            contextMock.when(() -> Context.call(sicxScore.getAddress(), "lastPriceInLoop")).thenReturn(sicxPriceInIcx);
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceWithinThreshold(BigInteger threshold) {
            //Case 1: Dex price equal to actual price (within threshold limit)
            // Manipulating actual price with bnusd/sicx price as icx/sicx price is 1:1
            // Increase price within threshold range
            rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(dexPriceOfBnusdInSicx).divide(EXA).divide(BigInteger.TEN);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.add(additionalPrice);
            contextMock.when(getBnusdPrice).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance");
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), any(String.class),
                    any(BigInteger.class)), never());

            // Decrease price within threshold range
            bnUSDPriceInIcx = dexPriceOfBnusdInSicx.subtract(additionalPrice);
            contextMock.when(getBnusdPrice).thenReturn(bnUSDPriceInIcx);
            expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance");
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), any(String.class),
                    any(BigInteger.class)), never());

            // Exactly equal price
            bnUSDPriceInIcx = dexPriceOfBnusdInSicx;
            contextMock.when(getBnusdPrice).thenReturn(bnUSDPriceInIcx);
            expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance");
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), any(String.class),
                    any(BigInteger.class)), never());
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceGreaterThanThreshold(BigInteger threshold) {
            // Case 2: Dex price more than actual price.
            rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(BigInteger.TWO).multiply(dexPriceOfBnusdInSicx).divide(EXA);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.add(additionalPrice);
            contextMock.when(getBnusdPrice).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance");
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), eq("raisePrice"),
                    any(BigInteger.class)));
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), eq("lowerPrice"),
                    any(BigInteger.class)), never());
        }

        @ParameterizedTest
        @MethodSource("network.balanced.score.core.rebalancing.RebalancingTest#provideDifferentThresholds")
        void priceLessThanThreshold(BigInteger threshold) {
            // Case 3: Dex price less than actual price.
            rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", threshold);
            BigInteger additionalPrice = threshold.multiply(BigInteger.TWO).multiply(dexPriceOfBnusdInSicx).divide(EXA);
            BigInteger bnUSDPriceInIcx = dexPriceOfBnusdInSicx.subtract(additionalPrice);
            contextMock.when(getBnusdPrice).thenReturn(bnUSDPriceInIcx);
            BigInteger expectedBnusdPriceInSicx = bnUSDPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
            assertRebalancingStatus(expectedBnusdPriceInSicx, sicxLiquidity, bnusdLiquidity, threshold);

            rebalancingScore.invoke(sm.createAccount(), "rebalance");
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), eq("raisePrice"),
                    any(BigInteger.class)), never());
            contextMock.verify(() -> Context.call(eq(loansScore.getAddress()), eq("lowerPrice"),
                    any(BigInteger.class)));
        }

        private BigInteger calculateOutputAmount(BigInteger fromTokenLiquidity, BigInteger toTokenLiquidity,
                                                 BigInteger tokensToSell) {
            return FEE.multiply(toTokenLiquidity).multiply(tokensToSell).divide(THOUSAND).divide(fromTokenLiquidity.add(FEE.multiply(tokensToSell).divide(THOUSAND)));
        }

        private void assertRebalancingStatus(BigInteger expectedBnusdPriceInSicx, BigInteger sicxLiquidity,
                                             BigInteger bnusdLiquidity, BigInteger threshold) {
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) rebalancingScore.call("getRebalancingStatus");

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
            BigInteger newBnusdPriceInSicx = newSicxLiquidity.multiply(EXA).divide(newBnusdLiquidity);
            BigInteger priceDifferencePercentage =
                    expectedBnusdPriceInSicx.subtract(newBnusdPriceInSicx).multiply(EXA).divide(expectedBnusdPriceInSicx);

            assertTrue(priceDifferencePercentage.abs().compareTo(threshold) <= 0);
        }
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}
