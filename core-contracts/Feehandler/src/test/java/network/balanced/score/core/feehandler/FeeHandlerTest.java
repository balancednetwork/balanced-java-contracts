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
import org.junit.jupiter.api.Assertions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class FeeHandlerTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();

    public static final Account baln = Account.newScoreAccount(2);
    public static final Account router = Account.newScoreAccount(22);
    public static final Account dividends = Account.newScoreAccount(23);
    public static final Account dex = Account.newScoreAccount(24);
    public static final Account sicxScore = Account.newScoreAccount(25);
    public static final Account bnusd = Account.newScoreAccount(26);
    public static final Account governance = Account.newScoreAccount(26);

    private Score feehandler;

    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(22);

    private static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() throws Exception {
        feehandler = sm.deploy(owner, FeeHandler.class, governance.getAddress());
        assert (feehandler.getAddress().isContract());
    }

    public void expectErrorMessage(Executable contractCall, String errorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(errorMessage, e.getMessage());
    }

    private void get_allowed_address(int i) {
        feehandler.call("get_allowed_address", i);
    }

    private void route_contract_balances() {
        feehandler.invoke(owner, "route_contract_balances");
    }

    @Test
    void name() {
        String contractName = "Balanced FeeHandler";
        assertEquals(contractName, feehandler.call("name"));
    }

    @Test
    void enableAndDisable() {
        feehandler.invoke(governance, "enable");
        assertEquals(true, feehandler.call("isEnabled"));

        feehandler.invoke(governance, "disable");
        assertEquals(false, feehandler.call("isEnabled"));
    }

    @Test
    void setAcceptedDividendTokens_NoPreviousTokens() {
        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicxScore.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feehandler.invoke(governance, "setAcceptedDividendTokens", addAcceptedTokens);

        assertEquals(expectedTokens, feehandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_PreviousTokens() {
        Object token = new Address[]{bnusd.getAddress()};
        feehandler.invoke(governance, "setAcceptedDividendTokens", token);

        List<Address> expectedTokens = new ArrayList<>();
        expectedTokens.add(sicxScore.getAddress());
        expectedTokens.add(baln.getAddress());
        Object addAcceptedTokens = expectedTokens.toArray(new Address[0]);

        feehandler.invoke(governance, "setAcceptedDividendTokens", addAcceptedTokens);

        assertEquals(expectedTokens, feehandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setGetRoute() {
        feehandler.invoke(governance, "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[]{bnusd.getAddress()});
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("fromToken", sicxScore.getAddress());
        expectedResult.put("toToken", baln.getAddress());
        expectedResult.put("path", List.of(bnusd.getAddress().toString()));

        Map<String, Object> actualResult = (Map<String, Object>) feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress());

        assertEquals(expectedResult, actualResult);
    }

    @Test
    void getEmptyRoute() {
        feehandler.invoke(governance, "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[]{bnusd.getAddress()});

        assertEquals(Map.of(), feehandler.call("getRoute", bnusd.getAddress(), baln.getAddress()));
    }

    @Test
    void deleteRoute() {
        feehandler.invoke(governance, "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[]{bnusd.getAddress()});

        feehandler.invoke(governance, "deleteRoute", sicxScore.getAddress(), baln.getAddress());

        assertEquals(Map.of(), feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress()));
    }

    @Test
    void setFeeProcessingInterval() {
        feehandler.invoke(governance, "setFeeProcessingInterval", BigInteger.TEN);

        assertEquals(BigInteger.TEN, feehandler.call("getFeeProcessingInterval"));
    }

    @Test
    void tokenFallback() {
        setAcceptedDividendTokens_NoPreviousTokens();
        setFeeProcessingInterval();

        feehandler.invoke(governance, "enable");
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dividends"))).thenReturn(dividends.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class), any(byte[].class))).thenReturn("done");

        feehandler.invoke(sicxScore, "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);

        contextMock.verify(() -> Context.call(any(Address.class), eq("transfer"), eq(dividends.getAddress()), any(BigInteger.class), any(byte[].class)));
    }

    @Test
    void add_get_allowed_address() {
        List<Address> expected = new ArrayList<>();
        expected.add(sicxScore.getAddress());

        feehandler.invoke(owner, "add_allowed_address", sicxScore.getAddress());

        assertEquals(expected, feehandler.call("get_allowed_address", 0));
    }

    @Test
    void get_allowed_address_NegativeOffset() {
        feehandler.invoke(owner, "add_allowed_address", sicxScore.getAddress());
        Executable contractCall = () -> get_allowed_address(-1);

        String expectedErrorMessage = "Negative value not allowed.";
        expectErrorMessage(contractCall, expectedErrorMessage);
    }

    @Test
    void route_contract_balances_with_path() {
        add_get_allowed_address();
        setGetRoute();

        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("baln"))).thenReturn(baln.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dividends"))).thenReturn(dividends.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("router"))).thenReturn(router.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class), any(byte[].class))).thenReturn("done");

        feehandler.invoke(owner, "route_contract_balances");

        contextMock.verify(() -> Context.call(any(Address.class), eq("transfer"), eq(router.getAddress()), any(BigInteger.class), any(byte[].class)));
    }

    @Test
    void route_contract_balances_with_empty_path() {
        feehandler.invoke(owner, "add_allowed_address", bnusd.getAddress());
        setGetRoute();

        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.TEN);
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("baln"))).thenReturn(baln.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dividends"))).thenReturn(dividends.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getContractAddress"), eq("dex"))).thenReturn(dex.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class), any(byte[].class))).thenReturn("done");

        feehandler.invoke(owner, "route_contract_balances");

        contextMock.verify(() -> Context.call(any(Address.class), eq("transfer"), eq(dex.getAddress()), any(BigInteger.class), any(byte[].class)));
    }

    @Test
    void route_contract_balances_with_no_fee() {
        feehandler.invoke(owner, "add_allowed_address", bnusd.getAddress());
        setGetRoute();
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.ZERO);

        Executable contractCall = this::route_contract_balances;

        String expectedErrorMessage = "No fees on the contract.";
        expectErrorMessage(contractCall, expectedErrorMessage);
    }
}