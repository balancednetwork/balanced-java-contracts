package network.balanced.score.integration;

import static network.balanced.score.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.integration.ScoreIntegrationTest.deploy;
import static network.balanced.score.integration.ScoreIntegrationTest.systemScore;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.interfaces.BalancedAddresses;
import network.balanced.score.interfaces.*;

public class Balanced {
    KeyWallet owner;

    public DefaultScoreClient governance;
    public DefaultScoreClient baln;
    public DefaultScoreClient bwt;
    public DefaultScoreClient dex;
    public DefaultScoreClient feehandler;
    public DefaultScoreClient loans;
    public DefaultScoreClient rebalancing;
    public DefaultScoreClient rewards;
    public DefaultScoreClient sicx;
    public DefaultScoreClient bnusd;
    public DefaultScoreClient daofund;
    public DefaultScoreClient dividends;
    public DefaultScoreClient oracle;
    public DefaultScoreClient reserve;
    public DefaultScoreClient router;
    public DefaultScoreClient staking;

    @ScoreClient
    Governance governanceScore;

    @ScoreClient
    StakingInterface stakingScore;

    public Balanced() {
       
    }

    public void deployBalanced() throws Exception {
        owner = createWalletWithBalance(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)));
        try {
            systemScore.registerPRep(BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), "test", "kokoa@example.com", "USA", "New York", "https://icon.kokoa.com", "https://icon.kokoa.com/json/details.json", "localhost:9082");
        } catch (Exception e) {
            //Already registerd
        }

        governance = deploy(owner, "governance", null);
        baln = deploy(owner, "baln", Map.of("_governance", governance._address()));
        bwt = deploy(owner, "bwt", Map.of("_governance", governance._address()));
        dex = deploy(owner, "dex", Map.of("_governance", governance._address()));
        feehandler = deploy(owner, "feehandler", Map.of("_governance", governance._address()));
        loans = deploy(owner, "loans", Map.of("_governance", governance._address()));
        rebalancing = deploy(owner, "rebalancing", Map.of("_governance", governance._address()));
        rewards = deploy(owner, "rewards", Map.of("_governance", governance._address()));
        sicx = deploy(owner, "sicx", Map.of("_admin", governance._address()));
        bnusd = deploy(owner, "bnusd", Map.of("_governance", governance._address()));
        daofund = deploy(owner, "daofund", Map.of("_governance", governance._address()));
        dividends = deploy(owner, "dividends", Map.of("_governance", governance._address()));
        oracle = deploy(owner, "oracle",null);
        reserve = deploy(owner, "reserve", Map.of("_governance", governance._address()));
        router = deploy(owner, "router", Map.of("_governance", governance._address()));
        staking = deploy(owner, "staking", null);

        governanceScore = new GovernanceScoreClient(governance);

        BalancedAddresses balancedAddresses = new BalancedAddresses();
        balancedAddresses.loans = loans._address();
        balancedAddresses.dex = dex._address();
        balancedAddresses.staking = staking._address();
        balancedAddresses.rewards = rewards._address();
        balancedAddresses.reserve = reserve._address();
        balancedAddresses.dividends = dividends._address();
        balancedAddresses.daofund = daofund._address();
        balancedAddresses.oracle = oracle._address();
        balancedAddresses.sicx = sicx._address();
        balancedAddresses.bnUSD = bnusd._address();
        balancedAddresses.baln = baln._address();
        balancedAddresses.bwt = bwt._address();
        balancedAddresses.router = router._address();
        balancedAddresses.rebalancing = rebalancing._address();
        balancedAddresses.feehandler = feehandler._address();

        governanceScore.setAddresses(balancedAddresses);

        stakingScore = new StakingInterfaceScoreClient(staking);

        stakingScore.setSicxAddress(sicx._address());
        governanceScore.configureBalanced();
        governanceScore.launchBalanced();
        stakingScore.toggleStakingOn();
        // delegate(adminWallet);
        // createBnusdMarket(adminWallet, BigInteger.valueOf(210).multiply(BigInteger.TEN.pow(18)));


    }
}
