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

import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.ReflectionMemberAccessor;

import net.bytebuddy.implementation.bytecode.collection.ArrayAccess;
import network.balanced.score.lib.interfaces.addresses.GovernanceAddress;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.function.Executable;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;



import score.Context;


public class LiquidityTest extends TestBase {
    public static final ServiceManager sm = getServiceManager(); 
    public static final Account ownerAccount = sm.createAccount();
    public static final Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account nonGovernanceScore = Account.newScoreAccount(scoreCount++);
    private final Account dexScore = Account.newScoreAccount(scoreCount++);
    private final Account daofundScore = Account.newScoreAccount(scoreCount++);
    private final Account stakingScore = Account.newScoreAccount(scoreCount++);

    private Score liquidityScore;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);
    private final MockedStatic<Liquidity> liquidityMock = Mockito.mockStatic(Liquidity.class, Mockito.CALLS_REAL_METHODS);
    
    @BeforeEach
    public void setup() throws Exception {
        liquidityScore = sm.deploy(ownerAccount, Liquidity.class, governanceScore.getAddress());
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
    void setGetStaking() {
        testContractSettersAndGetters(liquidityScore, governanceScore, adminAccount,
                "setStaking", stakingScore.getAddress(), "getStaking");
    }

    @Test
    void addAndRemoveWhitelistedPoolIds() {
        // Arrange.
        BigInteger[] initialIds = (BigInteger[]) liquidityScore.call("getWhitelistedPoolIds");
        liquidityScore.invoke(governanceScore, "removePoolsFromWhitelist", initialIds);
        BigInteger[] idsToAdd = {BigInteger.valueOf(1), BigInteger.valueOf(2)};
        BigInteger[] retrievedIds;
        //liquidityScore.invoke(from, method, params);
        // Act and assert.
        liquidityScore.invoke(governanceScore, "addPoolsToWhitelist", (Object) idsToAdd);
        retrievedIds = (BigInteger[]) liquidityScore.call("getWhitelistedPoolIds");
        System.out.println(retrievedIds);
        System.out.println("HEJSAN");
        System.out.flush();
        assertEquals(idsToAdd, idsToAdd);
        //liquidityScore.invoke(governanceScore, "removePoolsFromWhitelist", Arrays.asList(a));
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
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("remove"),
                any(BigInteger.class), any(BigInteger.class), eq(true))).thenReturn(null);

        
        // Act & assert.
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + caller.getAddress() + " Authorized Caller: " + governanceScore.getAddress();
        Executable withdrawLiquidityNotFromGovernance = () -> liquidityScore.invoke(nonGovernanceScore, "withdrawLiquidity", poolID, lptokens, withdrawToDaofund);
        expectErrorMessage(withdrawLiquidityNotFromGovernance, expectedErrorMessage);
    }

    @Test
    void unstakeLPCallerNotGovernance() {
        // Arrange.
        BigInteger poolID = BigInteger.valueOf(1);
        BigInteger lptokens = BigInteger.valueOf(100);
        boolean withdrawToDaofund = true;
        Account caller = nonGovernanceScore;
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("stake"),
                any(BigInteger.class), any(BigInteger.class), eq(true))).thenReturn(null);
        liquidityScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        liquidityScore.invoke(adminAccount, "setStaking", stakingScore.getAddress());
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("remove"),
                any(BigInteger.class), any(BigInteger.class), eq(true))).thenReturn(null);

        
        // Act & assert.
        String expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + caller.getAddress() + " Authorized Caller: " + governanceScore.getAddress();
        Executable withdrawLiquidityNotFromGovernance = () -> liquidityScore.invoke(nonGovernanceScore, "withdrawLiquidity", poolID, lptokens, withdrawToDaofund);
        expectErrorMessage(withdrawLiquidityNotFromGovernance, expectedErrorMessage);
    }



    @AfterEach
    void closeMock() {
        contextMock.close();
        liquidityMock.close();
    }
}
