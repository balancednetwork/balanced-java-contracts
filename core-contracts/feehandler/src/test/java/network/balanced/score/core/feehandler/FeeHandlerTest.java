package network.balanced.score.core.feehandler;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeeHandlerTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    private Score feehandler;
    public static final Account baln = Account.newScoreAccount(2);
    public static final Account bnusd = Account.newScoreAccount(3);
    public static final Account router = Account.newScoreAccount(22);
    public static final Account dividends = Account.newScoreAccount(23);
    public static final Account dex = Account.newScoreAccount(24);
    private Score sicxScore;
    private Score governance;
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(22);


    public static class SicxToken extends IRC2Mintable {
        public SicxToken(String _name, String _symbol, int _decimals) {
            super(_name, _symbol, _decimals);
        }
        public void transfer(Address _to, BigInteger _amount, byte[] data){
        }
    }

    public static class Governance{
        public Governance(){

        }
        public Address getContractAddress(String contract){
            switch (contract) {
                case "baln":
                    return baln.getAddress();
                case "router":
                    return router.getAddress();
                case "dividends":
                    return dividends.getAddress();
                case "dex":
                    return dex.getAddress();
                default:
                    return Address.fromString("");
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        governance = sm.deploy(owner, Governance.class);
        feehandler = sm.deploy(owner, FeeHandler.class, governance.getAddress());
        assert (feehandler.getAddress().isContract());
        sicxScore = sm.deploy(owner, SicxToken.class, "Sicx Token", "sICX", 18);
        sicxScore.invoke(owner, "mintTo", feehandler.getAddress(), MINT_AMOUNT);
    }

    @Test
    void name() {
        String contractName = "Balanced FeeHandler";
        assertEquals(contractName, feehandler.call("name"));
    }

    @Test
    void setAcceptedDividendTokens() {
        Address[] add = new Address[]{sicxScore.getAddress(),baln.getAddress()};
        feehandler.invoke(governance.getAccount(),"setAcceptedDividendTokens", (Object) add);
        List<Address> expc = new ArrayList<>();
        expc.add(sicxScore.getAddress());
        expc.add(baln.getAddress());
        assertEquals(expc, feehandler.call("getAcceptedDividendTokens"));
    }

    @Test
    void setRoute() {
        feehandler.invoke(governance.getAccount(), "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[] {bnusd.getAddress()});
        feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress());
    }

    @Test
    void deleteRoute(){
        feehandler.invoke(governance.getAccount(), "setRoute", sicxScore.getAddress(), baln.getAddress(), new Address[] {bnusd.getAddress()});
        feehandler.call("getRoute", sicxScore.getAddress(), baln.getAddress());

        feehandler.invoke(governance.getAccount(), "deleteRoute",sicxScore.getAddress(), baln.getAddress());

    }

    @Test
    void setFeeProcessingInterval(){
        feehandler.invoke(governance.getAccount(), "setFeeProcessingInterval", BigInteger.TEN.pow(1));
        assertEquals(BigInteger.TEN.pow(1), feehandler.call("getFeeProcessingInterval"));
    }

    @Test
    void tokenFallback(){
        setAcceptedDividendTokens();
        setFeeProcessingInterval();
        feehandler.invoke(governance.getAccount(), "enable");
        feehandler.invoke(owner, "tokenFallback", baln.getAddress(), BigInteger.TEN.pow(2), new byte[0]);
    }

    @Test
    void add_allowed_address(){
        feehandler.invoke(owner, "add_allowed_address", sicxScore.getAddress());
        List<Address> expected = new ArrayList<>();
        expected.add(sicxScore.getAddress());
        assertEquals(expected, feehandler.call("get_allowed_address", 0));
    }

    @Test
    void route_contract_balances(){
        add_allowed_address();
        setRoute();
        feehandler.invoke(owner, "route_contract_balances");
    }
}