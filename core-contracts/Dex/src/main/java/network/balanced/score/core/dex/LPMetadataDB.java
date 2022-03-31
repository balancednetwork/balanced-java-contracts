package network.balanced.score.core.router;

import score.Context;
import score.ArrayDB;
import score.DictDB;
import score.Address;
import network.balanced.score.core.router.EnumerableSetDB;

import java.math.BigInteger;
import scorex.util.HashMap;
import scorex.util.ArrayList;
import java.util.Map;
import java.util.Arrays;

public class LPMetadataDB {
    private static final String LP_METADATA_PREFIX = "lp";

    public EnumerableSetDB<Address> get(BigInteger id) {
        return new EnumerableSetDB<Address>(LP_METADATA_PREFIX + id.toString(10), Address.class);
    }

    }
