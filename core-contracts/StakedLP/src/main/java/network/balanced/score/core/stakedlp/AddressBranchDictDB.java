package network.balanced.score.core.stakedlp;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;

import java.math.BigInteger;

public class AddressBranchDictDB {

    private BranchDB<Address, DictDB<BigInteger, BigInteger>> legacyAddressDictDB;
    private BranchDB<String, DictDB<BigInteger, BigInteger>> addressDictDB;
    private BranchDB<Address, DictDB<BigInteger, Boolean>> migrationDB;

    public AddressBranchDictDB(String id) {
        this.legacyAddressDictDB = Context.newBranchDB(id, BigInteger.class);
        this.addressDictDB = Context.newBranchDB(id + "_migrated", BigInteger.class);
        this.migrationDB = Context.newBranchDB(id + "_migration_db", Boolean.class);
    }

    private BigInteger getLegacy(Address address, BigInteger key) {
        return legacyAddressDictDB.at(address).getOrDefault(key, BigInteger.ZERO);
    }

    private Boolean isMigrated(Address address, BigInteger key) {
        return migrationDB.at(address).getOrDefault(key, false);
    }

    public BigInteger get(NetworkAddress address, BigInteger id, Boolean readonly) {
        BigInteger total = addressDictDB.at(address.toString()).getOrDefault(id, BigInteger.ZERO);
        if (address.account().startsWith("hx") || address.account().startsWith("cx")) {
            Address iconAddr = Address.fromString(address.account());
            if (!isMigrated(iconAddr, id)) {
                total = total.add(getLegacy(iconAddr, id));
                if (!readonly) {
                    migrationDB.at(iconAddr).set(id, true);
                }
            }
        }

        return total;
    }

    public void set(NetworkAddress address, BigInteger id, BigInteger value) {
        addressDictDB.at(address.toString()).set(id,  value);
    }

}
