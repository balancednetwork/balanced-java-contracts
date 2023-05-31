package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.DictDB;
import score.BranchDB;

//Class to add btp address support to already existing branchDB address db to support string addresses
public class BranchedAddressDictDB<K, V> {
    private BranchDB<K, DictDB<Address, V>> legacyAddressDB;
    private BranchDB<K, DictDB<String, V>> addressDB;

    public BranchedAddressDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDB = Context.newBranchDB(id, valueClass);
        this.addressDB = Context.newBranchDB(id + "_migrated", valueClass);
    }

    public AddressDictDB<V> at(K key) {
        return new AddressDictDB<V>(legacyAddressDB.at(key), addressDB.at(key));
    };
}
