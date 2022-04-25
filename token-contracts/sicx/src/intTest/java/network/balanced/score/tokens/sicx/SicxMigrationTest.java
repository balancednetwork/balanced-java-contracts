package network.balanced.score.tokens.sicx;

import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.SicxScoreClient;
import network.balanced.score.lib.test.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import foundation.icon.jsonrpc.Address;


import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SicxMigrationTest implements ScoreIntegrationTest {

    static Wallet owner = ScoreIntegrationTest.getOrGenerateWallet(System.getProperties());
    private static final Address ownerAddress = Address.of(owner);

    static DefaultScoreClient sicxClient = DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));

    @ScoreClient
    Sicx sicxScore = new SicxScoreClient(sicxClient);

    static Wallet tester = ScoreIntegrationTest.getOrGenerateWallet(null);
    private static final Address testerAddress = Address.of(tester);


    @Test
    @Order(1)
    void Beforemigration() {
        BigInteger value = new BigInteger("40000000000000000000");
        sicxScore.mint(value, null);
        sicxScore.mintTo(testerAddress, new BigInteger("20000000000000000000"), null);
        assertEquals(new BigInteger("60000000000000000000"), sicxScore.totalSupply());
        assertEquals(new BigInteger("20000000000000000000"), sicxScore.balanceOf(testerAddress));
        assertEquals(new BigInteger("40000000000000000000"), sicxScore.balanceOf(ownerAddress));
        // rename the token name on python as old_sICX
        assertEquals("old_sICX", sicxScore.getPeg());
    }

    @Test
    @Order(2)
    void Afteremigration() {
        // update sicx contract with java file
        System.setProperty("scoreFilePath", "../../token-contracts/sicx/build/libs/sicx-0.1.0-optimized.jar");
        System.setProperty("isUpdate", "true");
        System.setProperty("address", String.valueOf(sicxClient._address()));
        DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));
        // test data after migration
        assertEquals("sICX", sicxScore.getPeg());
        assertEquals(new BigInteger("60000000000000000000"), sicxScore.totalSupply());
        assertEquals(new BigInteger("20000000000000000000"), sicxScore.balanceOf(testerAddress));
        assertEquals(new BigInteger("40000000000000000000"), sicxScore.balanceOf(ownerAddress));
        // call mint after migration
        sicxScore.mint(new BigInteger("20000000000000000000"), null);
        // call mintTo after migration
        sicxScore.mintTo(testerAddress, new BigInteger("20000000000000000000"), null);
        // call burn after migration
        sicxScore.burn(new BigInteger("10000000000000000000"));
        //call burnFrom after migration
        sicxScore.burnFrom(testerAddress, new BigInteger("10000000000000000000"));
        assertEquals(new BigInteger("80000000000000000000"), sicxScore.totalSupply());
        assertEquals(new BigInteger("30000000000000000000"), sicxScore.balanceOf(testerAddress));
        assertEquals(new BigInteger("50000000000000000000"), sicxScore.balanceOf(ownerAddress));
    }
}
