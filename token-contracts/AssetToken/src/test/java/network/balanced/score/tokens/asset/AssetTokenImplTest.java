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

package network.balanced.score.tokens.asset;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.interfaces.AssetManager;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import score.Address;
import score.Context;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetTokenImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score asset;
    // private AssetManagerImpl assetManagerSpy;
    private MockContract<Governance> governance;
    private MockContract<AssetManager> assetManager;


    private final String ETH_NID = "0x1.ETH";
    private final String BSC_NID = "0x1.BSC";
    private final String NATIVE_NID = "0x1.ICON";

    String name = "test";
    String symbol = "tst";
    BigInteger decimals = BigInteger.valueOf(16);

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        assetManager = mockBalanced.assetManager;
        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);
        asset = sm.deploy(owner, AssetTokenImpl.class, governance.getAddress(), name, symbol, decimals);
    }

    @Test
    void name() {
        assertEquals(name, asset.call("name"));
    }

    @Test
    void symbol() {
        assertEquals(symbol, asset.call("symbol"));
    }

    @Test
    void decimals() {
        assertEquals(decimals, asset.call("decimals"));
    }

    @Test
    void mintAndTransfer() {
        // Arrange
        NetworkAddress from = new NetworkAddress(BSC_NID, "0xTestBSC");
        NetworkAddress to = new NetworkAddress(ETH_NID, "0xTestETH");
        BigInteger amount = BigInteger.TEN;

        // Act
        asset.invoke(assetManager.account, "mintAndTransfer", from.toString(), to.toString(), amount, new byte[0]);

        // Assert
        assertEquals(amount, asset.call("xBalanceOf",  to.toString()));
        assertEquals(BigInteger.ZERO, asset.call("xBalanceOf", from.toString()));
        assertEquals(amount, asset.call("totalSupply"));
    }

    void mintAndTransfer_toSelf() {
        // Arrange
        NetworkAddress from = new NetworkAddress(BSC_NID, "0xTestBSC");
        BigInteger amount = BigInteger.TEN;

        // Act
        asset.invoke(assetManager.account, "mintAndTransfer", from.toString(), from.toString(), amount, new byte[0]);

        // Assert
        assertEquals(amount, asset.call("xBalanceOf",  from.toString()));
        assertEquals(amount, asset.call("totalSupply"));
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(governance.getAddress(), asset, "govHubTransfer", "", "", BigInteger.ZERO, new byte[0]);
        assertOnlyCallableBy(assetManager.getAddress(), asset, "burnFrom", "", BigInteger.ZERO);
        assertOnlyCallableBy(assetManager.getAddress(), asset, "mintAndTransfer", "","", BigInteger.ZERO, new byte[0]);
        assertOnlyCallableBy(mockBalanced.xCall.getAddress(), asset, "handleCallMessage", "", new byte[0], new String[0]);
    }
}
