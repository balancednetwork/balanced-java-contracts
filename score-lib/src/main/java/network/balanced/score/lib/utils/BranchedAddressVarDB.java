package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.VarDB;
import score.BranchDB;

//Class to add btp address support to already existing branchDB address db to support string addresses
public class BranchedAddressVarDB<K> {
    private BranchDB<K, VarDB<Address>> legacyAddressDB;
    private BranchDB<K, VarDB<String>> addressDB;

    public BranchedAddressVarDB(String id) {
        this.legacyAddressDB = Context.newBranchDB(id,  Address.class);
        this.addressDB = Context.newBranchDB(id + "_migrated", String.class);
    }

    public AddressVarDB at(K key) {
        return new AddressVarDB(legacyAddressDB.at(key), addressDB.at(key));
    };
}
