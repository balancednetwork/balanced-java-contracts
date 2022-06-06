package network.balanced.score.core.dex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DexTestSetGetAddresses extends DexTestBase {
    
    @BeforeEach
    public void configureContract() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
    }

    @Test
    void setGetAdmin() {
        testAdmin(dexScore, governanceScore, governanceScore);
    }
    
    @Test
    void setGetGovernance() {
        testGovernance(dexScore, governanceScore, ownerAccount);
    }

    @Test
    void setGetSicx() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                 "setSicx", dexScore.getAddress(), "getSicx");
    }
    
    @Test
    void setGetDividends() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setDividends", dividendsScore.getAddress(), "getDividends");
    }

    @Test
    void setGetStaking() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setStaking", stakingScore.getAddress(), "getStaking");
    }

    @Test
    void setGetRewards() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setRewards", rewardsScore.getAddress(), "getRewards");
    }

    @Test
    void setGetBnusd() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setBnusd", bnusdScore.getAddress(), "getBnusd");
    }

    @Test
    void setGetBaln() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setBaln", balnScore.getAddress(), "getBaln");
    }

    @Test
    void setGetFeehandler() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setFeeHandler", feehandlerScore.getAddress(), "getFeeHandler");
    }

    @Test
    void setGetStakedLP() {
        testContractSettersAndGetters(dexScore, governanceScore, governanceScore,
                "setStakedLp", stakedLPScore.getAddress(), "getStakedLp");
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}