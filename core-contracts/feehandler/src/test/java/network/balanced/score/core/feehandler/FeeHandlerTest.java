package network.balanced.score.core.feehandler;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.feehandler.utils.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.feehandler.utils.governance.baln;
import static org.junit.jupiter.api.Assertions.*;

class FeeHandlerTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    private Score feehandler;
    private Score sicxScore;
    private Score bnusd;
    private Score governance;
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(22);

    @BeforeEach
    void setUp() throws Exception {
        governance = sm.deploy(owner, governance.class);
        feehandler = sm.deploy(owner, FeeHandler.class, governance.getAddress());
        assert (feehandler.getAddress().isContract());
        sicxScore = sm.deploy(owner, sicx.class, "Sicx Token", "sICX", 18);
        sicxScore.invoke(owner, "mintTo", feehandler.getAddress(), MINT_AMOUNT);
        bnusd = sm.deploy(owner, bnUSD.class, "bnUSD Token", "bnUSD", 18);
    }

    @Test
    void name() {
        String contractName = "Balanced FeeHandler";
        assertEquals(contractName, feehandler.call("name"));
    }

    @Test
    void enableAndDisable(){
        feehandler.invoke(governance.getAccount(), "enable");
        feehandler.invoke(governance.getAccount(), "disable");
    }

    @Test
    void setAcceptedDividendTokens_NoPreviousTokens(){
        Address[] add = new Address[]{sicxScore.getAddress(),baln.getAddress()};
        feehandler.invoke(governance.getAccount(),"setAcceptedDividendTokens", (Object) add);
        List<Address> expc = new ArrayList<>();
        expc.add(sicxScore.getAddress());
        expc.add(baln.getAddress());
        assertEquals(expc, feehandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setAcceptedDividendTokens_PreviousTokens() {
        Address[] token = new Address[]{bnusd.getAddress()};
        feehandler.invoke(governance.getAccount(),"setAcceptedDividendTokens", (Object) token);

        Address[] add = new Address[]{sicxScore.getAddress(),baln.getAddress()};
        feehandler.invoke(governance.getAccount(),"setAcceptedDividendTokens", (Object) add);
        List<Address> expc = new ArrayList<>();
        expc.add(sicxScore.getAddress());
        expc.add(baln.getAddress());
        assertEquals(expc, feehandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setGetRoute() {
        feehandler.invoke(governance.getAccount(), "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[] {bnusd.getAddress()});
        Map<String, Object> res = (Map<String, Object>) feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress());
        assertEquals(baln.getAddress(), res.get("toToken"));
    }

    @Test
    void getEmptyRoute(){
        feehandler.invoke(governance.getAccount(), "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[] {bnusd.getAddress()});
        assertEquals(Map.of(), feehandler.call("getRoute", bnusd.getAddress(), baln.getAddress()));
    }

    @Test
    void deleteRoute(){
        feehandler.invoke(governance.getAccount(), "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[] {bnusd.getAddress()});
        feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress());
        feehandler.invoke(governance.getAccount(), "deleteRoute",sicxScore.getAddress(), baln.getAddress());
    }

    @Test
    void setFeeProcessingInterval(){
        feehandler.invoke(governance.getAccount(), "setFeeProcessingInterval", BigInteger.TEN);
        assertEquals(BigInteger.TEN, feehandler.call("getFeeProcessingInterval"));
    }

    @Test
    void tokenFallback(){
        setAcceptedDividendTokens_NoPreviousTokens();
        setFeeProcessingInterval();
        feehandler.invoke(governance.getAccount(), "enable");
        feehandler.invoke(sicxScore.getAccount(), "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);
    }

    @Test
    void add_get_allowed_address(){
        feehandler.invoke(owner, "add_allowed_address", sicxScore.getAddress());
        List<Address> expected = new ArrayList<>();
        expected.add(sicxScore.getAddress());
        assertEquals(expected, feehandler.call("get_allowed_address", 0));
    }

    @Test
    void get_allowed_address_NegativeOffset(){
        feehandler.invoke(owner, "add_allowed_address", sicxScore.getAddress());
        try{
            feehandler.call("get_allowed_address", -1);
        }catch (AssertionError e){
            assertEquals("Reverted(0): Negative value not allowed.", e.getMessage());
        }
    }

    @Test
    void route_contract_balances_with_path(){
        add_get_allowed_address();
        setGetRoute();
        feehandler.invoke(owner, "route_contract_balances");
    }

    @Test
    void route_contract_balances_with_empty_path(){
        bnusd.invoke(owner, "mintTo", feehandler.getAddress(), MINT_AMOUNT);
        feehandler.invoke(owner, "add_allowed_address", bnusd.getAddress());
        setGetRoute();
        feehandler.invoke(owner, "route_contract_balances");
    }

    @Test
    void route_contract_balances_with_no_fee(){
        feehandler.invoke(owner, "add_allowed_address", bnusd.getAddress());
        setGetRoute();
        try {
            feehandler.invoke(owner, "route_contract_balances");
        } catch (AssertionError e){
            assertEquals("Reverted(0): No fees on the contract.", e.getMessage());
        }
    }
}