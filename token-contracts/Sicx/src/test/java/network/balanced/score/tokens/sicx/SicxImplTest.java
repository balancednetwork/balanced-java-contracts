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

package network.balanced.score.tokens.sicx;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.test.UnitTest.scoreCount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

class SicxImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account staking = Account.newScoreAccount(scoreCount++);
    private static final Account scoreAddress = Account.newScoreAccount(scoreCount++);
    private static final Account user = sm.createAccount();
    private Score sicxScore;

    private static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class,
            Mockito.CALLS_REAL_METHODS);

    private static final MockedStatic.Verification getTodayRate = () -> Context.call(staking.getAddress(),
            "getTodayRate");
    private static final MockedStatic.Verification tokenFallback = () -> Context.call(any(Address.class), eq(
            "tokenFallback"), any(Address.class), any(BigInteger.class), any(byte[].class));
    private static final MockedStatic.Verification transferUpdateDelegations = () -> Context.call(any(Address.class),
            eq("transferUpdateDelegations"), any(Address.class), any(Address.class), any(BigInteger.class));

    @BeforeEach
    void setup() throws Exception {
        sicxScore = sm.deploy(owner, SicxImpl.class, staking.getAddress());
        assertEquals(staking.getAddress(), sicxScore.call("getStaking"));
        sicxScore.invoke(owner, "setMinter", staking.getAddress());
    }

    @Test
    void name() {
        assertEquals("Staked ICX", sicxScore.call("name"));
    }

    @Test
    void totalSupply() {
        assertEquals(BigInteger.ZERO, sicxScore.call("totalSupply"));
    }

    @Test
    void symbol() {
        assertEquals("sICX", sicxScore.call("symbol"));
    }

    @Test
    void decimals() {
        assertEquals(BigInteger.valueOf(18L), sicxScore.call("decimals"));
    }

    @Test
    void minter() {
        assertEquals(staking.getAddress(), sicxScore.call("getMinter"));

        // set user as minter
        sicxScore.invoke(owner, "setMinter", user.getAddress());
        assertEquals(user.getAddress(), sicxScore.call("getMinter"));
    }

    @Test
    void stakingAddress() {
        // setStakingAddress not called by owner
        Executable InvalidValue = () -> sicxScore.invoke(user, "setStaking", staking.getAddress());
        String expectedErrorMessage =
                "Reverted(0): SenderNotScoreOwner: Sender=" + user.getAddress() + "Owner=" + owner.getAddress();
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        assertEquals(expectedErrorMessage, e.getMessage());

        // setStakingAddress called by owner
        sicxScore.invoke(owner, "setStaking", staking.getAddress());
        assertEquals(staking.getAddress(), sicxScore.call("getStaking"));
    }

    @Test
    void getPeg() {
        assertEquals("sICX", sicxScore.call("getPeg"));
    }

    @Test
    void priceInLoop() {
        contextMock.when(getTodayRate).thenReturn(BigInteger.ONE);
        assertEquals(BigInteger.ONE, sicxScore.call("priceInLoop"));
    }

    @Test
    void lastPriceInLoop() {
        contextMock.when(getTodayRate).thenReturn(BigInteger.ONE);
        assertEquals(BigInteger.ONE, sicxScore.call("lastPriceInLoop"));
    }

    @Test
    void burn() {
        // sender not admin
        Executable invalidValue = () -> sicxScore.invoke(owner, "burn", new BigInteger("10"));
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " " +
                "Authorized Caller: " + staking.getAddress();
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // condition where user's balances is less than burn amount
        invalidValue = () -> sicxScore.invoke(staking, "burn", new BigInteger("1000"));
        expectedErrorMessage = "Staked ICX: Insufficient Balance";
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // mint 1000 sicx for owner
        sicxScore.invoke(staking, "mintTo", owner.getAddress(), new BigInteger("1000"), "".getBytes());

        //mint 100 sicx for stakingAddress
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mintTo", staking.getAddress(), new BigInteger("100"), "data".getBytes());
        contextMock.verify(tokenFallback);

        // condition where userBalance is less than 0
        invalidValue = () -> sicxScore.invoke(staking, "burn", new BigInteger("1000").negate());
        expectedErrorMessage = "Staked ICX: Amount needs to be positive";
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // invoke real burn method
        sicxScore.invoke(staking, "burn", new BigInteger("50"));
        assertEquals(new BigInteger("1050"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("50"), sicxScore.call("balanceOf", staking.getAddress()));

        // burnFrom method
        sicxScore.invoke(staking, "burnFrom", owner.getAddress(), new BigInteger("50"));
        assertEquals(new BigInteger("1000"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("950"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("50"), sicxScore.call("balanceOf", staking.getAddress()));
    }

    @Test
    void mint() {
        String data = "";
        // sender not admin
        Executable invalidValue = () -> sicxScore.invoke(owner, "mint", BigInteger.ZERO, data.getBytes());
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " " +
                "Authorized Caller: " + staking.getAddress();
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // invoke real method
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mint", new BigInteger("1000"), data.getBytes());
        contextMock.verify(tokenFallback);
        assertEquals(new BigInteger("1000"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", staking.getAddress()));
    }

    @Test
    void mintTo() {
        String data = "";

        // sender not admin
        Executable invalidValue = () -> sicxScore.invoke(owner, "mintTo", user.getAddress(), BigInteger.ZERO,
                data.getBytes());
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " " +
                "Authorized Caller: " + staking.getAddress();
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // invoke real method
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("1000"), data.getBytes());
        assertEquals(new BigInteger("1000"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", user.getAddress()));

        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mintTo", scoreAddress.getAddress(), new BigInteger("1000"), "data".getBytes());
        contextMock.verify(tokenFallback);
    }

    @Test
    void transfer() {
        String data = "";
        // trying to transfer sicx more than balance
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("50"), data.getBytes());
        Executable invalidValue = () -> sicxScore.invoke(user, "transfer", staking.getAddress(), new BigInteger("60")
                , data.getBytes());
        String expectedErrorMessage = "Staked ICX: Insufficient balance";
        expectErrorMessage(invalidValue, expectedErrorMessage);

        // transfer amount to another user
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("50"), data.getBytes());
        contextMock.when(transferUpdateDelegations).thenReturn(null);
        sicxScore.invoke(user, "transfer", owner.getAddress(), new BigInteger("30"), data.getBytes());
        contextMock.verify(transferUpdateDelegations);

        assertEquals(new BigInteger("30"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("70"), sicxScore.call("balanceOf", user.getAddress()));

        // transferring sICX to contract address
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(user, "transfer", scoreAddress.getAddress(), new BigInteger("10"), "data".getBytes());
        contextMock.verify(tokenFallback);
    }

    @AfterEach
    void resetMock() {
        contextMock.reset();
    }
}
