package network.balanced.score.test.integration;

import static network.balanced.score.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.test.integration.ScoreIntegrationTest.deploy;
import static network.balanced.score.test.integration.ScoreIntegrationTest.systemScore;
import static network.balanced.score.test.integration.ScoreIntegrationTest.*;

import java.math.BigInteger;
import java.util.Map;

import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.interfaces.*;

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
    Staking stakingScore;

    public Balanced() {
       
    }

    public void deployBalanced() throws Exception {
        owner = createWalletWithBalance(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)));
        deployPrep();

        governance = deploy(owner, "Governance", null);
        governanceScore = new GovernanceScoreClient(governance);
        deployContracts();
        setupAddresses();
        setupContracts();
        // delegate(adminWallet);
        // transfer(governance._address(), BigInteger.valueOf(10000).multiply(BigInteger.TEN.pow(18)));
        // ((GovernanceScoreClient)governanceScore).createBnusdMarket(BigInteger.valueOf(210).multiply(BigInteger.TEN.pow(18)));
    }

    protected void deployPrep() {
        try {
            systemScore.registerPRep(BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), "test", "kokoa@example.com", "USA", "New York", "https://icon.kokoa.com", "https://icon.kokoa.com/json/details.json", "localhost:9082");
        } catch (Exception e) {
            //Already registerd
        }
    }

    protected void deployContracts() {
        baln = deploy(owner, "Baln", Map.of("_governance", governance._address()));
        bwt = deploy(owner, "Bwt", Map.of("_governance", governance._address()));
        dex = deploy(owner, "Dex", Map.of("_governance", governance._address()));
        feehandler = deploy(owner, "Feehandler", Map.of("_governance", governance._address()));
        loans = deploy(owner, "Loans", Map.of("_governance", governance._address()));
        rebalancing = deploy(owner, "Rebalancing", Map.of("_governance", governance._address()));
        rewards = deploy(owner, "Rewards", Map.of("_governance", governance._address()));
        sicx = deploy(owner, "Sicx", Map.of("_admin", governance._address()));
        bnusd = deploy(owner, "Bnusd", Map.of("_governance", governance._address()));
        daofund = deploy(owner, "DAOfund", Map.of("_governance", governance._address()));
        dividends = deploy(owner, "Dividends", Map.of("_governance", governance._address()));
        oracle = deploy(owner, "Oracle",null);
        reserve = deploy(owner, "Reserve", Map.of("governance", governance._address()));
        router = deploy(owner, "Router", Map.of("_governance", governance._address()));
        staking = deploy(owner, "Staking", null);
    }

    protected void setupAddresses() {
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
    }

    protected void setupContracts() {
        stakingScore = new StakingScoreClient(staking);

        stakingScore.setSicxAddress(sicx._address());
        governanceScore.configureBalanced();
        governanceScore.launchBalanced();
        stakingScore.toggleStakingOn();
    }
}
