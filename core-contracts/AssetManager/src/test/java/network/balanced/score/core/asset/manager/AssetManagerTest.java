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

package network.balanced.score.core.asset.manager;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.SpokeAssetManagerMessages;
import network.balanced.score.lib.interfaces.tokens.AssetToken;
import network.balanced.score.lib.interfaces.tokens.AssetTokenScoreInterface;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static java.math.BigInteger.ZERO;
import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetManagerTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score assetManager;
    private AssetManagerImpl assetManagerSpy;
    private MockContract<Governance> governance;
    private MockContract<AssetToken> ethAsset1;
    private MockContract<AssetToken> ethAsset2;
    private MockContract<AssetToken> bscAsset1;
    private MockContract<AssetToken> injLinkAsset;
    private MockContract<AssetToken> linkAsset2;
    private MockContract<AssetToken> invalidAsset;


    private final String ETH_NID = "0x1.ETH";
    private final String BSC_NID = "0x1.BSC";
    private final String NATIVE_NID = "0x1.ICON";
    private final String INJ_NID = "0x1.INJ";
    private final String LINK2_NID = "0x1.link";


    NetworkAddress ethSpoke = new NetworkAddress(ETH_NID, "0x1");
    NetworkAddress bscSpoke = new NetworkAddress(BSC_NID, "0x2");
    NetworkAddress injSpoke = new NetworkAddress(INJ_NID, "inj1x1");
    NetworkAddress link2Spoke = new NetworkAddress(LINK2_NID, "link1x1");
    String ethAsset1Address = "0x3";
    String ethAsset2Address = "0x4";
    String bscAsset1Address = "0x5";
    String injAsset1Address = "inj1x3";
    String link2Asset1Address = "link1x3";


    String[] defaultProtocols = new String[]{"a", "b"};
    String[] defaultDestinationsProtocols = new String[]{"c", "d"};
    private final byte[] tokeBytes = "test".getBytes();

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);
        assetManager = sm.deploy(owner, AssetManagerImpl.class, governance.getAddress(), tokeBytes);

        when(mockBalanced.xCallManager.mock.getProtocols(ETH_NID)).thenReturn(Map.of("sources", defaultProtocols, "destinations", defaultDestinationsProtocols));
        when(mockBalanced.xCallManager.mock.getProtocols(BSC_NID)).thenReturn(Map.of("sources", defaultProtocols, "destinations", defaultDestinationsProtocols));

        assetManager.invoke(governance.account, "addSpokeManager", ethSpoke.toString());
        assetManagerSpy = (AssetManagerImpl) spy(assetManager.getInstance());
        assetManager.setInstance(assetManagerSpy);

        ethAsset1 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        ethAsset2 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        bscAsset1 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        injLinkAsset = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        linkAsset2 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);

        invalidAsset = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);

        try (MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            contextMock.when(() -> Context.deploy(tokeBytes, governance.getAddress(), "ETH1", "ETH TEST TOKEN 1", BigInteger.valueOf(18))).thenReturn(ethAsset1.getAddress());
            contextMock.when(() -> Context.call(AssetManagerImpl.getSystemScoreAddress(), "setScoreOwner", ethAsset1.getAddress(), governance.getAddress())).thenReturn(null);
            assetManager.invoke(governance.account, "deployAsset", new NetworkAddress(ETH_NID, ethAsset1Address).toString(), "ETH1", "ETH TEST TOKEN 1", BigInteger.valueOf(18));
        }

        try (MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            contextMock.when(() -> Context.deploy(tokeBytes, governance.getAddress(), "ETH2", "ETH TEST TOKEN 2", BigInteger.valueOf(18))).thenReturn(ethAsset2.getAddress());
            contextMock.when(() -> Context.call(AssetManagerImpl.getSystemScoreAddress(), "setScoreOwner", ethAsset2.getAddress(), governance.getAddress())).thenReturn(null);
            assetManager.invoke(governance.account, "deployAsset", new NetworkAddress(ETH_NID, ethAsset2Address).toString(), "ETH2", "ETH TEST TOKEN 2", BigInteger.valueOf(18));
        }

        assetManager.invoke(governance.account, "addSpokeManager", bscSpoke.toString());
        try (MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            contextMock.when(() -> Context.deploy(tokeBytes, governance.getAddress(), "BSC", "BSC TEST TOKEN", BigInteger.valueOf(18))).thenReturn(bscAsset1.getAddress());
            contextMock.when(() -> Context.call(AssetManagerImpl.getSystemScoreAddress(), "setScoreOwner", bscAsset1.getAddress(), governance.getAddress())).thenReturn(null);
            assetManager.invoke(governance.account, "deployAsset", new NetworkAddress(BSC_NID, bscAsset1Address).toString(), "BSC", "BSC TEST TOKEN", BigInteger.valueOf(18));
        }

    }

    @Test
    void verifyManagers() {
        assertArrayEquals(new String[]{ethSpoke.toString(), bscSpoke.toString()}, (String[]) assetManager.call("getSpokes"));
    }

    @Test
    void verifyAssets() {
        Map<String, String> assets = (Map<String, String>) assetManager.call("getAssets");
        assertEquals(assets.get(new NetworkAddress(ETH_NID, ethAsset1Address).toString()), ethAsset1.getAddress().toString());
        assertEquals(assets.get(new NetworkAddress(ETH_NID, ethAsset2Address).toString()), ethAsset2.getAddress().toString());
        assertEquals(assets.get(new NetworkAddress(BSC_NID, bscAsset1Address).toString()), bscAsset1.getAddress().toString());

        assertEquals(ethAsset1.getAddress(), (Address) assetManager.call("getAssetAddress", new NetworkAddress(ETH_NID, ethAsset1Address).toString()));
        assertEquals(ethAsset2.getAddress(), (Address) assetManager.call("getAssetAddress", new NetworkAddress(ETH_NID, ethAsset2Address).toString()));
        assertEquals(bscAsset1.getAddress(), (Address) assetManager.call("getAssetAddress", new NetworkAddress(BSC_NID, bscAsset1Address).toString()));
    }


    @Test
    void withdrawTo() {
        // Arrange
        Account user = sm.createAccount();
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        doReturn(amount).when(assetManagerSpy).getAssetDeposit(tokenAddress.toString());

        // Act
        assetManager.invoke(user, "withdrawTo", ethAsset1.getAddress(), ethAccount.toString(), amount);

        // Assert
        byte[] expectedMsg = SpokeAssetManagerMessages.WithdrawTo(tokenAddress.account(), ethAccount.account(), amount);
        byte[] expectedRollback = AssetManagerMessages.withdrawRollback(tokenAddress.toString(), ethAccount.toString(), amount);

        verify(mockBalanced.xCall.mock).sendCallMessage(ethSpoke.toString(), expectedMsg, expectedRollback, defaultProtocols, defaultDestinationsProtocols);
        verify(ethAsset1.mock).burnFrom(user.getAddress().toString(), amount);
    }

    @Test
    void withdrawNativeTo() {
        // Arrange
        Account user = sm.createAccount();
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        doReturn(amount).when(assetManagerSpy).getAssetDeposit(tokenAddress.toString());

        // Act
        assetManager.invoke(user, "withdrawNativeTo", ethAsset1.getAddress(), ethAccount.toString(), amount);

        // Assert
        byte[] expectedMsg = SpokeAssetManagerMessages.WithdrawNativeTo(tokenAddress.account(), ethAccount.account(), amount);
        byte[] expectedRollback = AssetManagerMessages.withdrawRollback(tokenAddress.toString(), ethAccount.toString(), amount);

        verify(mockBalanced.xCall.mock).sendCallMessage(ethSpoke.toString(), expectedMsg, expectedRollback, defaultProtocols, defaultDestinationsProtocols);
        verify(ethAsset1.mock).burnFrom(user.getAddress().toString(), amount);
    }


    @Test
    void withdrawTo_invalidNetwork() {
        // Arrange
        Account user = sm.createAccount();
        NetworkAddress bscAccount = new NetworkAddress(BSC_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;

        // Act & Assert
        Executable withdrawToWrongNetwork = () -> assetManager.invoke(user, "withdrawTo", ethAsset1.getAddress(), bscAccount.toString(), amount);
        expectErrorMessage(withdrawToWrongNetwork, "Wrong network");
    }

    @Test
    void withdrawRollback() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        NetworkAddress xCallAddress = new NetworkAddress(NATIVE_NID, mockBalanced.xCall.getAddress());
        byte[] rollback = AssetManagerMessages.withdrawRollback(tokenAddress.toString(), ethAccount.toString(), amount);

        // Act
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", xCallAddress.toString(), rollback, defaultProtocols);

        // Assert
        BigInteger assetDeposit = (BigInteger) assetManager.call("getAssetDeposit", tokenAddress.toString());
        assertEquals(assetDeposit, amount);
        verify(ethAsset1.mock).mintAndTransfer(ethAccount.toString(), ethAccount.toString(), amount, new byte[0]);
    }

    @Test
    void xCallWithdraw() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] withdraw = AssetManagerMessages.xWithdraw(ethAsset1.getAddress(), amount);
        doReturn(amount).when(assetManagerSpy).getAssetDeposit(tokenAddress.toString());

        // Act
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", ethAccount.toString(), withdraw, defaultProtocols);

        // Assert
        byte[] expectedMsg = SpokeAssetManagerMessages.WithdrawTo(tokenAddress.account(), ethAccount.account(), amount);
        byte[] expectedRollback = AssetManagerMessages.withdrawRollback(tokenAddress.toString(), ethAccount.toString(), amount);

        verify(mockBalanced.xCall.mock).sendCallMessage(ethSpoke.toString(), expectedMsg, expectedRollback, defaultProtocols, defaultDestinationsProtocols);
        verify(ethAsset1.mock).burnFrom(ethAccount.toString(), amount);
        verify(mockBalanced.daofund.mock).claimXCallFee(ETH_NID, true);
    }

    @Test
    void depositTo() {
        // Arrange
        Account user = sm.createAccount();
        String receiverAddress = new NetworkAddress(NATIVE_NID, user.getAddress()).toString();
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] deposit = AssetManagerMessages.deposit(ethAsset1Address, ethAccount.account(), receiverAddress, amount, new byte[0]);

        // Act
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", ethSpoke.toString(), deposit, defaultProtocols);

        // Assertk
        verify(ethAsset1.mock).mintAndTransfer(ethAccount.toString(), receiverAddress, amount, new byte[0]);
    }

    @Test
    void deposit() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] deposit = AssetManagerMessages.deposit(ethAsset1Address, ethAccount.account(), "", amount, new byte[0]);

        // Act
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", ethSpoke.toString(), deposit, defaultProtocols);

        // Assert
        BigInteger assetDeposit = (BigInteger) assetManager.call("getAssetDeposit", tokenAddress.toString());
        assertEquals(assetDeposit, amount);
        verify(ethAsset1.mock).mintAndTransfer(ethAccount.toString(), ethAccount.toString(), amount, new byte[0]);
    }

    @Test
    void deposit_invalidSpokeManager() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] deposit = AssetManagerMessages.deposit(ethAsset1Address, ethAccount.account(), "", amount, new byte[0]);

        // Act & Assert
        Executable invalidSpokeManager = () -> assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", "none/0x1", deposit, defaultProtocols);
        expectErrorMessage(invalidSpokeManager, "Asset manager needs to be whitelisted");
    }


    @Test
    void deposit_wrongSpokeManager() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] deposit = AssetManagerMessages.deposit(ethAsset1Address, ethAccount.account(), "", amount, new byte[0]);

        // Act & Assert
        Executable wrongSpokeManager = () -> assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", bscSpoke.toString(), deposit, defaultProtocols);
        expectErrorMessage(wrongSpokeManager, "Token is not yet deployed");
    }

    @Test
    void deposit_fakeNetworkID() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress tokenAddress = new NetworkAddress(ETH_NID, ethAsset1Address);
        byte[] deposit = AssetManagerMessages.deposit(new NetworkAddress(ETH_NID, ethAsset1Address).toString(), ethAccount.account(), "", amount, new byte[0]);

        // Act & Assert
        Executable wrongSpokeManager = () -> assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", bscSpoke.toString(), deposit, defaultProtocols);
        expectErrorMessage(wrongSpokeManager, "Token is not yet deployed");
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(governance.getAddress(), assetManager, "deployAsset", new NetworkAddress(BSC_NID, bscAsset1Address).toString(), "BSC", "BSC TEST TOKEN", BigInteger.valueOf(18));
        assertOnlyCallableBy(governance.getAddress(), assetManager, "addSpokeManager", "");
        assertOnlyCallableBy(mockBalanced.xCall.getAddress(), assetManager, "handleCallMessage", "", new byte[0], defaultProtocols);
    }

    @Test
    void linkToken() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        BigInteger amount = BigInteger.TEN;
        NetworkAddress injAccount = new NetworkAddress(INJ_NID, "inj1x32");

        // Act
        Executable noSpokeManager = () -> assetManager.invoke(mockBalanced.governance.account, "linkToken", tokenNetworkAddress.toString(), injLinkAsset.getAddress());
        expectErrorMessage(noSpokeManager, "Reverted(0): Add the spoke spoke manager first");

        assetManager.invoke(governance.account, "addSpokeManager", injSpoke.toString());
        assetManager.invoke(mockBalanced.governance.account, "linkToken", tokenNetworkAddress.toString(), injLinkAsset.getAddress());

        Executable alreadyLinked = () -> assetManager.invoke(mockBalanced.governance.account, "linkToken", tokenNetworkAddress.toString(), injLinkAsset.getAddress());
        expectErrorMessage(alreadyLinked, "Reverted(0): Token is already available");

        byte[] deposit = AssetManagerMessages.deposit(injAsset1Address, injAccount.account(), "", amount, new byte[0]);
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", injSpoke.toString(), deposit, defaultProtocols);

        // Assert
        verify(injLinkAsset.mock).mintAndTransfer(injAccount.toString(), injAccount.toString(), amount, new byte[0]);
    }

    @Test
    void linkToken_multipleNetworkAddresses() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        NetworkAddress link2TokenNetworkAddress = new NetworkAddress(LINK2_NID, link2Asset1Address);
        BigInteger amount = BigInteger.TEN;
        NetworkAddress injAccount = new NetworkAddress(INJ_NID, "inj1x32");
        NetworkAddress link2Account = new NetworkAddress(LINK2_NID, "link1x32");
        assetManager.invoke(governance.account, "addSpokeManager", injSpoke.toString());
        assetManager.invoke(governance.account, "addSpokeManager", link2Spoke.toString());
        assetManager.invoke(mockBalanced.governance.account, "linkToken", tokenNetworkAddress.toString(), injLinkAsset.getAddress());
        assetManager.invoke(mockBalanced.governance.account, "linkToken", link2TokenNetworkAddress.toString(), injLinkAsset.getAddress());

        // Act
        byte[] deposit = AssetManagerMessages.deposit(injAsset1Address, injAccount.account(), "", amount, new byte[0]);
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", injSpoke.toString(), deposit, defaultProtocols);
        byte[] link2deposit = AssetManagerMessages.deposit(link2Asset1Address, link2Account.account(), injAccount.toString(), amount, new byte[0]);
        assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", link2Spoke.toString(), link2deposit, defaultProtocols);

        // Assert
        verify(injLinkAsset.mock).mintAndTransfer(injAccount.toString(), injAccount.toString(), amount, new byte[0]);
        verify(injLinkAsset.mock).mintAndTransfer(link2Account.toString(), injAccount.toString(), amount, new byte[0]);
    }

    @Test
    void removeToken() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        BigInteger amount = BigInteger.TEN;
        NetworkAddress injAccount = new NetworkAddress(INJ_NID, "inj1x32");

        // Act
        Executable noSpokeManager = () -> assetManager.invoke(mockBalanced.governance.account, "removeToken", invalidAsset.getAddress(), INJ_NID);
        expectErrorMessage(noSpokeManager, "Reverted(0): Token is not available");

        assetManager.invoke(governance.account, "addSpokeManager", injSpoke.toString());
        assetManager.invoke(mockBalanced.governance.account, "linkToken", tokenNetworkAddress.toString(), injLinkAsset.getAddress());
        assetManager.invoke(mockBalanced.governance.account, "removeToken", injLinkAsset.getAddress(), INJ_NID);

        // Assert
        String nativeAddress = (String) assetManager.call("getNativeAssetAddress", injLinkAsset.getAddress(), INJ_NID);
        assertNull(nativeAddress);
    }

    @Test
    void chainDepositLimit() {
        // Arrange
        BigInteger limit = BigInteger.valueOf(1000000);
        String assetAddress = new NetworkAddress(ETH_NID, ethAsset1Address).toString();
        // Act
        assetManager.invoke(governance.account, "setAssetChainDepositLimit", assetAddress, limit);

        // Assert
        BigInteger ethLimit = (BigInteger) assetManager.call("getAssetChainDepositLimit", assetAddress);
        assertEquals(limit, ethLimit);
    }

    @Test
    void deposit_limitDeposit() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(ETH_NID, "0xTest");
        BigInteger limit = BigInteger.valueOf(5);
        assetManager.invoke(governance.account, "setAssetChainDepositLimit", new NetworkAddress(ETH_NID, ethAsset1Address).toString(), limit);
        BigInteger amount = BigInteger.TEN;
        byte[] deposit = AssetManagerMessages.deposit(ethAsset1Address, ethAccount.account(), "", amount, new byte[0]);

        // Act & Assert
        Executable maxSupplyReached = () -> assetManager.invoke(mockBalanced.xCall.account, "handleCallMessage", ethSpoke.toString(), deposit, defaultProtocols);
        expectErrorMessage(maxSupplyReached, "Max deposit limit exceeded");
    }


}