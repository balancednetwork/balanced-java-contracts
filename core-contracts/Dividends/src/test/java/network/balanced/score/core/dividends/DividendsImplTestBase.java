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

package network.balanced.score.core.dividends;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.core.dividends.utils.bnUSD;
import network.balanced.score.lib.test.UnitTest;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.Mockito.*;

class DividendsImplTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static final Object MINT_AMOUNT = BigInteger.TEN.pow(22);
    protected final BigInteger initialFees = BigInteger.TEN.pow(20);
    protected static final long DAY = 43200L;
    protected static Score bnUSDScore;
    protected static final Account owner = sm.createAccount();
    protected final Account admin = sm.createAccount();
    protected final Account prep_address = sm.createAccount();
    protected static final Account governanceScore = Account.newScoreAccount(1);
    protected static final Account loansScore = Account.newScoreAccount(2);
    protected static final Account daoScore = Account.newScoreAccount(3);
    protected static final Account balnScore = Account.newScoreAccount(7);
    protected static final Account bBalnScore = Account.newScoreAccount(8);
    protected static final Account dexScore = Account.newScoreAccount(6);
    protected static final Account stakingScore = Account.newScoreAccount(7);
    protected static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class,
            Mockito.CALLS_REAL_METHODS);
    protected Score dividendScore;
    protected final MockedStatic.Verification getAssetTokens = () -> Context.call(eq(loansScore.getAddress()), eq(
            "getAssetTokens"));
    protected final MockedStatic.Verification balanceOf = () -> Context.call(eq(balnScore.getAddress()), eq(
            "balanceOf"), any(Address.class));

    protected void addBnusdFees(BigInteger amount) {
        dividendScore.invoke(bnUSDScore.getAccount(), "tokenFallback", bnUSDScore.getAddress(), amount, new byte[0]);
    }

    protected BigInteger getDay() {
        return (BigInteger) dividendScore.call("getDay");
    }

    @SuppressWarnings("unchecked")
    protected BigInteger getFeePercentage(String source) {
        Map<String, BigInteger> percentages = (Map<String, BigInteger>) dividendScore.call("getDividendsPercentage");
        return percentages.get(source);

    }

    protected void setupBase() throws Exception {
        dividendScore = sm.deploy(owner, DividendsImpl.class, governanceScore.getAddress());
        assert (dividendScore.getAddress().isContract());

        bnUSDScore = sm.deploy(owner, bnUSD.class, "bnUSD Token", "bnUSD", 18);
        bnUSDScore.invoke(owner, "mint", MINT_AMOUNT);
        bnUSDScore.invoke(owner, "transfer", dividendScore.getAddress(), initialFees, new byte[0]);

        DividendsImpl dividendsSpy = (DividendsImpl) spy(dividendScore.getInstance());
        dividendScore.setInstance(dividendsSpy);
    }


}
