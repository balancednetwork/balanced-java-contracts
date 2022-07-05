/*
 * Copyright (c) 2022 Balanced.network.
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
import scorex.util.ArrayList;

import java.util.List;


public class BagDB<V> {

    private final ArrayDB<V> items;
    private final Boolean order;

    public BagDB(String key, Class<V> valueType, Boolean order) {
        this.items = Context.newArrayDB(key + "_BAGDB" + "_items", valueType);
        this.order = order != null && order;
    }

    public int size() {
        return items.size();
    }

    public V get(int index) {
        return items.get(index);
    }

    public void add(V item) {
        items.add(item);
    }

    public boolean contains(V item) {
        int itemsSize = this.items.size();
        for (int i = 0; i < itemsSize; i++) {
            if (objectsEquals(items.get(i), item)) {
                return true;
            }
        }
        return false;
    }

    public void remove(V item) {
        List<V> tmp = new ArrayList<>();
        int itemsSize = this.items.size();
        if (this.order) {
            for (int i = itemsSize-1; i >= 0; i--) {
                V cur = this.items.pop();
                if (!objectsEquals(cur, item)) {
                    tmp.add(cur);
                }else{
                    break;
                }
            }

            int tmpSize = tmp.size();
            for (int i = 0; i < tmpSize; i++) {
                V cur = tmp.remove(tmp.size() - 1);
                this.items.add(cur);
            }
        } else {
            for (int i = 0; i < itemsSize; i++) {
                if (objectsEquals(items.get(i), item)) {
                    V lastItem = items.pop();
                    if (i != itemsSize - 1) {
                        items.set(i, lastItem);
                        break;
                    }
                }
            }
        }
    }

    public static boolean objectsEquals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

}


