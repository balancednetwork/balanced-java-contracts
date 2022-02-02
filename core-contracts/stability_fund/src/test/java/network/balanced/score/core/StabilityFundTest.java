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

    private static final String nameStabilityfund = "StabilityFund";

    public static class IRC2BasicToken extends IRC2Basic {
        public IRC2BasicToken(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
            super(_name, _symbol, _decimals);
            _mint(Context.getCaller(), _totalSupply);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        stabilityFund = sm.deploy(owner, StabilityFund.class, nameStabilityfund, governance.getAddress(), admin.getAddress());
        sicx = deployIRC2Basic(owner, "Staked icx", "sicx", 18, BigInteger.valueOf(1000));
        bnusd = deployIRC2Basic(owner, "Balanced usd", "bnusd", 18, BigInteger.valueOf(1000));
    }

    @Test
    void name() {
        assertEquals(nameStabilityfund, stabilityFund.call("name"));
    }

    @Test
    void setGetDaofund() {
        assertNull(stabilityFund.call("getDaofund"));
        Account daofund = Account.newScoreAccount(1);

        stabilityFund.invoke(admin, "setDaofund", daofund.getAddress());

        assertEquals(daofund.getAddress(), stabilityFund.call("getDaofund"));
    }

    @Test
    void setGetSicx() {
        assertNull(stabilityFund.call("getSicx"));
        Account sicx = Account.newScoreAccount(1);

        stabilityFund.invoke(admin, "setSicx", sicx.getAddress());

        assertEquals(sicx.getAddress(), stabilityFund.call("getSicx"));
    }

    @Test
    void setGetRebalancing() {
        assertNull(stabilityFund.call("getRebalancing"));
        Account rebalancing = Account.newScoreAccount(1);

        stabilityFund.invoke(admin, "setRebalancing", rebalancing.getAddress());

        assertEquals(rebalancing.getAddress(), stabilityFund.call("getRebalancing"));
    }

    @Test
    void setGetDex() {
        assertNull(stabilityFund.call("getDex"));
        Account dex = Account.newScoreAccount(1);

        stabilityFund.invoke(admin, "setDex", dex.getAddress());

        assertEquals(dex.getAddress(), stabilityFund.call("getDex"));
    }

    @Test
    void setGetBnusd() {
        assertNull(stabilityFund.call("getbnUSD"));
        Account bnusd = Account.newScoreAccount(4);

        stabilityFund.invoke(admin, "setbnUSD", bnusd.getAddress());

        assertEquals(bnusd.getAddress(), stabilityFund.call("getbnUSD"));
    }

    @Test
    void getStabilityFundBalance() {
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

    private Score deployIRC2Basic(Account owner, String name, String symbol, int decimals, BigInteger initialSupply) throws Exception {
        return sm.deploy(owner, IRC2BasicToken.class, name, symbol, decimals, initialSupply);
   }
}