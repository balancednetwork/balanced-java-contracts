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

package network.balanced.score.core.stakedlp;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import foundation.icon.xcall.NetworkAddress;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import score.Address;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DBTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score dummyScore;

    public static class DummyScore extends AddressBranchDictDB {

        public DummyScore() {
            super("test");
        }

        public void setLegacy(Address address, BigInteger key, BigInteger value) {
            legacyAddressDictDB.at(address).set(key, value);
        }
    }

    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
    }

    @Test
    public void onlyLegacyValue() {
        // Arrange
        Address user = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.TEN;
        dummyScore.invoke(owner, "setLegacy", user, id, value);

        // Act
        BigInteger balance = (BigInteger) dummyScore.call("get", new NetworkAddress("test", user), id, true);

        // Assert
        assertEquals(balance, value);
    }

    @Test
    public void bothValues() {
        // Arrange
        Address user = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger legacyValue = BigInteger.TEN;
        BigInteger value = BigInteger.TWO;
        dummyScore.invoke(owner, "setLegacy", user, id, legacyValue);
        dummyScore.invoke(owner, "set", new NetworkAddress("test", user), id, value);

        // Act
        BigInteger balance = (BigInteger) dummyScore.call("get", new NetworkAddress("test", user), id, true);

        // Assert
        assertEquals(balance, legacyValue.add(value));
    }

    @Test
    public void migrateValue() {
        // Arrange
        Address user = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger legacyValue = BigInteger.TEN;
        BigInteger value = BigInteger.TWO;
        dummyScore.invoke(owner, "setLegacy", user, id, legacyValue);
        dummyScore.invoke(owner, "set", new NetworkAddress("test", user), id, value);

        // Act
        dummyScore.invoke(owner, "get", new NetworkAddress("test", user), id, false);

        // Assert
        // In a real scenario set would be called, but for this the value is simply
        // marked as migrated and wont be used anymore
        BigInteger balance = (BigInteger) dummyScore.call("get", new NetworkAddress("test", user), id, true);
        assertEquals(balance, value);
    }

    @Test
    public void migrateContractValue() {
        // Arrange
        Address user = dummyScore.getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger legacyValue = BigInteger.TEN;
        BigInteger value = BigInteger.TWO;
        dummyScore.invoke(owner, "setLegacy", user, id, legacyValue);
        dummyScore.invoke(owner, "set", new NetworkAddress("test", user), id, value);

        // Act
        dummyScore.invoke(owner, "get", new NetworkAddress("test", user), id, false);

        // Assert
        // In a real scenario set would be called, but for this the value is simply
        // marked as migrated and wont be used anymore
        BigInteger balance = (BigInteger) dummyScore.call("get", new NetworkAddress("test", user), id, true);
        assertEquals(balance, value);
    }

    @Test
    public void onlyNew() {
        // Arrange
        Address user = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.TWO;
        dummyScore.invoke(owner, "set", new NetworkAddress("test", user), id, value);

        // Act
        BigInteger balance = (BigInteger) dummyScore.call("get", new NetworkAddress("test", user), id, true);

        // Assert
        assertEquals(balance, value);
    }

    @Test
    public void networkAddress() {
        // Arrange
        NetworkAddress user = new NetworkAddress("test", "test");
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.TWO;
        dummyScore.invoke(owner, "set", user, id, value);

        // Act
        BigInteger balance = (BigInteger) dummyScore.call("get", user, id, true);

        // Assert
        assertEquals(balance, value);
    }

}
