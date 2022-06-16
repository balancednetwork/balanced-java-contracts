package network.balanced.score.core.dex;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.util.function.Consumer;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonStakedLPRewardsTest {

    @ScoreClient
    static Staking staking;

    @ScoreClient
    static Loans loans;

    @ScoreClient
    static Rewards rewards;

    @ScoreClient
    static Sicx sicx;

    @ScoreClient
    static StakedLP stakedLp;


    @ScoreClient
    static Baln baln;

    static Env.Chain chain = Env.getDefaultChain();
    static Balanced balanced;
    static Wallet userWallet;
    static Wallet tUserWallet;
    static Wallet testOwnerWallet = KeyWallet.load(new Bytes("573b555367d6734ea0fecd0653ba02659fa19f7dc6ee5b93ec781350bda27376"));
    static DefaultScoreClient dexScoreClient ;
    static DefaultScoreClient governanceScoreClient;
    static DefaultScoreClient stakingScoreClient;
    static DefaultScoreClient sIcxScoreClient;
    static DefaultScoreClient dividendScoreClient;
    static DefaultScoreClient balnScoreClient;
    static DefaultScoreClient rewardsScoreClient;
    static DefaultScoreClient feeHandlerScoreClient;


    static DefaultScoreClient daoFund;
    static Wallet ownerWallet;
    static File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexIntTestToken.jar");
    static {
        try {
            balanced = new Balanced();
            ownerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            stakingScoreClient = balanced.staking;
            sIcxScoreClient = balanced.sicx;
            dividendScoreClient = balanced.dividends;
            balnScoreClient = balanced.baln;
            rewardsScoreClient = balanced.rewards;
            feeHandlerScoreClient = balanced.feehandler;
            daoFund = balanced.daofund;
            ownerWallet = balanced.owner;
            staking = new StakingScoreClient(stakingScoreClient);
            rewards = new RewardsScoreClient(rewardsScoreClient);
            loans = new LoansScoreClient(balanced.loans);
            baln = new BalnScoreClient(balnScoreClient);
            sicx = new SicxScoreClient(sIcxScoreClient);
            stakedLp = new StakedLPScoreClient(balanced.stakedLp);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: "+e.getMessage());
        }

    }
    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());


    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());

    @ScoreClient
    static Sicx userSicxScoreClient = new SicxScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            sIcxScoreClient._address());
    @ScoreClient
    static Rewards userWalletRewardsClient = new RewardsScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            rewardsScoreClient._address());
    @ScoreClient
    static Baln userBalnScoreClient = new BalnScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            balnScoreClient._address());

    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);


    @ScoreClient
    static DAOfund userDaoFundScoreClient = new DAOfundScoreClient(daoFund);


    @Test
    void testNonStakedLpRewards() throws Exception {
        // test if the non staked lp token is rewarded or not once continuous rewards is activated.
        //balanced = new Balanced();
        //balanced.setupBalanced();
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();

        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        ((StakingScoreClient) staking).stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), userAddress
                , null);

        BigInteger loanAmount = BigInteger.valueOf(150).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18));


        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        waitForADay();
        balanced.syncDistributions();
        rewards.claimRewards();

        baln.transfer(userAddress, loanAmount, null);

        // deposit base token
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
        //deposit quote token
        userBalnScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
        dexUserScoreClient.add(balanced.baln._address(), balanced.sicx._address(), BigInteger.valueOf(100).multiply(EXA), BigInteger.valueOf(100).multiply(EXA), false);

        waitForADay();
        balanced.syncDistributions();
        userWalletRewardsClient.claimRewards();
        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
            governanceDexScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        }
        waitForADay();

        // continuous rewards starts here
        balanced.syncDistributions();
        userWalletRewardsClient.claimRewards();
        waitForADay();

        // next day starts
        Consumer<TransactionResult> distributeConsumer = result -> {};
        for(int i =0; i<10; i++){
            balanced.ownerClient.rewards.distribute(distributeConsumer);
        }
        waitForADay();

        // next day starts
        for(int i =0; i<10; i++){
            balanced.ownerClient.rewards.distribute(distributeConsumer);
        }
        // users without staking LP tokens will get 0 rewards
        assertEquals(BigInteger.ZERO, rewards.getBalnHolding(userAddress));

        byte[] stakeLp = "{\"method\":\"_stake\"}".getBytes();
        dexUserScoreClient.transfer(balanced.stakedLp._address(), BigInteger.valueOf(90),BigInteger.valueOf(4), stakeLp);

        // user gets rewards after lp token is staked
        assertTrue(rewards.getBalnHolding(userAddress).compareTo(BigInteger.ZERO) > 0);
        BigInteger previousUserBalance = baln.balanceOf(userAddress);
        userWalletRewardsClient.claimRewards();
        BigInteger newBalance = baln.balanceOf(userAddress);
        assertTrue(newBalance.compareTo(previousUserBalance) > 0);
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }
}
