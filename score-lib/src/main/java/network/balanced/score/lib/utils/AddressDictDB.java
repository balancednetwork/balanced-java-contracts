package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.DictDB;
import static network.balanced.score.lib.utils.Check.readonly;

//Class to add btp address support to already existing DictDB address db to support string addresses
public class AddressDictDB<V> {
    private DictDB<Address, V> legacyAddressDB;
    private DictDB<String, V> addressDB;

    public AddressDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDB = Context.newDictDB(id, valueClass);
        this.addressDB = Context.newDictDB(id + "_migrated", valueClass);
    }

    public AddressDictDB(DictDB<Address, V> legacy, DictDB<String, V> current) {
        this.legacyAddressDB = legacy;
        this.addressDB = current;
    }

    public void set(String key, V value) {
        addressDB.set(key, value);
    };

    public V get(String key) {
        V value = addressDB.get(key);
        if (value != null) {
            return value;
        }

        if (key.startsWith("hx") || key.startsWith("cx")) {
            value = legacyAddressDB.get(Address.fromString(key));
        }

        if (value != null && !readonly()) {
            set(key, value);
        }

        return value;
    }

    public V getOrDefault(String key, V _default) {
        V value = get(key);
        if (value == null) {
            return _default;
        }

        return value;
    }
}