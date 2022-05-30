package network.balanced.score.core.dividends;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.dividends.utils.bnUSD;
import network.balanced.score.lib.interfaces.addresses.BnusdAddress;
import network.balanced.score.lib.structs.DistributionPercentage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DividendsImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Object MINT_AMOUNT = BigInteger.TEN.pow(22);
    private final BigInteger initalFees = BigInteger.TEN.pow(20);
    private static final long DAY = 43200L;

    private static Score bnUSDScore;
    private static final Account owner = sm.createAccount();
    private final Account admin = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(1);
    private static final Account loansScore = Account.newScoreAccount(2);
    private static final Account daoScore = Account.newScoreAccount(3);
    private static final Account balnScore = Account.newScoreAccount(7);
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
        bnUSDScore.invoke(owner, "transfer", dividendScore.getAddress(), initalFees, new byte[0]);

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
    void setAndGetGovernance() {
        testGovernance(dividendScore, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(dividendScore, governanceScore, admin);
    }

    @Test
    void setAndGetLoans() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setLoans",
                loansScore.getAddress(), "getLoans");
    }

    @Test
    void setAndGetDao() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setDaofund",
                daoScore.getAddress(), "getDaofund");
    }

    @Test
    void setAndGetBaln() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setBaln",
                balnScore.getAddress(), "getBaln");
    }

    @Test
    void setAndGetDex() {
        testContractSettersAndGetters(dividendScore, governanceScore, admin, "setDex",
                dexScore.getAddress(), "getDex");
    }

    private void transferDaofundDiv(int start, int end) {
        dividendScore.invoke(owner, "transferDaofundDividends", start, end);

    }

    @Test
    void checkStartEndDay() {
        contractSetup();
        sm.getBlock().increase(4 * DAY);
        dividendScore.invoke(owner, "distribute");

        Executable contractCallCase1 = () -> transferDaofundDiv(-1, 2);
        String expectedErrorMessageCase1 = "Reverted(0): Invalid value of start provided.";
        expectErrorMessage(contractCallCase1, expectedErrorMessageCase1);

        Executable contractCallCase2 = () -> transferDaofundDiv(4, 5);
        String expectedErrorMessageCase2 = "Reverted(0): Invalid value of start provided.";
        expectErrorMessage(contractCallCase2, expectedErrorMessageCase2);

        Executable contractCallCase3 = () -> transferDaofundDiv(3, 2);
        String expectedErrorMessageCase3 = "Reverted(0): Start must not be greater than or equal to end.";
        expectErrorMessage(contractCallCase3, expectedErrorMessageCase3);

        Executable contractCallCase4 = () -> transferDaofundDiv(2, 2);
        String expectedErrorMessageCase4 = "Reverted(0): Start must not be greater than or equal to end.";
        expectErrorMessage(contractCallCase4, expectedErrorMessageCase4);

        Executable contractCallCase5 = () -> transferDaofundDiv(1, -2);
        String expectedErrorMessageCase5 = "Reverted(0): Invalid value of end provided.";
        expectErrorMessage(contractCallCase5, expectedErrorMessageCase5);

        Executable contractCallCase6 = () -> transferDaofundDiv(1, 4);
        String expectedErrorMessageCase6 = "Reverted(0): Maximum allowed range is 2";
        expectErrorMessage(contractCallCase6, expectedErrorMessageCase6);

        sm.getBlock().increase(-4 * DAY);
    }

    @Test
    void getBalance() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());

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
        expectedResult.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.TWO.multiply(pow(BigInteger.TEN, 19)));

        assertEquals(expectedResult, dividendScore.call("getDailyFees", BigInteger.valueOf(2)));
    }

    @Test
    void getDividendsCategories() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void removeDividendsCategory() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

//        add category
        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistributionPercentage[] dist = new DistributionPercentage[]{new DistributionPercentage(), new DistributionPercentage(), new DistributionPercentage()};
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));

