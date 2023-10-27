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

package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import score.Address;
import score.Context;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


class DexTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static Account ownerAccount = sm.createAccount();
    protected static Account adminAccount = sm.createAccount();
    protected static Account prep_address = sm.createAccount();

    int scoreCount = 0;
    protected MockBalanced mockBalanced;
    protected Account governanceScore;
    protected Account dividendsScore;
    protected Account stakingScore;
    protected Account rewardsScore;
    protected Account bnusdScore;
    protected Account balnScore;
    protected Account sicxScore;
    protected Account feehandlerScore;
    protected Account stakedLPScore;

    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    protected final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, ownerAccount);
        governanceScore = mockBalanced.governance.account;
        dividendsScore = mockBalanced.dividends.account;
        stakingScore = mockBalanced.staking.account;
        rewardsScore = mockBalanced.rewards.account;
        bnusdScore = mockBalanced.bnUSD.account;
        balnScore = mockBalanced.baln.account;
        sicxScore = mockBalanced.sicx.account;
        feehandlerScore = mockBalanced.feehandler.account;
        stakedLPScore = mockBalanced.stakedLp.account;

        contextMock.when(() -> Context.call(eq(governanceScore.getAddress()), eq("checkStatus"), any(String.class))).thenReturn(null);
        contextMock.when(() -> Context.call(eq(BigInteger.class), any(Address.class), eq("balanceOf"), any(Address.class))).thenReturn(BigInteger.ZERO);

        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);

        dexScore.invoke(governanceScore, "addQuoteCoin", bnusdScore.getAddress());
        dexScore.invoke(governanceScore, "addQuoteCoin", sicxScore.getAddress());
    }


    protected void depositToken(Account depositor, Account tokenScore, BigInteger value) {
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit",
                new HashMap<>()));
    }

    protected void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore,
                                   BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteTokenScore.getAddress());

        // Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit",
                new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit",
                new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue,
                quoteValue, withdrawUnused);
    }

    protected BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    protected void supplyIcxLiquidity(Account supplier, BigInteger value) {
        contextMock.when(Context::getValue).thenReturn(value);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBalanceAndSupply"),
                any(String.class), any(BigInteger.class), any(String.class), any(BigInteger.class))).thenReturn(null);
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }

    // Not done yet. Fails for some reason.
    protected void swapSicxToIcx(Account sender, BigInteger value, BigInteger sicxIcxConversionRate) {
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBalanceAndSupplyBatch"), any(),
                any(), any())).thenReturn(null);
        contextMock.when(() -> Context.call(eq(sicxScore.getAddress()), eq("transfer"),
                eq(feehandlerScore.getAddress()), any(BigInteger.class))).thenReturn(true);
        contextMock.when(() -> Context.transfer(eq(sender.getAddress()), any(BigInteger.class))).thenAnswer((Answer<Void>) invocation -> null);
        dexScore.invoke(sicxScore, "tokenFallback", sender.getAddress(), value, tokenData("_swap_icx",
                new HashMap<>()));
    }
}