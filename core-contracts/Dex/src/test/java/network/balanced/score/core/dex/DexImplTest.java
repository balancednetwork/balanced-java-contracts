package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

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


    // TODO: Set up a pool and test various sets and gets for that pool since they can't be tested in isolation?

    @Test
    void createNewPool() {
        
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
}
