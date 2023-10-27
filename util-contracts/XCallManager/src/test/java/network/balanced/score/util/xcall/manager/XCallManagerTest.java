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

package network.balanced.score.util.xcall.manager;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.structs.ProtocolConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class XCallManagerTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account receiver = sm.createAccount();
    private static final Account prep_address = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score xCallManager;

    private final BigInteger amount = new BigInteger("54321");

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        xCallManager = sm.deploy(owner, XCallManagerImpl.class, mockBalanced.governance.getAddress());
    }

    @Test
    public void configureProtocols() {
        // Arrange
        String nid = "nid";

        // Act
        xCallManager.invoke(owner, "configureProtocols", nid, new String[]{"a", "b"}, new String[]{"c", "d"});

        // Assert
        Map<String, String[]> protocols = (Map<String, String[]>) xCallManager.call("getProtocols", nid);
        assertArrayEquals(protocols.get("sources"), new String[]{"a", "b"});
        assertArrayEquals(protocols.get("destinations"), new String[]{"c", "d"});
    }

    @Test
    public void verifyProtocols() {
        // Arrange
        String nid = "nid";
        String[] sources = new String[]{"a", "b"};

        // Act
        xCallManager.invoke(owner, "configureProtocols", nid, sources, new String[]{"c", "d"});

        // Assert
        assertDoesNotThrow(() -> xCallManager.call("verifyProtocols", nid, sources));
    }

    @Test
    public void verifyProtocols_differentOrder() {
        // Arrange
        String nid = "nid";
        String[] sources = new String[]{"a", "b"};
        String[] sourcesReceived = new String[]{"b", "a"};

        // Act
        xCallManager.invoke(owner, "configureProtocols", nid, sources, new String[]{"c", "d"});

        // Assert
        assertDoesNotThrow(() -> xCallManager.call("verifyProtocols", nid, sources));
    }

    @Test
    public void verifyProtocols_partial() {
        // Arrange
        String nid = "nid";
        String[] sources = new String[]{"a", "b"};
        String[] sourcesReceived = new String[]{"a"};
        String expectedErrorMessage = "Invalid protocols used to deliver message";

        // Act
        xCallManager.invoke(owner, "configureProtocols", nid, sources, new String[]{"c", "d"});

        // Assert
        expectErrorMessage(() -> xCallManager.call("verifyProtocols", nid, sourcesReceived), expectedErrorMessage);
    }

    @Test
    public void verifyProtocols_wrong() {
        // Arrange
        String nid = "nid";
        String[] sources = new String[]{"a", "b"};
        String[] sourcesReceived = new String[]{"c", "d"};
        String expectedErrorMessage = "Invalid protocols used to deliver message";

        // Act
        xCallManager.invoke(owner, "configureProtocols", nid, sources, new String[]{"c", "d"});

        // Assert
        expectErrorMessage(() -> xCallManager.call("verifyProtocols", nid, sourcesReceived), expectedErrorMessage);
    }

}