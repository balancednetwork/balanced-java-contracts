package network.balanced.score.core.router;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class RouterTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static Account owner = sm.createAccount();
    private static Score routerScore;
    private static Account adminAccount = sm.createAccount();
    private static Account governanceScore = Account.newScoreAccount(0);

    @BeforeAll
    static void setup() throws Exception {
        routerScore = sm.deploy(owner, Router.class, governanceScore.getAddress());
    }

    @Test
    void testAdmin() {
        // initial constructor test
        assertEquals(
                routerScore.call("getAdmin"),
                governanceScore.getAddress()
        );
        Account testScore = governanceScore;
        routerScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        assertEquals(
                routerScore.call("getAdmin"),
                adminAccount.getAddress()
        );
    }

    @Test
    void testGovernance() {
        // initial constructor test
        assertEquals(
                routerScore.call("getGovernance"),
                governanceScore.getAddress()
        );
        Account governanceTestScore = Account.newScoreAccount(20);
        routerScore.invoke(owner, "setGovernance", governanceTestScore.getAddress());
        assertEquals(
                routerScore.call("getGovernance"),
                governanceTestScore.getAddress()
        );
    }

    @Test
    void testDex(){
        Account dexAccount = sm.createAccount();
        routerScore.invoke(
                governanceScore,
                "setDex",
                dexAccount.getAddress()
        );
        assertEquals(
                routerScore.call("getDex"),
                dexAccount.getAddress()
        );
    }

    @Test
    void testSicx(){
        Account sicxAccount = Account.newScoreAccount(1);
        Account testScore = governanceScore;
        routerScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        routerScore.invoke(
                adminAccount,
                "setSicx",
                sicxAccount.getAddress()
        );
        assertEquals(
                routerScore.call("getSicx"),
                sicxAccount.getAddress()
        );
    }

}
