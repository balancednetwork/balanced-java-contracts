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

package network.balanced.score.core.liquidity;

import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.Account;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.*;


public class LiquidityTest extends TestBase {
    public static final ServiceManager sm = getServiceManager(); 
    public static final Account ownerAccount = sm.createAccount();
    public static final Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account nonGovernanceScore = Account.newScoreAccount(scoreCount++);
    private final Account dexScore = Account.newScoreAccount(scoreCount++);
    private final Account daofundScore = Account.newScoreAccount(scoreCount++);
    private final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    private Score liquidityScore;

    @BeforeEach
    public void setup() throws Exception {
        liquidityScore = sm.deploy(ownerAccount, LiquidityImpl.class, governanceScore.getAddress());
    }

    @Test
    void name() {
        String contractName = "Balanced Liquidity";
        assertEquals(contractName, liquidityScore.call("name"));
    }

    @Test
    void setGetGovernance() {
        testGovernance(liquidityScore, governanceScore, ownerAccount);
    }
    
    @Test
    void setGetAdmin() {
        testAdmin(liquidityScore, governanceScore, adminAccount);
    }

    @Test
    void setGetDex() {
        testContractSettersAndGetters(liquidityScore, governanceScore, adminAccount,
                 "setDex", dexScore.getAddress(), "getDex");
    }
    
    @Test
    void setGetDaofund() {
        testContractSettersAndGetters(liquidityScore, governanceScore, adminAccount,
                "setDaofund", daofundScore.getAddress(), "getDaofund");
    }

    @Test
    void setGetStakedLP() {
        testContractSettersAndGetters(liquidityScore, governanceScore, adminAccount,
                "setStakedLP", stakedLPScore.getAddress(), "getStakedLP");
    }

    @Test
    void setInitialWhitelistedPoolIds() {
        // Arrange.
        BigInteger[] initialPoolIds = {
            BigInteger.valueOf(2), 
            BigInteger.valueOf(3), 
            BigInteger.valueOf(4), 
            BigInteger.valueOf(10), 
            BigInteger.valueOf(5)
        };
        BigInteger[] retrievedIds;
        String expectedErrorMessage = "Reverted(0): Initial poolids have already been set.";

        // Act & Assert: set initial poolids.
        liquidityScore.invoke(ownerAccount, "setInitialWhitelistedPoolIds");
        retrievedIds = (BigInteger[]) liquidityScore.call("getWhitelistedPoolIds");
        assertArrayEquals(initialPoolIds, retrievedIds);

        // Act & Assert: Set initial poolids again.
        Executable setInitialPoolIds = () -> liquidityScore.invoke(ownerAccount, "setInitialWhitelistedPoolIds");
        expectErrorMessage(setInitialPoolIds, expectedErrorMessage);
    }

    @Test
    void addWhitelistedPoolIdsCallerNotGovernance() {
        // Arrange.
        BigInteger[] idsToAdd = {BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3)};
        Account caller = nonGovernanceScore;

        // Act.
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + caller.getAddress() + " Authorized Caller: " + governanceScore.getAddress();
        Executable addWhitelistedPoolIdsCallerNotGovernance = () -> liquidityScore.invoke(nonGovernanceScore, "addPoolIdsToWhitelist", (Object) idsToAdd);
        
        // Assert.
        expectErrorMessage(addWhitelistedPoolIdsCallerNotGovernance, expectedErrorMessage);
    }

    @Test
    void addRemoveAndGetWhitelistedPoolIds() {
        // Arrange.
        BigInteger[] idsToAdd = {BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3)};
        BigInteger[] idsToRemove = {BigInteger.valueOf(1), BigInteger.valueOf(2)};
        BigInteger[] idsAfterRemoveal = {BigInteger.valueOf(3)};
        BigInteger[] retrievedIds;

        // Act & Assert: Test add ids.
        liquidityScore.invoke(governanceScore, "addPoolIdsToWhitelist", (Object) idsToAdd);
        retrievedIds = (BigInteger[]) liquidityScore.call("getWhitelistedPoolIds");
        assertArrayEquals(idsToAdd, retrievedIds);
        
        // Act & Assert: Test remove ids.
        liquidityScore.invoke(governanceScore, "removePoolIdsFromWhitelist", (Object) idsToRemove);
        retrievedIds = (BigInteger[]) liquidityScore.call("getWhitelistedPoolIds");
        assertArrayEquals(idsAfterRemoveal, retrievedIds);
    }

    @Test
    void withdrawLiquidityCallerNotGovernance() {
        // Arrange.
        BigInteger poolID = BigInteger.valueOf(1);
        BigInteger lptokens = BigInteger.valueOf(100);
        boolean withdrawToDaofund = true;
        Account caller = nonGovernanceScore;

        liquidityScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        liquidityScore.invoke(adminAccount, "setDex", dexScore.getAddress());

        // Act & assert.
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + caller.getAddress() + " Authorized Caller: " + governanceScore.getAddress();
        Executable withdrawLiquidityNotFromGovernance = () -> liquidityScore.invoke(nonGovernanceScore, "withdrawLiquidity", poolID, lptokens, withdrawToDaofund);
        expectErrorMessage(withdrawLiquidityNotFromGovernance, expectedErrorMessage);
    }

    @Test
    void unstakeLPCallerNotGovernance() {
        // Arrange.
        BigInteger lptokenId = BigInteger.valueOf(1);
        BigInteger lptokens = BigInteger.valueOf(100);
        Account caller = nonGovernanceScore;

        liquidityScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        liquidityScore.invoke(adminAccount, "setStakedLP", stakedLPScore.getAddress());
    
        // Act.
        Executable withdrawLiquidityNotFromGovernance = () -> liquidityScore.invoke(nonGovernanceScore, "unstakeLPTokens", lptokenId, lptokens);
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + caller.getAddress() + " Authorized Caller: " + governanceScore.getAddress();

        // Assert.
        expectErrorMessage(withdrawLiquidityNotFromGovernance, expectedErrorMessage);
    }
}
