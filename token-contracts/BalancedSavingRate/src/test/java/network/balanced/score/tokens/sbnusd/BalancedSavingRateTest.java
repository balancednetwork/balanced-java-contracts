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

 package network.balanced.score.tokens.bsr;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.interfaces.Savings;
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

import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalancedSavingRateTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score bsr;
    private MockContract<Governance> governance;
    private MockContract<Savings> savings;


    private final String ETH_NID = "0x1.ETH";
    private final String BSC_NID = "0x1.BSC";
    private final String NATIVE_NID = "0x1.ICON";

    BigInteger decimals = BigInteger.valueOf(16);

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governance = mockBalanced.governance;
        savings = mockBalanced.savings;
        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);
        bsr = sm.deploy(owner, BalancedSavingRateImpl.class, governance.getAddress());
    }

    @Test
    void mintWithTokenFallback() {
        // Arrange
        BigInteger amount = BigInteger.TEN;

        // Act
        bsr.invoke(savings.account, "mint", amount, new byte[0]);

        // Assert
        verify(savings.mock).tokenFallback(EOA_ZERO, amount, new byte[0]);
    }

    @Test
    void mintToWithTokenFallback() {
        // Arrange
        BigInteger amount = BigInteger.TEN;

        // Act
        bsr.invoke(savings.account, "mintTo", mockBalanced.loans.getAddress().toString(), amount, new byte[0]);

        // Assert
        verify(mockBalanced.loans.mock).tokenFallback(EOA_ZERO, amount, new byte[0]);

    }

    void mintTWithTokenFallbackHub() {
        // Arrange
        String user = "0x1.eth/0x3";
        BigInteger amount = BigInteger.TEN;

        // Act
        bsr.invoke(savings.account, "mintTo", user, amount, new byte[0]);

        // Assert
        assertEquals(amount, bsr.call("xBalanceOf", user));
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(governance.getAddress(), bsr, "govTransfer", owner.getAddress(), owner.getAddress(), BigInteger.ZERO, new byte[0]);
        assertOnlyCallableBy(governance.getAddress(), bsr, "govHubTransfer", "", "", BigInteger.ZERO, new byte[0]);
        assertOnlyCallableBy(savings.getAddress(), bsr, "burn", BigInteger.ZERO);
        assertOnlyCallableBy(savings.getAddress(), bsr, "burnFrom", owner.getAddress(), BigInteger.ZERO);
        assertOnlyCallableBy(savings.getAddress(), bsr, "mint", BigInteger.ZERO, new byte[0]);
        assertOnlyCallableBy(savings.getAddress(), bsr, "mintTo", "", BigInteger.ZERO,  new byte[0]);
    }
}
