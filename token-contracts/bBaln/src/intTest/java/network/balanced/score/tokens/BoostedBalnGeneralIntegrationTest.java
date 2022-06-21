package network.balanced.score.tokens;


import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;
import static network.balanced.score.tokens.TestHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BoostedBalnGeneralIntegrationTest implements ScoreIntegrationTest {
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
        //check the effect of checkpoint external method call
        owner.boostedBaln.checkpoint();
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

        long unlockTime = (System.currentTimeMillis()*1000)+(WEEK_IN_MICRO_SECONDS.multiply(BigInteger.valueOf(4))).longValue();
        System.out.println("unlock time is: "+unlockTime);
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        System.out.println("transfer amount: "+availableBalnBalance.divide(BigInteger.TWO));
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger balance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance is: "+balance);

        assertEquals(balance.divide(EXA), getExpectedBalance(availableBalnBalance.divide(BigInteger.TWO), unlockTime).divide(EXA));

        BigInteger totalSupply = owner.boostedBaln.totalSupply(BigInteger.valueOf(unlockTime));
        Context.println("total supply is: "+totalSupply);
        unlockTime = (unlockTime+(WEEK_IN_MICRO_SECONDS.multiply(BigInteger.TWO)).longValue());
        data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.valueOf(4)), data.getBytes());
        BigInteger newBalance = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("new balance is: "+newBalance);
        BigInteger expectedValue = (getExpectedBalance(availableBalnBalance.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(4)), unlockTime)).divide(EXA);
        System.out.println("expected value is: "+expectedValue);
        assertEquals(newBalance.divide(EXA), expectedValue);

        data = "{\"method\":\"depositFor\",\"params\":{\"address\":\"" + userAddress + "\"}}";
        owner.baln.transfer(owner.boostedBaln._address(), availableBalnBalance.divide(BigInteger.valueOf(4)), data.getBytes());
        BigInteger balanceAfterDeposit = owner.boostedBaln.balanceOf(userAddress, BigInteger.ZERO);
        System.out.println("balance after deposit: "+balanceAfterDeposit);
        BigInteger expectedValueAfter = (getExpectedBalance(availableBalnBalance, unlockTime)).divide(EXA);
        assertEquals(balanceAfterDeposit.divide(EXA), expectedValueAfter);

        BigInteger finalTotalSupply = owner.boostedBaln.totalSupply(BigInteger.valueOf(unlockTime));
        System.out.println("final total supply is: "+finalTotalSupply);

        BigInteger totalLocked = owner.boostedBaln.getTotalLocked();
        System.out.println("total locked is: "+totalLocked);
        assertEquals(totalLocked.divide(EXA), availableBalnBalance.divide(EXA));

        List<Address> users = owner.boostedBaln.getUsers(0, 100);
        assert users.size()==1;
        assertEquals(users.get(0), owner.getAddress());

        BigInteger lockedEnd = owner.boostedBaln.lockedEnd(owner.getAddress());
        assertEquals(lockedEnd, BigInteger.valueOf(getLockEnd(unlockTime)));

        BigInteger lastUserSlope = owner.boostedBaln.getLastUserSlope(owner.getAddress());
        assertEquals(lastUserSlope, getSlope(availableBalnBalance, unlockTime));

    }


    void waitForADay(){
        balanced.increaseDay(1);
    }




}
