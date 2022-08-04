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

package network.balanced.score.core.feehandler;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.feehandler.FeeHandlerImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

class FeeHandlerImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private final Account admin = sm.createAccount();

    private static final Account baln = Account.newScoreAccount(scoreCount++);
    private static final Account router = Account.newScoreAccount(scoreCount++);
    private static final Account dividends = Account.newScoreAccount(scoreCount++);
    private static final Account dex = Account.newScoreAccount(scoreCount++);
    private static final Account loans = Account.newScoreAccount(scoreCount++);
    private static final Account stabilityFund = Account.newScoreAccount(scoreCount++);
    private static final Account sicxScore = Account.newScoreAccount(scoreCount++);
    private static final Account bnusd = Account.newScoreAccount(scoreCount++);
    private static final Account governance = Account.newScoreAccount(scoreCount++);

    private Score feeHandler;

    private static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class,
            Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() throws Exception {
        feeHandler = sm.deploy(owner, FeeHandlerImpl.class, governance.getAddress());
        assert (feeHandler.getAddress().isContract());
    }

    private void getAllowedAddress(int i) {
        feeHandler.call("get_allowed_address", i);
    }

    private void routeContractBalances() {
        feeHandler.invoke(owner, "route_contract_balances");
    }

    void setAdmin() {
        feeHandler.invoke(governance, "setAdmin", admin.getAddress());
    }

    @Test
    void name() {
        String contractName = "Balanced FeeHandler";
        assertEquals(contractName, feeHandler.call("name"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(feeHandler, governance, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(feeHandler, governance, admin);
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
        String expectedErrorMessage = "Authorization Check: Address not set";
        Executable actWithoutAdmin = () -> feeHandler.invoke(admin, setterMethod);
        expectErrorMessage(actWithoutAdmin, expectedErrorMessage);

        testAdmin(feeHandler, governance, admin);

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + admin.getAddress();
        Executable enableNotFromAdmin = () -> feeHandler.invoke(nonAdmin, setterMethod);
        expectErrorMessage(enableNotFromAdmin, expectedErrorMessage);

        feeHandler.invoke(admin, setterMethod);
        assertEquals(expectedValue, feeHandler.call("isEnabled"));
    }

    @Test
    void setAcceptedDividendTokens_NoPreviousTokens() {
        setAdmin();
        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicxScore.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feeHandler.invoke(admin, "setAcceptedDividendTokens", addAcceptedTokens);
        assertEquals(expectedTokens, feeHandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_PreviousTokens() {
        setAdmin();

        Object token = new Address[]{bnusd.getAddress(), bnusd.getAddress(), bnusd.getAddress()};
        feeHandler.invoke(admin, "setAcceptedDividendTokens", token);

        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicxScore.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feeHandler.invoke(admin, "setAcceptedDividendTokens", addAcceptedTokens);
        assertEquals(expectedTokens, feeHandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_GreaterThanMaxSize() {
        setAdmin();
        int tokensCount = 11;
        List<Address> tokens = new ArrayList<>();
        for (int i = 0; i < tokensCount; i++) {
            tokens.add(Account.newScoreAccount(scoreCount++).getAddress());
        }
        Executable maxAcceptedTokens = () -> feeHandler.invoke(admin, "setAcceptedDividendTokens",
                (Object) tokens.toArray(new Address[0]));
        String expectedErrorMessage = TAG + ": There can be a maximum of 10 accepted dividend tokens.";
        expectErrorMessage(maxAcceptedTokens, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void setGetRoute() {
        setAdmin();
        feeHandler.invoke(admin, "setRoute", sicxScore.getAddress(), baln.getAddress(),
                new Address[]{bnusd.getAddress()});
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("fromToken", sicxScore.getAddress());
        expectedResult.put("toToken", baln.getAddress());
        expectedResult.put("path", List.of(bnusd.getAddress().toString()));

        Map<String, Object> actualResult = (Map<String, Object>) feeHandler.call("getRoute", sicxScore.getAddress(),
                baln.getAddress());
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void getEmptyRoute() {
        setAdmin();
        feeHandler.invoke(admin, "setRoute", sicxScore.getAddress(), baln.getAddress(),
                new Address[]{bnusd.getAddress()});
        assertEquals(Map.of(), feeHandler.call("getRoute", bnusd.getAddress(), baln.getAddress()));
    }

    @Test
    void deleteRoute() {
        setAdmin();
        feeHandler.invoke(admin, "setRoute", sicxScore.getAddress(), baln.getAddress(),
                new Address[]{bnusd.getAddress()});
        feeHandler.invoke(admin, "deleteRoute", sicxScore.getAddress(), baln.getAddress());
        assertEquals(Map.of(), feeHandler.call("getRoute", sicxScore.getAddress(), baln.getAddress()));
    }

    @Test
    void setFeeProcessingInterval() {
        setAdmin();
        feeHandler.invoke(admin, "setFeeProcessingInterval", BigInteger.TEN);
        assertEquals(BigInteger.TEN, feeHandler.call("getFeeProcessingInterval"));
    }

    @Test
    void tokenFallback() {
        setAcceptedDividendTokens_NoPreviousTokens();
        setFeeProcessingInterval();
        feeHandler.invoke(admin, "disable");

        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dividends"))).thenReturn(dividends.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class), any(byte[].class))).thenReturn("done");

        feeHandler.invoke(sicxScore, "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);
        contextMock.verify(() -> Context.call(sicxScore.getAddress(), "transfer", dividends.getAddress(),
                BigInteger.TEN, new byte[0]), times(0));

                feeHandler.invoke(admin, "enable");
                feeHandler.invoke(sicxScore, "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);
        contextMock.verify(() -> Context.call(sicxScore.getAddress(), "transfer", dividends.getAddress(),
                BigInteger.TEN, new byte[0]));

        contextMock.clearInvocations();
        feeHandler.invoke(sicxScore, "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);
        contextMock.verify(() -> Context.call(sicxScore.getAddress(), "transfer", dividends.getAddress(),
                BigInteger.TEN, new byte[0]), times(0));
    }

    @Test
    void addAndGetAllowedAddress() {
        List<Address> expected = new ArrayList<>();
        expected.add(sicxScore.getAddress());
        expected.add(bnusd.getAddress());

        feeHandler.invoke(owner, "addAllowedAddress", sicxScore.getAddress());
        feeHandler.invoke(owner, "addAllowedAddress", bnusd.getAddress());
        assertEquals(expected, feeHandler.call("get_allowed_address", 0));
    }

    @Test
    void get_allowed_address_NegativeOffset() {
        feeHandler.invoke(owner, "addAllowedAddress", sicxScore.getAddress());
        Executable contractCall = () -> getAllowedAddress(-1);

        String expectedErrorMessage = "Negative value not allowed.";
        expectErrorMessage(contractCall, expectedErrorMessage);
    }

    @Test
    void routeContractBalances_withPath() {
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("baln"))).thenReturn(baln.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dividends"))).thenReturn(dividends.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("router"))).thenReturn(router.getAddress());
        contextMock.when(() -> Context.call(governance.getAddress(), "getContractAddress", "dex")).thenReturn(dex.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class), any(byte[].class))).thenReturn("done");

        Executable noAllowedAddressSet = () -> feeHandler.invoke(owner, "route_contract_balances");
        String expectedErrorMessage = TAG + ": No allowed addresses.";
        expectErrorMessage(noAllowedAddressSet, expectedErrorMessage);

        addAndGetAllowedAddress();
        setGetRoute();

        feeHandler.invoke(owner, "route_contract_balances");
        contextMock.verify(() -> Context.call(eq(sicxScore.getAddress()), eq("transfer"), eq(router.getAddress()),
                any(BigInteger.class), any(byte[].class)));
        assertEquals(1, feeHandler.call("getNextAllowedAddressIndex"));

        feeHandler.invoke(owner, "route_contract_balances");
        contextMock.verify(() -> Context.call(eq(bnusd.getAddress()), eq("transfer"), eq(dex.getAddress()),
                any(BigInteger.class), any(byte[].class)));
        assertEquals(0, feeHandler.call("getNextAllowedAddressIndex"));
    }

    @Test
    void routeContractBalances_withNoFee() {
        addAndGetAllowedAddress();
        setGetRoute();

        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.ZERO);
        Executable contractCall = this::routeContractBalances;
        String expectedErrorMessage = TAG + ": No fees on the contract.";
        expectErrorMessage(contractCall, expectedErrorMessage);
    }

    @Test
    void feeData() {
        // Arrange
        setAdmin();
        Account usdc = Account.newScoreAccount(scoreCount++);
        feeHandler.invoke(owner, "setLoans", loans.getAddress());
        feeHandler.invoke(owner, "setDex", dex.getAddress());
        feeHandler.invoke(owner, "setStabilityFund", stabilityFund.getAddress());

        BigInteger loanFee1 = BigInteger.valueOf(5).multiply(ICX);
        BigInteger loanFee2 = BigInteger.valueOf(1).multiply(ICX);
        BigInteger dexFeeICX = BigInteger.valueOf(12).multiply(ICX);
        BigInteger dexFeeBnusd = BigInteger.valueOf(4).multiply(ICX);
        BigInteger dexFeeBaln = BigInteger.valueOf(7).multiply(ICX);
        BigInteger dexFeeUsdc = BigInteger.valueOf(9).multiply(ICX);
        BigInteger StabilityFeeBnsud = BigInteger.valueOf(20).multiply(ICX);

        Object token = new Address[]{sicxScore.getAddress(), bnusd.getAddress(), baln.getAddress()};
        feeHandler.invoke(admin, "setAcceptedDividendTokens", token);
        feeHandler.invoke(admin, "setSwapFeesAccruedDB");

        // Act
        feeHandler.invoke(bnusd, "tokenFallback", loans.getAddress(), loanFee1, new byte[0]);
        feeHandler.invoke(bnusd, "tokenFallback", loans.getAddress(), loanFee2, new byte[0]);

        feeHandler.invoke(sicxScore, "tokenFallback", dex.getAddress(), dexFeeICX, new byte[0]);
        feeHandler.invoke(bnusd, "tokenFallback", dex.getAddress(), dexFeeBnusd, new byte[0]);
        feeHandler.invoke(baln, "tokenFallback", dex.getAddress(), dexFeeBaln, new byte[0]);
        feeHandler.invoke(usdc, "tokenFallback", dex.getAddress(), dexFeeUsdc, new byte[0]);

        feeHandler.invoke(sicxScore, "tokenFallback", dex.getAddress(), dexFeeICX, new byte[0]);
        feeHandler.invoke(bnusd, "tokenFallback", dex.getAddress(), dexFeeBnusd, new byte[0]);
        feeHandler.invoke(baln, "tokenFallback", dex.getAddress(), dexFeeBaln, new byte[0]);
        feeHandler.invoke(usdc, "tokenFallback", dex.getAddress(), dexFeeUsdc, new byte[0]);

        feeHandler.invoke(bnusd, "tokenFallback", stabilityFund.getAddress(), StabilityFeeBnsud, new byte[0]);
        feeHandler.invoke(bnusd, "tokenFallback", stabilityFund.getAddress(), StabilityFeeBnsud, new byte[0]);

        assertEquals(loanFee1.add(loanFee2), feeHandler.call("getLoanFeesAccrued"));

        assertEquals(dexFeeICX.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken", sicxScore.getAddress()));
        assertEquals(dexFeeBnusd.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken", bnusd.getAddress()));
        assertEquals(dexFeeBaln.multiply(BigInteger.TWO), feeHandler.call("getSwapFeesAccruedByToken", baln.getAddress()));
        assertEquals(BigInteger.ZERO, feeHandler.call("getSwapFeesAccruedByToken", usdc.getAddress()));

        assertEquals(StabilityFeeBnsud.multiply(BigInteger.TWO), feeHandler.call("getStabilityFundFeesAccrued"));
    }  
}