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

package network.balanced.score.core.asset.manager;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.tokens.AssetToken;
import network.balanced.score.lib.interfaces.tokens.AssetTokenScoreInterface;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import score.Address;
import score.Context;
import xcall.score.lib.util.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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


    private final String ETH_NID = "0x1.ETH";
    private final String BSC_NID = "0x1.BSC";
    private final String NATIVE_NID = "0x1.ICON";

    NetworkAddress ethSpoke = new NetworkAddress(ETH_NID, "0x1");
    NetworkAddress bscSpoke = new NetworkAddress(BSC_NID, "0x2");
    String ethAsset1Address = "0x3";
    String ethAsset2Address = "0x4";
    String bscAsset1Address = "0x5";

    private final byte[] tokeBytes = "test".getBytes();

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);
        assetManager = sm.deploy(owner, AssetManagerImpl.class, governance.getAddress(), tokeBytes);

        assetManager.invoke(governance.account, "addSpokeManager", ethSpoke.toString());
        assetManagerSpy = (AssetManagerImpl) spy(assetManager.getInstance());
        assetManager.setInstance(assetManagerSpy);

        ethAsset1 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        ethAsset2 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);
        bscAsset1 = new MockContract<>(AssetTokenScoreInterface.class, AssetToken.class, sm, governance.account);

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
            contextMock.when(() -> Context.deploy(tokeBytes, governance.getAddress(),  "BSC", "BSC TEST TOKEN", BigInteger.valueOf(18))).thenReturn(bscAsset1.getAddress());
            contextMock.when(() -> Context.call(AssetManagerImpl.getSystemScoreAddress(), "setScoreOwner", bscAsset1.getAddress(), governance.getAddress())).thenReturn(null);
            assetManager.invoke(governance.account, "deployAsset", new NetworkAddress(BSC_NID, bscAsset1Address).toString(),  "BSC", "BSC TEST TOKEN", BigInteger.valueOf(18));
        }
    }

    @Test
    void verifyManagers() {
        assertArrayEquals(new String[]{ethSpoke.toString(), bscSpoke.toString()}, (String[])assetManager.call("getSpokes"));
    }

    @Test
    void verifyAssets() {
        Map<String, String> assets = (Map<String, String>)assetManager.call("getAssets");
        assertEquals(assets.get(new NetworkAddress(ETH_NID, ethAsset1Address).toString()), ethAsset1.getAddress().toString());
        assertEquals(assets.get(new NetworkAddress(ETH_NID, ethAsset2Address).toString()), ethAsset2.getAddress().toString());
        assertEquals(assets.get(new NetworkAddress(BSC_NID, bscAsset1Address).toString()), bscAsset1.getAddress().toString());

        assertEquals(ethAsset1.getAddress(), (Address)assetManager.call("getAssetAddress", new NetworkAddress(ETH_NID, ethAsset1Address).toString()));
        assertEquals(ethAsset2.getAddress(), (Address)assetManager.call("getAssetAddress", new NetworkAddress(ETH_NID, ethAsset2Address).toString()));
        assertEquals(bscAsset1.getAddress(), (Address)assetManager.call("getAssetAddress", new NetworkAddress(BSC_NID, bscAsset1Address).toString()));
    }

    // TODO more test when functionallity is more decided
}