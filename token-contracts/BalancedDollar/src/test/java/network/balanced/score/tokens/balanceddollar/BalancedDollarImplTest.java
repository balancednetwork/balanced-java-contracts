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

package network.balanced.score.tokens.balanceddollar;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BalancedDollarImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account admin = sm.createAccount();

    private static final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private static final Account oracleScore = Account.newScoreAccount(scoreCount++);

    private static Score bnUSDScore;
    private BalancedDollarImpl bnUSDSpy;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void deploy() throws Exception {
        bnUSDScore = sm.deploy(owner, BalancedDollarImpl.class, governanceScore.getAddress());

        bnUSDSpy = (BalancedDollarImpl) spy(bnUSDScore.getInstance());
        bnUSDScore.setInstance(bnUSDSpy);
    }

    private void setup() {
        bnUSDScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        bnUSDScore.invoke(admin, "setOracle", oracleScore.getAddress());
    }

    @Test
    void peg() {
        assertEquals("USD", bnUSDScore.call("getPeg"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(bnUSDScore, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(bnUSDScore, governanceScore, admin);
    }

    @Test
    void setAndGetOracle() {
        testContractSettersAndGetters(bnUSDScore, governanceScore, admin, "setOracle", oracleScore.getAddress(),
                "getOracle");
    }

    @Test
    void setAndGetOracleName() {
        assertEquals("BandChain", bnUSDScore.call("getOracleName"));

        String newOracleName = "ChainLink";
        testAdminControlMethods(bnUSDScore, governanceScore, admin, "setOracleName", newOracleName, "getOracleName");
    }

    @Test
    void setAndGetMinIntervalTime() {
        BigInteger THIRTY_SECONDS = BigInteger.valueOf(30_000_000);
        assertEquals(THIRTY_SECONDS, bnUSDScore.call("getMinInterval"));

        BigInteger newMinInterval = BigInteger.valueOf(20_000_000);
        testAdminControlMethods(bnUSDScore, governanceScore, admin, "setMinInterval", newMinInterval, "getMinInterval");
    }

    @Test
    void priceInLoop() {
        setup();
        BigInteger newBnusdPrice = BigInteger.valueOf(120).multiply(BigInteger.TEN.pow(16));

        Map<String, Object> priceData = Map.of("last_update_base", "0x5dd26d3bd5dcf", "last_update_quote",
                "0x5dd26cb9b4680", "rate", newBnusdPrice);
        contextMock.when(() -> Context.call(any(Address.class), eq("get_reference_data"), eq("USD"), eq("ICX"))).thenReturn(priceData);

        bnUSDScore.invoke(owner, "priceInLoop");
        verify(bnUSDSpy).OraclePrice("USDICX", "BandChain", oracleScore.getAddress(), newBnusdPrice);
        assertEquals(BigInteger.valueOf(sm.getBlock().getTimestamp()), bnUSDScore.call("getPriceUpdateTime"));
        assertEquals(newBnusdPrice, bnUSDScore.call("priceInLoop"));

        assertEquals(newBnusdPrice, bnUSDScore.call("lastPriceInLoop"));
    }

    @Test
    void govTransfer() {
        setup();

        Account nonGovernance = Account.newScoreAccount(scoreCount++);
        String expectedErrorMessage =
                "Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() + " Authorized " +
                        "Caller: " + governanceScore.getAddress();
        Executable nonGovernanceTransfer = () -> bnUSDScore.invoke(nonGovernance, "govTransfer", owner.getAddress(),
                sm.createAccount().getAddress(), ICX, new byte[0]);
        expectErrorMessage(nonGovernanceTransfer, expectedErrorMessage);

        bnUSDScore.invoke(owner, "setMinter", owner.getAddress());
        bnUSDScore.invoke(owner, "mint", BigInteger.valueOf(123).multiply(ICX), new byte[0]);

        Account receiverAccount = sm.createAccount();
        bnUSDScore.invoke(governanceScore, "govTransfer", owner.getAddress(), receiverAccount.getAddress(), ICX,
                new byte[0]);
        assertEquals(ICX, bnUSDScore.call("balanceOf", receiverAccount.getAddress()));
    }

    @AfterEach
    void contextClose() {
        contextMock.close();
    }
}
