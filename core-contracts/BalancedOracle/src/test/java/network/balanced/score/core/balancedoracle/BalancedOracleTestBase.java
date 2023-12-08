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

package network.balanced.score.core.balancedoracle;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.test.mock.MockBalanced;

import java.math.BigInteger;

import static org.mockito.Mockito.when;

class BalancedOracleTestBase extends UnitTest {
    protected static final long DAY = 43200L;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();

    protected MockBalanced mockBalanced;
    protected MockContract<Dex> dex;
    protected MockContract<Oracle> oracle;
    protected MockContract<Staking> staking;
    protected MockContract<BalancedToken> baln;
    protected MockContract<IRC2> iusdc;

    protected Score balancedOracle;

    protected static final BigInteger icxBnusdPoolId = BigInteger.TWO;

    protected void setup() throws Exception {
        mockReadonly();
        mockBalanced = new MockBalanced(sm, owner);
        dex = mockBalanced.dex;
        oracle = mockBalanced.oracle;
        staking = mockBalanced.staking;
        baln = mockBalanced.baln;
        iusdc = new MockContract<>(IRC2ScoreInterface.class, sm, owner);
        balancedOracle = sm.deploy(owner, BalancedOracleImpl.class, mockBalanced.governance.getAddress());

        when(iusdc.mock.decimals()).thenReturn(BigInteger.valueOf(6));
        sm.getBlock().increase(DAY);
    }
}