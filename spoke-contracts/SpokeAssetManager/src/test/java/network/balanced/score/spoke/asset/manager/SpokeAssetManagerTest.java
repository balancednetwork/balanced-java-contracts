/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.spoke.asset.manager;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.RevertedException;

import foundation.icon.xcall.NetworkAddress;
import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableByOwner;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SpokeAssetManagerTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static Account owner;
    private static Account user;

    public MockContract<XCall> xCall;
    public MockContract<SpokeXCallManager> xCallManager;
    public MockContract<HSP20> token;
    public Score assetManager;
    public static final String ICON_ASSET_MANAGER = "0x1.icon/cx1";
    public static final String NID = "0x111.icon";
    public static final String[] SOURCES = new String[] { "source1", "source2" };
    public static final String[] DESTINATIONS = new String[] { "dst1", "dst2" };

    @BeforeEach
    void setup() throws Exception {
        owner = sm.createAccount();
        user = sm.createAccount();
        xCall = new MockContract<>(XCallScoreInterface.class, XCall.class, sm, owner);
        xCallManager = new MockContract<>(SpokeXCallManagerScoreInterface.class, SpokeXCallManager.class, sm, owner);
        when(xCall.mock.getNetworkAddress()).thenReturn(new NetworkAddress(NID, xCall.getAddress()).toString());
        when(xCallManager.mock.getProtocols()).thenReturn(Map.of("sources", SOURCES, "destinations", DESTINATIONS));
        token = new MockContract<>(HSP20ScoreInterface.class, HSP20.class, sm, owner);
        assetManager = sm.deploy(owner, SpokeAssetManagerImpl.class, xCall.getAddress(), ICON_ASSET_MANAGER,
                xCallManager.getAddress());
    }

    @Test
    void name() {
        assertEquals(Names.SPOKE_ASSET_MANAGER, assetManager.call("name"));
    }

    @Test
    void withdrawTo() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] withdrawMsg = SpokeAssetManagerMessages.WithdrawTo(EOA_ZERO.toString(), user.getAddress().toString(),
                amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        assetManager.invoke(xCall.account, "handleCallMessage", ICON_ASSET_MANAGER, withdrawMsg, SOURCES);

        // Assert
        assertEquals(user.getBalance(), amount);
    }


    @Test
    void withdrawTo_token() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] withdrawMsg = SpokeAssetManagerMessages.WithdrawTo(token.getAddress().toString(), user.getAddress().toString(),
                amount);

        // Act
        assetManager.invoke(xCall.account, "handleCallMessage", ICON_ASSET_MANAGER, withdrawMsg, SOURCES);

        // Assert
        verify(token.mock).transfer(user.getAddress(), amount);
    }


    @Test
    void withdrawTo_onlyAssetManager() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] withdrawMsg = SpokeAssetManagerMessages.WithdrawTo(EOA_ZERO.toString(), user.getAddress().toString(),
                amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        Executable invalidCaller = () -> assetManager.invoke(xCall.account, "handleCallMessage", "other", withdrawMsg,
                SOURCES);

        // Assert
        assertEquals(user.getBalance(), BigInteger.ZERO);
        expectErrorMessage(invalidCaller, "Only ICON Asset Manager");
    }

    @Test
    void withdrawNativeTo() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] withdrawMsg = SpokeAssetManagerMessages.WithdrawNativeTo(EOA_ZERO.toString(),
                user.getAddress().toString(), amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        assetManager.invoke(xCall.account, "handleCallMessage", ICON_ASSET_MANAGER, withdrawMsg, SOURCES);

        // Assert
        assertEquals(user.getBalance(), amount);
    }

    @Test
    void withdrawNativeTo_onlyAssetManager() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] withdrawMsg = SpokeAssetManagerMessages.WithdrawNativeTo(EOA_ZERO.toString(),
                user.getAddress().toString(), amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        Executable invalidCaller = () -> assetManager.invoke(xCall.account, "handleCallMessage", "other", withdrawMsg,
                SOURCES);

        // Assert
        assertEquals(user.getBalance(), BigInteger.ZERO);
        expectErrorMessage(invalidCaller, "Only ICON Asset Manager");
    }

    @Test
    void handleCallMessage_invalidProtocols() {
        // Arrange
        doThrow(RevertedException.class).when(xCallManager.mock).verifyProtocols(SOURCES);

        // Act
        Executable invalidProtocols = () -> assetManager.invoke(xCall.account, "handleCallMessage", ICON_ASSET_MANAGER,
                new byte[0], SOURCES);

        // Assert
        assertThrows(RevertedException.class, invalidProtocols);
    }

    @Test
    void deposit() {
        // Arrange
        BigInteger amount = BigInteger.valueOf(100);
        BigInteger fee = BigInteger.TEN;
        String to = new NetworkAddress("0x1.icon", "hx1").toString();
        user.addBalance(amount);
        when(xCall.mock.getFee("0x1.icon", true, SOURCES)).thenReturn(fee);

        byte[] depositMsg = AssetManagerMessages.deposit(EOA_ZERO.toString(), user.getAddress().toString(), to,
                amount.subtract(fee), new byte[0]);
        byte[] revertMsg = SpokeAssetManagerMessages.DepositRevert(EOA_ZERO, user.getAddress(), amount.subtract(fee));

        // Act
        assetManager.invoke(user, amount, "deposit", to, new byte[0]);

        // Assert
        verify(xCall.mock).sendCallMessage(ICON_ASSET_MANAGER, depositMsg, revertMsg, SOURCES, DESTINATIONS);
    }

    @Test
    void depositRevert() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] depositRevert = SpokeAssetManagerMessages.DepositRevert(EOA_ZERO, user.getAddress(), amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        assetManager.invoke(xCall.account, "handleCallMessage", new NetworkAddress(NID, xCall.getAddress()).toString(),
                depositRevert, SOURCES);

        // Assert
        assertEquals(user.getBalance(), amount);
    }


    @Test
    void depositToken() {
        // Arrange
        BigInteger amount = BigInteger.valueOf(100);
        BigInteger fee = BigInteger.TEN;
        String to = new NetworkAddress("0x1.icon", "hx1").toString();
        user.addBalance(fee);
        when(xCall.mock.getFee("0x1.icon", true, SOURCES)).thenReturn(fee);
        when(token.mock.transferFrom(user.getAddress(), assetManager.getAddress(), amount)).thenReturn(true);

        byte[] depositMsg = AssetManagerMessages.deposit(token.getAddress().toString(), user.getAddress().toString(), to,
                amount, new byte[0]);
        byte[] revertMsg = SpokeAssetManagerMessages.DepositRevert(token.getAddress(), user.getAddress(), amount);

        // Act
        assetManager.invoke(user, fee, "depositToken", token.getAddress(), amount, to, new byte[0]);

        // Assert
        verify(token.mock).transferFrom(user.getAddress(), assetManager.getAddress(), amount);
        verify(xCall.mock).sendCallMessage(ICON_ASSET_MANAGER, depositMsg, revertMsg, SOURCES, DESTINATIONS);
    }

    @Test
    void depositRevert_token() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] depositRevert = SpokeAssetManagerMessages.DepositRevert(token.getAddress(), user.getAddress(), amount);

        // Act
        assetManager.invoke(xCall.account, "handleCallMessage", new NetworkAddress(NID, xCall.getAddress()).toString(),
                depositRevert, SOURCES);

        // Assert
        verify(token.mock).transfer(user.getAddress(), amount);
    }

    @Test
    void depositRevert_onlyXCall() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        byte[] depositRevert = SpokeAssetManagerMessages.DepositRevert(EOA_ZERO, user.getAddress(), amount);
        assetManager.getAccount().addBalance(amount);

        // Act
        Executable invalidCaller = () -> assetManager.invoke(xCall.account, "handleCallMessage", "other", depositRevert,
                SOURCES);

        // Assert
        assertEquals(user.getBalance(), BigInteger.ZERO);
        expectErrorMessage(invalidCaller, "Only XCall");
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(xCall.getAddress(), assetManager, "handleCallMessage", ICON_ASSET_MANAGER, new byte[0],
                SOURCES);
        assertOnlyCallableByOwner(owner.getAddress(), assetManager, "setXCallManager", user.getAddress());
        assertOnlyCallableByOwner(owner.getAddress(), assetManager, "setICONAssetManager", "");
        assertOnlyCallableByOwner(owner.getAddress(), assetManager, "setXCall", user.getAddress());
    }
}