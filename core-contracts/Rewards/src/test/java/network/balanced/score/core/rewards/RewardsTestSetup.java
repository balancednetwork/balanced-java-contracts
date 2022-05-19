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

package network.balanced.score.core.rewards;

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import score.Address;

import java.math.BigInteger;
import java.security.AccessControlContext;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import network.balanced.score.lib.structs.DistributionPercentage;

public class RewardsTestSetup extends RewardsTestBase {

    protected final Account testAccount = Account.newScoreAccount(scoreCount++);
    
    @BeforeEach
    void setup() throws Exception {
        rewardsScore = sm.deploy(owner, RewardsImpl.class, governance.getAddress());
    }

    @Test
    void name() {
        assertEquals("Balanced Rewards", rewardsScore.call("name"));
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(rewardsScore, governance, admin);
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(rewardsScore, governance, owner);
    }

    @Test
    void setAndGetBwt() {
        testContractSettersAndGetters(rewardsScore, governance, admin, "setBwt",
        testAccount.getAddress(), "getBwt");
    }

    @Test
    void setAndGetBaln() {
        testContractSettersAndGetters(rewardsScore, governance, admin, "setBaln",
        testAccount.getAddress(), "getBaln");
    }

    @Test
    void setAndGetReserve() {
        testContractSettersAndGetters(rewardsScore, governance, admin, "setReserve",
        testAccount.getAddress(), "getReserve");
    }

    @Test
    void setAndGetDaofund() {
        testContractSettersAndGetters(rewardsScore, governance, admin, "setDaofund",
        testAccount.getAddress(), "getDaofund");
    }

    @Test
    void setAndGetStakedLp() {
        testContractSettersAndGetters(rewardsScore, governance, admin, "setStakedLp",
        testAccount.getAddress(), "getStakedLp");
    }

    @Test
    void setAndGetBatchSize() {
        int batchSize = 1;
        String expectedErrorMessage = "Authorization Check: Address not set";

        Executable setWithoutAdmin = () -> rewardsScore.invoke(admin, "setBatchSize", batchSize);
        expectErrorMessage(setWithoutAdmin, expectedErrorMessage);

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + admin.getAddress();
        Executable setNotFromAdmin = () -> rewardsScore.invoke(nonAdmin, "setBatchSize", batchSize);
        expectErrorMessage(setNotFromAdmin, expectedErrorMessage);

        rewardsScore.invoke(admin, "setBatchSize", batchSize);
        assertEquals(batchSize, rewardsScore.call("getBatchSize"));
    }

    @Test
    void setAndGetTimeOffset() {
        BigInteger timeOffset = BigInteger.ONE;
        String expectedErrorMessage = "Authorization Check: Address not set";

        Executable setWithoutAdmin = () -> rewardsScore.invoke(admin, "setTimeOffset", timeOffset);
        expectErrorMessage(setWithoutAdmin, expectedErrorMessage);

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + admin.getAddress();
        Executable setNotFromAdmin = () -> rewardsScore.invoke(nonAdmin, "setTimeOffset", timeOffset);
        expectErrorMessage(setNotFromAdmin, expectedErrorMessage);

        rewardsScore.invoke(admin, "setTimeOffset", timeOffset);
        assertEquals(timeOffset, rewardsScore.call("getTimeOffset"));
    }

    @Test
    void setAndGetContinuousRewards() {
        BigInteger continuousRewardsDay = BigInteger.ONE;
        String expectedErrorMessage = "Authorization Check: Address not set";

        Executable setWithoutAdmin = () -> rewardsScore.invoke(admin, "setContinuousRewardsDay", continuousRewardsDay);
        expectErrorMessage(setWithoutAdmin, expectedErrorMessage);

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + admin.getAddress();
        Executable setNotFromAdmin = () -> rewardsScore.invoke(nonAdmin, "setContinuousRewardsDay", continuousRewardsDay);
        expectErrorMessage(setNotFromAdmin, expectedErrorMessage);

        rewardsScore.invoke(admin, "setContinuousRewardsDay", continuousRewardsDay);
        assertEquals(continuousRewardsDay, rewardsScore.call("getContinuousRewardsDay"));
    }

    @Test
    void addAndRemoveDataProviders() {
        Address dataProvider1 = Account.newScoreAccount(scoreCount++).getAddress();
        Address dataProvider2 = Account.newScoreAccount(scoreCount++).getAddress();
        
        Account nonOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + nonOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable addFromNotOwner= () -> rewardsScore.invoke(nonOwner, "addDataProvider", dataProvider1);
        expectErrorMessage(addFromNotOwner, expectedErrorMessage);

        rewardsScore.invoke(owner, "addDataProvider", dataProvider1);
        rewardsScore.invoke(owner, "addDataProvider", dataProvider2);

        List<Address> dataProviders = (List<Address>) rewardsScore.call("getDataProviders");
        assertTrue(dataProviders.contains(dataProvider1));
        assertTrue(dataProviders.contains(dataProvider2));

        Executable removeFromNotOwner= () -> rewardsScore.invoke(nonOwner, "removeDataProvider", dataProvider1);
        expectErrorMessage(removeFromNotOwner, expectedErrorMessage);

        rewardsScore.invoke(owner, "removeDataProvider", dataProvider1);

        dataProviders = (List<Address>) rewardsScore.call("getDataProviders");
        assertFalse(dataProviders.contains(dataProvider1));
        assertTrue(dataProviders.contains(dataProvider2));
    }
}


