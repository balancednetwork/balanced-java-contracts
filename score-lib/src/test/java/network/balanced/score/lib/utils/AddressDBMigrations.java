/*
 * Copyright (c) 2023 Balanced.network.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.utils.contracts.DBMigrationsContract;
import score.Address;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AddressDBMigrations extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score score;

    @BeforeEach
    public void setup() throws Exception {
        mockReadonly();

        score = sm.deploy(owner, DBMigrationsContract.class);
    }

    @Test
    public void testDictDBMigration() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account account3 = sm.createAccount();

        String legacyValue1 = "legacy value 1";
        String legacyValue2 = "legacy value 2";
        String value1 = "value 1";
        String value3 = "value 3";

        // Act
         // Value to be overwritten
        score.invoke(owner, "setLegacyDictDB", account1.getAddress(), legacyValue1);
         // Legacy value to be read
        score.invoke(owner, "setLegacyDictDB", account2.getAddress(), legacyValue2);
         // Overwrite value through new DB
        score.invoke(owner, "setDictDB", account1.getAddress().toString(), value1);
        // Add new value to new DB
        score.invoke(owner, "setDictDB", account3.getAddress().toString(), value3);

        // Assert
        assertEquals(value1, score.call("getDictDB", account1.getAddress().toString()));
        assertEquals(legacyValue2, score.call("getDictDB", account2.getAddress().toString()));
        assertEquals(value3, score.call("getDictDB", account3.getAddress().toString()));
    }

    @Test
    public void testBranchDBMigration() {
        // Arrange
        Address address1 = sm.createAccount().getAddress();
        Address address2 = sm.createAccount().getAddress();
        Address address3 = sm.createAccount().getAddress();

        String key1 = "key1";
        String key2 = "key2";
        String key3 = "key3";

        // Act
        // Value to be overwritten
        score.invoke(owner, "setLegacyBranchDB", key1, address1);
        // Legacy value to be read
        score.invoke(owner, "setLegacyBranchDB", key2, address2);
        // Overwrite value through new DB
        score.invoke(owner, "setBranchDB", key1, address1.toString());
        // Add new value to new DB
        score.invoke(owner, "setBranchDB", key3, address3.toString());

        // Assert
        assertEquals(address1.toString(), score.call("getBranchDB", key1));
        assertEquals(address2.toString(), score.call("getBranchDB", key2));
        assertEquals(address3.toString(), score.call("getBranchDB", key3));
    }

    @Test
    public void testBranchDictDBMigration() {
        // Arrange
        Address address1 = sm.createAccount().getAddress();
        Address address2 = sm.createAccount().getAddress();
        Address address3 = sm.createAccount().getAddress();

        String key1 = "key1";
        String key2 = "key2";
        String key3 = "key3";

        String legacyValue1 = "legacy value 1";
        String legacyValue2 = "legacy value 2";
        String value1 = "value 1";
        String value3 = "value 3";

        // Act
        // Value to be overwritten
        score.invoke(owner, "setLegacyBranchedDictDB", key1, address1, legacyValue1);
        // Legacy value to be read
        score.invoke(owner, "setLegacyBranchedDictDB", key2, address2, legacyValue2);
        // Overwrite value through new DB
        score.invoke(owner, "setBranchedDictDB", key1, address1.toString(), value1);
        // Add new value to new DB
        score.invoke(owner, "setBranchedDictDB", key3, address3.toString(), value3);

        // Assert
        assertEquals(value1, score.call("getBranchedDictDB", key1, address1.toString()));
        assertEquals(legacyValue2, score.call("getBranchedDictDB", key2, address2.toString()));
        assertEquals(value3, score.call("getBranchedDictDB", key3, address3.toString()));
    }
}
