package network.balanced.score.core.dividends;


import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.BalancedAddresses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DividendsIntegrationTest{
    static Wallet tester;
    static network.balanced.score.test.integration.Balanced balanced;

    @ScoreClient
    static Dividends dividends;

    @BeforeAll
    static void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));
        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new network.balanced.score.test.integration.Balanced();
        balanced.deployBalanced();

        dividends = new DividendsScoreClient(balanced.dividends);
    };

    @Test
    void testName() throws Exception{
//        System.out.println("here");
//        assertEquals("Balanced Dividends", dividends.name());
//        System.out.println("there");
//        balanced.dividends._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));
        assertEquals("Balanced Dividends", dividends.name());
    }

}

