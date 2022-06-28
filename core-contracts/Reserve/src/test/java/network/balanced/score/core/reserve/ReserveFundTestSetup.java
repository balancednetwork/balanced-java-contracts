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

package network.balanced.score.core.reserve;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReserveFundTestSetup extends ReserveFundTestBase {

    @BeforeEach
    public void setupContract() throws Exception {
       super.setup();
    }

    @Test
    @DisplayName("Deployment with non contract address")
    void testDeploy() {
        Account notContract = sm.createAccount();
        Executable deploymentWithNonContract = () -> sm.deploy(owner, ReserveFund.class, notContract.getAddress());

        String expectedErrorMessage = "Reverted(0): ReserveFund: Governance address should be a contract";
        InvocationTargetException e = Assertions.assertThrows(InvocationTargetException.class,
                deploymentWithNonContract);
        assertEquals(expectedErrorMessage, e.getCause().getMessage());
    }

    @Test
    void name() {
        String contractName = "Balanced Reserve Fund";
        assertEquals(contractName, reserve.call("name"));
    }

    @Test
    void setgetGovernance() {
        testGovernance(reserve, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(reserve, governanceScore, admin);
    }

    @Test
    void setAndGetLoans() {
        testContractSettersAndGetters(reserve, governanceScore, admin, "setLoans", loans.getAddress(), "getLoans");
    }

    @Test
    void testSetBaln() {
        testContractSettersAndGetters(reserve, governanceScore, admin, "setBaln", baln.getAddress(), "getBaln");
    }

    @Test
    void setAndGetSicx() {
        testContractSettersAndGetters(reserve, governanceScore, admin, "setSicx", baln.getAddress(), "getSicx");
    }
}