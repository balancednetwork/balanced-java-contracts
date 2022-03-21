package network.balanced.score.core;

import score.Address;
import score.Context;

public class Checks {

    public static Address defaultAddress = new Address(new byte[Address.LENGTH]);

    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void onlyGovernance() {
        Address sender = Context.getCaller();
        Address governance = FeeHandler.governance.getOrDefault(defaultAddress);
        Context.require(!governance.equals(defaultAddress), FeeHandler.TAG + ": Governance address not set");
        Context.require(sender.equals(governance), FeeHandler.TAG + ": Sender not governance contract");
    }

}
