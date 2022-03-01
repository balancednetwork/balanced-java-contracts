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
import com.iconloop.score.token.irc2.IRC2Basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import score.Context;


public class LiquidityTest extends TestBase {
    public static final ServiceManager sm = getServiceManager(); 
    public static final Account owner = sm.createAccount();

    private Score liquidity;
    private static Score sicx;
    private static Score bnusd;

    @BeforeEach
    public void setup() throws Exception {
        liquidity = sm.deploy(owner, Liquidity.class);
    }

    @Test
    void name() {
        String contractName = "Balanced Liquidity";
        assertEquals(contractName, liquidity.call("name"));
    }

    @Test
    void setGetDex() {
        assertNull(liquidity.call("getDex"));
        Account dex = Account.newScoreAccount(1);

        liquidity.invoke(owner, "setDex", dex.getAddress());

        assertEquals(dex.getAddress(), liquidity.call("getDex"));
    }
    
    @Test
    void setGetDaofund() {
        assertNull(liquidity.call("getDaofund"));
        Account daofund = Account.newScoreAccount(1);

        liquidity.invoke(owner, "setDaofund", daofund.getAddress());

        assertEquals(daofund.getAddress(), liquidity.call("getDaofund"));
    }

}
