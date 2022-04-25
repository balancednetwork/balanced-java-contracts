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

import com.iconloop.score.test.Score;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.tokens.IRC2MintableTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IRC2PresetFixedSupplyTest extends TestBase {

    private static Score tokenScore;

    @BeforeAll
    static void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC2PresetFixedSupply.class, name, symbol, initialSupply, decimals);
    }

    @Test
    void totalSupply() {
        assertEquals(initialSupply.multiply(BigInteger.TEN.pow(decimals.intValue())), tokenScore.call("totalSupply"));
    }
}