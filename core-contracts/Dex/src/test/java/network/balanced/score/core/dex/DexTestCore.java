/*
 * Copyright (c) 2022 Balanced.network.
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

package network.balanced.score.core.dex;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import network.balanced.score.core.dex.utils.Const;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.stubbing.Answer;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.core.dex.utils.Const.SICXICX_POOL_ID;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;


public class DexTestCore extends DexTestBase {
    
    @BeforeEach
    public void configureContract() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        setupAddresses();
        super.setup();
    }

    @Test
    void fallback() {
        Account account = sm.createAccount();
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        contextMock.when(() -> Context.getValue()).thenReturn(icxValue);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.call(any(Address.class), eq("getTodayRate"))).thenReturn(EXA);
        dexScore.invoke(ownerAccount, "fallback");

        BigInteger poolId = BigInteger.valueOf(SICXICX_POOL_ID);
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger lpBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), BigInteger.valueOf(SICXICX_POOL_ID));
        assertEquals(icxValue, lpBalance);
        assertEquals(lpBalance, poolStats.get("total_supply"));

        BigInteger additionIcxValue = BigInteger.valueOf(50L).multiply(EXA);
        contextMock.when(() -> Context.getValue()).thenReturn(additionIcxValue);
        dexScore.invoke(ownerAccount, "fallback");
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        lpBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), BigInteger.valueOf(SICXICX_POOL_ID));
        assertEquals(icxValue.add(additionIcxValue), lpBalance);
        assertEquals(icxValue.add(additionIcxValue), poolStats.get("total_supply"));

        dexScore.invoke(account, "fallback");
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger balanceOwner = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        BigInteger balanceAccount = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals(balanceOwner.add(balanceAccount), poolStats.get("total_supply"));
    }

    // Test fails on line with: activeAddresses.get(SICXICX_POOL_ID).remove(user);
    @Test
    void cancelSicxIcxOrder() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);

        turnDexOn();
        supplyIcxLiquidity(supplier, value);
        sm.getBlock().increase(100000);

        // Mock these.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.transfer(eq(supplier.getAddress()), eq(value))).thenAnswer((Answer<Void>) invocation -> null);
        
        // Act.
        dexScore.invoke(supplier, "cancelSicxicxOrder");

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(BigInteger.ZERO, IcxBalance);
    }

    @Test
    void withdrawSicxEarnings() {
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("getTodayRate"))).thenReturn(EXA);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(()->Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        BigInteger depositBalance = BigInteger.valueOf(100L).multiply(EXA);
        supplyIcxLiquidity(depositor, depositBalance);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);

        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert.
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);

        JsonObject jsonData = new JsonObject();
        jsonData.add("method", "_swap_icx");
        BigInteger swapValue = BigInteger.valueOf(50L).multiply(EXA);
        dexScore.invoke(sicxScore, "tokenFallback", depositor.getAddress(), swapValue, jsonData.toString().getBytes());

        BigInteger sicxEarning = (BigInteger) dexScore.call("getSicxEarnings", depositor.getAddress());
        dexScore.invoke(depositor, "withdrawSicxEarnings", sicxEarning);
        BigInteger newSicxEarning = (BigInteger) dexScore.call("getSicxEarnings", depositor.getAddress());
        assertEquals(BigInteger.ZERO, newSicxEarning);
    }

    @Test
    void getSicxEarnings() {
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("getTodayRate"))).thenReturn(EXA);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(()->Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        BigInteger depositBalance = BigInteger.valueOf(100L).multiply(EXA);
        supplyIcxLiquidity(depositor, depositBalance);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);

        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert.
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);

        JsonObject jsonData = new JsonObject();
        jsonData.add("method", "_swap_icx");
        BigInteger swapValue = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(sicxScore, "tokenFallback", depositor.getAddress(), swapValue, jsonData.toString().getBytes());

        BigInteger sicxEarning = (BigInteger) dexScore.call("getSicxEarnings", depositor.getAddress());
    }


    @Test
    void tokenFallback_deposit() {
        // Arrange.
        Account tokenScoreCaller = balnScore;
        Account tokenSender = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(1000000000);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act.
        dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), depositValue, tokenData("_deposit", new HashMap<>()));
        BigInteger retrievedDepositValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(), tokenSender.getAddress());

        // Assert.
        assertEquals(depositValue, retrievedDepositValue);
    }

    @Test
    void withdrawTokens_negativeAmount() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(-1000).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): Balanced DEX: Must specify a positive amount";
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawToken_insufficientBalance() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(1000).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): Balanced DEX: Insufficient Balance";
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawToken() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);
        
        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
    
        // Assert. 
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);
    }

    @Test
    void addLiquidity() {
        Account account = sm.createAccount();
        Account account1 = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(276L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(bnusdScore, "tokenFallback", account1.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account1.getAddress(), bnusdValue, data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);
        dexScore.invoke(account1, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals((Address) poolStats.get("base_token"), balnScore.getAddress());FIFTY.divide(BigInteger.TWO);
        assertEquals((Address) poolStats.get("quote_token"), bnusdScore.getAddress());
        assertEquals(bnusdValue.multiply(balnValue).sqrt(), balance);

        BigInteger account1_balance = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        assertEquals(balance.add(account1_balance), poolStats.get("total_supply"));
    }

    @Test
    void removeLiquidity_withdrawalLockActive() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Act & Assert.
        Executable fundsLocked = () -> dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
        expectErrorMessage(fundsLocked, "Reverted(0): Balanced DEX:  Assets must remain in the pool for 24 hours, please try again later.");
    }

    @Test
    void removeLiquidity() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        BigInteger usersLpTokens = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);

        // Arrange - increase blocks past withdrawal lock.
        sm.getBlock().increase(100000000);

         // Act & Assert.
         dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
         BigInteger usersLpTokensAfterRemoval = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
         assertEquals(usersLpTokens.subtract(lpTokensToRemove), usersLpTokensAfterRemoval);
    }

    @Test
    void tokenFallback_swap() {
        Account account = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);


        // test swap
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        JsonObject jsonData = new JsonObject();
        JsonObject params = new JsonObject();
        params.add("minimumReceive", BigInteger.valueOf(10L).toString());
        params.add("toToken", balnScore.getAddress().toString());
        jsonData.add("method", "_swap");
        jsonData.add("params", params);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(100L).multiply(EXA), jsonData.toString().getBytes());
        Map<String, Object> newPoolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger newBalance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        assertEquals(balance, newBalance);
    }

    @Test
    void tokenFallback_donate() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger initalBase = (BigInteger) poolStats.get("base");
        BigInteger initalQuote = (BigInteger) poolStats.get("quote");
        BigInteger initalSupply = (BigInteger) poolStats.get("total_supply");

        BigInteger bnusdDonation = BigInteger.valueOf(100L).multiply(EXA);
        JsonObject jsonData = new JsonObject();
        JsonObject params = new JsonObject();
        params.add("toToken", balnScore.getAddress().toString());
        jsonData.add("method", "_donate");
        jsonData.add("params", params);
        dexScore.invoke(bnusdScore, "tokenFallback", ownerAccount.getAddress(), bnusdDonation, jsonData.toString().getBytes());
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);

        assertEquals(initalBase, poolStats.get("base"));
        assertEquals(initalQuote.add(bnusdDonation), poolStats.get("quote"));

        BigInteger balnDonation = BigInteger.valueOf(50L).multiply(EXA);
        jsonData = new JsonObject();
        params = new JsonObject();
        params.add("toToken", bnusdScore.getAddress().toString());
        jsonData.add("method", "_donate");
        jsonData.add("params", params);
        dexScore.invoke(balnScore, "tokenFallback", ownerAccount.getAddress(), balnDonation, jsonData.toString().getBytes());
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);

        assertEquals(initalBase.add(balnDonation), poolStats.get("base"));
        assertEquals(initalQuote.add(bnusdDonation), poolStats.get("quote"));
        assertEquals(initalSupply, poolStats.get("total_supply"));
    }

    @Test
    void tokenfallback_swapSicx() {
        Account account = sm.createAccount();
        turnDexOn();
        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("getTodayRate"))).thenReturn(EXA);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(()->Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        supplyIcxLiquidity(account, FIFTY.multiply(BigInteger.TEN));

        // add liquidity pool
        BigInteger poolId = BigInteger.valueOf(Const.SICXICX_POOL_ID);
        BigInteger balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        // test swap
        JsonObject jsonData = new JsonObject();
        jsonData.add("method", "_swap_icx");
        BigInteger swapValue = BigInteger.valueOf(50L).multiply(EXA);
        dexScore.invoke(sicxScore, "tokenFallback", account.getAddress(), swapValue, jsonData.toString().getBytes());

        BigInteger newBalance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        BigInteger sicxEarning = (BigInteger) dexScore.call("getSicxEarnings", account.getAddress());
        assertEquals(balance.subtract(swapValue.subtract(swapValue.divide(BigInteger.valueOf(100L)))), newBalance);
        assertEquals(swapValue.multiply(BigInteger.valueOf(997L)).divide(BigInteger.valueOf(1000L)), sicxEarning);

    }

    @Test
    void onIRC31Received() {
        // Arrange.
        Account irc31Contract = Account.newScoreAccount(1);
        Address operator = sm.createAccount().getAddress();
        Address from = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        byte[] data = new byte[0];
        String expectedErrorMessage = "Reverted(0): Balanced DEX: IRC31 Tokens not accepted";

        // Act and assert.
        Executable onIRC31Received = () -> dexScore.invoke(irc31Contract, "onIRC31Received", operator, from, id, value, data);
        expectErrorMessage(onIRC31Received, expectedErrorMessage);
    }

    @Test
    void transfer() {
        Account account = sm.createAccount();
        Account account1 = sm.createAccount();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);

        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger transferValue = BigInteger.valueOf(5).multiply(EXA);
        BigInteger initialValue = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        dexScore.invoke(account, "transfer", account1.getAddress(), transferValue, poolId, data.getBytes());

        BigInteger value = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals(value, initialValue.subtract(transferValue));
        value = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        assertEquals(value, transferValue);
    }

    @Test
    void tokenFallback_swapIcx_revertOnIncompleteRewards() {
        // Arrange.
        Account tokenScoreCaller = sicxScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act & assert.
        Executable incompleteRewards = () -> dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
        expectErrorMessage(incompleteRewards, "Reverted(0): Balanced DEX: Rewards distribution in progress, please try again shortly");
    }

    // In the process of going through this.
    @Test
    void swap_sicx_icx() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(10000000).multiply(EXA);
        BigInteger sicxIcxConversionRate = new BigInteger("1100758881004412705");
        BigInteger swapValue = BigInteger.valueOf(100).multiply(EXA);

        // Act.
        supplyIcxLiquidity(ownerAccount, value);
        supplyIcxLiquidity(sm.createAccount(), value);
        supplyIcxLiquidity(sm.createAccount(), value);
        swapSicxToIcx(ownerAccount, swapValue, sicxIcxConversionRate);
    }

    @Test
    void balanceOfAt() {

        Account account = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));
        BigInteger balance = (BigInteger) dexScore.call("balanceOfAt", account.getAddress(), poolId, day, true);
        assertEquals(dexScore.call("balanceOf", account.getAddress(), poolId), balance);
    }

    @Test
    void totalSupplyAt() {
        Account account = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.call(any(Address.class), eq("getTodayRate"))).thenReturn(EXA);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger totalSupply = (BigInteger) dexScore.call("totalSupplyAt", poolId, day, false);
        assertEquals(poolStats.get("total_supply"), totalSupply);

        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        contextMock.when(() -> Context.getValue()).thenReturn(icxValue);
        contextMock.when(() -> Context.call(any(Address.class), eq("getTodayRate"))).thenReturn(EXA);
        dexScore.invoke(ownerAccount, "fallback");
        poolId = BigInteger.valueOf(SICXICX_POOL_ID);
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        totalSupply = (BigInteger) dexScore.call("totalSupplyAt", poolId, day, false);
        assertEquals(poolStats.get("total_supply"), totalSupply);
    }

    @Test
    void getTotalValue() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(276L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());

        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());

        String marketName = "BALN/BNUSD";
        dexScore.invoke(governanceScore, "setMarketName", poolId, marketName);
        BigInteger totalValue = (BigInteger) dexScore.call("getTotalValue", marketName, BigInteger.ONE);
        BigInteger totalSupply = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(totalSupply, totalValue);
    }

    @Test
    void totalBalnAt() {
        Account account = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        BigInteger balnDeposit = BigInteger.valueOf(120).multiply(EXA);
        BigInteger bnusdDeposit = BigInteger.valueOf(130).multiply(EXA);
        BigInteger balnAdd = FIFTY;
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), balnDeposit, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdDeposit, data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnAdd, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));

        BigInteger totalBaln = (BigInteger) dexScore.call("totalBalnAt", poolId, day, false);
        assertEquals(balnAdd, totalBaln);
    }

    @Test
    void getBalnSnapshot() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        BigInteger balnDeposit = BigInteger.valueOf(120).multiply(EXA);
        BigInteger bnusdDeposit = BigInteger.valueOf(130).multiply(EXA);
        BigInteger balnAdd = FIFTY;
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), balnDeposit, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdDeposit, data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnAdd, FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));
        String marketName = "BALN/bnUSD";
        dexScore.invoke(governanceScore, "setMarketName", poolId, marketName);
        BigInteger balnSnapshot = (BigInteger) dexScore.call("getBalnSnapshot", marketName, day);
        assertEquals(balnAdd, balnSnapshot);
    }

    @Test
    void loadBalancesAtSnapshot() {
        Account account = sm.createAccount();
        Account account1 = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.ZERO);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(1600L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(400L).multiply(EXA);
        BigInteger bnusdValueAccount1 = BigInteger.valueOf(200L).multiply(EXA);
        BigInteger balnValueAccount1 = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), balnValue, data.getBytes());
        dexScore.invoke(bnusdScore, "tokenFallback", account1.getAddress(), bnusdValueAccount1, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account1.getAddress(), balnValueAccount1, data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);

        // get pool stats before depositing further
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger poolBaseAmount = (BigInteger) dexScore.call("getPoolTotal", poolId, balnScore.getAddress());
        BigInteger poolQuoteAmount = (BigInteger) dexScore.call("getPoolTotal", poolId, bnusdScore.getAddress());
        BigInteger poolLpAmount = (BigInteger) dexScore.call("totalSupply", poolId);

        dexScore.invoke(account1, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValueAccount1, bnusdValueAccount1, false);
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));

        Map<String, BigInteger> balances = (Map<String, BigInteger>) dexScore.call("loadBalancesAtSnapshot", poolId, day, BigInteger.valueOf(2), BigInteger.ZERO);

        BigInteger account1_balance = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        BigInteger account_balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        BigInteger accountBalance = balnValue.multiply(bnusdValue).sqrt();
        BigInteger accountBalance1 = balnValueAccount1.multiply(bnusdValueAccount1).sqrt();
        assertEquals(accountBalance, balances.get(account.getAddress().toString()));

        BigInteger _baseValue = balnValueAccount1;
        BigInteger _quoteValue = bnusdValueAccount1;
        BigInteger baseFromQuote = _quoteValue.multiply(poolBaseAmount).divide(poolQuoteAmount);
        BigInteger quoteFromBase = _baseValue.multiply(poolQuoteAmount).divide(poolBaseAmount);
        BigInteger quoteToCommit = _quoteValue;
        BigInteger baseToCommit = _baseValue;
        if (quoteFromBase.compareTo(_quoteValue) <= 0) {
            quoteToCommit = quoteFromBase;
        } else {
            baseToCommit = baseFromQuote;
        }
        BigInteger liquidityFromBase = (poolLpAmount.multiply(baseToCommit)).divide(poolBaseAmount);
        BigInteger liquidityFromQuote = (poolLpAmount.multiply(quoteToCommit)).divide(poolQuoteAmount);
        BigInteger liquidity = liquidityFromBase.min(liquidityFromQuote);

        assertEquals(liquidity, balances.get(account1.getAddress().toString()));
    }

    @Test
    void getDataBatch() {
        Account account = sm.createAccount();
        Account account1 = sm.createAccount();
        turnDexOn();


        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("balanceOf"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.ZERO);
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(1600L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(400L).multiply(EXA);
        BigInteger bnusdValueAccount1 = BigInteger.valueOf(200L).multiply(EXA);
        BigInteger balnValueAccount1 = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), balnValue, data.getBytes());
        dexScore.invoke(bnusdScore, "tokenFallback", account1.getAddress(), bnusdValueAccount1, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account1.getAddress(), balnValueAccount1, data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);

        // get pool stats before depositing further
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger poolBaseAmount = (BigInteger) dexScore.call("getPoolTotal", poolId, balnScore.getAddress());
        BigInteger poolQuoteAmount = (BigInteger) dexScore.call("getPoolTotal", poolId, bnusdScore.getAddress());
        BigInteger poolLpAmount = (BigInteger) dexScore.call("totalSupply", poolId);

        dexScore.invoke(account1, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValueAccount1, bnusdValueAccount1, false);
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(BigInteger.valueOf(1000000L));


        String marketName = "BALN/BNUSD";
        dexScore.invoke(governanceScore, "setMarketName", poolId, marketName);
        Map<String, BigInteger> balances = (Map<String, BigInteger>) dexScore.call("getDataBatch", marketName, day, BigInteger.valueOf(2), BigInteger.ZERO);

        BigInteger account1_balance = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        BigInteger account_balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        BigInteger accountBalance = balnValue.multiply(bnusdValue).sqrt();
        BigInteger accountBalance1 = balnValueAccount1.multiply(bnusdValueAccount1).sqrt();
        assertEquals(accountBalance, balances.get(account.getAddress().toString()));

        BigInteger _baseValue = balnValueAccount1;
        BigInteger _quoteValue = bnusdValueAccount1;
        BigInteger baseFromQuote = _quoteValue.multiply(poolBaseAmount).divide(poolQuoteAmount);
        BigInteger quoteFromBase = _baseValue.multiply(poolQuoteAmount).divide(poolBaseAmount);
        BigInteger quoteToCommit = _quoteValue;
        BigInteger baseToCommit = _baseValue;
        if (quoteFromBase.compareTo(_quoteValue) <= 0) {
            quoteToCommit = quoteFromBase;
        } else {
            baseToCommit = baseFromQuote;
        }
        BigInteger liquidityFromBase = (poolLpAmount.multiply(baseToCommit)).divide(poolBaseAmount);
        BigInteger liquidityFromQuote = (poolLpAmount.multiply(quoteToCommit)).divide(poolQuoteAmount);
        BigInteger liquidity = liquidityFromBase.min(liquidityFromQuote);

        assertEquals(liquidity, balances.get(account1.getAddress().toString()));
    }

    @Test
    void getPoolStatsWithPair() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(276L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());

        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStatsForPair", balnScore.getAddress(), bnusdScore.getAddress());

        assertEquals(poolId, poolStats.get("id"));
        assertEquals(balnScore.getAddress(), poolStats.get("base_token"));
        assertEquals(bnusdScore.getAddress(), poolStats.get("quote_token"));
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}