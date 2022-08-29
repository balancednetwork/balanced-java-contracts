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

package network.balanced.score.core.reserve;

import static org.mockito.Mockito.when;

import java.math.BigInteger;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.BalancedOracle;
import network.balanced.score.lib.interfaces.BalancedOracleScoreInterface;
import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.interfaces.LoansScoreInterface;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;

public class ReserveFundTestBase extends UnitTest {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    Account admin = sm.createAccount();
    public static final BigInteger nrDecimalsIETH = BigInteger.valueOf(6);
    public static final BigInteger iETHDecimals = BigInteger.TEN.pow(6);


    public static final Account governanceScore = Account.newScoreAccount(1);
    protected Score reserve;

    MockContract<Loans> loans;
    MockContract<IRC2Mintable> baln;
    MockContract<IRC2Mintable> sicx;
    MockContract<IRC2Mintable> ieth;
    MockContract<BalancedOracle> balancedOracle;

    protected void setup() throws Exception {
        loans = new MockContract<>(LoansScoreInterface.class, sm, admin);
        baln = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        sicx = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        ieth = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        balancedOracle = new MockContract<>(BalancedOracleScoreInterface.class, sm, admin);
        reserve = sm.deploy(owner, ReserveFund.class, governanceScore.getAddress());

        when(sicx.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        when(ieth.mock.decimals()).thenReturn(nrDecimalsIETH);
    }
}

