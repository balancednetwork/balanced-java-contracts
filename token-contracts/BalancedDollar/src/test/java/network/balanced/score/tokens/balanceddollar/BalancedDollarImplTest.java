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

    private String name = "Balanced Dollar";

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void deploy() throws Exception {
        bnUSDScore = sm.deploy(owner, BalancedDollarImpl.class, governanceScore.getAddress());

        bnUSDSpy = (BalancedDollarImpl) spy(bnUSDScore.getInstance());
        bnUSDScore.setInstance(bnUSDSpy);
        contextMock.when(() -> Context.call(eq( governanceScore.getAddress()), eq("checkStatus"), any(String.class))).thenReturn(null);
    }

    private void setup() {
        bnUSDScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        bnUSDScore.invoke(governanceScore, "setOracle", oracleScore.getAddress());
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
        testGovernanceControlMethods(bnUSDScore, governanceScore, owner, "setOracle", oracleScore.getAddress(),
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
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() + " Authorized " +
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

    @Test
    void getSetMinter2() {
        Account nonContractMinter = sm.createAccount();
        Account minter2 = Account.newScoreAccount(scoreCount++);

        String expectedErrorMessage = "Reverted(0): Address Check: Address provided is an EOA address. A contract address is required.";
        Executable nonContractSet = () ->  bnUSDScore.invoke(owner, "setMinter2", nonContractMinter.getAddress());
        expectErrorMessage(nonContractSet, expectedErrorMessage);

        bnUSDScore.invoke(owner, "setMinter2", minter2.getAddress());
        assertEquals(minter2.getAddress(), bnUSDScore.call("getMinter2"));
    }

    @Test
    void mintTo_withTwoMinters() {
        bnUSDScore.invoke(owner, "setMinter", owner.getAddress());
        Account newReceiver = sm.createAccount();
        Account minter2 = Account.newScoreAccount(scoreCount++);
        mintTests(owner);
        bnUSDScore.invoke(owner, "setMinter2", minter2.getAddress());
        mintTests(owner);
        mintTests(minter2);

    }

    public void mintTests(Account minter) {
        Account newReceiver = sm.createAccount();
        BigInteger mintAmount = BigInteger.valueOf(193).multiply(ICX);
        Address minter2 = (Address) bnUSDScore.call("getMinter2");

        Account nonMinter = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonMinter.getAddress() + " Authorized Caller:" +
                        " " + owner.getAddress() + " or " + minter2;
        Executable nonMinterCall = () -> bnUSDScore.invoke(nonMinter, "mint", mintAmount, new byte[0]);
        expectErrorMessage(nonMinterCall, expectedErrorMessage);

        Executable nonMinterMintToCall = () -> bnUSDScore.invoke(nonMinter, "mintTo", sm.createAccount().getAddress()
                , mintAmount, new byte[0]);
        expectErrorMessage(nonMinterMintToCall, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): " + name+ ": Owner address cannot be zero address";
        Executable zeroAddressMint = () -> bnUSDScore.invoke(minter, "mintTo", new Address(new byte[Address.LENGTH]),
                mintAmount, new byte[0]);
        expectErrorMessage(zeroAddressMint, expectedErrorMessage);

        expectedErrorMessage ="Reverted(0): " + name + ": Amount needs to be positive";
        Executable negativeAmountMint = () -> bnUSDScore.invoke(minter, "mintTo", newReceiver.getAddress(),
                ICX.negate(), new byte[0]);
        expectErrorMessage(negativeAmountMint, expectedErrorMessage);

        BigInteger beforeTotalSupply = (BigInteger) bnUSDScore.call("totalSupply");
        bnUSDScore.invoke(minter, "mintTo", newReceiver.getAddress(), mintAmount, new byte[0]);
        assertEquals(mintAmount, bnUSDScore.call("balanceOf", newReceiver.getAddress()));
        assertEquals(beforeTotalSupply.add(mintAmount), bnUSDScore.call("totalSupply"));
        verify(bnUSDSpy).Transfer(new Address(new byte[Address.LENGTH]), newReceiver.getAddress(), mintAmount, "mint".getBytes());
    }


    @AfterEach
    void contextClose() {
        contextMock.close();
    }
}
