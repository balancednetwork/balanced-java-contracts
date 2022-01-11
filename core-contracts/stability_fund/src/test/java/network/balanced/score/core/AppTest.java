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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import score.Context;

import java.math.BigInteger;


class StabilityFundTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account governance = sm.createAccount();
    private static final Account admin = sm.createAccount();

    private static Score stabilityFund;
    private static Score sicx;
    private static Score bnusd;

    // Stabilityfund score deployment settings.
    private static final String nameStabilityfund = "StabilityFund";

    // Sicx score deployment settings.
    private static final String nameSicx = "Staked icx";
    private static final String symbolSicx = "SICX";
    private static final int decimalsSicx = 18;
    private static final BigInteger initalsupplySicx = BigInteger.valueOf(100);

    // Bnusd score deployment settings.
    private static final String nameBnusd = "Balanced usd";
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
    void setGetDaofund() {
        assertNull(stabilityFund.call("getDaofund"));
        Account daofund = sm.createAccount();

        stabilityFund.invoke(admin, "setDaofund", daofund.getAddress());

        assertEquals(daofund.getAddress(), stabilityFund.call("getDaofund"));
    }

    @Test
    void setGetSicx() {
        assertNull(stabilityFund.call("getSicx"));
        Account sicx = sm.createAccount();

        stabilityFund.invoke(admin, "setSicx", sicx.getAddress());

        assertEquals(sicx.getAddress(), stabilityFund.call("getSicx"));
    }

    @Test
    void setGetRebalancing() {
        assertNull(stabilityFund.call("getRebalancing"));
        Account rebalancing = sm.createAccount();

        stabilityFund.invoke(admin, "setRebalancing", rebalancing.getAddress());

        assertEquals(rebalancing.getAddress(), stabilityFund.call("getRebalancing"));
    }

    @Test
    void setGetDex() {
        assertNull(stabilityFund.call("getDex"));
        Account dex = sm.createAccount();

        stabilityFund.invoke(admin, "setDex", dex.getAddress());

        assertEquals(dex.getAddress(), stabilityFund.call("getDex"));
    }

    @Test
    void setGetBnusd() {
        assertNull(stabilityFund.call("getbnUSD"));
        Account bnusd = sm.createAccount();

        stabilityFund.invoke(admin, "setbnUSD", bnusd.getAddress());

        assertEquals(bnusd.getAddress(), stabilityFund.call("getbnUSD"));
    }

    @Test
    void getStabilityFundBalance () {
        // Set required addresses in stabilityfund contract.
        stabilityFund.invoke(admin, "setSicx", sicx.getAddress());
        stabilityFund.invoke(admin, "setbnUSD", bnusd.getAddress());

        // Set mock balances for stabilityfund contract.
        Account stabilityFundAccount = stabilityFund.getAccount();
        stabilityFundAccount.addBalance("sicx", BigInteger.valueOf(67));
        stabilityFundAccount.addBalance("bnusd", BigInteger.valueOf(65));

        // Transfer sicx and bnusd to stabilityfund.
        sicx.invoke(owner, "transfer", stabilityFund.getAddress(), stabilityFundAccount.getBalance("sicx"), new byte[0]);
        bnusd.invoke(owner, "transfer", stabilityFund.getAddress(), stabilityFundAccount.getBalance("bnusd"), new byte[0]);
        
        // Act.
        String balance = (String) stabilityFund.call("getStabilityFundBalance");
        JsonObject json = Json.parse(balance).asObject();

        // Assert.
        assertEquals(stabilityFundAccount.getBalance("sicx"), new BigInteger(json.get("sicx").asString()));
        assertEquals(stabilityFundAccount.getBalance("bnusd"), new BigInteger(json.get("bnusd").asString()));
    }
    // static Score deployIRC2Basic (Address owner, String name, String symbol, int decimals, BigInteger initialSupply) throws Exception {
    //     return sm.deploy(owner, IRC2Basic.class, name, symbol, decimals, initialSupply);
    //}
}