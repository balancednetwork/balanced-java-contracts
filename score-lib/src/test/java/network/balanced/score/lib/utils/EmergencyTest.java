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

package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import score.Context;
import scorex.util.HashMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

public class EmergencyTest extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score score;
    private static MockBalanced mockBalanced;

    public static class DummyScore extends BalancedEmergencyHandling {
        public DummyScore(Address governance) {
            BalancedAddressManager.setGovernance(governance);
        }

        public void test() {
            checkStatus();
        }
    }

    public static class proxyScore {
        Address target;

        public proxyScore(Address target) {
            this.target = target;
        }

        public void test() {
            Context.call(target, "test");
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        score = sm.deploy(owner, DummyScore.class, mockBalanced.governance.getAddress());
        BalancedEmergencyHandling.blacklist = null;
    }

    @Test
    void governanceNotConfigured() throws Exception {
        score = sm.deploy(owner, DummyScore.class, Account.newScoreAccount(scoreCount++).getAddress());
        Executable callTest = () -> score.invoke(owner, "test");
        assertDoesNotThrow(callTest);
    }

    @Test
    void disableAndEnable() {
        // Arrange
        String expectedErrorMessage = "Balanced is currently disabled";
        Executable callTest = () -> score.invoke(owner, "test");

        // Act
        assertDoesNotThrow(callTest);
        score.invoke(mockBalanced.governance.account, "disable");

        // Assert
        expectErrorMessage(callTest, expectedErrorMessage);

        score.invoke(mockBalanced.governance.account, "enable");
        assertDoesNotThrow(callTest);
    }



    @Test
    void blacklist() throws Exception {
        // Arrange
        String expectedErrorMessage = "This address is blacklisted";
        Account blacklistedUser = sm.createAccount();
        Executable callTest = () -> score.invoke(blacklistedUser, "test");

        // Act
        assertDoesNotThrow(callTest);
        when(mockBalanced.governance.mock.isBlacklisted(blacklistedUser.getAddress().toString())).thenReturn(true);

        // Act & Assert
        expectErrorMessage(callTest, expectedErrorMessage);
    }

    @Test
    void blacklistOrigin() throws Exception {
        // Arrange
        Score proxyScore = sm.deploy(owner, proxyScore.class, score.getAddress());

        String expectedErrorMessage = "This origin caller is blacklisted";
        Account blacklistedUser = sm.createAccount();
        Executable proxyCallTest = () -> proxyScore.invoke(blacklistedUser, "test");

        when(mockBalanced.governance.mock.isBlacklisted(blacklistedUser.getAddress().toString())).thenReturn(true);

        // Act & Assert
        expectErrorMessage(proxyCallTest, expectedErrorMessage);
    }
}