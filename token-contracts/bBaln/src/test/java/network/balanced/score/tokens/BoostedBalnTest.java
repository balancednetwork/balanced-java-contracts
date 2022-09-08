/*
 * Copyright (c) 2021-2022 Balanced.network.
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

package network.balanced.score.tokens;

import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.token.irc2.IRC2Basic;
import network.balanced.score.tokens.utils.DummyContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Context;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;

class BoostedBalnTest extends AbstractBoostedBalnTest {
    private static final ServiceManager sm = getServiceManager();
    private Score bBalnScore;

    private static final String name = "Balance Token";
    private static final String symbol = "BALN";
    private static final BigInteger decimals = BigInteger.valueOf(18);
    private static final BigInteger initialSupply = BigInteger.TEN.pow(21);

    private static final String bBalnName = "Boosted Balance";
    private static final String bBalnSymbol = "bBALN";

    public static class IRC2BasicToken extends IRC2Basic {
        public IRC2BasicToken(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
            super(_name, _symbol, _decimals);
            _mint(Context.getCaller(), _totalSupply);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        Score rewardScore = sm.deploy(owner, DummyContract.class);
        Score dividendsScore = sm.deploy(owner, DummyContract.class);
        bBalnScore = sm.deploy(owner, BoostedBalnImpl.class, tokenScore.getAddress(), rewardScore.getAddress(),
                dividendsScore.getAddress(),
                bBalnName, bBalnSymbol);
        BoostedBalnImpl scoreSpy = (BoostedBalnImpl) spy(bBalnScore.getInstance());
        bBalnScore.setInstance(scoreSpy);

        bBalnScore.invoke(owner, "setMinimumLockingAmount", ICX);
    }

    @Test
    void name() {
        assertEquals(bBalnName, bBalnScore.call("name"));
    }

    @Test
    void admin() {
        assertEquals(owner.getAddress(), bBalnScore.call("admin"));
    }

    @Test
    void futureAdmin() {
        assertNull(bBalnScore.call("futureAdmin"));
    }

    @Test
    void symbol() {
        assertEquals(bBalnSymbol, bBalnScore.call("symbol"));
    }

    @Test
    void decimals() {
        assertEquals(decimals, bBalnScore.call("decimals"));
    }

    @Test
    void totalSupply() {
        assertEquals(BigInteger.ZERO, bBalnScore.call("totalSupply", BigInteger.ZERO));
    }

}
