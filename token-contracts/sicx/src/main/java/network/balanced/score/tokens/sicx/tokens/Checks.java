package network.balanced.score.tokens.sicx.tokens;

import score.Address;
import score.Context;
import network.balanced.score.tokens.sicx.Sicx;
import static network.balanced.score.tokens.sicx.Sicx.TAG;

public class Checks {
    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void onlyAdmin() {
        Address admin = Sicx.admin.get();
        Address sender = Context.getCaller();
        Context.require(sender.equals(admin), TAG + ": Sender not admin");
    }
}
