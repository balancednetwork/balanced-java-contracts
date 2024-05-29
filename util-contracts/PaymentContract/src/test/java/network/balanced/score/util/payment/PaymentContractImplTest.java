/*
 * Copyright (c) 2024-2024 Balanced.network.
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

package network.balanced.score.util.payment;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.tokens.AssetToken;
import network.balanced.score.lib.interfaces.tokens.AssetTokenScoreInterface;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.test.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class PaymentContractImplTest extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score payments;
    private MockBalanced mockBalanced;
    private final Account recipient = sm.createAccount();

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        payments = sm.deploy(owner, PaymentContractImpl.class, mockBalanced.governance.getAddress());

    }

    @Test
    void basicPaymentContractFlow() {
        // Arrange
        BigInteger amount = BigInteger.valueOf(5000).multiply(EXA);
        BigInteger duration = BigInteger.valueOf(10000);

        when(mockBalanced.bnUSD.mock.balanceOf(payments.getAddress())).thenReturn(amount);

        // Act
        payments.invoke(owner, "configureContract", duration, recipient.getAddress());
        payments.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), amount, new byte[0]);

        // Assert
        sm.getBlock().increase(999); // 10% of time, one block will increase when we claim
        BigInteger expectedOutStandingPayment = BigInteger.valueOf(500).multiply(EXA);
        payments.invoke(recipient, "claimPayment");

        verify(mockBalanced.bnUSD.mock).transfer(recipient.getAddress(), expectedOutStandingPayment, new byte[0]);
        when(mockBalanced.bnUSD.mock.balanceOf(payments.getAddress())).thenReturn(amount.subtract(expectedOutStandingPayment));

        sm.getBlock().increase(duration.intValue()); // full duration passes
        expectedOutStandingPayment = BigInteger.valueOf(4500).multiply(EXA);
        payments.invoke(recipient, "claimPayment");

        verify(mockBalanced.bnUSD.mock).transfer(recipient.getAddress(), expectedOutStandingPayment, new byte[0]);
    }

    @Test
    void cancelContract() {
        // Arrange
        BigInteger amount = BigInteger.valueOf(5000).multiply(EXA);
        BigInteger duration = BigInteger.valueOf(10000);

        when(mockBalanced.bnUSD.mock.balanceOf(payments.getAddress())).thenReturn(amount);

        payments.invoke(owner, "configureContract", duration, recipient.getAddress());
        payments.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), amount, new byte[0]);

        sm.getBlock().increase(999); // 10% of time, one block will increase when we claim
        BigInteger expectedOutStandingPayment = BigInteger.valueOf(500).multiply(EXA);
        payments.invoke(recipient, "claimPayment");

        verify(mockBalanced.bnUSD.mock).transfer(recipient.getAddress(), expectedOutStandingPayment, new byte[0]);
        when(mockBalanced.bnUSD.mock.balanceOf(payments.getAddress())).thenReturn(amount.subtract(expectedOutStandingPayment));

        // Act
        payments.invoke(owner, "cancelContract");

        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.daofund.getAddress(), amount.subtract(expectedOutStandingPayment), new byte[0]);
    }

    public BigInteger getHeight() {
        return BigInteger.valueOf(sm.getBlock().getHeight());
    }
}
