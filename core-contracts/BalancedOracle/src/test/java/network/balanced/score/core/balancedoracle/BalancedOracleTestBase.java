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
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.Disbursement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

class BalancedOracleTestBase extends UnitTest {
    protected static final long DAY = 43200L;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();

    protected MockContract<Dex> dex;
    protected MockContract<Oracle> oracle;
    protected MockContract<Staking> staking;

    protected Score balancedOracle;
    protected static final Account governance = Account.newScoreAccount(scoreCount);
    
    protected static final BigInteger icxBnsudPoolId = BigInteger.TWO;

    protected void setup() throws Exception {
        dex = new MockContract<Dex>(DexScoreInterface.class, sm, owner);
        oracle = new MockContract<Oracle>(OracleScoreInterface.class, sm, owner);
        staking = new MockContract<Staking>(StakingScoreInterface.class, sm, owner);
        balancedOracle = sm.deploy(owner, BalancedOracleImpl.class, governance.getAddress());

        sm.getBlock().increase(DAY);
    }
}