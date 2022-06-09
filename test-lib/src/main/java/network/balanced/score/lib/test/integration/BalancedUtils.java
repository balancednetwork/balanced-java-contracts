package network.balanced.score.lib.test.integration;

import java.math.BigInteger;

import com.eclipsesource.json.JsonArray;

import network.balanced.score.lib.interfaces.Governance;

public class BalancedUtils {

    public static void executeVoteActions(Balanced balanced, BalancedClient voter, String name, JsonArray actions) throws Exception {
        BigInteger day = voter.governance.getDay();
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        
        voter.governance.defineVote(name, "test", voteStart, snapshot, actions.toString());
        BigInteger id = voter.governance.getVoteIndex(name);
        balanced.increaseDay(2);

        for (BalancedClient client : balanced.balancedClients.values()) {
            try {
                client.governance.castVote(id, true);
            } catch (Exception e) {}
        }

        balanced.increaseDay(2);

        voter.governance.evaluateVote(id);
    }
}
