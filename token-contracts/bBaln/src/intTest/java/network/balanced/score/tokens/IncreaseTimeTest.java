package network.balanced.score.tokens;

import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IncreaseTimeTest {

    private static Balanced balanced;
    private static BalancedClient owner;

    @BeforeEach
    void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
    }

    @Test
    void testIncreaseTIme(){
        DefaultScoreClient ownerClient  = getOwnerClient();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        owner.daofund.addAddressToSetdb();
        balanced.syncDistributions();
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        owner.governance.setContinuousRewardsDay(owner.dex.getDay().add(BigInteger.ONE));
        waitForADay();
        balanced.syncDistributions();
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        owner.rewards.claimRewards();
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+owner.baln.balanceOf(userAddress));

        long microSecondsInADay = 84_600_000_000L;
        long unlockTIme = (System.currentTimeMillis()*1000)+(30*microSecondsInADay);
        System.out.println("unlock time is: "+unlockTIme);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);
        System.out.println("expected balance is: "+availableBalnBalance.divide(BigInteger.TWO));

        BigInteger updateUnlockTIme = BigInteger.valueOf ((System.currentTimeMillis()*1000)+(60*microSecondsInADay));
        System.out.println("unlock time is: "+updateUnlockTIme);
        owner.boostedBaln.increaseUnlockTime( updateUnlockTIme );

        //test withdraw failed
        UserRevertedException exception = assertThrows(UserRevertedException.class, ()->{
            owner.boostedBaln.withdraw();
        });

        assertEquals(exception.getMessage(), "Reverted(0)");
        BigInteger balanceAfterWithdraw = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance after withdraw: "+balanceAfterWithdraw);

        Map<String, BigInteger> lockedAmountAndTime = owner.boostedBaln.getLocked(userAddress);
        System.out.println("locked amount: "+lockedAmountAndTime.get("amount"));
        System.out.println("locked end: "+lockedAmountAndTime.get("end"));

    }

    @Test
    void withdrawTest(){
        DefaultScoreClient ownerClient  = getOwnerClient();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        owner.daofund.addAddressToSetdb();
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        owner.governance.setContinuousRewardsDay(owner.dex.getDay().add(BigInteger.ONE));
        waitForADay();
        balanced.syncDistributions();
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        owner.rewards.claimRewards();
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+owner.baln.balanceOf(userAddress));

        long sec20MicroSeconds = 20_000_000L;
        long unlockTIme = (System.currentTimeMillis()*1000)+sec20MicroSeconds;
        System.out.println("unlock time is: "+unlockTIme);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);
        System.out.println("expected balance is: "+availableBalnBalance.divide(BigInteger.TWO));
        try {
            Thread.sleep(sec20MicroSeconds / 1000);
        }catch (Exception ignored){}

        owner.boostedBaln.withdraw();
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }

    DefaultScoreClient getOwnerClient(){
        return new DefaultScoreClient(
                owner.baln.endpoint(),
                owner.baln._nid(),
                balanced.owner,
                DefaultScoreClient.ZERO_ADDRESS
        );
    }
}
