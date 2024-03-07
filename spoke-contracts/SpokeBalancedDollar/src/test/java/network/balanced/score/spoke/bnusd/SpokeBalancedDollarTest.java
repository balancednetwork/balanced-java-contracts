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

package network.balanced.score.spoke.bnusd;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import score.UserRevertedException;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableByOwner;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SpokeBalancedDollarTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account user = sm.createAccount();
    public MockContract<XCall> xCall;
    public MockContract<SpokeXCallManager> xCallManager;
    public Score bnUSD;
    public static final String ICON_BNUSD = "0x1.icon/cx1";
    public static final String NID = "0x111.icon";
    public static final String[] SOURCES = new String[]{"source1","source2"};
    public static final String[] DESTINATIONS = new String[]{"dst1","dst2"};

    @BeforeEach
    void setup() throws Exception {
        xCall = new MockContract<>(XCallScoreInterface.class, XCall.class, sm, owner);
        xCallManager = new MockContract<>(SpokeXCallManagerScoreInterface.class, SpokeXCallManager.class, sm, owner);

        when(xCall.mock.getNetworkAddress()).thenReturn(new NetworkAddress(NID, xCall.getAddress()).toString());
        when(xCallManager.mock.getProtocols()).thenReturn(Map.of("sources", SOURCES, "destinations", DESTINATIONS));
        bnUSD = sm.deploy(owner, SpokeBalancedDollarImpl.class, xCall.getAddress(), ICON_BNUSD, xCallManager.getAddress());
    }

    @Test
    void name() {
        assertEquals(Names.BNUSD, bnUSD.call("name"));
    }

    @Test
    void xCrossTransfer() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        String to = new NetworkAddress(NID, user.getAddress()).toString();
        byte[] transferMsg =  SpokeBalancedDollarMessages.xCrossTransfer("0x1.icon/hx2", to, amount, new byte[0]);

        // Act
        bnUSD.invoke(xCall.account, "handleCallMessage", ICON_BNUSD, transferMsg, SOURCES);

        // Assert
        assertEquals(amount, bnUSD.call("balanceOf", user.getAddress()));
    }


    @Test
    void xCrossTransfer_onlyICONBnUSD() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        String to = new NetworkAddress(NID, user.getAddress()).toString();
        byte[] transferMsg =  SpokeBalancedDollarMessages.xCrossTransfer("0x1.icon/hx2", to, amount, new byte[0]);

        // Act
        Executable onlyICONBnUSD = () -> bnUSD.invoke(xCall.account, "handleCallMessage", "Other", transferMsg, SOURCES);

        // Assert
        expectErrorMessage(onlyICONBnUSD, "Only ICON Balanced dollar");
    }


    @Test
    void handleCallMessage_invalidProtocols() {
        // Arrange
        doThrow(AssertionError.class).when(xCallManager.mock).verifyProtocols(SOURCES);

        // Act
        Executable invalidProtocols = () -> bnUSD.invoke(xCall.account, "handleCallMessage", ICON_BNUSD, new byte[0], SOURCES);

        // Assert
        assertThrows(AssertionError.class, invalidProtocols);
    }

    @Test
    void crossTransfer() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        String to = new NetworkAddress("0x1.icon", "hx1").toString();
        String userAddress = new NetworkAddress(NID, user.getAddress()).toString();
        addBalance(user.getAddress(), amount);
        byte[] expectedMessage =  SpokeBalancedDollarMessages.xCrossTransfer(userAddress, to, amount, new byte[0]);
        byte[] revertMsg = SpokeBalancedDollarMessages.xCrossTransferRevert(user.getAddress(), amount);

        // Act
        bnUSD.invoke(user, "crossTransfer", to, amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, bnUSD.call("balanceOf", user.getAddress()));
        verify(xCall.mock).sendCallMessage(ICON_BNUSD, expectedMessage, revertMsg, SOURCES, DESTINATIONS);
    }

    @Test
    void xTransferRevert_notXCall() {
       // Arrange
       BigInteger amount = BigInteger.TEN;
       byte[] revertMsg = SpokeBalancedDollarMessages.xCrossTransferRevert(user.getAddress(), amount);

       // Act
       Executable onlyICONBnUSD = () -> bnUSD.invoke(xCall.account, "handleCallMessage", "Other", revertMsg, SOURCES);

       // Assert
       expectErrorMessage(onlyICONBnUSD, "Only XCall");
    }


    @Test
    void xTransferRevert() {
       // Arrange
       BigInteger amount = BigInteger.TEN;
       byte[] revertMsg = SpokeBalancedDollarMessages.xCrossTransferRevert(user.getAddress(), amount);

       // Act
       bnUSD.invoke(xCall.account, "handleCallMessage", new NetworkAddress(NID, xCall.getAddress()).toString(), revertMsg, SOURCES);

       // Assert
       assertEquals(amount, bnUSD.call("balanceOf", user.getAddress()));
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(xCall.getAddress(), bnUSD, "handleCallMessage", ICON_BNUSD, new byte[0], SOURCES);
        assertOnlyCallableByOwner(owner.getAddress(), bnUSD, "setXCallManager", user.getAddress());
        assertOnlyCallableByOwner(owner.getAddress(), bnUSD, "setICONBnUSD", "");
        assertOnlyCallableByOwner(owner.getAddress(), bnUSD, "setXCall", user.getAddress());
    }

    void addBalance(Address user, BigInteger amount) {
        String to = new NetworkAddress(NID, user).toString();
        byte[] transferMsg =  SpokeBalancedDollarMessages.xCrossTransfer("0x1.icon/hx2", to, amount, new byte[0]);
        bnUSD.invoke(xCall.account, "handleCallMessage", ICON_BNUSD, transferMsg, SOURCES);
    }

}