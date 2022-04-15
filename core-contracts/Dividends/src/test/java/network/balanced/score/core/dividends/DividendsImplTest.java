package network.balanced.score.core.dividends;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.dividends.utils.bnUSD;
import network.balanced.score.lib.structs.DistPercentDict;
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

import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DividendsImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Object MINT_AMOUNT = BigInteger.TEN.pow(22);
    private static final long DAY = 43200L;

    private static Score bnUSDScore;
    private static final Account owner = sm.createAccount();
    private final Account admin = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(1);
    private static final Account loansScore = Account.newScoreAccount(2);
    private static final Account daoScore = Account.newScoreAccount(3);
    private static final Account balnScore = Account.newScoreAccount(4);
    private static final Account dexScore = Account.newScoreAccount(6);

    private static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    private Score dividendScore;

    private final MockedStatic.Verification getAssetTokens = () -> Context.call(eq(loansScore.getAddress()), eq("getAssetTokens"));
    private final MockedStatic.Verification balanceOf = () -> Context.call(eq(balnScore.getAddress()), eq("balanceOf"), any(Address.class));


    @BeforeEach
    void setup() throws Exception {
        dividendScore = sm.deploy(owner, DividendsImpl.class, governanceScore.getAddress());
        assert (dividendScore.getAddress().isContract());

        bnUSDScore = sm.deploy(owner, bnUSD.class, "bnUSD Token", "bnUSD", 18);
        bnUSDScore.invoke(owner, "mint", MINT_AMOUNT);
        bnUSDScore.invoke(owner, "transfer", dividendScore.getAddress(), BigInteger.TEN.pow(20), new byte[0]);

        DividendsImpl dividendsSpy = (DividendsImpl) spy(dividendScore.getInstance());
        dividendScore.setInstance(dividendsSpy);
    }

    @Test
    void name() {
        String contractName = "Balanced Dividends";
        assertEquals(contractName, dividendScore.call("name"));
    }

    @Test
    void setDistributionActivationStatus() {
        dividendScore.invoke(governanceScore, "setDistributionActivationStatus", true);
        assertEquals(true, dividendScore.call("getDistributionActivationStatus"));
    }

    @Test
    void setGovernance() {
        dividendScore.invoke(owner, "setGovernance", governanceScore.getAddress());
        assertEquals(governanceScore.getAddress(), dividendScore.call("getGovernance"));
    }

    @Test
    void setAdmin() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        assertEquals(admin.getAddress(), dividendScore.call("getAdmin"));
    }

    @Test
    void setLoan() {
        setAdmin();
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        assertEquals(loansScore.getAddress(), dividendScore.call("getLoans"));
    }

    @Test
    void setDao() {
        setAdmin();
        dividendScore.invoke(admin, "setDaofund", daoScore.getAddress());
        assertEquals(daoScore.getAddress(), dividendScore.call("getDaofund"));
    }

    @Test
    void setBaln() {
        setAdmin();
        dividendScore.invoke(admin, "setBaln", balnScore.getAddress());
        assertEquals(balnScore.getAddress(), dividendScore.call("getBaln"));
    }

    @Test
    void setDex() {
        setAdmin();
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());
        assertEquals(dexScore.getAddress(), dividendScore.call("getDex"));
    }

    @Test
    void checkStartEndDay() {
        contractSetup();

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(-1), BigInteger.valueOf(2));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Invalid value of start provided.", e.getMessage());
        }

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(4), BigInteger.valueOf(5));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Invalid value of start provided.", e.getMessage());
        }

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(3), BigInteger.valueOf(2));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Start must not be greater than or equal to end.", e.getMessage());
        }

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(2), BigInteger.valueOf(2));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Start must not be greater than or equal to end.", e.getMessage());
        }

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(1), BigInteger.valueOf(-2));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Invalid value of end provided.", e.getMessage());
        }

        try {
            dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(1), BigInteger.valueOf(4));
        } catch (AssertionError e) {
            assertEquals("Reverted(0): Maximum allowed range is 2", e.getMessage());
        }
    }

    @Test
    void getBalance() {
        setLoan();
        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        contextMock.when(getAssetTokens).thenReturn(asset);
        contextMock.when(balanceOf).thenReturn(BigInteger.TEN.pow(4));

        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put("baln", BigInteger.TEN.pow(4));
        expectedResult.put("ICX", BigInteger.ZERO);

        assertEquals(expectedResult, dividendScore.call("getBalances"));
    }

    @Test
    void getAcceptedTokens() {
        List<Address> expected_list = new ArrayList<>();
        expected_list.add(balnScore.getAddress());

        dividendScore.invoke(governanceScore, "addAcceptedTokens", balnScore.getAddress());

        assertEquals(expected_list, dividendScore.call("getAcceptedTokens"));

    }

    @Test
    void getDailyFees() {
        contractSetup();

        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.TWO.multiply(pow(BigInteger.TEN, 20)));

        assertEquals(expectedResult, dividendScore.call("getDailyFees", BigInteger.valueOf(1)));
    }

    @Test
    void getDividendsCategories() {
        setAdmin();
        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void removeDividendsCategory() {
        setAdmin();
        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

//        add category
        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistPercentDict[] dist = new DistPercentDict[]{new DistPercentDict(), new DistPercentDict(), new DistPercentDict()};
        dist[0].category = "daofund";
        dist[1].category = "baln_holders";
        dist[2].category = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));