//        remove category
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(6).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.ZERO;

        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        dividendScore.invoke(admin, "removeDividendsCategory", "loans");

        expected_list.remove(expected_list.size() - 1);
        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));
    }

    @Test
    void setGetDividendsBatchSize() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDividendsBatchSize", BigInteger.valueOf(2));

        assertEquals(BigInteger.valueOf(2), dividendScore.call("getDividendsBatchSize"));
    }

    @Test
    void getSnapshotId() {
        assertEquals(BigInteger.ZERO, dividendScore.call("getSnapshotId"));
    }

    @Test
    void setGetDividendsOnlyToStakedBalnDay() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDividendsOnlyToStakedBalnDay", BigInteger.TWO);
        assertEquals(BigInteger.TWO, dividendScore.call("getDividendsOnlyToStakedBalnDay"));
    }

    @Test
    void getTimeOffset() {
        contractSetup();
        dividendScore.invoke(owner, "distribute");

        assertEquals(BigInteger.valueOf(2 * DAY), dividendScore.call("getTimeOffset"));
    }

    @Test
    void getDividendsPercentage() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        contextMock.when(() -> Context.call(eq(loansScore.getAddress()), eq("getDay"))).thenReturn(BigInteger.valueOf(1));

        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("getDividendsPercentage"));
    }

    @Test
    void getDay() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());

        BigInteger currentDay = (BigInteger) dividendScore.call("getDay");
        sm.getBlock().increase(4 * DAY);

        assertEquals(currentDay.add(BigInteger.valueOf(4)), dividendScore.call("getDay"));
        sm.getBlock().increase(-4 * DAY);
    }

    @Test
    void tokenFallback() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        dividendScore.invoke(governanceScore, "addAcceptedTokens", bnUSDScore.getAddress());

        getDay();

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        asset.put("bnUSD", String.valueOf(bnUSDScore.getAddress()));

        contextMock.when(getAssetTokens).thenReturn(asset);
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("getTimeOffset"))).thenReturn(BigInteger.valueOf(2 * DAY));
        sm.getBlock().increase(2 * DAY);
        dividendScore.invoke(owner, "distribute");

//        from accepted token
        dividendScore.invoke(bnUSDScore.getAccount(), "tokenFallback", bnUSDScore.getAddress(), BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 18)), new byte[0]);

//        from non accepted token
        dividendScore.invoke(balnScore, "tokenFallback", balnScore.getAddress(), BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)), new byte[0]);
        sm.getBlock().increase(-2 * DAY);
    }

    @Test
    void transferDaofundDividends() {
        contractSetup();

        sm.getBlock().increase(3*DAY);        
        addBnusdFees(BigInteger.TEN.pow(20));
        dividendScore.invoke(owner, "distribute");

        contextMock.when(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(daoScore.getAddress()), any(BigInteger.class))).thenReturn("Token Transferred");
        dividendScore.invoke(owner, "transferDaofundDividends", 0, 0);
        contextMock.verify(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(daoScore.getAddress()), any(BigInteger.class)));

        sm.getBlock().increase(-3*DAY);
    }

    @Test
    void claim() {
        contractSetup();
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn("Token Transferred");

        sm.getBlock().increase(3*DAY);        
        addBnusdFees(BigInteger.TEN.pow(20));
        dividendScore.invoke(owner, "distribute");

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

        
        contextMock.when(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()), any(BigInteger.class))).thenReturn("Token Transferred");
        dividendScore.invoke(owner, "claim", 0, 0);
        contextMock.verify(() -> Context.call(eq(bnUSDScore.getAddress()), eq("transfer"), eq(owner.getAddress()), any(BigInteger.class)));
        sm.getBlock().increase(-3*DAY);
    }

    @Test
    void getUserDividends() {
        contractSetup();
        sm.getBlock().increase(4 * DAY);
        dividendScore.invoke(owner, "distribute");

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

//        non-continuous rewards
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), new BigInteger("9866666666666666666"));

        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), 2, 3));
        sm.getBlock().increase(-4 * DAY);
    }

    @Test
    void getUserDividendsContinuousRewards() {
        setGetDividendsOnlyToStakedBalnDay();
        contractSetup();

        sm.getBlock().increase(4 * DAY);
        dividendScore.invoke(owner, "distribute");
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("stakedBalanceOfAt"), any(Address.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("totalStakedBalanceOfAt"), any(BigInteger.class))).thenReturn(BigInteger.valueOf(200).multiply(pow(BigInteger.TEN, 18)));

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("balanceOfAt"), any(Address.class), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(30).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalSupplyAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(50).multiply(pow(BigInteger.TEN, 18)));
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("totalBalnAt"), any(BigInteger.class), any(BigInteger.class))).thenReturn(BigInteger.valueOf(80).multiply(pow(BigInteger.TEN, 18)));

