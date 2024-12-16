package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.*;

public class BranchedNetworkAddressArrayDB<V> {
    private final BranchDB<Address, ArrayDB<V>> legacyAddressDB;
    private final BranchDB<String, ArrayDB<V>> addressDB;

    public BranchedNetworkAddressArrayDB(String id, Class<V> valueClass) {
        this.legacyAddressDB = Context.newBranchDB(id, valueClass);
        this.addressDB = Context.newBranchDB(id + "_migrated", valueClass);
    }

    public ArrayDB<V> at(NetworkAddress user, boolean readOnly){
        ArrayDB<V> value = addressDB.at(user.toString());
        if (value.size()>0) {
            return value;
        }

        if (user.account().startsWith("hx") || user.account().startsWith("cx")) {
            value = legacyAddressDB.at(Address.fromString(user.account()));
            if(!readOnly){
                int valueSize = value.size();
                for(int i=0; i<valueSize; i++){
                    set(user, value.get(i));
                }
                value = addressDB.at(user.toString());
            }
        }

        return value;
    }

    public void set(NetworkAddress address, V value) {
        addressDB.at(address.toString()).add(value);
    }
}
