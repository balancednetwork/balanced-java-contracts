/*
 * Copyright (c) 2024-2024 Balanced.network.
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

package network.balanced.score.util.trickler;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.tokens.AssetToken;
import network.balanced.score.lib.interfaces.tokens.AssetTokenScoreInterface;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.test.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.assertOnlyCallableBy;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TricklerImplTest extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score trickler;
    private MockBalanced mockBalanced;
    private final Account savingsContract = sm.createAccount();

    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        trickler = sm.deploy(owner, TricklerImpl.class, mockBalanced.governance.getAddress());

        trickler.invoke(owner, "addAllowedToken", mockBalanced.bnUSD.getAddress());
        trickler.invoke(owner, "addAllowedToken", mockBalanced.sicx.getAddress());
    }

    @Test
    void name() {
        assertEquals("Balanced Trickler", trickler.call("name"));
    }

    @Test
    void version() {
        assertEquals("v1.0.0", trickler.call("version"));
    }

    @Test
    void setDistributionPeriod() {
        testOwnerControlMethods(trickler, "setDistributionPeriod", "getDistributionPeriod", BigInteger.ONE);
    }

    @Test
    void distribution_continuous() {
        // Arrange
        BigInteger initialBnUSDAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger initialSICXAmount = BigInteger.valueOf(20).multiply(EXA);
        BigInteger period = TricklerImpl.DEFAULT_DISTRIBUTION_PERIOD;

        BigInteger expectedBnUSDRate = initialBnUSDAmount.divide(period);
        BigInteger expectedSICXRate = initialSICXAmount.divide(period);

        BigInteger currentBlock = getHeight();
        when(mockBalanced.bnUSD.mock.balanceOf(trickler.getAddress())).thenReturn(initialBnUSDAmount);
        when(mockBalanced.sicx.mock.balanceOf(trickler.getAddress())).thenReturn(initialSICXAmount);
        trickler.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), initialBnUSDAmount, new byte[0]);
        trickler.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), initialSICXAmount, new byte[0]);

        BigInteger blockDiff = BigInteger.valueOf(100);
        sm.getBlock().increase(blockDiff.intValue());

        // Act
        BigInteger expectedBnUSDRewards = expectedBnUSDRate.multiply(blockDiff.add(BigInteger.TWO));
        BigInteger expectedSICXRewards = expectedSICXRate.multiply(blockDiff.add(BigInteger.TWO));

        BigInteger additionalBnUSDAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger additionalSICXAmount = BigInteger.valueOf(20).multiply(EXA);

        BigInteger newBnUSDBalance = initialBnUSDAmount.subtract(expectedBnUSDRewards).add(additionalBnUSDAmount);
        BigInteger newSICXBalance = initialSICXAmount.subtract(expectedSICXRewards).add(additionalSICXAmount);

        expectedBnUSDRate = newBnUSDBalance.divide(period);
        expectedSICXRate = newSICXBalance.divide(period);

        when(mockBalanced.bnUSD.mock.balanceOf(trickler.getAddress())).thenReturn(newBnUSDBalance);
        when(mockBalanced.sicx.mock.balanceOf(trickler.getAddress())).thenReturn(newSICXBalance);
        trickler.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), additionalBnUSDAmount, new byte[0]);
        trickler.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), additionalSICXAmount, new byte[0]);

        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.savings.getAddress(), expectedBnUSDRewards, new byte[0]);
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.savings.getAddress(), expectedSICXRewards, new byte[0]);

        // Assert
        sm.getBlock().increase(blockDiff.intValue());
        trickler.invoke(owner, "claimAllRewards");
        expectedBnUSDRewards = expectedBnUSDRate.multiply(blockDiff.add(BigInteger.TWO));
        expectedSICXRewards = expectedSICXRate.multiply(blockDiff.add(BigInteger.ONE));

        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.savings.getAddress(),expectedBnUSDRewards, new byte[0]);
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.savings.getAddress(), expectedSICXRewards, new byte[0]);
    }

    @Test
    void distribution_full() {
        // Arrange
        BigInteger initialBnUSDAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger initialSICXAmount = BigInteger.valueOf(20).multiply(EXA);
        BigInteger period = TricklerImpl.DEFAULT_DISTRIBUTION_PERIOD;

        BigInteger expectedBnUSDRate = initialBnUSDAmount.divide(period);
        BigInteger expectedSICXRate = initialSICXAmount.divide(period);

        // Act
        BigInteger currentBlock = getHeight();
        when(mockBalanced.bnUSD.mock.balanceOf(trickler.getAddress())).thenReturn(initialBnUSDAmount);
        when(mockBalanced.sicx.mock.balanceOf(trickler.getAddress())).thenReturn(initialSICXAmount);
        trickler.invoke(mockBalanced.bnUSD.account, "tokenFallback", mockBalanced.daofund.getAddress(), initialBnUSDAmount, new byte[0]);
        trickler.invoke(mockBalanced.sicx.account, "tokenFallback", mockBalanced.daofund.getAddress(), initialSICXAmount, new byte[0]);

        BigInteger blockDiff = BigInteger.valueOf(100);
        sm.getBlock().increase(blockDiff.intValue());

        // Assert
        trickler.invoke(owner, "claimAllRewards");
        BigInteger expectedBnUSDRewards = expectedBnUSDRate.multiply(blockDiff.add(BigInteger.TWO));
        BigInteger expectedSICXRewards = expectedSICXRate.multiply(blockDiff.add(BigInteger.ONE));
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.savings.getAddress(), expectedBnUSDRewards, new byte[0]);
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.savings.getAddress(), expectedSICXRewards, new byte[0]);

        when(mockBalanced.bnUSD.mock.balanceOf(trickler.getAddress())).thenReturn(initialBnUSDAmount.subtract(expectedBnUSDRewards));
        when(mockBalanced.sicx.mock.balanceOf(trickler.getAddress())).thenReturn(initialSICXAmount.subtract(expectedSICXRewards));

        // Act
        sm.getBlock().increase(period.intValue());
        trickler.invoke(owner, "claimAllRewards");
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.savings.getAddress(), initialBnUSDAmount.subtract(expectedBnUSDRewards), new byte[0]);
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.savings.getAddress(), initialSICXAmount.subtract(expectedSICXRewards), new byte[0]);
    }

    public BigInteger getHeight() {
        return BigInteger.valueOf(sm.getBlock().getHeight());
    }
}
