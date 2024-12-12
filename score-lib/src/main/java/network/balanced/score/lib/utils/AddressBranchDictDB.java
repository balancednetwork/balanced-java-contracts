package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;


public class AddressBranchDictDB<K, V> {

    private final BranchDB<Address, DictDB<K, V>> legacyAddressDictDB;
    private final BranchDB<String, DictDB<K, V>> addressDictDB;

    public AddressBranchDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDictDB = Context.newBranchDB(id, valueClass);
        this.addressDictDB = Context.newBranchDB(id + "_migrated", valueClass);
    }


    public DictDB<K, V> at(NetworkAddress key) {
        DictDB<K, V> value = addressDictDB.at(key.toString());
        if (value != null) {
            return value;
        }

        if (key.account().startsWith("hx") || key.account().startsWith("cx")) {
            value = legacyAddressDictDB.at(Address.fromString(key.account()));
        }


        return value;
    }

}
