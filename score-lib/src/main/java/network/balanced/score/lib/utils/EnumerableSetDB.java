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

import score.ArrayDB;
import score.Context;
import score.DictDB;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;


public class EnumerableSetDB<V> {
    private final ArrayDB<V> entries;
    private final DictDB<V, Integer> indexes;

    public EnumerableSetDB(String varKey, Class<V> valueClass) {
        // array of valueClass
        this.entries = Context.newArrayDB(varKey + "_es_entries", valueClass);
        // value => array index
        this.indexes = Context.newDictDB(varKey + "_es_indexes", Integer.class);
    }

    public int length() {
        return entries.size();
    }

    public V at(int index) {
        return entries.get(index);
    }

    public boolean contains(V value) {
        return indexes.get(value) != null;
    }

    public Integer indexOf(V value) {
        Integer result = indexes.get(value);
        if (result != null) {
            return result - 1;
        }
        return null;
    }

    public void add(V value) {
        if (!contains(value)) {
            entries.add(value);
            indexes.set(value, entries.size());
        }
    }

    public V remove(V value) {
        Integer valueIndex = indexOf(value);

        if (valueIndex != null) {
            int lastIndex = entries.size();
            V lastValue = entries.pop();
            indexes.set(value, null);
            if (lastIndex != valueIndex) {
                entries.set(valueIndex - 1, lastValue);
                indexes.set(lastValue, valueIndex);
                return lastValue;
            }
        }
        return null;
    }

    public List<V> range(BigInteger start, BigInteger stop) {
        List<V> data = new ArrayList<>();
        BigInteger size = BigInteger.valueOf(entries.size());
        if (start.compareTo(BigInteger.ZERO) >= 0 && start.compareTo(size) < 0 && start.compareTo(stop) < 0) {
            BigInteger end = (stop.compareTo(size) <= 0) ? stop : size;
            for (int i = start.intValue(); i < end.intValue(); i++) {
                data.add(entries.get(i));
            }
        }
        return data;
    }
}

