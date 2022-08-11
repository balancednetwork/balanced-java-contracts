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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.*;

import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReserveFundTestBase extends UnitTest {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    Account admin = sm.createAccount();


    public Account governanceScore;
    protected Score reserve;

    MockBalanced mockBalanced;
    MockContract<Loans> loans;
    MockContract<BalancedToken> baln;
    MockContract<Sicx> sicx;
    MockContract<IRC2Mintable> ieth;
    MockContract<BalancedOracle> balancedOracle;

    protected void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governanceScore = mockBalanced.governance.account;
        loans = mockBalanced.loans;
        baln = mockBalanced.baln;
        sicx = mockBalanced.sicx;
        ieth = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        balancedOracle = mockBalanced.balancedOracle;
        reserve = sm.deploy(owner, ReserveFund.class, governanceScore.getAddress());    
    }
}

