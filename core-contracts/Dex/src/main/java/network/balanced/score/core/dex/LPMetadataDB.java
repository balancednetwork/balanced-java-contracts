package network.balanced.score.core.dex;

import network.balanced.score.lib.utils.EnumerableSetDB;
import score.Address;

import java.math.BigInteger;

public class LPMetadataDB {
    private static final String LP_METADATA_PREFIX = "lp";

    public EnumerableSetDB<Address> get(BigInteger id) {
        return new EnumerableSetDB<>(LP_METADATA_PREFIX + id.toString(10), Address.class);
    }

}
