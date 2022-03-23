package network.balanced.score.core.dividends;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static network.balanced.score.core.dividends.Helpers.pow10;

public class DividendsTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    private final Account bob = sm.createAccount();
    Account admin = sm.createAccount();
    public static final Account governanceScore = Account.newScoreAccount(1);
    public static final Account loansScore = Account.newScoreAccount(2);
    public static final Account daoScore = Account.newScoreAccount(3);
    public static final Account balnScore = Account.newScoreAccount(4);
    public static final Account dexScore = Account.newScoreAccount(5);

    private final MockedStatic<Context> utilities = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    private Score dividendScore;


    @BeforeEach
    public void setup() throws Exception {
        dividendScore = sm.deploy(owner, Dividends.class, governanceScore.getAddress());
        assert (dividendScore.getAddress().isContract());
    }

    @Test
    public void name() {
        String contractName = "Balanced Dividends";
        assertEquals(contractName, dividendScore.call("name"));
    }

    @Test
    public void setDistributionActivationStatus() {
        dividendScore.invoke(governanceScore, "setDistributionActivationStatus", true);
        assertEquals(true, dividendScore.call("getDistributionActivationStatus"));
    }

    @Test
    public void setGovernance() {
        dividendScore.invoke(owner, "setGovernance", governanceScore.getAddress());
        assertEquals(governanceScore.getAddress(), dividendScore.call("getGovernance"));
    }

    @Test
    public void setAdmin() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        assertEquals(admin.getAddress(), dividendScore.call("getAdmin"));
    }

    @Test
    public void setLoan() {
        setAdmin();
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        assertEquals(loansScore.getAddress(), dividendScore.call("getLoans"));
    }

    @Test
    public void setDao() {
        setAdmin();
        dividendScore.invoke(admin, "setDaofund", daoScore.getAddress());
        assertEquals(daoScore.getAddress(), dividendScore.call("getDaofund"));
    }

    @Test
    public void setBaln() {
        setAdmin();
        dividendScore.invoke(admin, "setBaln", balnScore.getAddress());
        assertEquals(balnScore.getAddress(), dividendScore.call("getBaln"));
    }

    @Test
    public void setDex() {
        setAdmin();
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());
        assertEquals(dexScore.getAddress(), dividendScore.call("getDex"));
    }

    @Test
    public void getBalance() {
        setLoan();
        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        try {
            dividendScore.call("getBalances");
            utilities.when(() -> Context.call(loansScore.getAddress(), "getAssetTokens")).thenReturn(asset);
            utilities.when(() -> Context.call(balnScore.getAddress(), "balanceOf", Context.getAddress())).thenReturn(100);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void getAcceptedTokens() {
        List<Address> lis = new ArrayList<>();
        lis.add(Address.fromString("cx0000000000000000000000000000000000000000"));
        lis.add(balnScore.getAddress());
        dividendScore.invoke(governanceScore, "addAcceptedTokens", balnScore.getAddress());
        assertEquals(lis, dividendScore.call("getAcceptedTokens"));

    }

    @Test
    public void getDividendsCategories() {
        setAdmin();
        List<String> lis = new ArrayList<>();
        lis.add("daofund");
        lis.add("baln_holders");
        lis.add("loans");
        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        assertEquals(lis, dividendScore.call("getDividendsCategories"));
    }

    @Test
    public void removeDividendsCategory() {
        setAdmin();
        List<String> lis = new ArrayList<>();
        lis.add("daofund");
        lis.add("baln_holders");
        lis.add("loans");
        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        Dividends.DistPercentDict[] dist = new Dividends.DistPercentDict[]{new Dividends.DistPercentDict(), new Dividends.DistPercentDict(), new Dividends.DistPercentDict()};
        dist[0].category = "daofund";
        dist[1].category = "baln_holders";
        dist[2].category = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow10(17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow10(17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow10(17));

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        dividendScore.invoke(admin, "updateAddCategoriesToArrayDB");

        assertEquals(lis, dividendScore.call("getDividendsCategories"));

        dist[0].category = "daofund";
        dist[1].category = "baln_holders";
        dist[2].category = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow10(17));
        dist[1].dist_percent = BigInteger.valueOf(6).multiply(pow10(17));
        dist[2].dist_percent = BigInteger.ZERO;
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        dividendScore.invoke(admin, "removeDividendsCategory", "loans");
        lis.remove(lis.size() - 1);
        assertEquals(lis, dividendScore.call("getDividendsCategories"));
    }

    @Test
    public void getDividendsBatchSize() {
        setAdmin();
       dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(50));
       assertEquals(BigInteger.valueOf(50), dividendScore.call("getDividendsBatchSize"));
    }

    @Test
    public void getSnapshotId(){
        assertEquals(BigInteger.ONE, dividendScore.call("getSnapshotId"));
    }

    @Test
    public void getDay(){
        System.out.println(" = " + dividendScore.call("getDay"));
        utilities.when(() -> Context.call(loansScore.getAddress(), "setTimeOffset")).thenReturn(BigInteger.TEN);

    }

    @Test
    public void distribute(){
        dividendScore.invoke(owner, "distribute");
    }

    @Test
    public void transferDaofundDividends(){
        setDistributionActivationStatus();
        dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.ZERO, BigInteger.valueOf(2));
    }

}
