package network.balanced.score.lib.utils;

import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;

//Class to add btp address support to already existing branchDB address db to support string addresses
public class BranchedNetworkAddressDictDB<K, V> {
    private BranchDB<K, DictDB<Address, V>> legacyAddressDB;
    private BranchDB<K, DictDB<String, V>> addressDB;

    public BranchedNetworkAddressDictDB(String id, Class<V> valueClass) {
        this.legacyAddressDB = Context.newBranchDB(id, valueClass);
        this.addressDB = Context.newBranchDB(id + "_migrated", valueClass);
    }

    public NetworkAddressDictDB<V> at(K key) {
        return new NetworkAddressDictDB<V>(legacyAddressDB.at(key), addressDB.at(key));
    };
}
