package network.balanced.score.core.db;

import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class IdFactory {
    private static final String NAME = "_ID_FACTORY";
    private final VarDB<BigInteger> uid;
    public IdFactory(String key) {
        String name = key + NAME;
        this.uid = Context.newVarDB(name + "_uid", BigInteger.class);
    }

    public BigInteger getUid(){
        uid.set(uid.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        return uid.getOrDefault(BigInteger.ZERO);
    }
}
