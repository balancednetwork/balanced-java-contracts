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

package network.balanced.score.lib.test;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.utils.Check;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UnitTest extends TestBase {
    public static int scoreCount = 1;
    private static final ServiceManager sm = getServiceManager();

    protected static MockedStatic<Check> mockedCheck;

    public static void mockReadonly() {
        mockedCheck = Mockito.mockStatic(Check.class, Mockito.CALLS_REAL_METHODS);
        mockedCheck.when(Check::readonly).thenAnswer((I) -> {
            try {
                // fails the fetch caller of readonly method
                Context.getCaller();
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }

    @AfterEach
    public void teardownMocks() {
        if (mockedCheck != null) {
            mockedCheck.close();
        }
    }

    public static void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertTrue(e.getMessage().contains(expectedErrorMessage));
    }

    public static void testGovernance(Score contractUnderTest, Account governanceScore, Account owner) {
        assertEquals(governanceScore.getAddress(), contractUnderTest.call("getGovernance"));

        Account newGovernance = Account.newScoreAccount(scoreCount++);
        contractUnderTest.invoke(owner, "setGovernance", newGovernance.getAddress());
        assertEquals(newGovernance.getAddress(), contractUnderTest.call("getGovernance"));

        Account sender = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): SenderNotScoreOwner: Sender=" + sender.getAddress() + "Owner=" + owner.getAddress();
        Executable setGovernanceNotFromOwner = () -> contractUnderTest.invoke(sender, "setGovernance",
                governanceScore.getAddress());
        expectErrorMessage(setGovernanceNotFromOwner, expectedErrorMessage);
    }

    public static void testAdmin(Score contractUnderTest, Account governanceScore, Account admin) {
        Account nonGovernance = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() +
                        " Authorized Caller: " + governanceScore.getAddress();
        Executable setAdminNotFromGovernance = () -> contractUnderTest.invoke(nonGovernance, "setAdmin",
                admin.getAddress());
        expectErrorMessage(setAdminNotFromGovernance, expectedErrorMessage);

        contractUnderTest.invoke(governanceScore, "setAdmin", admin.getAddress());
        assertEquals(admin.getAddress(), contractUnderTest.call("getAdmin"));
    }

    public static void testContractSettersAndGetters(Score contractUnderTest, Account governanceScore,
                                                     Account adminAccount, String setterMethod, Address addressToSet,
                                                     String getterMethod) {
        testAdminControlMethods(contractUnderTest, governanceScore, adminAccount, setterMethod, addressToSet,
                getterMethod);
        String expectedErrorMessage = "Reverted(0): Address Check: Address provided is an EOA address. A contract " +
                "address is " +
                "required.";
        Executable setScoreToInvalidAddress = () -> contractUnderTest.invoke(adminAccount, setterMethod,
                sm.createAccount().getAddress());
        expectErrorMessage(setScoreToInvalidAddress, expectedErrorMessage);
    }

    public static <T> void testAdminControlMethods(Score contractUnderTest, Account governanceScore,
                                                   Account adminAccount, String setterMethod, T parameterToSet,
                                                   String getterMethod) {
        testAdmin(contractUnderTest, governanceScore, adminAccount);
        assertOnlyCallableByAdmin(contractUnderTest, setterMethod, parameterToSet);

        contractUnderTest.invoke(adminAccount, setterMethod, parameterToSet);
        assertEquals(parameterToSet, contractUnderTest.call(getterMethod));
    }

    public static <T> void testGovernanceControlMethods(Score contractUnderTest, Account governanceScore,
                                                        Account owner, String setterMethod, T parameterToSet,
                                                        String getterMethod) {

        assertOnlyCallableByGovernance(contractUnderTest, setterMethod, parameterToSet);
        contractUnderTest.invoke(governanceScore, setterMethod, parameterToSet);
        assertEquals(parameterToSet, contractUnderTest.call(getterMethod));
    }

    public static <T> void testOwnerControlMethods(Score contractUnderTest, String setterMethod,
                                                   String getterMethod, T parameterToSet) {
        Account nonOwner = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): SenderNotScoreOwner: Sender=" + nonOwner.getAddress() + "Owner=" + contractUnderTest.getOwner().getAddress();
        Executable notOwnerCall = () -> contractUnderTest.invoke(nonOwner, setterMethod, parameterToSet);
        expectErrorMessage(notOwnerCall, expectedErrorMessage);

        contractUnderTest.invoke(contractUnderTest.getOwner(), setterMethod, parameterToSet);
        assertEquals(parameterToSet, contractUnderTest.call(getterMethod));
    }

    public static void testIsContract(Account caller, Score contractUnderTest, String setterMethod,
                                      Address parameterToSet, String getterMethod) {
        Account nonContractAddress = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): Address Check: Address provided is an EOA address. A contract " +
                "address is required.";
        Executable nonContractParameter = () -> contractUnderTest.invoke(caller, setterMethod,
                nonContractAddress.getAddress());
        expectErrorMessage(nonContractParameter, expectedErrorMessage);

        contractUnderTest.invoke(contractUnderTest.getOwner(), setterMethod, parameterToSet);
        assertEquals(parameterToSet, contractUnderTest.call(getterMethod));
    }

    public static byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }

    public static void assertOnlyCallableByGovernance(Score contractUnderTest, String method, Object... params) {
        Address governance = (Address) contractUnderTest.call("getGovernance");
        Account nonGovernance = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() +
                        " Authorized Caller: " + governance;
        System.out.println(expectedErrorMessage);
        Executable setScoreNotFromGovernance = () -> contractUnderTest.invoke(nonGovernance, method, params);
        expectErrorMessage(setScoreNotFromGovernance, expectedErrorMessage);
    }

    public static void assertOnlyCallableByAdmin(Score contractUnderTest, String method, Object... params) {
        Address admin = (Address) contractUnderTest.call("getAdmin");

        Account nonAdmin = sm.createAccount();
        assertNotEquals(nonAdmin.getAddress(), admin);
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                        " Authorized Caller: " + admin;
        Executable setScoreNotFromAdmin = () -> contractUnderTest.invoke(nonAdmin, method, params);
        expectErrorMessage(setScoreNotFromAdmin, expectedErrorMessage);
    }

    public static void assertOnlyCallableBy(Address caller, Score contractUnderTest, String method, Object... params) {
        Account nonAuthorizedCaller = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonAuthorizedCaller.getAddress() +
                        " Authorized Caller: " + caller;
        System.out.println(expectedErrorMessage);
        Executable unAuthorizedCall = () -> contractUnderTest.invoke(nonAuthorizedCaller, method, params);
        expectErrorMessage(unAuthorizedCall, expectedErrorMessage);
    }

}
