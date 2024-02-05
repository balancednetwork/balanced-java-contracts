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

package network.balanced.score.core.feehandler;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.BalancedDollar;
import network.balanced.score.lib.interfaces.BalancedToken;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static network.balanced.score.core.feehandler.FeeHandlerImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.test.UnitTest.scoreCount;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeeHandlerImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static MockBalanced mockBalanced;
    private static MockContract<BalancedToken> baln;
    private static MockContract<Sicx> sicx;
    private static MockContract<BalancedDollar> bnusd;
    private static MockContract<Governance> governance;

    private Score feeHandler;

    @BeforeEach
    void setUp() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        sicx = mockBalanced.sicx;
        bnusd = mockBalanced.bnUSD;
        baln = mockBalanced.baln;


        feeHandler = sm.deploy(owner, FeeHandlerImpl.class, governance.getAddress());


    }

    @Test
    void name() {
        String contractName = Names.FEEHANDLER;
        assertEquals(contractName, feeHandler.call("name"));
    }

    @Test
    void enable() {
        testEnableAndDisable("enable", true);
    }

    @Test
    void disable() {
        testEnableAndDisable("disable", false);
    }

    private void testEnableAndDisable(String setterMethod, boolean expectedValue) {
        Account nonAdmin = sm.createAccount();
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + governance.getAddress();
        Executable enableNotFromAdmin = () -> feeHandler.invoke(nonAdmin, setterMethod);
        expectErrorMessage(enableNotFromAdmin, expectedErrorMessage);

        feeHandler.invoke(governance.account, setterMethod);
        assertEquals(expectedValue, feeHandler.call("isEnabled"));
    }

    @Test
    void setAcceptedDividendTokens_NoPreviousTokens() {
        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicx.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", addAcceptedTokens);
        assertEquals(expectedTokens, feeHandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_PreviousTokens() {
        Object token = new Address[]{bnusd.getAddress(), bnusd.getAddress(), bnusd.getAddress()};
        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", token);

        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicx.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", addAcceptedTokens);
        assertEquals(expectedTokens, feeHandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_GreaterThanMaxSize() {
        int tokensCount = 11;
        List<Address> tokens = new ArrayList<>();
        for (int i = 0; i < tokensCount; i++) {
            tokens.add(Account.newScoreAccount(scoreCount++).getAddress());
        }
        Executable maxAcceptedTokens = () -> feeHandler.invoke(governance.account, "setAcceptedDividendTokens",
                (Object) tokens.toArray(new Address[0]));
        String expectedErrorMessage = TAG + ": There can be a maximum of 10 accepted dividend tokens.";
        expectErrorMessage(maxAcceptedTokens, expectedErrorMessage);
    }

    @Test
    void setFeeProcessingInterval() {
        feeHandler.invoke(governance.account, "setFeeProcessingInterval", BigInteger.TEN);
        assertEquals(BigInteger.TEN, feeHandler.call("getFeeProcessingInterval"));
    }

    @Test
    void tokenFallback() {
        // Arrange
        setAcceptedDividendTokens_NoPreviousTokens();
        setFeeProcessingInterval();
        feeHandler.invoke(governance.account, "disable");
        BigInteger amount = BigInteger.valueOf(11);
        when(sicx.mock.balanceOf(feeHandler.getAddress())).thenReturn(amount);

        // Act
        feeHandler.invoke(sicx.account, "tokenFallback", baln.getAddress(), amount, new byte[0]);
        feeHandler.invoke(governance.account, "enable");
        feeHandler.invoke(sicx.account, "tokenFallback", baln.getAddress(), amount, new byte[0]);
        feeHandler.invoke(sicx.account, "tokenFallback", baln.getAddress(), amount, new byte[0]);

        // Assert
        verify(sicx.mock, times(1)).transfer(mockBalanced.iconBurner.getAddress(), BigInteger.valueOf(5), new byte[0]);
        verify(sicx.mock, times(1)).transfer(mockBalanced.dividends.getAddress(), BigInteger.valueOf(6), new byte[0]);
    }

    @Test
    void feeData() {
        // Arrange
        feeHandler.invoke(governance.account, "disable");
        Account usdc = Account.newScoreAccount(scoreCount++);

        BigInteger loanFee1 = BigInteger.valueOf(5).multiply(ICX);
        BigInteger loanFee2 = BigInteger.valueOf(1).multiply(ICX);
        BigInteger dexFeeICX = BigInteger.valueOf(12).multiply(ICX);
        BigInteger dexFeeBnusd = BigInteger.valueOf(4).multiply(ICX);
        BigInteger dexFeeBaln = BigInteger.valueOf(7).multiply(ICX);
        BigInteger dexFeeUsdc = BigInteger.valueOf(9).multiply(ICX);
        BigInteger StabilityFeeBnsud = BigInteger.valueOf(20).multiply(ICX);

        Object token = new Address[]{sicx.getAddress(), bnusd.getAddress(), baln.getAddress()};
        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", token);
        feeHandler.invoke(governance.account, "setSwapFeesAccruedDB");

        // Act
        feeHandler.invoke(bnusd.account, "tokenFallback", EOA_ZERO, loanFee1, new byte[0]);
        feeHandler.invoke(bnusd.account, "tokenFallback", EOA_ZERO, loanFee2, new byte[0]);

        feeHandler.invoke(sicx.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeICX, new byte[0]);
        feeHandler.invoke(bnusd.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeBnusd, new byte[0]);
        feeHandler.invoke(baln.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeBaln, new byte[0]);
        feeHandler.invoke(usdc, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeUsdc, new byte[0]);

        feeHandler.invoke(sicx.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeICX, new byte[0]);
        feeHandler.invoke(bnusd.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeBnusd, new byte[0]);
        feeHandler.invoke(baln.account, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeBaln, new byte[0]);
        feeHandler.invoke(usdc, "tokenFallback", mockBalanced.dex.getAddress(), dexFeeUsdc, new byte[0]);

        feeHandler.invoke(bnusd.account, "tokenFallback", mockBalanced.stability.getAddress(), StabilityFeeBnsud,
                new byte[0]);
        feeHandler.invoke(bnusd.account, "tokenFallback", mockBalanced.stability.getAddress(), StabilityFeeBnsud,
                new byte[0]);

        assertEquals(loanFee1.add(loanFee2), feeHandler.call("getLoanFeesAccrued"));

        assertEquals(dexFeeICX.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken",
                sicx.getAddress()));
        assertEquals(dexFeeBnusd.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken",
                bnusd.getAddress()));
        assertEquals(dexFeeBaln.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken",
                baln.getAddress()));
        assertEquals(BigInteger.ZERO, feeHandler.call("getSwapFeesAccruedByToken", usdc.getAddress()));

        assertEquals(StabilityFeeBnsud.multiply(BigInteger.TWO), feeHandler.call("getStabilityFundFeesAccrued"));
    }

    @Test
    void setRoute() {
        // Arrange
        Address[] path = new Address[]{bnusd.getAddress(), baln.getAddress()};
        Object acceptedTokens = new Address[]{bnusd.getAddress()};
        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", acceptedTokens);

        // Act
        feeHandler.invoke(governance.account, "setRoute", sicx.getAddress(), path);
        Executable addAcceptedToken = () -> feeHandler.invoke(governance.account, "setRoute", bnusd.getAddress(),
                new Address[]{});

        // Assert
        List<String> route = (List<String>) feeHandler.call("getRoute", sicx.getAddress());
        List<Address> routes = (List<Address>) feeHandler.call("getRoutedTokens");

        assertEquals(bnusd.getAddress().toString(), route.get(0));
        assertEquals(baln.getAddress().toString(), route.get(1));
        assertEquals(2, route.size());

        assertEquals(sicx.getAddress(), routes.get(0));
        assertEquals(1, routes.size());

        expectErrorMessage(addAcceptedToken, "Token is accepted, should not be routed");
    }

    @Test
    void addDefaultRoute_requirements() {
        // Arrange
        Account caller = sm.createAccount();

        Account acceptedToken = Account.newScoreAccount(scoreCount++);
        Account nonQuoteToken = Account.newScoreAccount(scoreCount++);
        Account tokenWithPath = Account.newScoreAccount(scoreCount++);
        Account tokenWithoutPool = Account.newScoreAccount(scoreCount++);
        Account tokenWithoutSupportedPool = Account.newScoreAccount(scoreCount++);

        Address[] defaultPath = new Address[]{bnusd.getAddress(), baln.getAddress()};

        Object acceptedTokens = new Address[]{acceptedToken.getAddress()};
        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", acceptedTokens);

        when(mockBalanced.dex.mock.isQuoteCoinAllowed(nonQuoteToken.getAddress())).thenReturn(false);
        when(mockBalanced.dex.mock.isQuoteCoinAllowed(tokenWithPath.getAddress())).thenReturn(true);
        when(mockBalanced.dex.mock.isQuoteCoinAllowed(tokenWithoutPool.getAddress())).thenReturn(true);
        when(mockBalanced.dex.mock.isQuoteCoinAllowed(tokenWithoutSupportedPool.getAddress())).thenReturn(true);

        feeHandler.invoke(governance.account, "setRoute", tokenWithPath.getAddress(), defaultPath);

        BigInteger pid = BigInteger.valueOf(4);
        when(mockBalanced.dex.mock.getPoolId(tokenWithoutSupportedPool.getAddress(), bnusd.getAddress())).thenReturn(pid);
        when(mockBalanced.stakedLp.mock.isSupportedPool(pid)).thenReturn(false);


        // Act & Assert
        Executable withAcceptedToken = () -> feeHandler.invoke(caller, "addDefaultRoute", acceptedToken.getAddress());
        Executable nonQuote = () -> feeHandler.invoke(caller, "addDefaultRoute", nonQuoteToken.getAddress());
        Executable withPath = () -> feeHandler.invoke(caller, "addDefaultRoute", tokenWithPath.getAddress());
        Executable withoutPool = () -> feeHandler.invoke(caller, "addDefaultRoute", tokenWithoutPool.getAddress());
        Executable withoutSupportedPool = () -> feeHandler.invoke(caller, "addDefaultRoute",
                tokenWithoutSupportedPool.getAddress());

        expectErrorMessage(withAcceptedToken, "Token is accepted, should not be routed");
        expectErrorMessage(nonQuote, "Only quote coins should be routed");
        expectErrorMessage(withPath, "Route is already defined");
        expectErrorMessage(withoutPool, "no default path exists for " + tokenWithoutPool.getAddress());
        expectErrorMessage(withoutSupportedPool,
                "no default path exists for " + tokenWithoutSupportedPool.getAddress());
    }

    @Test
    void addDefaultRoute_Direct() {
        // Arrange
        Account caller = sm.createAccount();
        Account token = Account.newScoreAccount(scoreCount++);
        BigInteger pid = BigInteger.valueOf(4);

        when(mockBalanced.dex.mock.isQuoteCoinAllowed(token.getAddress())).thenReturn(true);
        when(mockBalanced.dex.mock.getPoolId(token.getAddress(), baln.getAddress())).thenReturn(pid);
        when(mockBalanced.stakedLp.mock.isSupportedPool(pid)).thenReturn(true);

        // Act
        feeHandler.invoke(caller, "addDefaultRoute", token.getAddress());

        // Assert
        List<String> route1 = (List<String>) feeHandler.call("getRoute", token.getAddress());
        assertTrue(route1.isEmpty());
    }

    @Test
    void addDefaultRoute_via_bnUSD() {
        // Arrange
        Account caller = sm.createAccount();
        Account token = Account.newScoreAccount(scoreCount++);
        BigInteger pid = BigInteger.valueOf(4);

        when(mockBalanced.dex.mock.isQuoteCoinAllowed(token.getAddress())).thenReturn(true);
        when(mockBalanced.dex.mock.getPoolId(token.getAddress(), bnusd.getAddress())).thenReturn(pid);
        when(mockBalanced.stakedLp.mock.isSupportedPool(pid)).thenReturn(true);

        // Act
        feeHandler.invoke(caller, "addDefaultRoute", token.getAddress());

        // Assert
        List<String> route = (List<String>) feeHandler.call("getRoute", token.getAddress());

        assertEquals(bnusd.getAddress().toString(), route.get(0));
        assertEquals(baln.getAddress().toString(), route.get(1));
        assertEquals(2, route.size());
    }

    @Test
    void removeRoute() {
        // Arrange
        Address[] path = new Address[]{bnusd.getAddress(), baln.getAddress()};
        feeHandler.invoke(governance.account, "setRoute", sicx.getAddress(), path);
        List<String> route = (List<String>) feeHandler.call("getRoute", sicx.getAddress());
        assertEquals(2, route.size());

        // Act
        feeHandler.invoke(governance.account, "deleteRoute", sicx.getAddress());

        // Assert
        assertNull(feeHandler.call("getRoute", sicx.getAddress()));

    }

    @Test
    void routeFees() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        Address[] path = new Address[]{bnusd.getAddress(), baln.getAddress()};
        MockContract<IRC2> token1 = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        MockContract<IRC2> token2 = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        BigInteger balance1 = BigInteger.valueOf(7);
        BigInteger balance2 = BigInteger.valueOf(3);
        feeHandler.invoke(governance.account, "setRoute", token1.getAddress(), path);
        feeHandler.invoke(governance.account, "setRoute", token2.getAddress(), new Address[]{});

        when(token1.mock.balanceOf(feeHandler.getAddress())).thenReturn(balance1);
        when(token2.mock.balanceOf(feeHandler.getAddress())).thenReturn(balance2);

        // Act
        feeHandler.invoke(caller, "routeFees");
        feeHandler.invoke(caller, "routeFees");
        feeHandler.invoke(caller, "routeFees");

        // Assert
        verify(token2.mock, times(2)).transfer(eq(mockBalanced.dex.getAddress()), eq(balance2), any(byte[].class));
        verify(token1.mock).transfer(eq(mockBalanced.router.getAddress()), eq(balance1), any(byte[].class));
    }

    @Test
    void manualRoute() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        Address[] path = new Address[]{bnusd.getAddress(), baln.getAddress()};
        MockContract<IRC2> token1 = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        MockContract<IRC2> token2 = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        BigInteger balance1 = BigInteger.valueOf(7);
        BigInteger balance2 = BigInteger.valueOf(3);

        when(token1.mock.balanceOf(feeHandler.getAddress())).thenReturn(balance1);
        when(token2.mock.balanceOf(feeHandler.getAddress())).thenReturn(balance2);

        // Act
        feeHandler.invoke(caller, "routeToken", token1.getAddress(), path);
        feeHandler.invoke(caller, "routeToken", token2.getAddress(), new Address[]{});

        // Assert
        verify(token1.mock).transfer(eq(mockBalanced.router.getAddress()), eq(balance1), any(byte[].class));
        verify(token2.mock).transfer(eq(mockBalanced.dex.getAddress()), eq(balance2), any(byte[].class));
    }

    @Test
    void manualRoute_restrictions() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        Address[] path = new Address[]{bnusd.getAddress(), baln.getAddress()};
        MockContract<IRC2> acceptedToken = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        MockContract<IRC2> quoteToken = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        MockContract<IRC2> tokenWithPath = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        MockContract<IRC2> zeroBalanceToken = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);

        Object acceptedTokens = new Address[]{acceptedToken.getAddress()};
        feeHandler.invoke(governance.account, "setAcceptedDividendTokens", acceptedTokens);
        feeHandler.invoke(governance.account, "setRoute", tokenWithPath.getAddress(), path);

        when(zeroBalanceToken.mock.balanceOf(feeHandler.getAddress())).thenReturn(BigInteger.ZERO);

        when(mockBalanced.dex.mock.isQuoteCoinAllowed(quoteToken.getAddress())).thenReturn(true);
        when(mockBalanced.dex.mock.isQuoteCoinAllowed(tokenWithPath.getAddress())).thenReturn(false);
        when(mockBalanced.dex.mock.isQuoteCoinAllowed(zeroBalanceToken.getAddress())).thenReturn(false);

        // Act
        Executable withAcceptedToken = () -> feeHandler.invoke(caller, "routeToken", acceptedToken.getAddress(), path);
        Executable withQuoteToken = () -> feeHandler.invoke(caller, "routeToken", quoteToken.getAddress(), path);
        Executable withDefinedPath = () -> feeHandler.invoke(caller, "routeToken", tokenWithPath.getAddress(), path);
        Executable withZeroBalance = () -> feeHandler.invoke(caller, "routeToken", zeroBalanceToken.getAddress(), path);

        // Assert
        expectErrorMessage(withAcceptedToken, "Token is accepted, can not be routed");
        expectErrorMessage(withQuoteToken, "Only non quote coins can manually be routed");
        expectErrorMessage(withDefinedPath, "Automatically routed tokens can't be manually routed");
        expectErrorMessage(withZeroBalance, zeroBalanceToken.getAddress() + " balance is 0");
    }

    @Test
    void configureRouteLimit_direct() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        BigInteger balnRouteLimit = (BigInteger) feeHandler.call("getBalnRouteLimit");
        BigInteger pid = BigInteger.TWO;
        BigInteger tokenPriceInBaln = BigInteger.valueOf(15).multiply(BigInteger.TEN.pow(17));
        BigInteger expectedLimit = balnRouteLimit.multiply(EXA).divide(tokenPriceInBaln);

        feeHandler.invoke(governance.account, "setRoute", token.getAddress(), new Address[]{});

        when(mockBalanced.dex.mock.getPoolId(mockBalanced.baln.getAddress(), token.getAddress())).thenReturn(pid);
        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(tokenPriceInBaln);
        when(mockBalanced.dex.mock.getPoolBase(pid)).thenReturn(token.getAddress());

        // Act
        feeHandler.invoke(caller, "calculateRouteLimit", token.getAddress());

        // Assert
        assertEquals(expectedLimit, feeHandler.call("getRouteLimit", token.getAddress()));
    }

    @Test
    void configureRouteLimit_direct_balnBase() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        BigInteger balnRouteLimit = (BigInteger) feeHandler.call("getBalnRouteLimit");
        BigInteger pid = BigInteger.TWO;
        BigInteger balnPriceInToken = BigInteger.valueOf(15).multiply(BigInteger.TEN.pow(17));
        BigInteger expectedLimit = balnRouteLimit.multiply(balnPriceInToken).divide(EXA);

        feeHandler.invoke(governance.account, "setRoute", token.getAddress(), new Address[]{});

        when(mockBalanced.dex.mock.getPoolId(mockBalanced.baln.getAddress(), token.getAddress())).thenReturn(pid);
        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(balnPriceInToken);
        when(mockBalanced.dex.mock.getPoolBase(pid)).thenReturn(baln.getAddress());

        // Act
        feeHandler.invoke(caller, "calculateRouteLimit", token.getAddress());

        // Assert
        assertEquals(expectedLimit, feeHandler.call("getRouteLimit", token.getAddress()));
    }

    @Test
    void configureRouteLimit_path() throws Exception {
        // Arrange
        Account caller = sm.createAccount();
        Address[] path = new Address[]{sicx.getAddress(), bnusd.getAddress(), baln.getAddress()};
        MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);
        BigInteger balnRouteLimit = (BigInteger) feeHandler.call("getBalnRouteLimit");

        BigInteger pidTokenSicx = BigInteger.ONE;
        BigInteger pidSicxBnUSD = BigInteger.TWO;
        BigInteger pidBnUSDBaln = BigInteger.valueOf(3);

        BigInteger tokenPriceInSicx = BigInteger.valueOf(7).multiply(BigInteger.TEN.pow(17));
        BigInteger sicxPriceInBnUSD = BigInteger.valueOf(2).multiply(BigInteger.TEN.pow(18));
        BigInteger balnPriceInBnUSD = BigInteger.valueOf(15).multiply(BigInteger.TEN.pow(17));

        BigInteger amountOfBnUSD = balnRouteLimit.multiply(balnPriceInBnUSD).divide(EXA);
        BigInteger amountOfSicx = amountOfBnUSD.multiply(EXA).divide(sicxPriceInBnUSD);
        BigInteger expectedLimit = amountOfSicx.multiply(EXA).divide(tokenPriceInSicx);

        feeHandler.invoke(governance.account, "setRoute", token.getAddress(), path);

        when(mockBalanced.dex.mock.getPoolId(sicx.getAddress(), token.getAddress())).thenReturn(pidTokenSicx);
        when(mockBalanced.dex.mock.getPoolId(bnusd.getAddress(), sicx.getAddress())).thenReturn(pidSicxBnUSD);
        when(mockBalanced.dex.mock.getPoolId(baln.getAddress(), bnusd.getAddress())).thenReturn(pidBnUSDBaln);

        when(mockBalanced.dex.mock.getPrice(pidTokenSicx)).thenReturn(tokenPriceInSicx);
        when(mockBalanced.dex.mock.getPrice(pidSicxBnUSD)).thenReturn(sicxPriceInBnUSD);
        when(mockBalanced.dex.mock.getPrice(pidBnUSDBaln)).thenReturn(balnPriceInBnUSD);

        when(mockBalanced.dex.mock.getPoolBase(pidTokenSicx)).thenReturn(token.getAddress());
        when(mockBalanced.dex.mock.getPoolBase(pidSicxBnUSD)).thenReturn(sicx.getAddress());
        when(mockBalanced.dex.mock.getPoolBase(pidBnUSDBaln)).thenReturn(baln.getAddress());

        // Act
        feeHandler.invoke(caller, "calculateRouteLimit", token.getAddress());

        // Assert
        assertEquals(expectedLimit, feeHandler.call("getRouteLimit", token.getAddress()));
    }

    @Test
    void configureRouteLimit_limits() throws Exception {
        Account caller = sm.createAccount();
        MockContract<IRC2> token = new MockContract<>(IRC2ScoreInterface.class, IRC2.class, sm, owner);

        Executable withNoRoute = () -> feeHandler.invoke(caller, "calculateRouteLimit", token.getAddress());
        expectErrorMessage(withNoRoute, "No Route exists for " + token.getAddress());
    }
}