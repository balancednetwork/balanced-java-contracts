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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static network.balanced.score.lib.test.UnitTest.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;

import java.math.BigInteger;

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

    private Score liquidityScore;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);
    
    @BeforeEach
    public void setup() throws Exception {
        liquidityScore = sm.deploy(ownerAccount, Liquidity.class, governanceScore.getAddress());
        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("remove"),
                any(BigInteger.class), any(BigInteger.class), any(boolean.class))).thenReturn(null);
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
                "setStakedLP", daofundScore.getAddress(), "getStakedLP");
    }

    //@Test
    //void testAccessToWithdrawLiquidity() {
    //    BigInteger poolID = BigInteger.valueOf(1);
    //    BigInteger lptokens = BigInteger.valueOf(100);
    //    boolean withdrawToDaofund = false;
//
    //    liquidityScore.invoke(governanceScore, "withdrawLiquidity", poolID, lptokens, withdrawToDaofund);
    //    contextMock.verify(() -> Context.call(eq(dexScore.getAddress()), eq("remove"),
    //    any(BigInteger.class), any(BigInteger.class), any(boolean.class)), never());
    //}

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}
