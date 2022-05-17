package network.balanced.score.core.dex;

import score.Context;
import score.VarDB;

public class Check {
    public static void isOn(VarDB<Boolean> scoreAddressOn) {
        Context.require(scoreAddressOn.get().equals(true), "NotLaunched: Function cannot be called before the DEX is turned on");
    }
}
