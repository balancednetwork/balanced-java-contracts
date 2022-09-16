/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.lib.test.integration;

import com.eclipsesource.json.JsonArray;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;

import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BalancedUtils {

    public static void executeVoteActions(Balanced balanced, BalancedClient voter, String name, JsonArray actions) {
        BigInteger day = voter.governance.getDay();
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);

        voter.governance.defineVote(name, "test", voteStart, snapshot, actions.toString());
        BigInteger id = voter.governance.getVoteIndex(name);
        balanced.increaseDay(2);

        for (BalancedClient client : balanced.balancedClients.values()) {
            try {
                client.governance.castVote(id, true);
            } catch (Exception ignored) {
            }
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
        DefaultScoreClient assetClient = _deploy(Env.getDefaultChain().getEndpointURL(),
                Env.getDefaultChain().networkId, owner.wallet, path, Map.of("name", name, "symbol", symbol));
        return assetClient._address();
    }


}
