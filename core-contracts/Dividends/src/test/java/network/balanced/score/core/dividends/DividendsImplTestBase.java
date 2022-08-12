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

package network.balanced.score.core.dividends;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.Mockito.*;

class DividendsImplTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static final Object MINT_AMOUNT = BigInteger.TEN.pow(22);
    protected final BigInteger initialFees = BigInteger.TEN.pow(20);
    protected static final long DAY = 43200L;

    protected static final Account owner = sm.createAccount();
    protected final Account admin = sm.createAccount();
    protected final Account prep_address = sm.createAccount();

    protected static MockContract<Governance> governance;
    protected static MockContract<Staking> staking;
    protected static MockContract<BalancedDollar> bnusd;
    protected static MockContract<BalancedToken> baln;
    protected static MockContract<DAOfund> daofund;
    protected static MockContract<Dex> dex;
    protected static MockContract<Loans> loans;
    protected static MockBalanced mockBalanced;
    protected Score dividendScore;

    protected void addBnusdFees(BigInteger amount) {
        dividendScore.invoke(bnusd.account, "tokenFallback", bnusd.getAddress(), amount, new byte[0]);
    }

    protected BigInteger getDay() {
        return (BigInteger) dividendScore.call("getDay");
    }

    protected BigInteger getFeePercentage(String source) {
        Map<String, BigInteger> percentages = (Map<String, BigInteger>) dividendScore.call("getDividendsPercentage");
        return percentages.get(source);

    }
    protected void setupBase() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        staking = mockBalanced.staking;
        bnusd = mockBalanced.bnUSD;
        baln = mockBalanced.baln;
        daofund = mockBalanced.daofund;
        dex = mockBalanced.dex;
        loans = mockBalanced.loans;
        dividendScore = sm.deploy(owner, DividendsImpl.class, governance.getAddress());

        DividendsImpl dividendsSpy = (DividendsImpl) spy(dividendScore.getInstance());
        dividendScore.setInstance(dividendsSpy);
    }

  

}
