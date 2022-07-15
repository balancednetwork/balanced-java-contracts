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


public class DataSourceDB {
    public static final ArrayDB<String> names = Context.newArrayDB("names", String.class); // depreacted

    public static DataSourceImpl get(String name) {
        DataSourceImpl dataSource = _get(name);
        Context.require(name.equals(dataSource.getName()),  TAG + ": Data source does not exist");

        return dataSource;
    }

    private static DataSourceImpl _get(String name) {
        return new DataSourceImpl(DATASOURCE_DB_PREFIX + "|" + name);
    }

    // deprecated
    public static int size() {
        return names.size();
    }

    public static void newSource(String name, Address contractAddress, Address dataProvider) {
        DataSourceImpl dataSource = _get(name);
        Context.require(!name.equals(dataSource.getName()),  TAG + ": Data source does not exist");

        dataSource.setName(name);
        dataSource.setContractAddress(contractAddress);
        dataSource.setDataProvider(dataProvider);
    }
}