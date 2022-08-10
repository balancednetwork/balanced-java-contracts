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

import java.math.BigInteger;

import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

class BalancedOracleTestBase extends UnitTest {
    protected static final long DAY = 43200L;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();
    protected static final BigInteger icxBnsudPoolId = BigInteger.TWO;

    protected MockContract<Dex> dex;
    protected MockContract<Oracle> oracle;
    protected MockContract<Staking> staking;

    protected Score balancedOracle;
    protected Account governance;
    

    protected void setup() throws Exception {
        MockBalanced mockBalanced = new MockBalanced(sm, owner);
        dex = mockBalanced.dex;
        oracle = mockBalanced.oracle;
        staking = mockBalanced.staking;
        governance = mockBalanced.governance.account;
        balancedOracle = sm.deploy(owner, BalancedOracleImpl.class, governance.getAddress());

        sm.getBlock().increase(DAY);
    }
}