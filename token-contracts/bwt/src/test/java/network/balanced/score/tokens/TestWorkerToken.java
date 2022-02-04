package network.balanced.score.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWorkerToken extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account admin = sm.createAccount();
    private static Address governance = owner.getAddress();
    private static Score tokenScore;
    private static Account governanceScore = Account.newScoreAccount(1);


    @BeforeAll
    static void setup() throws Exception {
        ArrayList<Integer> list = new ArrayList<>();
        governance = owner.getAddress();
        tokenScore = sm.deploy(owner, WorkerToken.class, governanceScore.getAddress());
        tokenScore.invoke(governanceScore,"setAdmin", admin.getAddress());
    }

    @Test
    void testGovernance(){
        //test for if condition for isContract is satisfied
        //tokenScore.invoke(owner, "setGovernance", admin.getAddress());

        //test for if for only owner is satisfied
        //tokenScore.invoke(governanceScore, "setGovernance", admin.getAddress());

        assertEquals(tokenScore.call("getGovernance"), governanceScore.getAddress());
    }

    @Test
    void testAdmin(){
        assertEquals(tokenScore.call("getAdmin"), admin.getAddress());
    }

    @Test
    void transferTest(){
        Account testAccount = sm.createAccount();
        var ownerBalance = BigInteger.valueOf(100).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        var testBalance = BigInteger.valueOf(0);
        testAccount.addBalance("BALW", testBalance);

        var transferAmount = BigInteger.valueOf(50).multiply(WorkerToken.pow(BigInteger.TEN, 6));
        String info = "Hello there";
        tokenScore.invoke(admin, "adminTransfer", owner.getAddress(), testAccount.getAddress(), transferAmount, info.getBytes());

        assertEquals(ownerBalance.subtract(transferAmount), tokenScore.call("balanceOf", owner.getAddress()));
        assertEquals(testBalance.add(transferAmount), tokenScore.call("balanceOf", testAccount.getAddress()));
    }

}
