package network.balanced.score.core;

import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.Account;
import com.iconloop.score.token.irc2.IRC2Basic;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import score.Context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;


class StabilityFundTest extends TestBase {

    // Servicemanager.
    private static final ServiceManager sm = getServiceManager();

    // Accounts.
    private static final Account owner = sm.createAccount();
    private static final Account governance = sm.createAccount();
    private static final Account admin = sm.createAccount();

    // Scores to be deployed.
    private static Score stabilityFund;
    private static Score sicx;
    private static Score bnusd;

    // Stabilityfund score deployment settings.
    private static final String nameStabilityfund = "StabilityFund";

    // Sicx score deployment settings.
    private static String nameSicx = "Staked icx";
    private static String symbolSicx = "SICX";
    private static int decimalsSicx = 18;
    private static BigInteger initalsupplySicx = BigInteger.valueOf(100);

    // Bnusd score deployment settings.
    private static String nameBnusd = "Balanced usd";
    private static String symbolBnusd = "BNUSD";
    private static int decimalsBnusd = 18;
    private static BigInteger initalsupplyBnusd = BigInteger.valueOf(100);
    

    public static class IRC2BasicToken extends IRC2Basic {
        public IRC2BasicToken(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
            super(_name, _symbol, _decimals);
            _mint(Context.getCaller(), _totalSupply);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        stabilityFund = sm.deploy(owner, StabilityFund.class, nameStabilityfund, governance.getAddress(), admin.getAddress());
        sicx = sm.deploy(owner, IRC2BasicToken.class, nameSicx, symbolSicx, decimalsSicx, initalsupplySicx);
        bnusd = sm.deploy(owner, IRC2BasicToken.class, nameBnusd, symbolBnusd, decimalsBnusd, initalsupplyBnusd);
    }

    @Test
    void name() {
        assertEquals(nameStabilityfund, stabilityFund.call("name"));      
    }

    @Test
    void testSetGetDaofund() {
        // Check that sicx is not set.
        System.out.println(stabilityFund.call("getDaofund"));
        System.out.flush();
        assertNull(stabilityFund.call("getDaofund"));

        // Set sicx and check that it's set.
        Account daofund = sm.createAccount();
        stabilityFund.invoke(admin, "setDaofund", daofund.getAddress());
        assertEquals(daofund.getAddress(), stabilityFund.call("getDaofund"));
    }

    @Test
    void testSetGetSicx() {
        // Check that sicx is not set.
        assertNull(stabilityFund.call("getSicx"));

        // Set sicx and check that it's set.
        Account sicx = sm.createAccount();
        stabilityFund.invoke(admin, "setSicx", sicx.getAddress());
        assertEquals(sicx.getAddress(), stabilityFund.call("getSicx"));
    }

    @Test
    void testSetGetRebalancing() {
        // Check that bnusd is not set.
        assertNull(stabilityFund.call("getRebalancing"));

        // Set bnusd and check that it's set.
        Account rebalancing = sm.createAccount();
        stabilityFund.invoke(admin, "setRebalancing", rebalancing.getAddress());
        assertEquals(rebalancing.getAddress(), stabilityFund.call("getRebalancing"));
    }

    @Test
    void testSetGetDex() {
        // Check that bnusd is not set.
        assertNull(stabilityFund.call("getDex"));

        // Set bnusd and check that it's set.
        Account dex = sm.createAccount();
        stabilityFund.invoke(admin, "setDex", dex.getAddress());
        assertEquals(dex.getAddress(), stabilityFund.call("getDex"));
    }

    @Test
    void testSetGetBnusd() {
        // Check that bnusd is not set.
        assertNull(stabilityFund.call("getbnUSD"));

        // Set bnusd and check that it's set.
        Account bnusd = sm.createAccount();
        stabilityFund.invoke(admin, "setbnUSD", bnusd.getAddress());
        assertEquals(bnusd.getAddress(), stabilityFund.call("getbnUSD"));
    }

    @Test
    void testGetStabilityFundBalance () {

        // Set required addresses in stabilityfund.
        stabilityFund.invoke(admin, "setSicx", sicx.getAddress());
        stabilityFund.invoke(admin, "setbnUSD", bnusd.getAddress());

        // Transfer icx and bnusd to stabilityfund.
        sicx.invoke(owner, "transfer", stabilityFund.getAddress(), BigInteger.valueOf(67), "test".getBytes());
        bnusd.invoke(owner, "transfer", stabilityFund.getAddress(), BigInteger.valueOf(65), "test".getBytes());

        // Test getStabilityFundBalance method.
        String balance = (String) stabilityFund.call("getStabilityFundBalance");
        JsonObject json = Json.parse(balance).asObject();
        assertEquals(67, Integer.parseInt(json.get("sicx").asString()));
        assertEquals(65, Integer.parseInt(json.get("bnusd").asString()));
    }

    // static Score deployTokenScore (String name, String symbol, int decimals, BigInteger initialSupply) throws Exception {
    //     return sm.deploy(owner, IRC2Basic.class, name, symbol, decimals, initialSupply);
    //}
}