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
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

class DexTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static Account ownerAccount = sm.createAccount();
    protected static Account adminAccount = sm.createAccount();
    protected static Account prep_address = sm.createAccount();

    protected static MockBalanced mockBalanced;
    protected static MockContract<Governance> governance;
    protected static MockContract<Dividends> dividends;
    protected static MockContract<Staking> staking;
    protected static MockContract<Rewards> rewards;
    protected static MockContract<BalancedDollar> bnusd;
    protected static MockContract<BalancedToken> baln;
    protected static MockContract<Sicx> sicx;
    protected static MockContract<FeeHandler> feehandler;
    protected static MockContract<StakedLP> stakedLP;
    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, ownerAccount);
        governance = mockBalanced.governance;
        dividends = mockBalanced.dividends;
        staking = mockBalanced.staking;
        rewards = mockBalanced.rewards;
        bnusd = mockBalanced.bnUSD;
        baln = mockBalanced.baln;
        sicx = mockBalanced.sicx;
        feehandler = mockBalanced.feehandler;
        stakedLP = mockBalanced.stakedLp;
        
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governance.getAddress());
        dexScore.invoke(governance.account, "setAdmin", governance.getAddress());
        dexScore.invoke(governance.account, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);
        dexScore.invoke(governance.account, "turnDexOn");
        dexScore.invoke(governance.account, "addQuoteCoin", sicx.getAddress());
        dexScore.invoke(governance.account, "addQuoteCoin", bnusd.getAddress());
    }

    protected void turnDexOn() {
        dexScore.invoke(governance.account, "turnDexOn");
    }

    protected void depositToken(Account depositor, Account tokenScore, BigInteger value) {
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    protected void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore, 
                                 BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        turnDexOn();
        dexScore.invoke(governance.account, "addQuoteCoin", quoteTokenScore.getAddress());

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue, quoteValue, withdrawUnused);
    }

    protected BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    protected void supplyIcxLiquidity(Account supplier, BigInteger value) {
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }

    // Not done yet. Fails for some reason.
    protected void swapSicxToIcx(Account sender, BigInteger value, BigInteger sicxIcxConversionRate) {
        turnDexOn();
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();
        dexScore.invoke(sicx.account, "tokenFallback", sender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));  
    }
}