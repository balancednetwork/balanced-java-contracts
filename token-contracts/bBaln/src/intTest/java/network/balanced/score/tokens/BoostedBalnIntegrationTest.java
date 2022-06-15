package network.balanced.score.tokens;


import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import network.balanced.score.tokens.interfaces.BoostedToken;
import network.balanced.score.tokens.interfaces.BoostedTokenScoreClient;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;
import static network.balanced.score.lib.utils.Constants.EXA;

public class BoostedBalnIntegrationTest {
    static Balanced balanced;
    static Wallet userWallet;
    static DefaultScoreClient balnScoreClient;
    static DefaultScoreClient bBalnScoreClient;
    static DefaultScoreClient rewardsScoreClient;
    static DefaultScoreClient dexScoreClient ;
    static DefaultScoreClient governanceScoreClient;
    static DefaultScoreClient daoFund;
    static Wallet ownerWallet;

    static {
        try {
            balanced = new Balanced();
            ownerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(2000).multiply(EXA));
            balanced.setupBalanced();
            balnScoreClient = balanced.baln;
            bBalnScoreClient = balanced.bBaln;
            rewardsScoreClient = balanced.rewards;
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            daoFund = balanced.daofund;
            ownerWallet = balanced.owner;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: "+e.getMessage());
        }

    }
    static DefaultScoreClient userClient  = new DefaultScoreClient(
            chain.getEndpointURL(),
            chain.networkId,
            userWallet,
            DefaultScoreClient.ZERO_ADDRESS
    );
    @ScoreClient
    static Baln userBalnScoreClient = new BalnScoreClient(balnScoreClient.endpoint(), balnScoreClient._nid(), userWallet,
            balnScoreClient._address());

    @ScoreClient
    static BoostedToken userBoostedBalnScoreClient = new BoostedTokenScoreClient(balnScoreClient.endpoint(), balnScoreClient._nid(), userWallet,
            bBalnScoreClient._address());
    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(balnScoreClient.endpoint(), balnScoreClient._nid(), userWallet,
            dexScoreClient._address());
    @ScoreClient
    static Rewards userRewardScoreClient = new RewardsScoreClient(rewardsScoreClient);

    @ScoreClient
    static Governance bBalnGovernanceScoreClient = new GovernanceScoreClient(rewardsScoreClient);

    @ScoreClient
    static DAOfund userDaoFundScoreClient = new DAOfundScoreClient(daoFund);

    @Test
    void testDepositFor(){
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();
        userClient._transfer(dexScoreClient._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
            bBalnGovernanceScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        }
        System.out.println("CR day is: "+dexUserScoreClient.getDay());
        waitForADay();
        balanced.syncDistributions();
        System.out.println("CR day is: "+dexUserScoreClient.getDay());
        BigInteger updatedBalnHolding = userRewardScoreClient.getBalnHolding(userClient._address());
        System.out.println("baln balance: "+updatedBalnHolding);
    }


    void waitForADay(){
        balanced.increaseDay(2);
    }




}
