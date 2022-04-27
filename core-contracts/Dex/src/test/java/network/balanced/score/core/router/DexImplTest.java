package network.balanced.score.core.router;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.dex.DexImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.testAdmin;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class DexImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static Account owner = sm.createAccount();
    private static Score dexScore;
    private static Account adminAccount = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(1);

    @BeforeAll
    static void setup() throws Exception {
        dexScore = sm.deploy(owner, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
    }

    @Test
    void testGovernance() {
        // initial constructor test
        assertEquals(
                dexScore.call("getGovernance"),
                governanceScore.getAddress()
        );
        Account governanceTestScore = Account.newScoreAccount(20);
        dexScore.invoke(owner, "setGovernance", governanceTestScore.getAddress());
        assertEquals(
                dexScore.call("getGovernance"),
                governanceTestScore.getAddress()
        );
    }

    @Test
    void testName(){
        assertEquals(
                dexScore.call("name"),
                "BalancedDEX"
        );
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(dexScore, governanceScore, adminAccount);
    }
}
