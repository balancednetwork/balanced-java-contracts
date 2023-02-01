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

package network.balanced.score.lib.utils.contracts;

import network.balanced.score.lib.utils.AddressDictDB;
import network.balanced.score.lib.utils.BranchedAddressVarDB;
import score.*;
import score.annotation.External;

public class DBMigrationsContract {

    private static final String BRANCH_DB_ID = "branch_db";
    private static final BranchDB<String, VarDB<Address>> legacyBranchDB = Context.newBranchDB(BRANCH_DB_ID, String.class);
    private static final BranchedAddressVarDB<String> migratedBranchDB = new BranchedAddressVarDB<String>(BRANCH_DB_ID);

    private static final String DICT_DB_ID = "dict_db";
    private static final DictDB<Address, String> legacyDictDB = Context.newDictDB(DICT_DB_ID, String.class);
    private static final AddressDictDB<String> migratedDictDB = new AddressDictDB<>(DICT_DB_ID, String.class);

    public DBMigrationsContract() {
    }

    @External(readonly = true)
    public String getBranchDB(String key) {
        return migratedBranchDB.at(key).get();
    }


    @External(readonly = true)
    public String getDictDB(String address) {
        return migratedDictDB.get(address);
    }

    @External
    public void setLegacyBranchDB(String key, Address value) {
        legacyBranchDB.at(key).set(value);
    }

    @External
    public void setBranchDB(String key, String value) {
        migratedBranchDB.at(key).set(value);
    }

    @External
    public void setLegacyDictDB(Address address, String value) {
        legacyDictDB.set(address, value);
    }

    @External
    public void setDictDB(String address, String value) {
        migratedDictDB.set(address, value);
    }
}