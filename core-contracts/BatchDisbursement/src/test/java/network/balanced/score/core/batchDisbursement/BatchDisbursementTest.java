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

package network.balanced.score.core.batchDisbursement;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BatchDisbursementTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private MockBalanced mockBalanced;
    private Score disbursement;

    @Test
    void deployAndTransfer() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        BigInteger balance = BigInteger.TEN;
        when(mockBalanced.sicx.mock.balanceOf(any(Address.class))).thenReturn(balance);

        disbursement = sm.deploy(owner, BatchDisbursement.class, mockBalanced.governance.getAddress());

        verify(mockBalanced.sicx.mock).transfer(mockBalanced.daofund.getAddress(), balance, new byte[0]);
        verify(mockBalanced.sicx.mock).balanceOf(disbursement.getAddress());
    }
}