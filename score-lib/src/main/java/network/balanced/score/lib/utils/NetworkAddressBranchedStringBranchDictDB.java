package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.Optional;

public class NetworkAddressBranchedStringBranchDictDB<K1, K2, V> {
    private final BranchDB<Address, BranchDB<K1, DictDB<K2, V>>> legacyAddressDictDB;
    private final BranchDB<String, BranchDB<K1, DictDB<K2, V>>> addressDictDB;

    public NetworkAddressBranchedStringBranchDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDictDB = Context.newBranchDB(id, valueClass);
        this.addressDictDB = Context.newBranchDB(id + "_migrated", valueClass);
    }


    public V get(NetworkAddress address, K1 key1, K2 key2, boolean readOnly, @Optional V defaultValue) {
        V value = addressDictDB.at(address.toString()).at(key1).get(key2);
        if (value != null) {
            return value;
        }

        if (address.account().startsWith("hx") || address.account().startsWith("cx")) {
            value = legacyAddressDictDB.at(Address.fromString(address.account())).at(key1).get(key2);
            if(!readOnly){
                set(address, key1, key2, value);
            }
        }

        if(value==null){
            return defaultValue;
        }

        return value;
    }

    public V getOrDefault(NetworkAddress address, K1 key1, K2 key2, V defaultValue) {
        return get(address, key1, key2, true, defaultValue);
    }


    public void set(NetworkAddress address, K1 key1, K2 key2, V value) {
        addressDictDB.at(address.toString()).at(key1).set(key2, value);
    }
}
