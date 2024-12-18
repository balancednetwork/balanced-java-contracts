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

package network.balanced.score.tokens.wicx;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.test.VarargAnyMatcher;
import network.balanced.score.lib.test.mock.MockBalanced;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class WICXTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static MockBalanced mockBalanced;
    private static Account governanceScore;

    private static Score wICX;
    private static WICX wICXSpy;
    private static final Account user = sm.createAccount();

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governanceScore = mockBalanced.governance.account;
        wICX = sm.deploy(owner, WICX.class, governanceScore.getAddress());

        wICXSpy = (WICX) spy(wICX.getInstance());
        wICX.setInstance(wICXSpy);
    }

    @Test
    void testTransferToEOA() {
        Account user2 = sm.createAccount();
        BigInteger icxSent = BigInteger.valueOf(500);
        doReturn(icxSent).when(wICXSpy).getICXDeposit();
        wICX.getAccount().addBalance("ICX", icxSent);

        // Execute transfer function
        wICX.invoke(user, "transfer", user2.getAddress(), icxSent, new byte[0]);

        // Verify mint is called if Context.getValue() > 0
        verify(wICXSpy).mint(user.getAddress(), icxSent);

        // Verify super.transfer is called
        verify(wICXSpy).transfer(user2.getAddress(), icxSent, new byte[0]);

        // Since `user1` is an EOA, it should call burn and transfer ICX
        verify(wICXSpy).burn(user2.getAddress(), icxSent);
        assertEquals(user2.getBalance(), icxSent);

    }

    @Test
    void testTransferToContract() {
        BigInteger icxSent = BigInteger.valueOf(500);
        doReturn(icxSent).when(wICXSpy).getICXDeposit();
        wICX.getAccount().addBalance("ICX", icxSent);

        // Execute transfer function
        wICX.invoke(user, "transfer", mockBalanced.dex.getAddress(), icxSent, new byte[0]);

        // Verify mint is called if Context.getValue() > 0
        verify(wICXSpy).mint(user.getAddress(), icxSent);

        // Verify super.transfer is called
        verify(wICXSpy).transfer(mockBalanced.dex.getAddress(), icxSent, new byte[0]);

        assertEquals(wICX.call("balanceOf", mockBalanced.dex.getAddress()), icxSent);
        assertEquals(mockBalanced.dex.account.getBalance(), BigInteger.ZERO);
        assertEquals(wICX.getAccount().getBalance(), icxSent);

    }

    @Test
    void testFallback() {
        BigInteger icxSent = BigInteger.valueOf(500);
        doReturn(icxSent).when(wICXSpy).getICXDeposit();
        wICX.getAccount().addBalance("ICX", icxSent);

        // Execute transfer function
        wICX.invoke(user, "fallback");

        // Assert
        assertEquals(wICX.call("balanceOf", user.getAddress()), icxSent);
        assertEquals(wICX.getAccount().getBalance(), icxSent);

    }

    @Test
    void testUnwrap() {
        BigInteger icxSent = BigInteger.valueOf(500);
        BigInteger unwrapAmount = BigInteger.valueOf(200);

        doReturn(icxSent).when(wICXSpy).getICXDeposit();
        wICX.getAccount().addBalance("ICX", icxSent);

        // Execute transfer function
        wICX.invoke(user, "fallback");
        wICX.invoke(user, "unwrap", unwrapAmount);

        assertEquals(wICX.call("balanceOf", user.getAddress()), icxSent.subtract(unwrapAmount));
        assertEquals(user.getBalance(), unwrapAmount);
        assertEquals(wICX.getAccount().getBalance(), icxSent.subtract(unwrapAmount));
    }

}
