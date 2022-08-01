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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static network.balanced.score.core.rewards.utils.RewardsConstants.HUNDRED_PERCENTAGE;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

import static org.junit.jupiter.api.Assertions.*;

class RewardsTestSetup extends RewardsTestBase {

    private final Account testAccount = Account.newScoreAccount(scoreCount++);

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
    void setAndGetTimeOffset() {
        BigInteger timeOffset = BigInteger.ONE.multiply(MICRO_SECONDS_IN_A_DAY).negate();
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
    void setBoostWeight() {
        BigInteger weight = BigInteger.valueOf(50).multiply(HUNDRED_PERCENTAGE).divide(BigInteger.valueOf(100));
        BigInteger weightAbove = HUNDRED_PERCENTAGE.add(BigInteger.ONE);
        BigInteger weightBelow = HUNDRED_PERCENTAGE.divide(BigInteger.valueOf(100)).subtract(BigInteger.ONE);
        String expectedErrorMessage = "Authorization Check: Address not set";

        Executable setWithoutAdmin = () -> rewardsScore.invoke(admin, "setBoostWeight", weight);
        expectErrorMessage(setWithoutAdmin, expectedErrorMessage);

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());

        Account nonAdmin = sm.createAccount();
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() +
                " Authorized Caller: " + admin.getAddress();
        Executable setNotFromAdmin = () -> rewardsScore.invoke(nonAdmin, "setBoostWeight", weight);
        expectErrorMessage(setNotFromAdmin, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): Boost weight has to be above 1%";
        Executable setWeightAbove = () -> rewardsScore.invoke(admin, "setBoostWeight", weightBelow);
        expectErrorMessage(setWeightAbove, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): Boost weight has to be below 100%";
        Executable setWeightBelow = () -> rewardsScore.invoke(admin, "setBoostWeight", weightAbove);
        expectErrorMessage(setWeightBelow, expectedErrorMessage);

        rewardsScore.invoke(admin, "setBoostWeight", weight);
        assertEquals(weight, rewardsScore.call("getBoostWeight"));
    }

    @Test
    void addNewDataSource() {
        Account account = sm.createAccount();
        String name = "newLP";
       
        rewardsScore.invoke(account, "addNewDataSource", name, testAccount.getAddress(), testAccount.getAddress());

        String expectedErrorMessage = "Reverted(0): BalancedRewards: Data source does already exist";
        Executable duplicateName = () -> rewardsScore.invoke(account, "addNewDataSource", name, testAccount.getAddress(), testAccount.getAddress());
        expectErrorMessage(duplicateName, expectedErrorMessage);
    }
}


