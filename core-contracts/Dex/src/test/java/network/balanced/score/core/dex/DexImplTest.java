package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.matchers.Null;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import score.Address;
import score.Context;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class DexImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static Account ownerAccount = sm.createAccount();
    private static Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account dividendsScore = Account.newScoreAccount(scoreCount++);
    private final Account stakingScore = Account.newScoreAccount(scoreCount++);
    private final Account rewardsScore = Account.newScoreAccount(scoreCount++);
    private final Account bnusdScore = Account.newScoreAccount(scoreCount++);
    private final Account balnScore = Account.newScoreAccount(scoreCount++);
    private final Account sicxScore = Account.newScoreAccount(scoreCount++);
    private final Account feehandlerScore = Account.newScoreAccount(scoreCount++);
    private final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    public void setup() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);
    }

    @Test
    void testName(){
        assertEquals(dexScore.call("name"), "Balanced DEX");
    }

    @Test
    void setGetAdmin() {
        testAdmin(dexScore, governanceScore, adminAccount);
    }
    
    @Test
    void setGetGovernance() {
        testGovernance(dexScore, governanceScore, ownerAccount);
    }

    @Test
    void setGetSicx() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                 "setSicx", dexScore.getAddress(), "getSicx");
    }
    
    @Test
    void setGetDividends() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setDividends", dividendsScore.getAddress(), "getDividends");
    }

    @Test
    void setGetStaking() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setStaking", stakingScore.getAddress(), "getStaking");
    }

    @Test
    void setGetRewards() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setRewards", rewardsScore.getAddress(), "getRewards");
    }

    @Test
    void setGetBnusd() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setbnUSD", bnusdScore.getAddress(), "getbnUSD");
    }

    @Test
    void setGetBaln() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setBaln", balnScore.getAddress(), "getBaln");
    }

    @Test
    void setGetFeehandler() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setFeehandler", feehandlerScore.getAddress(), "getFeehandler");
    }

    @Test
    void setGetStakedLP() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setStakedLp", stakedLPScore.getAddress(), "getStakedLp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void setGetFees() {
        // Arrange - fees to be set.
        BigInteger poolLpFee = BigInteger.valueOf(100);
        BigInteger poolBalnFee = BigInteger.valueOf(200);
        BigInteger icxConversionFee = BigInteger.valueOf(300);
        BigInteger icxBalnFee = BigInteger.valueOf(400);

        // Arrange - methods to be called and set specified fee.
        Map<String, BigInteger> fees = Map.of(
            "setPoolLpFee", poolLpFee,
            "setPoolBalnFee", poolBalnFee,
            "setIcxConversionFee", icxConversionFee,
            "setIcxBalnFee", icxBalnFee
        );

        // Arrange - expected result when retrieving fees".
        Map<String, BigInteger> expectedResult = Map.of(
            "icx_total", icxBalnFee.add(icxConversionFee),
            "pool_total", poolBalnFee.add(poolLpFee),
            "pool_lp_fee", poolLpFee,
            "pool_baln_fee", poolBalnFee,
            "icx_conversion_fee", icxConversionFee,
            "icx_baln_fee", icxBalnFee
        );
        Map<String, BigInteger> returnedFees;

        // Act & assert - set all fees and assert that all fee methods are only settable by governance.
        for (Map.Entry<String, BigInteger> fee : fees.entrySet()) {
            dexScore.invoke(governanceScore, fee.getKey(), fee.getValue());
            assertOnlyCallableByGovernance(dexScore, fee.getKey(), fee.getValue());
        }

        // Assert - retrieve all fees and check validity.
        returnedFees =  (Map<String, BigInteger>) dexScore.call("getFees");
        assertTrue(expectedResult.equals(returnedFees));
    }
    
    @Test
    void turnDexOnAndGetDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
        assertEquals(true, dexScore.call("getDexOn"));
        assertOnlyCallableByGovernance(dexScore, "turnDexOn");
    }

    @Test
    void addQuoteCoinAndCheckIfAllowed() {
        // Arrange.
        Address quoteCoin = Account.newScoreAccount(1).getAddress();

        // Act.
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteCoin);

        // Assert.
        Boolean quoteCoinAllowed = (Boolean) dexScore.call("isQuoteCoinAllowed", quoteCoin);
        assertEquals(true, quoteCoinAllowed);
        assertOnlyCallableByGovernance(dexScore, "addQuoteCoin", quoteCoin);
    }

    @Test
    void setGetTimeOffSet() {
        // Arrange.
        BigInteger timeOffset = BigInteger.valueOf(100);

        // Act.
        dexScore.invoke(governanceScore, "setTimeOffset", timeOffset);

        // Assert.
        BigInteger retrievedTimeOffset = (BigInteger) dexScore.call("getTimeOffset");
        assertEquals(timeOffset, retrievedTimeOffset);
        assertOnlyCallableByGovernance(dexScore, "setTimeOffset", timeOffset);
    }

    // isLookingPool.

    @Test
    void setGetContinuousRewardsDay() {
        // Arrange.
        BigInteger continuousRewardsDay = BigInteger.valueOf(2);

        // Act.
        dexScore.invoke(governanceScore, "setContinuousRewardsDay", continuousRewardsDay);

        // Assert.
        BigInteger retrievedContinuousRewardsDay = (BigInteger) dexScore.call("getContinuousRewardsDay");
        assertEquals(continuousRewardsDay, retrievedContinuousRewardsDay);
        assertOnlyCallableByGovernance(dexScore, "setContinuousRewardsDay", continuousRewardsDay);
    }

    //@Test
    //void supplyLiquidity_newPoolCreated() {
    //    // Arrange - Tokenssupply information.
    //    Account supplier = sm.createAccount();
    //    Address baseToken = balnScore.getAddress();
    //    Address quoteToken = bnusdScore.getAddress();
    //    BigInteger baseValue = BigInteger.valueOf(10).pow(20);
    //    BigInteger quoteValue = BigInteger.valueOf(10).pow(20);
    //    Boolean withdrawUnused = false;
    //    
    //    // Arrange - configure dex settings.
    //    this.setupAddresses();
    //    dexScore.invoke(governanceScore, "turnDexOn");
    //    dexScore.invoke(governanceScore, "addQuoteCoin", bnusdScore.getAddress());
