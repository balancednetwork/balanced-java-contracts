package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class DexImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static Account ownerAccount = sm.createAccount();
    private static Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account nonGovernanceScore = Account.newScoreAccount(scoreCount++);
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
    void setPoolLPFee() {
        // Arrange.
        BigInteger fee = BigInteger.valueOf(100);
        BigInteger returnedFee;

        // Act.
        assertOnlyCallableByGovernance(dexScore, "setPoolLpFee", fee);
        dexScore.invoke(governanceScore, "setPoolLpFee", fee);
        returnedFee =  ((Map<String, BigInteger>) dexScore.call("getFees")).get("pool_lp_fee");

        // Assert.
        assertEquals(fee, returnedFee);
    }
}
