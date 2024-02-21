package network.balanced.score.core.asset.manager;

import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigInteger;

public class AssetManagerIntTest {

    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;
    private static BalancedClient user;


    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        user = balanced.newClient();
    }
    
}
