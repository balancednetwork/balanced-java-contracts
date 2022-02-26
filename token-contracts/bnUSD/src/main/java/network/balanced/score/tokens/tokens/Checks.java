package network.balanced.score.tokens.tokens;

import network.balanced.score.tokens.BalancedDollar;
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
        Address governance = BalancedDollar.governance.getOrDefault(defaultAddress);
        Context.require(!governance.equals(defaultAddress), BalancedDollar.TAG + ": Governance address not set");
        Context.require(sender.equals(governance), BalancedDollar.TAG + ": Sender not governance contract");
    }

    public static void onlyAdmin() {
        Address admin = BalancedDollar.admin.getOrDefault(defaultAddress);
        Address sender = Context.getCaller();
        Context.require(!admin.equals(defaultAddress), BalancedDollar.TAG + ": Admin address not set");
        Context.require(sender.equals(admin), BalancedDollar.TAG + ": Sender not admin");
    }
}