package network.balanced.score.tokens;


import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BoostedBalnIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
    }

    DefaultScoreClient getOwnerClient(){
        return new DefaultScoreClient(
                owner.baln.endpoint(),
                owner.baln._nid(),
                balanced.owner,
                DefaultScoreClient.ZERO_ADDRESS
        );
    }

    @Test
    void testCreateLockIncreaseTimeAndDeposit(){
        DefaultScoreClient ownerClient  = getOwnerClient();
        owner.daofund.addAddressToSetdb();
        balanced.syncDistributions();
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        owner.governance.setContinuousRewardsDay(owner.dex.getDay().add(BigInteger.ONE));
        waitForADay();
        balanced.syncDistributions();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        owner.rewards.claimRewards();
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+owner.baln.balanceOf(userAddress));

        UserRevertedException exception = assertThrows(UserRevertedException.class, () -> {
            String data = "{\"method\":\"depositFor\",\"params\":{\"address\":\"" + userAddress + "\"}}";
            owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)"); //"Deposit for: No existing lock found");

        exception = assertThrows(UserRevertedException.class, () -> {
           String data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + System.currentTimeMillis()*1000 + "}}";
            owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)");//"Increase amount: No existing lock found");

        exception = assertThrows(UserRevertedException.class, () -> {
            String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + System.currentTimeMillis() + "}}";
            owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance, data.getBytes());
        });
        assert exception.getMessage().equals("Reverted(0)");//"Increase amount: No existing lock found");

        long microSecondsInADay = 84_600_000_000L;
        long unlockTIme = (System.currentTimeMillis()*1000)+(30*microSecondsInADay);
        System.out.println("unlock time is: "+unlockTIme);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);
        System.out.println("expected balance is: "+availableBalnBalance.divide(BigInteger.TWO));

        //assertEquals(balance, availableBalnBalance.divide(BigInteger.TWO));

        data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.valueOf(4)), data.getBytes());
        BigInteger newBalance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("new balance is: "+newBalance);
        System.out.println(" exoected new balance is: "+availableBalnBalance.multiply(BigInteger.valueOf(3)).multiply(BigInteger.valueOf(4)));
        //assertEquals(newBalance, availableBalnBalance.multiply(BigInteger.valueOf(3)).multiply(BigInteger.valueOf(4)));

        data = "{\"method\":\"increaseAmount\",\"params\":{\"address\":\"" + userAddress + "\"}}";
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.valueOf(4)), data.getBytes());
        BigInteger balanceAfterDeposit = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance after deposit: "+balanceAfterDeposit);
    }


    void waitForADay(){
        balanced.increaseDay(1);
    }




}
