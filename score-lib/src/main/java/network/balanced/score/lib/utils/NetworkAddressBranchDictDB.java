package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.Optional;

public class NetworkAddressBranchDictDB<K, V> {

    private final BranchDB<Address, DictDB<K, V>> legacyAddressDictDB;
    private final BranchDB<String, DictDB<K, V>> addressDictDB;

    public NetworkAddressBranchDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDictDB = Context.newBranchDB(id, valueClass);
        this.addressDictDB = Context.newBranchDB(id + "_migrated", valueClass);
    }


    public V get(NetworkAddress address, K id, boolean readOnly, @Optional V defaultValue) {
        V value = addressDictDB.at(address.toString()).get(id);
        if (value != null) {
            return value;
        }

        if (address.account().startsWith("hx") || address.account().startsWith("cx")) {
            value = legacyAddressDictDB.at(Address.fromString(address.account())).get(id);
            if(!readOnly){
                set(address, id, value);
            }
        }

        if(value==null){
            return defaultValue;
        }

        return value;
    }

    public V getOrDefault(NetworkAddress address, K id, V defaultValue) {
        return get(address, id, true, defaultValue);
    }


    public void set(NetworkAddress address, K id, V value) {
        addressDictDB.at(address.toString()).set(id, value);
    }
}
