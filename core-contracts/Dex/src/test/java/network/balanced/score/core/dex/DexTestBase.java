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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;

import score.Context;
import score.Address;
import score.annotation.Optional;

import org.mockito.stubbing.Answer;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.math.BigInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.List;


import static network.balanced.score.lib.utils.Constants.*;
import network.balanced.score.lib.test.UnitTest;

class DexTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static Account ownerAccount = sm.createAccount();
    protected static Account adminAccount = sm.createAccount();
    protected static Account prep_address = sm.createAccount();

    int scoreCount = 0;
    protected final Account governanceScore = Account.newScoreAccount(scoreCount++);
    protected final Account dividendsScore = Account.newScoreAccount(scoreCount++);
    protected final Account stakingScore = Account.newScoreAccount(scoreCount++);
    protected final Account rewardsScore = Account.newScoreAccount(scoreCount++);
    protected final Account bnusdScore = Account.newScoreAccount(scoreCount++);
    protected final Account balnScore = Account.newScoreAccount(scoreCount++);
    protected final Account sicxScore = Account.newScoreAccount(scoreCount++);
    protected final Account feehandlerScore = Account.newScoreAccount(scoreCount++);
    protected final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    protected final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    public void setup() {
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);
        dexScore.invoke(governanceScore, "turnDexOn");
    }

    protected void turnDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
    }

    protected void setupAddresses() {
        dexScore.invoke(governanceScore, "setAdmin", governanceScore.getAddress());

        Map<String, Address> addresses = Map.of(
            "setDividends", dividendsScore.getAddress(),
            "setStaking", stakingScore.getAddress(),
            "setRewards", rewardsScore.getAddress(),
            "setBnusd", bnusdScore.getAddress(),
            "setBaln", balnScore.getAddress(),
            "setSicx", sicxScore.getAddress(),
            "setFeehandler", feehandlerScore.getAddress(),
            "setStakedLp", stakedLPScore.getAddress()
        );
        
        for (Map.Entry<String, Address> address : addresses.entrySet()) {
            dexScore.invoke(governanceScore, address.getKey(), address.getValue());
        }
    }

    protected void depositToken(Account depositor, Account tokenScore, BigInteger value) {
        setupAddresses();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    protected void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore, 
                                 BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        turnDexOn();
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteTokenScore.getAddress());

        // Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue, quoteValue, withdrawUnused);
    }

    protected BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    protected void supplyIcxLiquidity(Account supplier, BigInteger value) {
        contextMock.when(Context::getValue).thenReturn(value);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }

    // Not done yet. Fails for some reason.
    protected void swapSicxToIcx(Account sender, BigInteger value, BigInteger sicxIcxConversionRate) {
        turnDexOn();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), eq("sICX/ICX"), eq(BigInteger.class), any(List.class))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(sicxScore.getAddress()), eq("transfer"), eq(feehandlerScore.getAddress()), any(BigInteger.class))).thenReturn(true);
        contextMock.when(() -> Context.transfer(eq(sender.getAddress()), any(BigInteger.class))).thenAnswer((Answer<Void>) invocation -> null);
        dexScore.invoke(sicxScore, "tokenFallback", sender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));  
    }
}