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

package network.balanced.score.core.daofund;

import static network.balanced.score.core.daofund.DAOfundImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.mock.MockBalanced;
import score.Address;
import score.Context;

class DAOfundImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account receiver = sm.createAccount();
    private static final Account prep_address = sm.createAccount();

    private MockBalanced mockBalanced;

    private Score daofundScore;
    private DAOfundImpl daofundSpy;

    private final BigInteger amount = new BigInteger("54321");

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        daofundScore = sm.deploy(owner, DAOfundImpl.class, mockBalanced.governance.getAddress());

        daofundSpy = (DAOfundImpl) spy(daofundScore.getInstance());
        daofundScore.setInstance(daofundSpy);
    }

    @Test
    void name() {
        assertEquals("Balanced DAOfund", daofundScore.call("name"));
    }

    @Test
    void delegate() {
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};


        daofundScore.invoke(mockBalanced.governance.account, "delegate", (Object) preps);

        verify(mockBalanced.staking.mock).delegate(any());
    }

    @Test
    void addSymbolToSetdb() {
        try (MockedStatic<Context> loansMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            loansMock
                    .when(() -> Context.call(mockBalanced.loans.getAddress(), "getAssetTokens"))
                    .thenReturn(Map.of("sICX", mockBalanced.sicx.getAddress().toString(),
                            "BALN", mockBalanced.baln.getAddress().toString(),
                            "bnUSD", mockBalanced.bnUSD.getAddress().toString()));
            daofundScore.invoke(owner, "addAddressToSetdb");
            Map<String, BigInteger> expectedBalances = Map.of(mockBalanced.sicx.getAddress().toString(), BigInteger.ZERO,
                mockBalanced.baln.getAddress().toString(), BigInteger.ZERO,
                mockBalanced.bnUSD.getAddress().toString(), BigInteger.ZERO);
            assertEquals(expectedBalances, daofundScore.call("getBalances"));
        }
    }

    @Test
    void receiveTokens() {
        addSymbolToSetdb();

        Executable depositFromNonAllowedToken = () -> daofundScore.invoke(owner, "tokenFallback", owner.getAddress(),
                BigInteger.TEN.pow(20), new byte[0]);
        expectErrorMessage(depositFromNonAllowedToken, "Reverted(0): " + TAG + ": Daofund can't receive this token");

        daofundScore.invoke(mockBalanced.sicx.account, "tokenFallback", owner.getAddress(), BigInteger.TEN.pow(20), new byte[0]);
        Map<String, BigInteger> expectedBalances = Map.of(mockBalanced.sicx.getAddress().toString(), BigInteger.TEN.pow(20),
                mockBalanced.baln.getAddress().toString(), BigInteger.ZERO,
                mockBalanced.bnUSD.getAddress().toString(), BigInteger.ZERO);
        assertEquals(expectedBalances, daofundScore.call("getBalances"));
    }

    @Test
    @DisplayName("Allocate the tokens for disbursement")
    void disburseTokens() {
        receiveTokens();

        Disbursement disbursement = new Disbursement();
        disbursement.address = mockBalanced.sicx.getAddress();
        disbursement.amount = amount;
        Disbursement[] amounts = new Disbursement[]{disbursement};
        daofundScore.invoke(mockBalanced.governance.account, "disburse", receiver.getAddress(), amounts);

        Map<String, Object> expectedDisbursement = Map.of("user", receiver.getAddress(),
                "claimableTokens", Map.of(mockBalanced.sicx.getAddress().toString(), amount,
                        mockBalanced.baln.getAddress().toString(), BigInteger.ZERO,
                        mockBalanced.bnUSD.getAddress().toString(), BigInteger.ZERO));
        assertEquals(expectedDisbursement, daofundScore.call("getDisbursementDetail", receiver.getAddress()));

        disbursement.address = mockBalanced.baln.getAddress();
        Executable disburseInsufficientFund = () -> daofundScore.invoke(mockBalanced.governance.account, "disburse",
                receiver.getAddress(), new Disbursement[]{disbursement});
        expectErrorMessage(disburseInsufficientFund,
                "Reverted(0): " + TAG + ": Insufficient balance of asset " + mockBalanced.baln.getAddress().toString() + " in" +
                        " DAOfund");
    }

    @Test
    void claimTokens() {
        disburseTokens();

        try (MockedStatic<Context> tokenMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            tokenMock
                    .when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                            any(BigInteger.class), any(byte[].class)))
                    .thenReturn(null);

            daofundScore.invoke(receiver, "claim");
            verify(daofundSpy).TokenTransfer(receiver.getAddress(), amount,
                    "Balanced DAOfund disbursement " + amount + " sent to " + receiver.getAddress());
        }

        Map<String, Object> expectedDisbursement = Map.of("user", receiver.getAddress(),
                "claimableTokens", Map.of(mockBalanced.sicx.getAddress().toString(), BigInteger.ZERO,
                        mockBalanced.baln.getAddress().toString(), BigInteger.ZERO,
                        mockBalanced.bnUSD.getAddress().toString(), BigInteger.ZERO));
        assertEquals(expectedDisbursement, daofundScore.call("getDisbursementDetail", receiver.getAddress()));
    }

    // @External
    // public void claimRewards() {
    //     POLManager.claimRewards();
    // }
        //     String data;
        // boolean hasLocked = Context.call(Boolean.class, getBoostedBaln(), "hasLocked", Context.getAddress());
        // if (!hasLocked) {
        //     data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        // } else {
        //     data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        // }

        // Context.call(Context.getCaller(), "transfer", getBoostedBaln(), amount, data.getBytes());
    @Test
    void claimRewards_noLock() {
        // Arrange
        BigInteger expectedBalnRewards = BigInteger.TEN;
        String[] sources = {"sICX/bnUSD", "BALN/bnUSD"};
        when(mockBalanced.rewards.mock.getUserSources(daofundScore.getAddress())).thenReturn(sources);
        when(mockBalanced.bBaln.mock.hasLocked(daofundScore.getAddress())).thenReturn(false);
        doAnswer(invocation -> {
                daofundScore.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.rewards.getAddress(), expectedBalnRewards, new byte[0]);
                return null;
        }).when(mockBalanced.rewards.mock).claimRewards(sources);

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimRewards");

        // Assert
        verify(mockBalanced.baln.mock).transfer(eq(mockBalanced.bBaln.getAddress()), eq(expectedBalnRewards), any(byte[].class));
        BigInteger balnRewards  = (BigInteger) daofundScore.call("getBalnEarnings");
        assertEquals(expectedBalnRewards, balnRewards);
    }

    @Test
    void claimRewards_hasLock() {
        // Arrange
        BigInteger expectedBalnRewards = BigInteger.TEN;
        String[] sources = {"sICX/bnUSD", "BALN/bnUSD"};
        when(mockBalanced.rewards.mock.getUserSources(daofundScore.getAddress())).thenReturn(sources);
        when(mockBalanced.bBaln.mock.hasLocked(daofundScore.getAddress())).thenReturn(true);
        doAnswer(invocation -> {
                daofundScore.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.rewards.getAddress(), expectedBalnRewards, new byte[0]);
                return null;
        }).when(mockBalanced.rewards.mock).claimRewards(sources);

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimRewards");

        // Assert
        verify(mockBalanced.baln.mock).transfer(eq(mockBalanced.bBaln.getAddress()), eq(expectedBalnRewards), any(byte[].class));
        BigInteger balnRewards  = (BigInteger) daofundScore.call("getBalnEarnings");
        assertEquals(expectedBalnRewards, balnRewards);
    }


    @Test
    @SuppressWarnings("unchecked")
    void claimNetworkFees() {
        // Arrange
        BigInteger bnUSDFees = BigInteger.TEN;
        BigInteger sICXFees = BigInteger.TWO;

        doAnswer(invocation -> {
                daofundScore.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.dividends.getAddress(), sICXFees, new byte[0]);
                daofundScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.dividends.getAddress(), bnUSDFees, new byte[0]);
                return null;
        }).when(mockBalanced.dividends.mock).claimDividends();

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimNetworkFees");

        // Assert
        Map<Address, BigInteger> fees = (Map<Address, BigInteger>) daofundScore.call("getFeeEarnings");
        assertEquals(sICXFees, fees.get(mockBalanced.sicx.getAddress()));
        assertEquals(bnUSDFees, fees.get(mockBalanced.bnUSD.getAddress()));
    }

    @Test
    void supplyLiquidity() {
        // Arrange
        byte[] tokenDepositData = "{\"method\":\"_deposit\"}".getBytes();
        Address baseToken = mockBalanced.sicx.getAddress();
        BigInteger baseAmount = EXA;

        Address quoteToken = mockBalanced.bnUSD.getAddress();
        BigInteger quoteAmount = EXA.multiply(BigInteger.TWO);

        BigInteger pid = BigInteger.TWO;
        BigInteger lpBalance = BigInteger.TEN;
        when(mockBalanced.dex.mock.getPoolId(baseToken, quoteToken)).thenReturn(pid);
        when(mockBalanced.dex.mock.balanceOf(daofundScore.getAddress(), pid)).thenReturn(lpBalance);

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "supplyLiquidity", baseToken, baseAmount, quoteToken, quoteAmount);
        daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity", baseToken, baseAmount, quoteToken, quoteAmount);

        // Assert
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.dex.getAddress(), baseAmount, tokenDepositData);
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.dex.getAddress(), quoteAmount, tokenDepositData);
        verify(mockBalanced.dex.mock).add(baseToken, quoteToken, baseAmount, quoteAmount, true);
        verify(mockBalanced.dex.mock).transfer(mockBalanced.stakedLp.getAddress(), lpBalance, pid, new byte[0]);
    }

    @Test
    void withdrawLiquidity() {
        // Arrange
        BigInteger pid = BigInteger.TWO;
        BigInteger lpBalance = BigInteger.TEN;

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "withdrawLiquidity", pid, lpBalance);
        daofundScore.invoke(mockBalanced.governance.account, "withdrawLiquidity", pid, lpBalance);

        // Assert
        verify(mockBalanced.stakedLp.mock).unstake(pid, lpBalance);
        verify(mockBalanced.dex.mock).remove(pid, lpBalance, true);
    }
}