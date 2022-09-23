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

package network.balanced.score.core.bribing;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;;


class BribingImplTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;
    protected static final BigInteger WEEK_IN_MS = BigInteger.valueOf(7).multiply(MICRO_SECONDS_IN_A_DAY);

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();

    protected static final Account oracle = Account.newScoreAccount(scoreCount);

    protected MockContract<Rewards> rewards;
    protected MockContract<IRC2> bribeToken;

    protected Score bribing;

    protected void setupBase() throws Exception {
        rewards = new MockContract<>(RewardsScoreInterface.class, sm, owner);
        bribeToken = new MockContract<>(IRC2ScoreInterface.class, sm, owner);
        bribing = sm.deploy(owner, BribingImpl.class, rewards.getAddress());

    }

    protected BigInteger getPeriod() {
        return BigInteger.valueOf(sm.getBlock().getTimestamp()).divide(WEEK_IN_MS).multiply(WEEK_IN_MS);
    }

    protected void addBribe(String source, BigInteger amount) {
        Map<String, Object> params = Map.of("source", source);
        byte[] data = tokenData("addBribe", params);
        bribing.invoke(bribeToken.account, "tokenFallback", bribeToken.getAddress(), amount, data);
    }

    protected void scheduledBribes(String source, BigInteger total, BigInteger[] amounts) {
        Map<String, Object> params = Map.of(
            "source", source,
            "amounts", amounts
        );

        byte[] data = tokenData("scheduledBribes", params);
        bribing.invoke(bribeToken.account, "tokenFallback", bribeToken.getAddress(), total, data);
    }
}
