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

package network.balanced.score.lib.test;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnitTest extends TestBase {
    public static int scoreCount = 1;
    private static final ServiceManager sm = getServiceManager();

    public static void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(expectedErrorMessage, e.getMessage());
    }

    public static void testGovernance(Score contractUnderTest, Account governanceScore, Account owner) {
        assertEquals(governanceScore.getAddress(), contractUnderTest.call("getGovernance"));

        Account newGovernance = Account.newScoreAccount(scoreCount++);
        contractUnderTest.invoke(owner, "setGovernance", newGovernance.getAddress());
        assertEquals(newGovernance.getAddress(), contractUnderTest.call("getGovernance"));

        Account sender = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + sender.getAddress() + "Owner=" + owner.getAddress();
        Executable setGovernanceNotFromOwner = () -> contractUnderTest.invoke(sender, "setGovernance",
                governanceScore.getAddress());
        expectErrorMessage(setGovernanceNotFromOwner, expectedErrorMessage);
    }

    public static void testAdmin(Score contractUnderTest, Account governanceScore, Account admin) {
        Account nonGovernance = sm.createAccount();
        String expectedErrorMessage =
                "Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() +
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
        String expectedErrorMessage = "Address Check: Address provided is an EOA address. A contract address is " +
                "required.";
        Executable setScoreToInvalidAddress = () -> contractUnderTest.invoke(adminAccount, setterMethod,
                sm.createAccount().getAddress());
        expectErrorMessage(setScoreToInvalidAddress, expectedErrorMessage);
    }

    public static <T> void testAdminControlMethods(Score contractUnderTest, Account governanceScore,
                                                   Account adminAccount, String setterMethod, T parameterToSet,
                                                   String getterMethod) {
        String expectedErrorMessage = "Authorization Check: Address not set";
        Executable setScoreWithoutAdmin = () -> contractUnderTest.invoke(adminAccount, setterMethod, parameterToSet);
        expectErrorMessage(setScoreWithoutAdmin, expectedErrorMessage);

        testAdmin(contractUnderTest, governanceScore, adminAccount);

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + adminAccount.getAddress();
        Executable setScoreNotFromAdmin = () -> contractUnderTest.invoke(nonAdmin, setterMethod, parameterToSet);
        expectErrorMessage(setScoreNotFromAdmin, expectedErrorMessage);

        contractUnderTest.invoke(adminAccount, setterMethod, parameterToSet);
        assertEquals(parameterToSet, contractUnderTest.call(getterMethod));
    }

    public static byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }
}
