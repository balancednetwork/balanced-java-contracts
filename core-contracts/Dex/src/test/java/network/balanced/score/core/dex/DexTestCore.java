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

package network.balanced.score.core.dex;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import network.balanced.score.core.dex.utils.Const;
import network.balanced.score.lib.structs.PrepDelegations;
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

import static network.balanced.score.core.dex.utils.Const.FEE_SCALE;
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

    @SuppressWarnings("unchecked")
    @Test
    void fallback() {
        Account account = sm.createAccount();
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        contextMock.when(Context::getValue).thenReturn(icxValue);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"),
                any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.call(any(Address.class), eq("getTodayRate"))).thenReturn(EXA);
        dexScore.invoke(ownerAccount, "fallback");

        BigInteger poolId = BigInteger.valueOf(SICXICX_POOL_ID);
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger lpBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(),
                BigInteger.valueOf(SICXICX_POOL_ID));
        assertEquals(icxValue, lpBalance);
        assertEquals(lpBalance, poolStats.get("total_supply"));

        BigInteger additionIcxValue = BigInteger.valueOf(50L).multiply(EXA);
        contextMock.when(Context::getValue).thenReturn(additionIcxValue);
        dexScore.invoke(ownerAccount, "fallback");
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        lpBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(),
                BigInteger.valueOf(SICXICX_POOL_ID));
        assertEquals(icxValue.add(additionIcxValue), lpBalance);
        assertEquals(icxValue.add(additionIcxValue), poolStats.get("total_supply"));

        dexScore.invoke(account, "fallback");
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger balanceOwner = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        BigInteger balanceAccount = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals(balanceOwner.add(balanceAccount), poolStats.get("total_supply"));
    }

    // Test fails in line with: activeAddresses.get(SICXICX_POOL_ID).remove(user);
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
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"),
                any(String.class), any(BigInteger.class), any())).thenReturn(null);
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        BigInteger depositBalance = BigInteger.valueOf(100L).multiply(EXA);
        supplyIcxLiquidity(depositor, depositBalance);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()),
                eq(withdrawValue))).thenReturn(null);

        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert.
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(),
                depositor.getAddress());
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        BigInteger depositBalance = BigInteger.valueOf(100L).multiply(EXA);
        supplyIcxLiquidity(depositor, depositBalance);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()),
                eq(withdrawValue))).thenReturn(null);

        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert.
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(),
                depositor.getAddress());
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
        dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), depositValue, tokenData(
                "_deposit", new HashMap<>()));
        BigInteger retrievedDepositValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(),
                tokenSender.getAddress());

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
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(),
                withdrawValue);
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
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(),
                withdrawValue);
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

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()),
                eq(withdrawValue))).thenReturn(null);

        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert. 
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(),
                depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);
    }

    @SuppressWarnings("unchecked")
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

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
        assertEquals(poolStats.get("base_token"), balnScore.getAddress());
        assertEquals(poolStats.get("quote_token"), bnusdScore.getAddress());
        assertEquals(bnusdValue.multiply(balnValue).sqrt(), balance);

        BigInteger account1_balance = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        assertEquals(balance.add(account1_balance), poolStats.get("total_supply"));
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
        BigInteger usersLpTokensAfterRemoval = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(),
                poolId);
        assertEquals(usersLpTokens.subtract(lpTokensToRemove), usersLpTokensAfterRemoval);
    }

    @SuppressWarnings("unchecked")
    @Test
    void tokenFallbackSwapFromTokenIs_poolQuote() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY,
                FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        Map<String, BigInteger> fees = (Map<String, BigInteger>) dexScore.call("getFees");
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger oldFromToken = (BigInteger) poolStats.get("quote");
        BigInteger oldToToken = (BigInteger) poolStats.get("base");

        BigInteger value = BigInteger.valueOf(100L).multiply(EXA);
        BigInteger lp_fee = value.multiply(fees.get("pool_lp_fee")).divide(FEE_SCALE);
        BigInteger baln_fee = value.multiply(fees.get("pool_baln_fee")).divide(FEE_SCALE);
        BigInteger total_fee = lp_fee.add(baln_fee);

        BigInteger inputWithoutFees = value.subtract(total_fee);
        BigInteger newFromToken = oldFromToken.add(inputWithoutFees);

        BigInteger newToToken = (oldFromToken.multiply(oldToToken)).divide(newFromToken);
        BigInteger sendAmount = oldToToken.subtract(newToToken);
        newFromToken = newFromToken.add(lp_fee);

        // test swap
        JsonObject jsonData = new JsonObject();
        JsonObject params = new JsonObject();
        params.add("minimumReceive", BigInteger.valueOf(10L).toString());
        params.add("toToken", balnScore.getAddress().toString());
        jsonData.add("method", "_swap");
        jsonData.add("params", params);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), value, jsonData.toString().getBytes());
        Map<String, Object> newPoolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger newBalance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        assertEquals(newFromToken, newPoolStats.get("quote"));
        assertEquals(newToToken, newPoolStats.get("base"));
        assertEquals(balance, newBalance);
    }


    @Test
    void tokenFallbackSwapFromTokenIs_poolBase() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY,
                FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger balance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        Map<String, BigInteger> fees = (Map<String, BigInteger>) dexScore.call("getFees");
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger oldFromToken = (BigInteger) poolStats.get("base");
        BigInteger oldToToken = (BigInteger) poolStats.get("quote");

        BigInteger value = BigInteger.valueOf(100L).multiply(EXA);
        BigInteger lp_fee = value.multiply(fees.get("pool_lp_fee")).divide(FEE_SCALE);
        BigInteger baln_fee = value.multiply(fees.get("pool_baln_fee")).divide(FEE_SCALE);
        BigInteger total_fee = lp_fee.add(baln_fee);

        BigInteger inputWithoutFees = value.subtract(total_fee);
        BigInteger newFromToken = oldFromToken.add(inputWithoutFees);

        BigInteger newToToken = (oldFromToken.multiply(oldToToken)).divide(newFromToken);
        BigInteger sendAmount = oldToToken.subtract(newToToken);
        newFromToken = newFromToken.add(lp_fee);

        // swapping fees to quote token
        oldFromToken = newFromToken;
        oldToToken = newToToken;
        BigInteger newFromTokenWithBalnFee = oldFromToken.add(baln_fee);
        BigInteger newToTokenAfterFeeSwap = (oldFromToken.multiply(oldToToken)).divide(newFromTokenWithBalnFee);

        // test swap when from token is pool base token
        JsonObject jsonData = new JsonObject();
        JsonObject params = new JsonObject();
        params.add("minimumReceive", BigInteger.valueOf(10L).toString());
        params.add("toToken", bnusdScore.getAddress().toString());
        jsonData.add("method", "_swap");
        jsonData.add("params", params);
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), value, jsonData.toString().getBytes());
        Map<String, Object> newPoolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger newBalance = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);

        assertEquals(newFromTokenWithBalnFee, newPoolStats.get("base"));
        assertEquals(newToTokenAfterFeeSwap, newPoolStats.get("quote"));
        assertEquals(balance, newBalance);
    }

    @SuppressWarnings("unchecked")
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        //deposit
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY,
                FIFTY.divide(BigInteger.TWO), false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);
        BigInteger initialBase = (BigInteger) poolStats.get("base");
        BigInteger initialQuote = (BigInteger) poolStats.get("quote");
        BigInteger initialSupply = (BigInteger) poolStats.get("total_supply");

        BigInteger bnusdDonation = BigInteger.valueOf(100L).multiply(EXA);
        JsonObject jsonData = new JsonObject();
        JsonObject params = new JsonObject();
        params.add("toToken", balnScore.getAddress().toString());
        jsonData.add("method", "_donate");
        jsonData.add("params", params);
        dexScore.invoke(bnusdScore, "tokenFallback", ownerAccount.getAddress(), bnusdDonation,
                jsonData.toString().getBytes());
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);

        assertEquals(initialBase, poolStats.get("base"));
        assertEquals(initialQuote.add(bnusdDonation), poolStats.get("quote"));

        BigInteger balnDonation = BigInteger.valueOf(50L).multiply(EXA);
        jsonData = new JsonObject();
        params = new JsonObject();
        params.add("toToken", bnusdScore.getAddress().toString());
        jsonData.add("method", "_donate");
        jsonData.add("params", params);
        dexScore.invoke(balnScore, "tokenFallback", ownerAccount.getAddress(), balnDonation,
                jsonData.toString().getBytes());
        poolStats = (Map<String, Object>) dexScore.call("getPoolStats", poolId);

        assertEquals(initialBase.add(balnDonation), poolStats.get("base"));
        assertEquals(initialQuote.add(bnusdDonation), poolStats.get("quote"));
        assertEquals(initialSupply, poolStats.get("total_supply"));
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);
        contextMock.when(() -> Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);

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
        Executable onIRC31Received = () -> dexScore.invoke(irc31Contract, "onIRC31Received", operator, from, id,
                value, data);
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA),
                data.getBytes());
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY,
                FIFTY.divide(BigInteger.TWO), false);

        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger transferValue = BigInteger.valueOf(5).multiply(EXA);
        BigInteger initialValue = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        dexScore.invoke(account, "transfer", account1.getAddress(), transferValue, poolId, data.getBytes());

        BigInteger value = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals(value, initialValue.subtract(transferValue));
        value = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        assertEquals(value, transferValue);
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
    void getTotalValue() {
        Account account = sm.createAccount();
        turnDexOn();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

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

    @SuppressWarnings("unchecked")
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
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                any(BigInteger.class))).thenReturn(null);

        //deposit
        BigInteger bnusdValue = BigInteger.valueOf(276L).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(100L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), bnusdValue, data.getBytes());

        // add liquidity pool
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), balnValue, bnusdValue, false);
        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call("getPoolStatsForPair",
                balnScore.getAddress(), bnusdScore.getAddress());

        assertEquals(poolId, poolStats.get("id"));
        assertEquals(balnScore.getAddress(), poolStats.get("base_token"));
        assertEquals(bnusdScore.getAddress(), poolStats.get("quote_token"));
    }

    @Test
    void delegate() {
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn(
                "Staking delegate called");
        dexScore.invoke(governanceScore, "delegate", (Object) preps);

        contextMock.verify(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any()));
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}