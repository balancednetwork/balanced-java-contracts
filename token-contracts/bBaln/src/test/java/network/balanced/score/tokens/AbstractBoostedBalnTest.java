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

package network.balanced.score.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.tokens.utils.IRC2Token;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigInteger;

public class AbstractBoostedBalnTest extends UnitTest {

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();

    protected static Score tokenScore;
    private static final BigInteger INITIAL_SUPPLY = BigInteger.valueOf(1000).multiply(ICX);

    @BeforeAll
    public static void init() throws Exception {
        tokenScore = sm.deploy(owner, IRC2Token.class, INITIAL_SUPPLY);
    }
}
