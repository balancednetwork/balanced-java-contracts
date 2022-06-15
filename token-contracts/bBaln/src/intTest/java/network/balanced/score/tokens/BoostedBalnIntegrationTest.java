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
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());
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
    static Rewards userRewardScoreClient = new RewardsScoreClient(balnScoreClient.endpoint(), balnScoreClient._nid(), userWallet,
            rewardsScoreClient._address());

    @ScoreClient
    static Governance bBalnGovernanceScoreClient = new GovernanceScoreClient(governanceScoreClient);

    @ScoreClient
    static DAOfund userDaoFundScoreClient = new DAOfundScoreClient(daoFund);

    @Test
    void testDepositFor(){
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();
        userClient._transfer(dexScoreClient._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        bBalnGovernanceScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        waitForADay();
        balanced.syncDistributions();
        BigInteger updatedBalnHolding = userRewardScoreClient.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        userRewardScoreClient.claimRewards();
        BigInteger availableBalnBalance = userBalnScoreClient.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+userBalnScoreClient.balanceOf(userAddress));

        UserRevertedException exception = assertThrows(UserRevertedException.class, () -> {
            String data = "{\"method\":\"depositFor\",\"params\":{\"address\":\"" + userAddress + "\"}}";
            userBalnScoreClient.transfer(bBalnScoreClient._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)"); //"Deposit for: No existing lock found");

        exception = assertThrows(UserRevertedException.class, () -> {
           String data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + System.currentTimeMillis()*1000 + "}}";
            userBalnScoreClient.transfer(bBalnScoreClient._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)");//"Increase amount: No existing lock found");

        exception = assertThrows(UserRevertedException.class, () -> {
            String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + System.currentTimeMillis() + "}}";
            userBalnScoreClient.transfer(bBalnScoreClient._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)");//"Increase amount: No existing lock found");

        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + (System.currentTimeMillis()+(30*84600000))*1000 + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.subtract(availableBalnBalance.divide(BigInteger.TWO)));
        userBalnScoreClient.transfer(bBalnScoreClient._address(), availableBalnBalance.subtract(availableBalnBalance.divide(BigInteger.TWO)), data.getBytes());

        /*data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":\"" + (System.currentTimeMillis()+60*84600)*1000 + "\"}}";
        userBalnScoreClient.transfer(bBalnScoreClient._address(), updatedBalnHolding, data.getBytes());*/

    }


    void waitForADay(){
        balanced.increaseDay(1);
    }




}