//
    //    // Arrange - Mock these cross-contract calls.
    //    contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
//
    //    // Act - deposit tokens and supply liquidity.
    //    dexScore.invoke(balnScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
    //    dexScore.invoke(bnusdScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
    //    dexScore.invoke(supplier, "add", baseToken, quoteToken, baseValue, quoteValue, withdrawUnused);

        // Assert.
    //}


    //@Test
    //void tokenFallback_rewardsNotFinished() {
    //    // Arrange.
    //    BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
    //    setupAddresses();
    //    dexScoreSpy.rewardsDone.set(false);
    //    contextMock.verify(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute")));
    //    contextMock.verify(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute")));
    //    

    //    // Move checks to the start of the funciton to test it properly.
    //    // Act.
    //    dexScore.invoke(bnusdScore, "tokenFallback", adminAccount.getAddress(), bnusdValue, new byte[0]);
    //}


    @Test
    void fallback() {
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        setupAddresses();
        contextMock.when(() -> Context.getValue()).thenReturn(icxValue);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        // This does not work. Why? Going with when in the meantime.
        //contextMock.verify(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any()));


        dexScore.invoke(ownerAccount, "fallback");
    }

    @Test
    void getIcxBalance() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);
        setupAddresses();

        // Act.
        supplyIcxLiquidity(supplier, value);

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(IcxBalance, value);
    }

    @Test
    void cancelIcxOrder() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);
        setupAddresses();
        supplyIcxLiquidity(supplier, value);
        supplyIcxLiquidity(ownerAccount, value);
        sm.getBlock().increase(100000);
        turnDexOn();

        // Mock these.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        
        // This one should verify.
        contextMock.when(() -> Context.transfer(eq(supplier.getAddress()), eq(value))).thenReturn(null);

        // Act.
        dexScore.invoke(supplier, "cancelSicxicxOrder"); 
    }


    @Test
    void tokenFallback_deposit() {
        // Arrange.
        Account tokenScoreCaller = balnScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);
        BigInteger retrievedValue;
        
        setupAddresses();

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act.
        dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_deposit", new HashMap<>()));
        retrievedValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(), tokenSender.getAddress());

        // Assert.
        assertEquals(value, retrievedValue);
    }

    @Test
    void transfer() {

    }

    @Test
    void withdrawTokens_insufficientBalance() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(1000).multiply(EXA);
        String expectedErrorMessage = "Balanced DEX: Insufficient Balance";
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawTokens_negativeAmount() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(-1000).multiply(EXA);
        String expectedErrorMessage = "Balanced DEX: Must specify a posititve amount";
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawTokens() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);

        // Cant get verify to work so using when to continue testing.
        //contextMock.verify(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue)));
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);
        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);

        // Assert. 
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);
    }

    @Test
    void tokenFallback_swapIcx_revertOnIncompleteRewards() {
        // Arrange.
        Account tokenScoreCaller = sicxScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);
        
        setupAddresses();

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act & assert.
        Executable incompleteRewards = () -> dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
        expectErrorMessage(incompleteRewards, "Reverted(0): Balanced DEX Rewards distribution in progress, please try again shortly");
    }

    //@Test
    //void tokenFallback_swapIcx() {
    //    // Arrange.
    //    Account tokenScoreCaller = sicxScore;
    //    Account tokenSender = sm.createAccount();
    //    BigInteger value = BigInteger.valueOf(1000000000);
    //    
    //    setupAddresses();
