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

package network.balanced.score.tokens.balancedtoken;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.utils.Names;

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

import static java.math.BigInteger.*;
import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalancedTokenImplTest extends TestBase {
    protected static final Long DAY = 43200L;

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private Score balancedToken;
    private BalancedTokenImpl balancedTokenSpy;
    private Score mockDexScore;

    private final Account addressProvider = sm.createAccount();
    private final Account adminAccount = sm.createAccount();

    private final Account dexScore = Account.newScoreAccount(scoreCount++);
    private final Account oracleScore = Account.newScoreAccount(scoreCount++);
    private final Account bnusdScore = Account.newScoreAccount(scoreCount++);
    private final Account dividendsScore = Account.newScoreAccount(scoreCount++);


    private static MockBalanced mockBalanced;
    private static Account governanceScore;

    @BeforeEach
    void deploy() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governanceScore = mockBalanced.governance.account;

        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn("0x1.icon");
        balancedToken = sm.deploy(owner, BalancedTokenImpl.class, governanceScore.getAddress());
        mockDexScore = sm.deploy(owner, MockDexScore.class);

        balancedTokenSpy = (BalancedTokenImpl) spy(balancedToken.getInstance());
        balancedToken.setInstance(balancedTokenSpy);
    }

    @Test
    void getPeg() {
        assertEquals("BALN", balancedToken.call("getPeg"));
    }


    @SuppressWarnings("unchecked")
    @Test
    void ShouldMint() {
        balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

        BigInteger amount = BigInteger.valueOf(10000L);
        balancedToken.invoke(adminAccount, "mint", amount, "init gold".getBytes());
        BigInteger balance = (BigInteger) balancedToken.call("balanceOf", adminAccount.getAddress());

        assertEquals(amount, balance);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ShouldMintToSomeone() {
        Account accountToMint = sm.createAccount();
        BigInteger amount = BigInteger.valueOf(10000L);
        balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

        balancedToken.invoke(adminAccount, "mintTo", accountToMint.getAddress(), amount, "init gold".getBytes());

        BigInteger balance = (BigInteger) balancedToken.call("balanceOf", accountToMint.getAddress());

        assertEquals(amount, balance);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ShouldBurn() {
        balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());
        BigInteger amount = BigInteger.valueOf(10000L);
        balancedToken.invoke(adminAccount, "mint", amount, "init gold".getBytes());

        BigInteger balanceToBurn = amount.divide(TWO);
        balancedToken.invoke(adminAccount, "burn", balanceToBurn);

        BigInteger balance = (BigInteger) balancedToken.call("balanceOf", adminAccount.getAddress());

        assertEquals(amount.subtract(balanceToBurn), balance);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ShouldBurnFrom() {
        Account accountToMint = sm.createAccount();
        balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());
        BigInteger amount = BigInteger.valueOf(10000L);
        balancedToken.invoke(adminAccount, "mintTo", accountToMint.getAddress(), amount, "init gold".getBytes());
        BigInteger balanceToBurn = amount.divide(TWO);
        balancedToken.invoke(adminAccount, "burnFrom", accountToMint.getAddress(), balanceToBurn);


        BigInteger balance = (BigInteger) balancedToken.call("balanceOf", accountToMint.getAddress());

        assertEquals(amount.subtract(balanceToBurn), balance);
    }

    @Test
    void mintBurnPermissions() {
        Account nonMinter = sm.createAccount();
        balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

        Executable wrongMinter = () -> balancedToken.invoke(nonMinter, "mint", BigInteger.ONE, new byte[0]);
        expectErrorMessage(wrongMinter, "Authorization Check: Authorization failed.");

        wrongMinter = () -> balancedToken.invoke(nonMinter, "mintTo", adminAccount.getAddress(), BigInteger.ONE, new byte[0]);
        expectErrorMessage(wrongMinter, "Authorization Check: Authorization failed.");

        wrongMinter = () -> balancedToken.invoke(nonMinter, "burn", BigInteger.ONE);
        expectErrorMessage(wrongMinter, "Authorization Check: Authorization failed.");

        wrongMinter = () -> balancedToken.invoke(nonMinter, "burnFrom", adminAccount.getAddress(), BigInteger.ONE);
        expectErrorMessage(wrongMinter, "Authorization Check: Authorization failed.");
    }

}
