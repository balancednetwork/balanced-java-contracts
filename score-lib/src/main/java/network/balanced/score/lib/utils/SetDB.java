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

package network.balanced.score.lib.utils;

import score.Context;


public class SetDB<T> extends BagDB<T> {

    public SetDB(String key, Class<T> valueType, Boolean order) {
        super(key + "_SETDB", valueType, order);
    }

    public void add(T item) {
        if (!super.contains(item)) {
            super.add(item);
        }
    }

    public void remove(T item) {
        if (!super.contains(item)) {
            Context.revert("Item not found " + item);
        }
        super.remove(item);
    }

}


