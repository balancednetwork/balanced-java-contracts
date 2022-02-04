package network.balanced.score.tokens.tokens;

import network.balanced.score.tokens.WorkerToken;
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
        Address governance = WorkerToken._governance.getOrDefault(defaultAddress);
        Context.require(!governance.equals(defaultAddress), WorkerToken.TAG + ": Governance address not set");
        Context.require(sender.equals(governance), WorkerToken.TAG + ": Sender not governance contract");
    }

    public static void onlyAdmin() {
        Address admin = WorkerToken._admin.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!admin.equals(defaultAddress), WorkerToken.TAG + ": Admin address not set");
        Context.require(sender.equals(admin), WorkerToken.TAG + ": Sender not admin");
    }
}