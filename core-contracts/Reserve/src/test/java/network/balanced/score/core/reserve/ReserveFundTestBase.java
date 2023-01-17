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

package network.balanced.score.core.reserve;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.BalancedOracle;
import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;

import java.math.BigInteger;

import static org.mockito.Mockito.when;

public class ReserveFundTestBase extends UnitTest {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    Account admin = sm.createAccount();
    public static final BigInteger nrDecimalsIETH = BigInteger.valueOf(6);
    public static final BigInteger iETHDecimals = BigInteger.TEN.pow(6);


    public static final Account governanceScore = Account.newScoreAccount(1);
    protected Score reserve;
    MockBalanced mockBalanced;
    MockContract<Loans> loans;
    MockContract<? extends IRC2Mintable> baln;
    MockContract<? extends IRC2Mintable> sicx;
    MockContract<? extends IRC2Mintable> ieth;
    MockContract<BalancedOracle> balancedOracle;

    protected void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, admin);
        loans = mockBalanced.loans;
        baln = mockBalanced.baln;
        sicx = mockBalanced.sicx;
        ieth = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        balancedOracle = mockBalanced.balancedOracle;
        reserve = sm.deploy(owner, ReserveFund.class, mockBalanced.governance.getAddress());

        when(sicx.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        when(ieth.mock.decimals()).thenReturn(nrDecimalsIETH);
    }
}

