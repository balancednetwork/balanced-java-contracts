package network.balanced.score.lib.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.Map;

import com.eclipsesource.json.JsonArray;

import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
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
        assertEquals("Executed", voter.governance.checkVote(id).get("status"));
    }

    public static BigInteger hexObjectToBigInteger(Object hexNumber) {
        String hexString = (String) hexNumber;
        if (hexString.startsWith("0x")) {
            return new BigInteger(hexString.substring(2), 16);
        }
        return new BigInteger(hexString, 16);

    }

    public static Address createIRC2Token(BalancedClient owner, String name, String symbol) {
        String path = System.getProperty("user.dir") + "/../../test-lib/util-contracts/IRC2Token.jar";
        DefaultScoreClient assetClient = _deploy(Env.getDefaultChain().getEndpointURL(), Env.getDefaultChain().networkId, owner.wallet, path,  Map.of("name", name, "symbol", symbol));
        return assetClient._address();
    }

   
}
