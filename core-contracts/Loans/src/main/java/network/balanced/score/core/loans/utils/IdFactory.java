package network.balanced.score.core.loans.utils;

import score.Context;
import score.VarDB;

public class IdFactory {
    private static final String _NAME = "_ID_FACTORY";
    private static VarDB<Integer> uid;
    
    public IdFactory (String varKey) {
        String key = varKey + _NAME;
        uid =  Context.newVarDB(key + "_uid", Integer.class);
    }

    public int getUid() {
        int newUid = uid.getOrDefault(0) + 1;
        uid.set(newUid);
        return newUid;
    }

    public int getLastUid() {
        return uid.getOrDefault(0);
    }
}