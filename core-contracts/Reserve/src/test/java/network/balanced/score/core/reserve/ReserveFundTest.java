package network.balanced.score.core.reserve;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import network.balanced.score.core.reserve.utils.Loans;
import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReserveFundTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    private Score ReserveScore;
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(22);
    Account admin = sm.createAccount();

    public static final Account governanceScore = Account.newScoreAccount(1);
    private final Account bob = sm.createAccount();
    private Score loansScore ;
    private final Account balnScore = Account.newScoreAccount(7);
    private Score sicxScore ;

    public static class SicxToken extends IRC2Mintable {
        public SicxToken(String _name, String _symbol, int _decimals) {
            super(_name, _symbol, _decimals);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        ReserveScore = sm.deploy(owner, ReserveFund.class, governanceScore.getAddress());
        assert (ReserveScore.getAddress().isContract());
        sicxScore = sm.deploy(owner, SicxToken.class, "Sicx Token", "sICX", 18);
        sicxScore.invoke(owner, "mintTo", owner.getAddress(), MINT_AMOUNT);
        loansScore = sm.deploy(owner, Loans.class, ReserveScore.getAddress());
    }

    @Test
    @DisplayName("Deployment with non contract address")
    void testDeploy() {
        Account notContract = sm.createAccount();
        Executable deploymentWithNonContract = () -> sm.deploy(owner, ReserveFund.class, notContract.getAddress());

        String expectedErrorMessage = "ReserveFund: Governance address should be a contract";
        InvocationTargetException e = Assertions.assertThrows(InvocationTargetException.class, deploymentWithNonContract);
        assertEquals(expectedErrorMessage, e.getCause().getMessage());
    }

    @Test
    void name() {
        String contractName = "Balanced Reserve Fund";
        assertEquals(contractName, ReserveScore.call("name"));
    }

    @Test
    void getGovernance() {
        assertEquals(governanceScore.getAddress(), ReserveScore.call("getGovernance"));
    }

    @Test
    void setAndGetAdmin() {
        ReserveScore.invoke(Account.getAccount(governanceScore.getAddress()), "setAdmin", admin.getAddress());
        Address actualAdmin = (Address) ReserveScore.call("getAdmin");
        assertEquals(admin.getAddress(), actualAdmin);
    }

    @Test
    void setAndGetLoans() {
        setAndGetAdmin();
        ReserveScore.invoke(Account.getAccount(admin.getAddress()), "setLoans", loansScore.getAddress());
        Address actualLoan = (Address) ReserveScore.call("getLoans");
        assertEquals(loansScore.getAddress(), actualLoan);
    }

    @Test
    void testSetBaln() {
        setAndGetAdmin();
        ReserveScore.invoke(Account.getAccount(admin.getAddress()), "setBaln", balnScore.getAddress());
        Address actualBaln = (Address) ReserveScore.call("getBaln");
        assertEquals(balnScore.getAddress(), actualBaln);
    }

    @Test
    void setAndGetSicx() {
        setAndGetAdmin();
        ReserveScore.invoke(Account.getAccount(admin.getAddress()), "setSicx", sicxScore.getAddress());
        Address actualSicx = (Address) ReserveScore.call("getSicx");
        assertEquals(sicxScore.getAddress(), actualSicx);
    }

    @Test
    void testRedeem() {
        setAndGetLoans();
        setAndGetSicx();

        BigInteger prevSicxinOwner = (BigInteger) sicxScore.call("balanceOf", owner.getAddress());
        BigInteger prevSicxinLoans = (BigInteger) sicxScore.call("balanceOf", loansScore.getAddress());

        sicxScore.invoke(owner, "transfer", ReserveScore.getAddress(), BigInteger.TEN.pow(21), new byte[0]);
        loansScore.invoke(owner, "redeem", loansScore.getAddress(), BigInteger.TEN.pow(19), BigInteger.TEN.pow(18));

        BigInteger afterSicxinOwner = (BigInteger) sicxScore.call("balanceOf", owner.getAddress());
        BigInteger afterSicxinLoans = (BigInteger) sicxScore.call("balanceOf", loansScore.getAddress());

        assertEquals(prevSicxinLoans.add(BigInteger.TEN.pow(19)), afterSicxinLoans);
        assertEquals(prevSicxinOwner.subtract(BigInteger.TEN.pow(21)),  afterSicxinOwner);
    }

    @Test
    void testDisburseSicx(){
        setAndGetSicx();
        testSetBaln();
        setAndGetLoans();
        ReserveFund.Disbursement[] amt = new ReserveFund.Disbursement[]{new ReserveFund.Disbursement()};
        amt[0].address = sicxScore.getAddress();
        amt[0].amount = BigInteger.TEN.pow(20);
        sicxScore.invoke(owner, "transfer", ReserveScore.getAddress(), BigInteger.TEN.pow(21), new byte[0]);
        ReserveScore.invoke(governanceScore, "disburse", bob.getAddress(), amt);

        BigInteger sicxBefore = (BigInteger) sicxScore.call("balanceOf", bob.getAddress());
        ReserveScore.invoke(bob, "claim");
        BigInteger sicxAfter = (BigInteger) sicxScore.call("balanceOf", bob.getAddress());
        assertEquals(sicxAfter, sicxBefore.add(BigInteger.TEN.pow(20)));
    }
}

