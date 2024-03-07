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

package network.balanced.score.spoke.xcall.manager;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.structs.ProtocolConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import foundation.icon.xcall.NetworkAddress;

import java.util.Map;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class SpokeXCallManagerTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account user = sm.createAccount();
    private static final Account proposer = sm.createAccount();
    public MockContract<XCall> xCall;
    public MockContract<SpokeXCallManager> xCallManager;
    public Score manager;
    public static final String ICON_GOVERNANCE = "0x1.icon/cx1";
    public static final String NID = "0x111.icon";
    public static final String[] SOURCES = new String[] { "source1", "source2" };
    public static final String[] DESTINATIONS = new String[] { "dst1", "dst2" };

    @BeforeEach
    void setup() throws Exception {
        xCall = new MockContract<>(XCallScoreInterface.class, XCall.class, sm, owner);

        when(xCall.mock.getNetworkAddress()).thenReturn(new NetworkAddress(NID, xCall.getAddress()).toString());
        manager = sm.deploy(owner, SpokeXCallManagerImpl.class, xCall.getAddress(), ICON_GOVERNANCE,
                new ProtocolConfig(SOURCES, DESTINATIONS));
    }

    @Test
    void name() {
        assertEquals(Names.SPOKE_XCALL_MANAGER, manager.call("name"));
    }

    @Test
    void handleCallMessage_notGovernance() {
        // Act
        Executable nonGovernance = () -> manager.invoke(xCall.account, "handleCallMessage", "Not Governance",
                new byte[0], SOURCES);

        // Assert
        expectErrorMessage(nonGovernance, "Only ICON Governance");
    }

    @Test
    void handleCallMessage_invalidProtocols() {
        // Arrange
        byte[] msg = SpokeXCallManagerMessages.execute("");

        // Act
        Executable invalidProtocols = () -> manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg,
                DESTINATIONS);

        // Assert
        assertThrows(AssertionError.class, invalidProtocols);
    }

    @Test
    void configureProtocol() {
        // Arrange
        String[] sources = new String[] { "new src" };
        String[] destinations = new String[] { "new dst" };
        byte[] msg = SpokeXCallManagerMessages.configureProtocols(sources, destinations);

        // Act
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg, SOURCES);

        // Assert
        Map<String, String[]> cfg = (Map<String, String[]>) manager.call("getProtocols");
        assertArrayEquals(sources, cfg.get("sources"));
        assertArrayEquals(destinations, cfg.get("destinations"));
        assertDoesNotThrow(() -> manager.call("verifyProtocols", (Object) sources));
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) SOURCES),
                "Invalid protocols used to deliver message");

    }

    @Test
    void configureProtocol_default() {
        // Arrange
        byte[] msg = SpokeXCallManagerMessages.configureProtocols(new String[] {}, new String[] {});

        // Act
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg, SOURCES);

        // Assert
        Map<String, String[]> cfg = (Map<String, String[]>) manager.call("getProtocols");
        assertArrayEquals(new String[] {}, cfg.get("sources"));
        assertArrayEquals(new String[] {}, cfg.get("destinations"));
        assertDoesNotThrow(() -> manager.call("verifyProtocols", (Object) new String[] {}));
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) new String[] { "Single" }),
                "Invalid protocols used to deliver message");
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) SOURCES),
                "Invalid protocols used to deliver message");

    }

    @Test
    void configureProtocol_withProposedRemoval() {
        // Arrange
        String[] sources = new String[] { "new src" };
        String[] destinations = new String[] { "new dst" };
        String[] deliverySources = new String[] { SOURCES[0] };
        String removedSource = SOURCES[1];
        byte[] msg = SpokeXCallManagerMessages.configureProtocols(sources, destinations);

        // Act
        manager.invoke(owner, "proposeRemoval", removedSource);
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg, deliverySources);

        // Assert
        Map<String, String[]> cfg = (Map<String, String[]>) manager.call("getProtocols");
        assertArrayEquals(sources, cfg.get("sources"));
        assertArrayEquals(destinations, cfg.get("destinations"));
        assertDoesNotThrow(() -> manager.call("verifyProtocols", (Object) sources));
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) SOURCES),
                "Invalid protocols used to deliver message");
    }

    @Test
    void configureProtocol_backToDefault_withProposedRemoval() {
        // Arrange
        String[] sources = new String[] { "src" };
        String[] destinations = new String[] { "dst" };
        byte[] setupMsg = SpokeXCallManagerMessages.configureProtocols(sources, destinations);
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, setupMsg, SOURCES);

        String[] newSources = new String[] { "new src" };
        String[] newDestinations = new String[] { "new dst" };
        byte[] msg = SpokeXCallManagerMessages.configureProtocols(newSources, newDestinations);

        // Act
        manager.invoke(owner, "proposeRemoval", sources[0]);
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg, new String[] {});

        // Assert
        Map<String, String[]> cfg = (Map<String, String[]>) manager.call("getProtocols");
        assertArrayEquals(newSources, cfg.get("sources"));
        assertArrayEquals(newDestinations, cfg.get("destinations"));
        assertDoesNotThrow(() -> manager.call("verifyProtocols", (Object) newSources));
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) sources),
                "Invalid protocols used to deliver message");
        expectErrorMessage(() -> manager.call("verifyProtocols", (Object) new String[] {}),
                "Invalid protocols used to deliver message");
    }

    @Test
    void configureProtocol_invalidProtocol() {
        // Arrange
        String[] sources = new String[] { "new src" };
        String[] destinations = new String[] { "new dst" };
        String removedSource = SOURCES[1];
        byte[] msg = SpokeXCallManagerMessages.configureProtocols(sources, destinations);

        // Act
        manager.invoke(owner, "proposeRemoval", removedSource);
        Executable invalidProtocols = () -> manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, msg,
                DESTINATIONS);

        // Assert
        expectErrorMessage(invalidProtocols, "Invalid protocols used to deliver message");
    }

    @Test
    void execute() {
        // Arrange
        JsonObject param = new JsonObject()
                .add("type", "Address")
                .add("value", user.getAddress().toString());

        JsonObject data = new JsonObject()
                .add("address", manager.getAddress().toString())
                .add("method", "setXCall")
                .add("parameters", new JsonArray().add(param));
        String transactions = new JsonArray().add(data).toString();
        byte[] executeMessage = SpokeXCallManagerMessages.execute(transactions);

        // Act
        manager.invoke(xCall.account, "handleCallMessage", ICON_GOVERNANCE, executeMessage, SOURCES);

        // Assert
        assertEquals(user.getAddress(), manager.call("getXCall"));
    }

    @Test
    void permissions() {
        Account admin = sm.createAccount();
        manager.invoke(owner, "setAdmin", admin.getAddress());

        assertOnlyCallableBy(xCall.getAddress(), manager, "handleCallMessage", ICON_GOVERNANCE, new byte[0], SOURCES);
        assertOnlyCallableBy(admin.getAddress(), manager, "setAdmin", user.getAddress());
        assertOnlyCallableBy(admin.getAddress(), manager, "proposeRemoval", "");
        assertOnlyCallableByContractOrOwner("setXCall", user.getAddress());
    }

    protected void assertOnlyCallableByContractOrOwner(String method, Object... params) {
        Account nonAuthorizedCaller = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): SenderNotScoreOwnerOrContract: Sender="
                + nonAuthorizedCaller.getAddress() +
                " Owner=" + owner.getAddress() + " Contract=" + manager.getAccount().getAddress();
        Executable unAuthorizedCall = () -> manager.invoke(nonAuthorizedCaller, method, params);
        expectErrorMessage(unAuthorizedCall, expectedErrorMessage);
    }
}