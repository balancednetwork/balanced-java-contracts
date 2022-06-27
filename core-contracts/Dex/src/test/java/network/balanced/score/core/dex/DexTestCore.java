package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;

import network.balanced.score.lib.structs.PrepDelegations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;

import static network.balanced.score.lib.utils.Constants.*;


public class DexTestCore extends DexTestBase {
    
    @BeforeEach
    public void configureContract() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        setupAddresses();
        super.setup();
    }

    @Test
    void fallback() {
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        contextMock.when(() -> Context.getValue()).thenReturn(icxValue);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        dexScore.invoke(ownerAccount, "fallback");
    }

    // Test fails on line with: activeAddresses.get(SICXICX_POOL_ID).remove(user);
    @Test
    void cancelSicxIcxOrder() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);

        turnDexOn();
        supplyIcxLiquidity(supplier, value);
        sm.getBlock().increase(100000);

        // Mock these.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.transfer(eq(supplier.getAddress()), eq(value))).thenAnswer((Answer<Void>) invocation -> null);
        
        // Act.
        dexScore.invoke(supplier, "cancelSicxicxOrder");

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(BigInteger.ZERO, IcxBalance);
    }

    @Test
    void withdrawSicxEarnings() {
        // Supply liquidity to sicx/icx pool.
        // Swap some sicx to icx.
        // Withdraw earnings.
        // Verify transfer called with correct arguments.
        // Assert that getSicxEarnings returns 0.
    }

    @Test
    void tokenFallback_deposit() {
        // Arrange.
        Account tokenScoreCaller = balnScore;
        Account tokenSender = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(1000000000);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act.
        dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), depositValue, tokenData("_deposit", new HashMap<>()));
        BigInteger retrievedDepositValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(), tokenSender.getAddress());

        // Assert.
        assertEquals(depositValue, retrievedDepositValue);
    }

    @Test
    void withdrawTokens_negativeAmount() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(-1000).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): Balanced DEX: Must specify a positive amount";
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawToken_insufficientBalance() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(1000).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): Balanced DEX: Insufficient Balance";
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawToken() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositToken(depositor, balnScore, depositValue);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);
        
        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
    
        // Assert. 
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);
    }

    @Test
    void addLiquidity() {
        // Todo.
    }

    @Test
    void removeLiquidity_withdrawalLockActive() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Act & Assert.
        Executable fundsLocked = () -> dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
        expectErrorMessage(fundsLocked, "Reverted(0): Balanced DEX:  Assets must remain in the pool for 24 hours, please try again later.");
    }

    @Test
    void removeLiquidity() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        BigInteger usersLpTokens = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);

        // Arrange - increase blocks past withdrawal lock.
        sm.getBlock().increase(100000000);

         // Act & Assert.
         dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
         BigInteger usersLpTokensAfterRemoval = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
         assertEquals(usersLpTokens.subtract(lpTokensToRemove), usersLpTokensAfterRemoval);
    }

    @Test
    void tokenFallback_swap() {

    }

    @Test
    void tokenfallback_swapSicx() {
        // Todo.
    }

    @Test
    void onIRC31Received() {
        // Arrange.
        Account irc31Contract = Account.newScoreAccount(1);
        Address operator = sm.createAccount().getAddress();
        Address from = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        byte[] data = new byte[0];
        String expectedErrorMessage = "Reverted(0): Balanced DEX: IRC31 Tokens not accepted";

        // Act and assert.
        Executable onIRC31Received = () -> dexScore.invoke(irc31Contract, "onIRC31Received", operator, from, id, value, data);
        expectErrorMessage(onIRC31Received, expectedErrorMessage);
    }

    @Test
    void transfer() {
        Account account = sm.createAccount();
        Account account1 = sm.createAccount();

        final String data = "{" +
                "\"method\": \"_deposit\"" +
                "}";

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class))).thenReturn(null);

        BigInteger FIFTY = BigInteger.valueOf(50L).multiply(EXA);
        dexScore.invoke(bnusdScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(balnScore, "tokenFallback", account.getAddress(), BigInteger.valueOf(50L).multiply(EXA), data.getBytes());
        dexScore.invoke(account, "add", balnScore.getAddress(), bnusdScore.getAddress(), FIFTY, FIFTY.divide(BigInteger.TWO), false);

        BigInteger poolId = (BigInteger) dexScore.call("getPoolId", balnScore.getAddress(), bnusdScore.getAddress());
        BigInteger transferValue = BigInteger.valueOf(5).multiply(EXA);
        BigInteger initialValue = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        dexScore.invoke(account, "transfer", account1.getAddress(), transferValue, poolId, data.getBytes());

        BigInteger value = (BigInteger) dexScore.call("balanceOf", account.getAddress(), poolId);
        assertEquals(value, initialValue.subtract(transferValue));
        value = (BigInteger) dexScore.call("balanceOf", account1.getAddress(), poolId);
        assertEquals(value, transferValue);
    }

    @Test
    void tokenFallback_swapIcx_revertOnIncompleteRewards() {
        // Arrange.
        Account tokenScoreCaller = sicxScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act & assert.
        Executable incompleteRewards = () -> dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
        expectErrorMessage(incompleteRewards, "Reverted(0): Balanced DEX: Rewards distribution in progress, please try again shortly");
    }

    // In the process of going through this.
    @Test
    void swap_sicx_icx() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(10000000).multiply(EXA);
        BigInteger sicxIcxConversionRate = new BigInteger("1100758881004412705");
        BigInteger swapValue = BigInteger.valueOf(100).multiply(EXA);

        // Act.
        supplyIcxLiquidity(ownerAccount, value);
        supplyIcxLiquidity(sm.createAccount(), value);
        supplyIcxLiquidity(sm.createAccount(), value);
        swapSicxToIcx(ownerAccount, swapValue, sicxIcxConversionRate);
    }

    @Test
    void delegate(){
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn("Staking delegate called");
        dexScore.invoke(governanceScore, "delegate", (Object) preps);
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}


    /*
    == Tests left == 

    == Icx/sicx pool methods == 
    getSicxEarnings
    withdrawSicxEarnings
    fallback
    
    == Snapshot methods ==
    getBalnSnapshot
    loadBalancesAtSnapshot
    getDataBatch
    totalSupplyAt
    totalBalnAt
    balanceOfAt
    getTotalValue
    
    == Normal liquidity pool methods ==
    tokenFallback
    remove
    add
    addLpAddresses -> No getter.


    == Others ==
    transfer  -> IRC31 transfer method.
    */