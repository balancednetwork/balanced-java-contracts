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

package network.balanced.score.lib.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IRC2PresetFixedSupplyTest extends TestBase {

    private static final String name = "Test IRC2 Token";
    private static final String symbol = "TEST";
    private static final BigInteger decimals = BigInteger.valueOf(18);
    private static final BigInteger initialSupply = BigInteger.valueOf(1000);

    private static final BigInteger totalSupply = initialSupply.multiply(BigInteger.TEN.pow(decimals.intValue()));
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score tokenScore;

    @BeforeAll
    public static void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC2PresetFixedSupply.class, name, symbol, initialSupply, decimals);
        owner.addBalance(symbol, totalSupply);
    }

    @Test
    void name() {
        assertEquals(name, tokenScore.call("name"));
    }

    @Test
    void symbol() {
        assertEquals(symbol, tokenScore.call("symbol"));
    }

    @Test
    void decimals() {
        assertEquals(decimals, tokenScore.call("decimals"));
    }

    @Test
    void totalSupply() {
        assertEquals(totalSupply, tokenScore.call("totalSupply"));
    }

    @Test
    void balanceOf() {
        assertEquals(owner.getBalance(symbol), tokenScore.call("balanceOf", owner.getAddress()));
    }

    @Test
    void transfer() {
        Account alice = sm.createAccount();
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        tokenScore.invoke(owner, "transfer", alice.getAddress(), value, new byte[0]);
        owner.subtractBalance(symbol, value);
        assertEquals(owner.getBalance(symbol), tokenScore.call("balanceOf", tokenScore.getOwner().getAddress()));
        assertEquals(value, tokenScore.call("balanceOf", alice.getAddress()));

        tokenScore.invoke(alice, "transfer", alice.getAddress(), value, new byte[0]);
        assertEquals(value, tokenScore.call("balanceOf", alice.getAddress()));
    }
}