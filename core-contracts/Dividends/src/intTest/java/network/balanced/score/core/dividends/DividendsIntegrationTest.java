package network.balanced.score.core.dividends;


import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.DividendsScoreClient;
//import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.test.ScoreIntegrationTest;

import network.balanced.score.lib.interfaces.Dividends;
import network.balanced.score.lib.interfaces.Governance;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DividendsIntegrationTest implements ScoreIntegrationTest {

//    private static final Wallet tester = ScoreIntegrationTest.getOrGenerateWallet(null);
//    private static final Address testerAddress = Address.of(tester);

    private static final Wallet owner = ScoreIntegrationTest.getOrGenerateWallet(System.getProperties());
    private static final Address ownerAddress = Address.of(owner);


    private static final DefaultScoreClient governanceClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/governance.zip", null);
    private static final DefaultScoreClient loansClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/loans.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient dexClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/dex.zip", Map.of("_governance", governanceClient._address()));
//    private static final DefaultScoreClient stakingClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/staking.zip", null);
    private static final DefaultScoreClient rewardsClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/rewards.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient reserveClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/reserve.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient daofundClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/daofund.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient oracleClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/oracle.zip", null);
//    private static final DefaultScoreClient sicxClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/sicx.zip", Map.of("_admin", stakingClient._address()));
    private static final DefaultScoreClient bnusdClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/bnusd.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient balnClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/baln.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient bwtClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/bwt.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient routerClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/router.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient feehandlerClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/feehandler.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient stakedLpClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/rebalancing.zip", Map.of("_governance", governanceClient._address()));
    private static final DefaultScoreClient rebalancingClient = DefaultScoreClient._deploy("http://localhost:9082/api/v3", new BigInteger("3"), owner,"../../zip/stakedLp.zip", Map.of("_governance", governanceClient._address()));


    private static final DefaultScoreClient dividendsClient = DefaultScoreClient.of(System.getProperties(), Map.of("_governance"
            , governanceClient._address()));


    @ScoreClient
    private static final Governance governanceScore = new GovernanceScoreClient(governanceClient);

    @ScoreClient
    private static final Dividends dividendsScore = new DividendsScoreClient(dividendsClient);


    @Test
    @Order(1)
    void name() {
        BalancedAddresses addr = new BalancedAddresses();

        addr.loans = loansClient._address();
        addr.dex = dexClient._address();
        addr.staking = governanceClient._address();
        addr.rewards = rewardsClient._address();
        addr.reserve = reserveClient._address();
        addr.dividends = dividendsClient._address();
        addr.daofund = daofundClient._address();
        addr.oracle = oracleClient._address();
        addr.sicx = governanceClient._address();
        addr.bnUSD = bnusdClient._address();
        addr.baln = balnClient._address();
        addr.bwt = bwtClient._address();
        addr.router = routerClient._address();
        addr.rebalancing = rebalancingClient._address();
        addr.feehandler = feehandlerClient._address();
        addr.stakedLp = stakedLpClient._address();
        BalancedAddresses[] addresses = new BalancedAddresses[]{addr};

        governanceScore.setAddresses(addr);
//        governanceScore.configureBalanced();
//        governanceScore.launchBalanced();
        assertEquals("Balanced Dividends", dividendsScore.name());
    }

    @Test
    @Order(1)
    void settersAndGetters() {
        dividendsScore.setBaln(balnClient._address());
        assertEquals(balnClient._address(), dividendsScore.getBaln());

    }

}

