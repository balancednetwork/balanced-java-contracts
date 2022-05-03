package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;


import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;
import java.util.HashMap;

import static network.balanced.score.lib.test.UnitTest.*;

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
    private final Account feehandlerScore = Account.newScoreAccount(scoreCount++);
    private final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    private static Score dexScore;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    public void setup() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
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

        // Act & assert - retrieve all fees and assert they are equal to their expected value.
        returnedFees =  (Map<String, BigInteger>) dexScore.call("getFees");
        assertTrue(expectedResult.equals(returnedFees));
    }

    // setMarketName - no isolated getter.
    
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
        Boolean quoteCoinAllowed;

        // Act.
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteCoin);
        quoteCoinAllowed = (Boolean) dexScore.call("isQuoteCoinAllowed", quoteCoin);

        // Assert.
        assertEquals(true, quoteCoinAllowed);
        assertOnlyCallableByGovernance(dexScore, "addQuoteCoin", quoteCoin);
    }

    @Test
    void setGetTimeOffSet() {
        // Arrange.
        BigInteger timeOffset = BigInteger.valueOf(100);
        BigInteger retrievedTimeOffset;

        // Act.
        dexScore.invoke(governanceScore, "setTimeOffset", timeOffset);
        retrievedTimeOffset = (BigInteger) dexScore.call("getTimeOffset");

        // Assert.
        assertEquals(timeOffset, retrievedTimeOffset);
        assertOnlyCallableByGovernance(dexScore, "setTimeOffset", timeOffset);
    }

    // isLookingPool.

    @Test
    void setGetContinuousRewardsDay() {
        // Arrange.
        BigInteger continuousRewardsDay = BigInteger.valueOf(2);
        BigInteger retrievedContinuousRewardsDay;

        // Act.
        dexScore.invoke(governanceScore, "setContinuousRewardsDay", continuousRewardsDay);
        retrievedContinuousRewardsDay = (BigInteger) dexScore.call("getContinuousRewardsDay");

        // Assert.
        assertEquals(continuousRewardsDay, retrievedContinuousRewardsDay);
        assertOnlyCallableByGovernance(dexScore, "setContinuousRewardsDay", continuousRewardsDay);
    }

    // TODO: Set up a pool and test various sets and gets for that pool since they can't be tested in isolation.


    @Test
    void supplyLiquidity_newPoolCreated() {
        // Arrange - Tokenssupply information.
        Account supplier = sm.createAccount();
        Address baseToken = balnScore.getAddress();
        Address quoteToken = bnusdScore.getAddress();
        BigInteger baseValue = BigInteger.valueOf(10).pow(20);
        BigInteger quoteValue = BigInteger.valueOf(10).pow(20);
        Boolean withdrawUnused = false;
        
        // Arrange - configure dex settings.
        this.setupAddresses();
        dexScore.invoke(governanceScore, "turnDexOn");
        dexScore.invoke(governanceScore, "addQuoteCoin", bnusdScore.getAddress());

        // Arrange - Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act - deposit tokens and supply liquidity.
        dexScore.invoke(balnScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(bnusdScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseToken, quoteToken, baseValue, quoteValue, withdrawUnused);

        // Assert.
        // TODO. Test all getters for pools.

    }

    private void setupAddresses() {
        dexScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());

        Map<String, Address> addresses = Map.of(
            "setDividends", dividendsScore.getAddress(),
            "setStaking", stakingScore.getAddress(),
            "setRewards", rewardsScore.getAddress(),
            "setbnUSD", bnusdScore.getAddress(),
            "setBaln", balnScore.getAddress(),
            "setFeehandler", feehandlerScore.getAddress(),
            "setStakedLp", stakedLPScore.getAddress()
        );
        
        for (Map.Entry<String, Address> address : addresses.entrySet()) {
            dexScore.invoke(adminAccount, address.getKey(), address.getValue());
        }
    }

    @Test
    void tokenFallback_deposit() {
        // Arrange.
        Account tokenScoreCaller = balnScore;
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);
        BigInteger retrievedValue;
        
        setupAddresses();

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act.
        dexScore.invoke(tokenScoreCaller, "tokenFallback", supplier.getAddress(), value, tokenData("_deposit", new HashMap<>()));
        retrievedValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(), supplier.getAddress());

        // Assert.
        assertEquals(value, retrievedValue);


    }

    private void depositToken(Account depositor, Account tokenScore, BigInteger value) {
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    /*
    fallback
    tokenFallback
    cancelSicxIcxOrder
    transfer
    onIRC31Received
    precompute (??)
    getDeposit
    getSicxEarnings
    getWithdrawLock
    getPoolId
    getNonce
    getNamedPools
    lookupPid
    getPoolTotal
    totalSupply
    balanceOf
    getPoolBase
    getPoolQuote
    getQuotePriceInBase
    getBasePriceInQuote
    getPrice
    getBalnPrice
    getSicxBnusdPrice
    getBnusdValue
    getPriceByName
    getICXBalance
    getPoolName
    getPoolStats
    totalDexAddresses
    getBalanceAndSupply
    balanceOfAt
    totalSupplyAt
    totalBalnAt
    getTotalValue
    getBalnSnapshot
    loadBalancesAtSnapshot
    getDataBatch
    permit
    withdraw
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