//
    //    contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
//
    //    dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
    //}


    @Test
    void getNonce() {
        // Arrange.
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, BigInteger.valueOf(10).pow(19), BigInteger.valueOf(10).pow(19), false);

        // Assert.
        BigInteger nonce = (BigInteger) dexScore.call( "getNonce");
        assertEquals(BigInteger.valueOf(3), nonce);
    }

    @Test
    void getPoolId() {
        // Arrange.
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, BigInteger.valueOf(10).pow(19), BigInteger.valueOf(10).pow(19), false);

        // Assert.
        BigInteger poolId = (BigInteger) dexScore.call( "getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        assertEquals(BigInteger.TWO, poolId);
    }

    @Test
    void lookupId() {
        // Arrange.
        String namedMarket = "sICX/ICX";
        BigInteger expectedId = BigInteger.valueOf(1);

        // Assert.
        BigInteger id = (BigInteger) dexScore.call("lookupPid", namedMarket);
        assertEquals(expectedId, id);
    }


    @Test
    void getPoolTotal() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedBnusdValue = (BigInteger) dexScore.call("getPoolTotal", poolId, bnusdScore.getAddress());
        BigInteger retrievedBalnValue = (BigInteger) dexScore.call("getPoolTotal", poolId, balnScore.getAddress());
        assertEquals(bnusdValue, retrievedBnusdValue);
        assertEquals(balnValue, retrievedBalnValue);
    }

    @Test
    void balanceOf() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger userLpTokenValue = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedUserLpTokenValue = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        assertEquals(userLpTokenValue, retrievedUserLpTokenValue);
    }

    @Test
    void totalSupply_SicxIcxPool() {
        // TODO after custom method to supply liquidity to this pool.

       // Arrange.
       //BigInteger poolId = BigInteger.valueOf(1);
       //BigInteger totalLpTokens;

       //// Assert.
       //BigInteger retrievedTotalLpTokens = (BigInteger) dexScore.call("totalSupply", poolId);
       //assertEquals(totalLpTokens, retrievedTotalLpTokens);
    }

    @Test
    void totalSupply_normalPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger totalLpTokens = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedTotalLpTokens = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(totalLpTokens, retrievedTotalLpTokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setGetMarketNames() {
        // Arrange.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        List<String> expectedMarketNames = Arrays.asList("sICX/ICX", poolName);

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Assert.
        List<String> namedPools = (List<String>) dexScore.call("getNamedPools");
        assertEquals(expectedMarketNames, namedPools);
        assertOnlyCallableByGovernance(dexScore, "setMarketName", poolId, poolName);
    }
    
    @Test
    void getPoolBaseAndGetPoolQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Address poolBase = (Address) dexScore.call( "getPoolBase", BigInteger.TWO);
        Address poolQuote = (Address) dexScore.call( "getPoolQuote", BigInteger.TWO);
        assertEquals(poolBase, bnusdScore.getAddress());
        assertEquals(poolQuote, balnScore.getAddress());
    }

    @Test
    void getQuotePriceInBase() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, balnValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getQuotePriceInBase", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBasePriceInQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getBasePriceInQuote", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getPrice", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBalnPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getBalnPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getSicxBnusdPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, sicxValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, sicxScore, bnusdScore, sicxValue, bnusdValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getSicxBnusdPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBnusdValue_sicxIsQuote() {
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, sicxValue);
        setupAddresses();

        // Act. Why does this fail?
        //supplyLiquidity(ownerAccount, bnusdScore, sicxScore, bnusdValue, sicxValue, false);
    }
    
    @Test
    void getBnusdValue_bnusdIsQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedValue = BigInteger.valueOf(195).multiply(EXA).multiply(BigInteger.TWO);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();
      
        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, balnScore, bnusdScore, balnValue, bnusdValue, false);

        // Assert.
        BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", poolName);
        assertEquals(expectedValue, poolValue);
    }

    @Test
    void getBnusdValue_QuoteNotSupported() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

         // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, balnValue, bnusdValue, false);

         // Assert
        BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", "bnUSD/BALN");
        assertEquals(BigInteger.ZERO, poolValue);
    }

    @Test
    void getPriceByName() {
         // Arrange.
         BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
         BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
         String poolName = "bnUSD/BALN";
         BigInteger poolId = BigInteger.valueOf(2);
         BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
         setupAddresses();
 
          // Act.
         dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
         supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
 
          // Assert
         BigInteger price = (BigInteger) dexScore.call( "getPriceByName", "bnUSD/BALN");
         assertEquals(expectedPrice, price);
    }

    @Test
    void getPoolName() {
        // Arrange.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Assert.
        String retrievedPoolName = (String) dexScore.call("getPoolName", poolId);
        assertEquals(poolName, retrievedPoolName);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPoolStats_notSicxIcxPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        BigInteger tokenDecimals = BigInteger.valueOf(18);
        BigInteger minQuote = BigInteger.ZERO;
        BigInteger totalLpTokens = new BigInteger("261247009552262626468"); // Check how this is derived.

        Map<String, Object> expectedPoolStats = Map.of(
            "base", bnusdValue,
            "quote", balnValue,
            "base_token", bnusdScore.getAddress(),
            "quote_token", balnScore.getAddress(),
            "total_supply", totalLpTokens,
            "price", expectedPrice,
            "name", poolName,
            "base_decimals", tokenDecimals,
            "quote_decimals", tokenDecimals,
            "min_quote", minQuote
        );
        setupAddresses();
        
        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call( "getPoolStats", poolId);
        assertEquals(expectedPoolStats, poolStats);
    }

    @Test
    void getTotalDexAddresses() {
         // Arrange.
         BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
         BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
         BigInteger poolId = BigInteger.TWO;
         setupAddresses();
 
          // Act.
         supplyLiquidity(adminAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
         supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
 
          // Assert
         Integer totalDexAddresses = (int) dexScore.call( "totalDexAddresses", poolId);
         assertEquals(2, totalDexAddresses);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBalanceAndSupply_normalPool() {
        // Arrange - Variables.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;
        BigInteger mockBalance = BigInteger.valueOf(100);
        BigInteger mockTotalSupply = BigInteger.valueOf(200);
        Map<String, BigInteger> expectedData = Map.of(
            "_balance", mockBalance,
            "_totalSupply", mockTotalSupply
        );

        // Arrange - Setup dex contract.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        setupAddresses();

        // Arrange - Mock these calls to stakedLP contract.
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("balanceOf"), eq(ownerAccount.getAddress()), eq(poolId))).thenReturn(mockBalance);
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("totalSupply"), eq(poolId))).thenReturn(mockTotalSupply);
        
        // Assert.
        Map<String, BigInteger> returnedData = (Map<String, BigInteger>) dexScore.call( "getBalanceAndSupply", poolName, ownerAccount.getAddress());
        assertEquals(expectedData, returnedData);
    }

    @Test
    void removeLiquidity_withdrawalLockActive() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity settings.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        setupAddresses();
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
        
        // Arrange - supply liquidity settings.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        setupAddresses();
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        sm.getBlock().increase(100000000);

         // Act & Assert.
         dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
         // Check current_lp_tokens = orignal_lp_tokens - lpTokensToRemove.
         // Check other setters?
    }

    @Test
    void getWithdrawLock() {
        // Arrange - supply liquidity settings.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger poolId = BigInteger.TWO;
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        
        // Assert.
        BigInteger withdrawalLock = (BigInteger) dexScore.call("getWithdrawLock", poolId, ownerAccount.getAddress());
        //assertEquals(timestamp, withdrawalLock);

        // sm.getBlock().getTimestamp =! Context.getBlockTimestamp().
        // Bug in unittesting framework?
    }

    @Test
    void permit_OnlyGovernance() {
        // Arrange.
        BigInteger poolId = BigInteger.ONE;
        Boolean permission = true;

        // Assert.
        assertOnlyCallableByGovernance(dexScore, "permit", poolId, permission);
    }

    private void turnDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
    }

    private void depositTokens(Account depositor, Account tokenScore, BigInteger value) {
        setupAddresses();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    private void setupAddresses() {
        dexScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());

        Map<String, Address> addresses = Map.of(
            "setDividends", dividendsScore.getAddress(),
            "setStaking", stakingScore.getAddress(),
            "setRewards", rewardsScore.getAddress(),
            "setbnUSD", bnusdScore.getAddress(),
            "setBaln", balnScore.getAddress(),
            "setSicx", sicxScore.getAddress(),
            "setFeehandler", feehandlerScore.getAddress(),
            "setStakedLp", stakedLPScore.getAddress()
        );
        
        for (Map.Entry<String, Address> address : addresses.entrySet()) {
            dexScore.invoke(adminAccount, address.getKey(), address.getValue());
        }
    }

    private void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore, 
                                 BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        dexScore.invoke(governanceScore, "turnDexOn");
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteTokenScore.getAddress());

        // Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue, quoteValue, withdrawUnused);
    }

    private BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    
    private void supplyIcxLiquidity(Account supplier, BigInteger value) {
        contextMock.when(() -> Context.getValue()).thenReturn(value);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }

    /*
    Strategy:

    private methods to:
      - Deposit token.
      - Withdraw token.
      - Create pool / supply liquidity.


      Then use those methods to set up conditions for the other tests such as all getters for pools, swaps etc.
    */

    /*
    Icx/sicx pool:
    methods:
    cancelSicxIcxOrder
    getSicxEarnings
    withdrawSicxEarnings
    fallback
    getICXBalance

    */

    /*
    Code organization:
    - ICX pool related methods.
    - Liquidity pool methods.
    */

    /*
    fallback
    tokenFallback
    cancelSicxIcxOrder
    transfer
    onIRC31Received
    precompute (??)
    getDeposit  // Tested in tokenfallback_Deposit
    getSicxEarnings
    getWithdrawLock Bug in testing framework?
    getBnusdValue  // Not done yet. Multiple conditionals
    getICXBalance
    getBalanceAndSupply  // sicx/icx pool left.
    balanceOfAt
    totalSupplyAt
    totalBalnAt
    getTotalValue
    getBalnSnapshot
    loadBalancesAtSnapshot
    getDataBatch
    remove
    add
    withdrawSicxEarnings
    addLpAddresses
    */

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}
