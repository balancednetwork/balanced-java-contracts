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

package network.balanced.score.core.liquidity;

import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.Account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class LiquidityTest extends TestBase {
    public static final ServiceManager sm = getServiceManager(); 
    public static final Account owner = sm.createAccount();
    public static final Account governance = sm.createAccount();
    public static final Account admin = sm.createAccount();

    private Score liquidity;

    @BeforeEach
    public void setup() throws Exception {
        liquidity = sm.deploy(owner, Liquidity.class, governance.getAddress(), admin.getAddress());
    }

    @Test
    void name() {
        String contractName = "Balanced Liquidity";
        assertEquals(contractName, liquidity.call("name"));
    }

    @Test
    void setGetDex() {
        Account dex = Account.newScoreAccount(1);
        liquidity.invoke(admin, "setDex", dex.getAddress());
        assertEquals(dex.getAddress(), liquidity.call("getDex"));
    }
    
    @Test
    void setGetDaofund() {
        Account daofund = Account.newScoreAccount(1);
        liquidity.invoke(admin, "setDaofund", daofund.getAddress());
        assertEquals(daofund.getAddress(), liquidity.call("getDaofund"));
    }

    @Test
    void setGetStakedLP() {
        Account stakedLP = Account.newScoreAccount(1);
        liquidity.invoke(admin, "setStakedLP", stakedLP.getAddress());
        assertEquals(stakedLP.getAddress(), liquidity.call("getStakedLP"));
    }
}
