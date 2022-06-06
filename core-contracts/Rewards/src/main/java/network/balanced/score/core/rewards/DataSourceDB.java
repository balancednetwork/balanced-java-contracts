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

package network.balanced.score.core.rewards;

import score.Address;
import score.ArrayDB;
import score.Context;

import static network.balanced.score.core.rewards.RewardsImpl.TAG;
import static network.balanced.score.core.rewards.utils.RewardsConstants.DATASOURCE_DB_PREFIX;
import static network.balanced.score.lib.utils.DBHelpers.contains;

public class DataSourceDB {
    public static final ArrayDB<String> names = Context.newArrayDB("names", String.class);

    private DataSourceDB() {}

    public static DataSourceImpl get(String name) {
        return new DataSourceImpl(DATASOURCE_DB_PREFIX + "|" + name);
    }

    public static int size() {
        return names.size();
    }

    public static void newSource(String name, Address address) {
        Context.require(!contains(names, name), TAG + ": Data source already exists");

        names.add(name);
        DataSourceImpl dataSource = get(name);
        dataSource.setName(name);
        dataSource.setDay(RewardsImpl.getDay());
        dataSource.setContractAddress(address);
    }

    public static void removeSource(String name) {
        // TODO Shouldn't be removed (Also add test cases)
        //  Avoid removing data source, must be disabled instead
        // TODO Double check, remove one, return boolean
        if (!contains(names, name)) {
            return;
        }
        DataSourceImpl dataSource = get(name);
        dataSource.setName(null);
        dataSource.setDay(null);
        dataSource.setContractAddress(null);

        // TODO Use helper method to remove from array db
        String topSourceName = names.pop();
        if (topSourceName.equals(name)) {
            return;
        }

        for(int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(name)) {
                names.set(i, topSourceName);
            }
        }
    }   
}