package network.balanced.score.lib.utils;

import score.ArrayDB;
import score.Context;

import java.util.List;

import scorex.util.ArrayList;


public class BagDB<V> {
    private static final String NAME = "_BAGDB";

    private final String name;
    private final ArrayDB<V> items;
    private Boolean order;

    public BagDB(String key, Class<V> valueType, Boolean order) {
        name = key + NAME;
        items = Context.newArrayDB(name + "_items", valueType);
        this.order = order;
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
            if (items.get(i) == item) {
                return true;
            }
        }
        return false;
    }


    public void remove(V item) {
        if (order == null) {
            order = false;
        }
        List<V> tmp = new ArrayList<>();
        int itemsSize = this.items.size();
        if (order) {
            for (int i = 0; i < itemsSize; i++) {
                if (items.get(i) == null) {
                    break;
                }
                V cur = items.pop();
                if (cur != item) {
                    tmp.add(cur);
                } else {
                    break;
                }
            }

            for (int i = 0; i < tmp.size(); i++) {
                if (tmp.get(i) == null) {
                    break;
                }
                V cur = tmp.remove(tmp.size() - 1);
                items.add(cur);
            }
        } else {
            for (int i = 0; i < itemsSize; i++) {
                if (items.get(i) == item) {
                    if (i == itemsSize - 1) {
                        items.pop();
                    } else {
                        items.set(i, items.pop());
                    }
                }
            }
        }
    }

}


