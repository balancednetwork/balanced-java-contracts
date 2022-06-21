package network.balanced.score.tokens;

import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;
import static network.balanced.score.tokens.Constants.ZERO_ADDRESS;
import static network.balanced.score.tokens.TestHelper.getExpectedBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BoostedBalnIncreaseTimeTest {

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
        waitDays(1);
        balanced.syncDistributions();
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        owner.rewards.claimRewards();
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+owner.baln.balanceOf(userAddress));

        long unlockTime = (System.currentTimeMillis()*1000)+(BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        System.out.println("unlock time is: "+unlockTime);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);
        assertEquals(balance.divide(EXA), getExpectedBalance(availableBalnBalance.divide(BigInteger.TWO), unlockTime).divide(EXA));

        BigInteger updateUnlockTime = BigInteger.valueOf(unlockTime).add(BigInteger.TWO.multiply(WEEK_IN_MICRO_SECONDS));
        System.out.println("unlock time is: "+updateUnlockTime);
        owner.boostedBaln.increaseUnlockTime( updateUnlockTime );
        balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        assertEquals(balance.divide(EXA), getExpectedBalance(availableBalnBalance.divide(BigInteger.TWO), updateUnlockTime.longValue()).divide(EXA));


    }

    @Test
    void withdrawTest(){
        owner.boostedBaln.setPenaltyAddress(ZERO_ADDRESS);
        DefaultScoreClient ownerClient  = getOwnerClient();
        score.Address userAddress = score.Address.fromString(balanced.owner.getAddress().toString());
        owner.daofund.addAddressToSetdb();
        ownerClient._transfer(owner.dex._address(), BigInteger.valueOf(1000).multiply(EXA), null);
        owner.governance.setContinuousRewardsDay(owner.dex.getDay().add(BigInteger.ONE));
        waitDays(1);
        balanced.syncDistributions();
        BigInteger updatedBalnHolding = owner.rewards.getBalnHolding(userAddress);
        System.out.println("baln holding from reward: "+updatedBalnHolding);
        owner.rewards.claimRewards();
        BigInteger availableBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("available balance of baln: "+availableBalnBalance);
        System.out.println("total balance of baln: "+owner.baln.balanceOf(userAddress));

        long unlockTIme = (System.currentTimeMillis()*1000)+(BigInteger.valueOf(7).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        System.out.println("unlock time is: "+unlockTIme);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTIme + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);
        System.out.println("expected balance is: "+availableBalnBalance.divide(BigInteger.TWO));

        owner.boostedBaln.withdraw();

        BigInteger balanceAfterWithdraw = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        assertEquals(balanceAfterWithdraw, BigInteger.ZERO);

        BigInteger newBalnBalance = owner.baln.availableBalanceOf(userAddress);
        System.out.println("new baln balance is: "+newBalnBalance);
        System.out.println("available baln balance is: "+availableBalnBalance);
        assert  newBalnBalance.compareTo(availableBalnBalance)<0;
    }

    void waitDays(int days){
        balanced.increaseDay(days);
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
