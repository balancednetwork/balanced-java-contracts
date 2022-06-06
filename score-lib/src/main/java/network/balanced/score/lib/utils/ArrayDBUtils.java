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

import score.Address;
import score.ArrayDB;

public class ArrayDBUtils {
    public static <T> Boolean arrayDbContains(ArrayDB<T> arrayDB, T item) {
        final int size = arrayDB.size();
        for (int i = 0; i < size; i++) {
            if (arrayDB.get(i).equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static <T> Boolean removeFromArraydb(T _item, ArrayDB<T> _array) {
        final int size = _array.size();
        if (size < 1) {
            return false;
        }
        T top = _array.get(size - 1);
        for (int i = 0; i < size; i++) {
            if (_array.get(i).equals(_item)) {
                _array.set(i, top);
                _array.pop();
                return true;
            }
        }

        return false;
    }
}
