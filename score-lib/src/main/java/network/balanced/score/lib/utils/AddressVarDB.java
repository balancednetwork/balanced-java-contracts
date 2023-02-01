package network.balanced.score.lib.utils;

import score.Address;
import score.VarDB;

import static network.balanced.score.lib.utils.Check.readonly;

//Class to add btp address support to already existing DictDB address db to support string addresses
public class AddressVarDB {
    private VarDB<Address> legacyAddressDB;
    private VarDB<String> addressDB;

    public AddressVarDB(VarDB<Address> legacy, VarDB<String> current) {
        this.legacyAddressDB = legacy;
        this.addressDB = current;
    }

    public void set(String value) {
        addressDB.set(value);
    };

    public String get() {
        String value = addressDB.get();
        if (value != null) {
            return value;
        }

        value = legacyAddressDB.get().toString();
        if (value != null && !readonly()) {
            set(value);
        }

        return value;
    }

    public String getOrDefault(String _default) {
        String value = get();
        if (value == null) {
            return _default;
        }

        return value;
    }
}
