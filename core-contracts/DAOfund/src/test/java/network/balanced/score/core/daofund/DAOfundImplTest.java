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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.daofund.DAOfundImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    @DisplayName("Allocate the tokens for disbursement")
    void disburseTokens() {
        // Arrange
        when(mockBalanced.sicx.mock.balanceOf(daofundScore.getAddress())).thenReturn(amount.subtract(BigInteger.ONE));

        // Act & Assert
        String expectedErrorMessage =
                "Reverted(0): " + TAG + ": Insufficient balance of asset " + mockBalanced.sicx.getAddress().toString() +
                        " in DAOfund";
        Executable disburseInsufficientFund = () -> daofundScore.invoke(mockBalanced.governance.account, "disburse",
                mockBalanced.sicx.getAddress(), receiver.getAddress(), amount, new byte[0]);
        expectErrorMessage(disburseInsufficientFund, expectedErrorMessage);

        // Act
        when(mockBalanced.sicx.mock.balanceOf(daofundScore.getAddress())).thenReturn(amount);
        daofundScore.invoke(mockBalanced.governance.account, "disburse", mockBalanced.sicx.getAddress(),
                receiver.getAddress(), amount, new byte[1]);

        // Assert
        verify(mockBalanced.sicx.mock).transfer(receiver.getAddress(), amount, new byte[1]);
    }

    @Test
    void claimRewards_noLock() {
        // Arrange
        BigInteger expectedBalnRewards = BigInteger.TEN;
        String[] sources = {"sICX/bnUSD", "BALN/bnUSD"};
        when(mockBalanced.rewards.mock.getUserSources(daofundScore.getAddress().toString())).thenReturn(sources);
        when(mockBalanced.bBaln.mock.hasLocked(daofundScore.getAddress())).thenReturn(false);
        doAnswer(invocation -> {
            daofundScore.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.rewards.getAddress(),
                    expectedBalnRewards, new byte[0]);
            return null;
        }).when(mockBalanced.rewards.mock).claimRewards(sources);

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimRewards");

        // Assert
        verify(mockBalanced.baln.mock).transfer(eq(mockBalanced.bBaln.getAddress()), eq(expectedBalnRewards),
                any(byte[].class));
        BigInteger balnRewards = (BigInteger) daofundScore.call("getBalnEarnings");
        assertEquals(expectedBalnRewards, balnRewards);
    }

    @Test
    void claimRewards_hasLock() {
        // Arrange
        BigInteger expectedBalnRewards = BigInteger.TEN;
        String[] sources = {"sICX/bnUSD", "BALN/bnUSD"};
        when(mockBalanced.rewards.mock.getUserSources(daofundScore.getAddress().toString())).thenReturn(sources);
        when(mockBalanced.bBaln.mock.hasLocked(daofundScore.getAddress())).thenReturn(true);
        doAnswer(invocation -> {
            daofundScore.invoke(mockBalanced.baln.account, "tokenFallback", mockBalanced.rewards.getAddress(),
                    expectedBalnRewards, new byte[0]);
            return null;
        }).when(mockBalanced.rewards.mock).claimRewards(sources);

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimRewards");

        // Assert
        verify(mockBalanced.baln.mock).transfer(eq(mockBalanced.bBaln.getAddress()), eq(expectedBalnRewards),
                any(byte[].class));
        BigInteger balnRewards = (BigInteger) daofundScore.call("getBalnEarnings");
        assertEquals(expectedBalnRewards, balnRewards);
    }


    @Test
    @SuppressWarnings("unchecked")
    void claimNetworkFees() {
        // Arrange
        BigInteger bnUSDFees = BigInteger.TEN;
        BigInteger sICXFees = BigInteger.TWO;

        doAnswer(invocation -> {
            daofundScore.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.dividends.getAddress(),
                    sICXFees, new byte[0]);
            daofundScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.dividends.getAddress(),
                    bnUSDFees, new byte[0]);
            return null;
        }).when(mockBalanced.dividends.mock).claimDividends();

        // Act
        daofundScore.invoke(mockBalanced.governance.account, "claimNetworkFees");

        // Assert
        Map<String, BigInteger> fees = (Map<String, BigInteger>) daofundScore.call("getFeeEarnings");
        assertEquals(sICXFees, fees.get(mockBalanced.sicx.getAddress().toString()));
        assertEquals(bnUSDFees, fees.get(mockBalanced.bnUSD.getAddress().toString()));
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
        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(quoteAmount.multiply(EXA).divide(baseAmount));
        when(mockBalanced.dex.mock.getPoolId(baseToken, quoteToken)).thenReturn(pid);
        when(mockBalanced.dex.mock.balanceOf(daofundScore.getAddress(), pid)).thenReturn(lpBalance);

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "supplyLiquidity", baseToken,
                baseAmount, quoteToken, quoteAmount, BigInteger.valueOf(100));
        daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity", baseToken, baseAmount, quoteToken,
                quoteAmount, BigInteger.valueOf(100));

        // Assert
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.dex.getAddress(), baseAmount, tokenDepositData);
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.dex.getAddress(), quoteAmount, tokenDepositData);

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "stakeLpTokens", pid, lpBalance);
        daofundScore.invoke(mockBalanced.governance.account, "stakeLpTokens", pid, lpBalance);

        verify(mockBalanced.dex.mock).add(baseToken, quoteToken, baseAmount, quoteAmount, true, BigInteger.valueOf(100));
        verify(mockBalanced.dex.mock).transfer(mockBalanced.stakedLp.getAddress(), lpBalance, pid, new byte[0]);
    }

    @Test
    void supplyLiquidity_ToLargePriceChange() {
        // Arrange
        Address baseToken = mockBalanced.sicx.getAddress();
        BigInteger baseAmount = EXA;

        Address quoteToken = mockBalanced.bnUSD.getAddress();
        BigInteger quoteAmount = EXA.multiply(BigInteger.TWO);

        BigInteger pid = BigInteger.TWO;
        BigInteger lpBalance = BigInteger.TEN;
        when(mockBalanced.dex.mock.getPoolId(baseToken, quoteToken)).thenReturn(pid);
        when(mockBalanced.dex.mock.balanceOf(daofundScore.getAddress(), pid)).thenReturn(lpBalance);

        BigInteger price = quoteAmount.multiply(EXA).divide(baseAmount);
        BigInteger priceChangeThreshold = (BigInteger) daofundScore.call("getPOLSupplySlippage");
        BigInteger maxDiff = price.multiply(priceChangeThreshold).divide(POINTS);

        // Act & Assert
        String expectedErrorMessage = "Price on dex was above allowed threshold";
        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(price.add(maxDiff));
        Executable aboveThreshold = () -> daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity",
                baseToken, baseAmount, quoteToken, quoteAmount, BigInteger.valueOf(100));
        expectErrorMessage(aboveThreshold, expectedErrorMessage);

        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(price.add(maxDiff).subtract(BigInteger.ONE));
        daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity", baseToken, baseAmount, quoteToken,
                quoteAmount, BigInteger.valueOf(100));

        // Act & Assert
        expectedErrorMessage = "Price on dex was below allowed threshold";
        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(price.subtract(maxDiff));
        Executable belowThreshold = () -> daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity",
                baseToken, baseAmount, quoteToken, quoteAmount, BigInteger.valueOf(100));
        expectErrorMessage(belowThreshold, expectedErrorMessage);

        when(mockBalanced.dex.mock.getPrice(pid)).thenReturn(price.subtract(maxDiff).add(BigInteger.ONE));
        daofundScore.invoke(mockBalanced.governance.account, "supplyLiquidity", baseToken, baseAmount, quoteToken,
                quoteAmount, BigInteger.valueOf(100));
    }

    @Test
    void unstakeLpTokens() {
        // Arrange
        BigInteger pid = BigInteger.TWO;
        BigInteger lpBalance = BigInteger.TEN;

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "unstakeLpTokens", pid, lpBalance);
        daofundScore.invoke(mockBalanced.governance.account, "unstakeLpTokens", pid, lpBalance);

        // Assert
        verify(mockBalanced.stakedLp.mock).unstake(pid, lpBalance);
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
        verify(mockBalanced.dex.mock).remove(pid, lpBalance, true);
    }

    @Test
    void stakeLP() {
        // Arrange
        BigInteger pid = BigInteger.TWO;
        BigInteger lpBalance = BigInteger.TEN;
        when(mockBalanced.dex.mock.balanceOf(daofundScore.getAddress(), pid)).thenReturn(lpBalance);

        // Act
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "stakeLpTokens", pid, lpBalance);
        daofundScore.invoke(mockBalanced.governance.account, "stakeLpTokens", pid, lpBalance);

        // Assert
        verify(mockBalanced.dex.mock).transfer(mockBalanced.stakedLp.getAddress(), lpBalance, pid, new byte[0]);
    }

    @Test
    void setPOLSupplySlippage() {
        BigInteger slippage = BigInteger.valueOf(700);

        assertOnlyCallableBy(mockBalanced.governance.getAddress(), daofundScore, "setPOLSupplySlippage", slippage);
        daofundScore.invoke(mockBalanced.governance.account, "setPOLSupplySlippage", slippage);

        assertEquals(slippage, daofundScore.call("getPOLSupplySlippage"));
    }

}