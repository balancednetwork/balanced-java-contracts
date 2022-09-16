/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.dex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
                "setFeehandler", feehandlerScore.getAddress(), "getFeehandler");
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