//        continuous rewards
        Map<String, BigInteger> expected_result = new HashMap<>();
        expected_result.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.valueOf(12).multiply(pow(BigInteger.TEN, 18)));

        assertEquals(expected_result, dividendScore.call("getUserDividends", owner.getAddress(), 2, 3));
        sm.getBlock().increase(-4 * DAY);
    }

    @Test
    void getDaoFundDividends() {
        contractSetup();
        sm.getBlock().increase(4 * DAY);
        dividendScore.invoke(owner, "distribute");

        Map<String, BigInteger> result = new HashMap<>();
        result.put(String.valueOf(bnUSDScore.getAddress()), BigInteger.valueOf(8).multiply(pow(BigInteger.TEN, 18)));

        assertEquals(result, dividendScore.call("getDaoFundDividends", 2, 3));
        sm.getBlock().increase(-4 * DAY);
        dividendScore.invoke(owner, "distribute");
    }

    @Test
    void dividendsAt() {
        contractSetup();

        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("dividendsAt", BigInteger.valueOf(2)));
    }

    @Test
    void dividendsAt_snapshottedDistributions() {
        contractSetup();
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());

        List<String> expected_list = new ArrayList<>();
        expected_list.add("daofund");
        expected_list.add("baln_holders");
        expected_list.add("loans");

        dividendScore.invoke(admin, "addDividendsCategory", "loans");
        DistributionPercentage[] dist = new DistributionPercentage[]{new DistributionPercentage(), new DistributionPercentage(), new DistributionPercentage()};
        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        assertEquals(expected_list, dividendScore.call("getDividendsCategories"));

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(2).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        BigInteger secondChangeDay = (BigInteger) dividendScore.call("getDay");

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(8).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(1).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(1).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);
        BigInteger thirdChangeDay = (BigInteger) dividendScore.call("getDay");

        dist[0].recipient_name = "daofund";
        dist[1].recipient_name = "baln_holders";
        dist[2].recipient_name = "loans";
        dist[0].dist_percent = BigInteger.valueOf(3).multiply(pow(BigInteger.TEN, 17));
        dist[1].dist_percent = BigInteger.valueOf(3).multiply(pow(BigInteger.TEN, 17));
        dist[2].dist_percent = BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17));

        sm.getBlock().increase(DAY);
        dividendScore.invoke(admin, "setDividendsCategoryPercentage", (Object) dist);

        Map<String, BigInteger> expectedOutput2 = new HashMap<>();
        expectedOutput2.put("daofund", BigInteger.valueOf(400000000000000000L));
        expectedOutput2.put("baln_holders", BigInteger.valueOf(200000000000000000L));
        expectedOutput2.put("loans", BigInteger.valueOf(400000000000000000L));
        assertEquals(expectedOutput2, dividendScore.call("dividendsAt", secondChangeDay));

        Map<String, BigInteger> expectedOutput3 = new HashMap<>();
        expectedOutput3.put("daofund", BigInteger.valueOf(800000000000000000L));
        expectedOutput3.put("baln_holders", BigInteger.valueOf(100000000000000000L));
        expectedOutput3.put("loans", BigInteger.valueOf(100000000000000000L));
        assertEquals(expectedOutput3, dividendScore.call("dividendsAt", thirdChangeDay));
        sm.getBlock().increase(-4*DAY);
    }

    private void contractSetup() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDaofund", daoScore.getAddress());
        dividendScore.invoke(admin, "setBaln", balnScore.getAddress());
        setDistributionActivationStatus();
        tokenFallback();
        setGetDividendsBatchSize();
    }

    private void addBnusdFees(BigInteger amount) {
        dividendScore.invoke(bnUSDScore.getAccount(), "tokenFallback", bnUSDScore.getAddress(), amount, new byte[0]);
    }

}
