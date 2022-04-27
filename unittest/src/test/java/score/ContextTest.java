/*
 * Copyright 2021 ICONLOOP Inc.
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

package score;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.impl.Crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score echoScore;
    private static Score helloScore;

    @BeforeEach
    void setUp() throws Exception {
        echoScore = sm.deploy(owner, Echo.class);
        helloScore = sm.deploy(owner, HelloWorld.class, "Alice", echoScore.getAddress());
    }

    @Test
    void getAddress() {
        assertEquals(helloScore.getAddress(), helloScore.call("getAddress"));
    }

    @Test
    void getOwner() {
        assertEquals(owner.getAddress(), helloScore.call("getOwner"));
    }

    @Test
    void getBlockTimestamp() {
        long start = sm.getBlock().getTimestamp();
        assertEquals(start, helloScore.call("getBlockTimestamp"));
        sm.getBlock().increase(100);
        assertEquals(start + 2_000_000 * 100, helloScore.call("getBlockTimestamp"));
    }

    @Test
    void hash() {
        byte[] data = "Hello world".getBytes();
        assertArrayEquals(Crypto.hash("sha3-256", data),
                (byte[]) helloScore.call("computeHash", "sha3-256", data));
    }

    @Test
    void callCasted() {
       String echoMessage = "test";
       assertEquals(echoMessage, helloScore.call("castedEcho", echoMessage));
    }

    @Test
    void callTyped() {
        String echoMessage = "test";
       assertEquals(echoMessage, helloScore.call("typedEcho", echoMessage));
    }
}
