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
import score.DictDB;
import scorex.util.ArrayList;

import java.util.List;

public class IterableDictDB<K, V> {

    private static final String NAME = "_ITERABLE_DICTDB";

    public final DictDB<K, V> values;
    public final SetDB<K> keys;


    public IterableDictDB(String key, Class<V> valueType, Class<K> keyType, Boolean order) {
        this.keys = new SetDB<>(key + NAME + "_keys", keyType, order);
        this.values = Context.newDictDB(key + NAME + "_values", valueType);
    }

    public List<K> keys() {
        int size = this.keys.size();
        List<K> keyList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            keyList.add(this.keys.get(i));
        }
        return keyList;
    }

    public void set(K key, V value) {
        this.keys.add(key);
        this.values.set(key, value);
    }

    public V get(K key) {
        return this.values.get(key);
    }


}



