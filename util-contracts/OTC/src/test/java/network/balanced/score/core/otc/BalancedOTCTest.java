/*
 * Copyright (c) 2023-2023 Balanced.network.
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

package network.balanced.score.core.otc;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalancedOTCTest extends UnitTest {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;
    protected MockContract<IRC2> eth;
    protected MockContract<IRC2> btc;
    private Score otcScore;

    private static final BigInteger DEFAULT_PRICE = EXA;
    private static final String BTC_SYMBOL = "iBTC";
    private static final String ETH_SYMBOL = "IETH";

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        otcScore = sm.deploy(owner, BalancedOTC.class,
                mockBalanced.governance.getAddress());

        eth = new MockContract<>(IRC2ScoreInterface.class, sm, owner);
        when(eth.mock.symbol()).thenReturn(ETH_SYMBOL);
        when(eth.mock.decimals()).thenReturn(BigInteger.valueOf(18));

        btc = new MockContract<>(IRC2ScoreInterface.class, sm, owner);
        when(btc.mock.symbol()).thenReturn(BTC_SYMBOL);
        when(btc.mock.decimals()).thenReturn(BigInteger.valueOf(18));

        setPrice(ETH_SYMBOL, DEFAULT_PRICE);
        setPrice(BTC_SYMBOL, DEFAULT_PRICE);
    }

    @Test
    void name() {
        assertEquals(Names.BALANCED_OTC, otcScore.call("name"));
    }

    @Test
    void createOrder() {
        // Arrange
        BigInteger amount = EXA;

        // Act
        addOrder(amount, btc.getAddress());

        // Assert
        List<Map<String, Object>> orders = (List<Map<String, Object>>) otcScore.call("getOrders");
        Map<String, Object> order = orders.get(0);
        assertEquals(1, orders.size());
        assertEquals(btc.getAddress().toString(), order.get("token"));
        assertEquals(amount, order.get("orderSize"));
        assertEquals(otcScore.call("getDiscount", btc.getAddress()), order.get("discount"));
    }

    @Test
    void addToOrder() {
        // Arrange
        BigInteger amount1 = BigInteger.TWO.multiply(EXA);
        BigInteger amount2 = EXA;

        // Act
        addOrder(amount1, btc.getAddress());
        addOrder(amount2, btc.getAddress());

        // Assert
        List<Map<String, Object>> orders = (List<Map<String, Object>>) otcScore.call("getOrders");
        Map<String, Object> order = orders.get(0);
        assertEquals(1, orders.size());
        assertEquals(btc.getAddress().toString(), order.get("token"));
        assertEquals(amount1.add(amount2), order.get("orderSize"));
        assertEquals(otcScore.call("getDiscount", btc.getAddress()), order.get("discount"));
    }

    @Test
    void createOrder_restrictions() {
        Executable nonBnUSDToken = () -> otcScore.invoke(mockBalanced.sicx.account, "tokenFallback",
                mockBalanced.daofund.getAddress(), BigInteger.TEN,
                btc.getAddress().toString().getBytes());
        expectErrorMessage(nonBnUSDToken, "Authorization Check: Authorization failed");

        Executable notFromDaofund = () -> otcScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                mockBalanced.governance.getAddress(), BigInteger.TEN,
                btc.getAddress().toString().getBytes());
        expectErrorMessage(notFromDaofund, "Only daofund is allowed to create orders");

        setPrice(BTC_SYMBOL, null);
        Executable notOraclePrice = () -> otcScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                mockBalanced.daofund.getAddress(), BigInteger.TEN,
                btc.getAddress().toString().getBytes());
        expectErrorMessage(notOraclePrice, "Token must be supported by the balanced Oracle");

        when(btc.mock.decimals()).thenReturn(BigInteger.valueOf(17));
        Executable not18Decimals = () -> otcScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                mockBalanced.daofund.getAddress(), BigInteger.TEN,
                btc.getAddress().toString().getBytes());
        expectErrorMessage(not18Decimals, "Currently only supports purchase of tokens with 18 decimal precision");
    }

    @Test
    void buyPartial() {
        // Arrange
        Account buyer = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(100).multiply(EXA); // 100 bnUSD
        BigInteger discount = BigInteger.valueOf(200); // 2%
        BigInteger price = BigInteger.TWO.multiply(EXA); // 1 token = 2 bnUSD

        BigInteger btcToSell = BigInteger.TEN.multiply(EXA); // 10 BTC = 20 bnUSD
        BigInteger expectedReceived = BigInteger.valueOf(204).multiply(BigInteger.TEN.pow(17)); // 20.4 bnUSD

        setPrice(BTC_SYMBOL, price);
        otcScore.invoke(mockBalanced.governance.account, "setDiscount", btc.getAddress(), discount);

        addOrder(amount, btc.getAddress());

        // Act
        assertEquals(expectedReceived, getExpectedBnUSDAmount(btc.getAddress(), btcToSell));
        buyOrder(buyer.getAddress(), btc, btcToSell);

        // Assert
        verify(btc.mock).transfer(mockBalanced.daofund.getAddress(), btcToSell, new byte[0]);
        verify(mockBalanced.bnUSD.mock).transfer(buyer.getAddress(), expectedReceived, new byte[0]);
        List<Map<String, Object>> orders = (List<Map<String, Object>>) otcScore.call("getOrders");
        Map<String, Object> order = orders.get(0);
        assertEquals(1, orders.size());
        assertEquals(btc.getAddress().toString(), order.get("token"));
        assertEquals(amount.subtract(expectedReceived), order.get("orderSize"));
        assertEquals(discount, order.get("discount"));
    }

    @Test
    void buyAll() {
        // Arrange
        Account buyer = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(101).multiply(EXA); // 101 bnUSD
        BigInteger discount = BigInteger.valueOf(100); // 1%
        BigInteger price = BigInteger.TWO.multiply(EXA); // 1 token = 2 bnUSD

        BigInteger btcToSell = BigInteger.valueOf(50).multiply(EXA); // 50 BTC = 100 bnUSD

        setPrice(BTC_SYMBOL, price);
        otcScore.invoke(mockBalanced.governance.account, "setDiscount", btc.getAddress(), discount);

        addOrder(amount, btc.getAddress());

        // Act
        assertEquals(amount, getExpectedBnUSDAmount(btc.getAddress(), btcToSell));
        buyOrder(buyer.getAddress(), btc, btcToSell);

        // Assert
        verify(btc.mock).transfer(mockBalanced.daofund.getAddress(), btcToSell, new byte[0]);
        verify(mockBalanced.bnUSD.mock).transfer(buyer.getAddress(), amount, new byte[0]);
        List<Map<String, Object>> orders = (List<Map<String, Object>>) otcScore.call("getOrders");
        assertEquals(0, orders.size());
    }

    @Test
    void buyFromEmptyOrder() {
        // Arrange
        buyAll();
        Account buyer = sm.createAccount();

        // Act
        Executable buyFromEmptyOrder = () -> buyOrder(buyer.getAddress(), btc, BigInteger.ONE);
        expectErrorMessage(buyFromEmptyOrder, "Order does not exist");
    }

    @Test
    void buyAboveSize() {
        // Arrange
        Account buyer = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(101).multiply(EXA); // 101 bnUSD
        BigInteger discount = BigInteger.valueOf(100); // 1%
        BigInteger price = BigInteger.TWO.multiply(EXA); // 1 token = 2 bnUSD

        BigInteger btcToSell = BigInteger.valueOf(51).multiply(EXA); // 51 BTC = 102 bnUSD

        setPrice(BTC_SYMBOL, price);
        otcScore.invoke(mockBalanced.governance.account, "setDiscount", btc.getAddress(), discount);

        addOrder(amount, btc.getAddress());

        // Act & Assert
        String expectedErrorMessage = "Only " + amount + " left in the order for the token: " + btc.getAddress();
        Executable buyFromEmptyOrder = () -> buyOrder(buyer.getAddress(), btc, btcToSell);
        expectErrorMessage(buyFromEmptyOrder, expectedErrorMessage);

    }

    @Test
    void setGetDiscount() {
        BigInteger ethDiscount = BigInteger.valueOf(150);
        BigInteger btcDiscount = BigInteger.valueOf(200);

        // Act
        otcScore.invoke(mockBalanced.governance.account, "setDiscount", btc.getAddress(), btcDiscount);
        otcScore.invoke(mockBalanced.governance.account, "setDiscount", eth.getAddress(), ethDiscount);

        // Assert
        assertEquals(btcDiscount, otcScore.call("getDiscount", btc.getAddress()));
        assertEquals(ethDiscount, otcScore.call("getDiscount", eth.getAddress()));

        String expectedErrorMessage = "Discount can't be higher than 7.5%";
        Executable aboveMaxDiscount = () -> otcScore.invoke(mockBalanced.governance.account, "setDiscount",
                eth.getAddress(), BalancedOTC.MAX_DISCOUNT.add(BigInteger.ONE));
        expectErrorMessage(aboveMaxDiscount, expectedErrorMessage);
    }

    @Test
    void cancelOrder() {
        // Arrange
        BigInteger amount = BigInteger.valueOf(101).multiply(EXA);
        addOrder(amount, btc.getAddress());

        // Act
        otcScore.invoke(mockBalanced.governance.account, "cancelOrder", btc.getAddress());

        // Assert
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.daofund.getAddress(), amount, new byte[0]);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) otcScore.call("getOrders");
        assertEquals(0, orders.size());
    }

    private void setPrice(String symbol, BigInteger price) {
        when(mockBalanced.balancedOracle.mock.getPriceInUSD(symbol)).thenReturn(price);
        when(mockBalanced.balancedOracle.mock.getLastPriceInUSD(symbol)).thenReturn(price);
    }

    private void buyOrder(Address from, MockContract<IRC2> token, BigInteger amount) {
        otcScore.invoke(token.account, "tokenFallback", from, amount, new byte[0]);
    }

    private void addOrder(BigInteger bnUSDAmount, Address wantedToken) {
        otcScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), bnUSDAmount,
                wantedToken.toString().getBytes());
    }

    private BigInteger getExpectedBnUSDAmount(Address token, BigInteger amount) {
        return (BigInteger) otcScore.call("getExpectedBnUSDAmount", token, amount);
    }

}