//        remove category
        dist[0].category = "daofund";
        dist[1].category = "baln_holders";
        dist[2].category = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(6).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.ZERO;

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        dividendScore.invoke(admin, "removeDividendsCategory", "loans");

        expected_list.remove(expected_list.size() - 1);
        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void set_getDividendsBatchSize() {
        setAdmin();
        dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(2));

        assertEquals(BigInteger.valueOf(2), dividendScore.call("getDividendsBatchSize"));
    }

    @Test
    void getSnapshotId() {
        assertEquals(BigInteger.ONE, dividendScore.call("getSnapshotId"));
    }

    @Test
    void getDividendsOnlyToStakedBalnDay() {
        setAdmin();
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", BigInteger.ONE);
        assertEquals(BigInteger.ONE, dividendScore.call("getDividendsOnlyToStakedBalnDay"));
    }

    @Test
    void getTimeOffset() {
        contractSetup();
        dividendScore.invoke(owner, "distribute");

        assertEquals(BigInteger.valueOf(2 * DAY), dividendScore.call("getTimeOffset"));
    }

    @Test
    void getDividendsPercentage() {
        setLoan();
        contextMock.when(() -> Context.call(eq(loansScore.getAddress()), eq("getDay"))).thenReturn(BigInteger.valueOf(1));

        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("getDividendsPercentage"));
    }

    @Test
    void getDay() {
        setDex();
        BigInteger currentDay = (BigInteger) dividendScore.call("getDay");
        sm.getBlock().increase(4 * DAY);

        assertEquals(currentDay.add(BigInteger.valueOf(4)), dividendScore.call("getDay"));
    }

    @Test
    void tokenFallback() {
        setLoan();
        dividendScore.invoke(governanceScore, "addAcceptedTokens", bnUSDScore.getAddress());

        getDay();

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        asset.put("bnUSD", String.valueOf(bnUSDScore.getAddress()));

        contextMock.when(getAssetTokens).thenReturn(asset);
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("getTimeOffset"))).thenReturn(BigInteger.valueOf(2 * DAY));

//        from accepted token
        dividendScore.invoke(bnUSDScore.getAccount(), "tokenFallback", bnUSDScore.getAddress(), BigInteger.TWO.multiply(pow(BigInteger.TEN, 20)), new byte[0]);

//        from non accepted token
        dividendScore.invoke(balnScore, "tokenFallback", balnScore.getAddress(), BigInteger.TWO.multiply(pow(BigInteger.TEN, 30)), new byte[0]);
    }

    @Test
    void transferDaofundDividends() {
        contractSetup();

        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn("Token Transferred");

        dividendScore.invoke(owner, "transferDaofundDividends", BigInteger.valueOf(0), BigInteger.valueOf(2));
    }

    @Test
    void claim() {
        contractSetup();
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn("Token Transferred");

        dividendScore.invoke(owner, "claim", BigInteger.valueOf(1), BigInteger.valueOf(2));
    }

    @Test
    void getUserDividends() {
        contractSetup();
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(300));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80));

//        non-continuous rewards
        dividendScore.call("getUserDividends", owner.getAddress(), BigInteger.ONE, BigInteger.valueOf(2));

//        continuous rewards
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.valueOf(8).multiply(pow(BigInteger.TEN, 19)));

        getDividendsOnlyToStakedBalnDay();
        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), BigInteger.ONE, BigInteger.valueOf(2)));
    }

    @Test
    void getDaoFundDividends() {
        contractSetup();

        Map<String, BigInteger> result = new HashMap<>();
        result.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.valueOf(8).multiply(pow(BigInteger.TEN, 19)));

        assertEquals(result, dividendScore.call("getDaoFundDividends", BigInteger.valueOf(1), BigInteger.valueOf(2)));
    }

    @Test
    void dividendsAt() {
        contractSetup();

        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("dividendsAt", BigInteger.valueOf(1)));
    }

    private void contractSetup() {
        setDao();
        setBaln();
        setDistributionActivationStatus();
        tokenFallback();
        set_getDividendsBatchSize();
    }
}
