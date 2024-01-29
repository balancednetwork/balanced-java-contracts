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

package network.balanced.score.util.burner;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ICONBurnerTest extends UnitTest {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private MockBalanced mockBalanced;

    private Score burner;
    private ICONBurnerImpl spy;
    @BeforeEach
    void setup() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        burner = sm.deploy(owner, ICONBurnerImpl.class, mockBalanced.governance.getAddress());
        spy = (ICONBurnerImpl) spy(burner.getInstance());
        burner.setInstance(spy);
        doNothing().when(spy).callBurn(any());

    }

    @Test
    public void setSwapSlippage() {
        testOwnerControlMethods(burner, "setSwapSlippage", "getSwapSlippage", BigInteger.ONE);
    }

    @Test
    public void burn() {
        // Arrange
        BigInteger bnUSDBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sICXPriceInUSD = BigInteger.TEN.pow(17); // $0.1
        BigInteger sICXBalance = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger unstakedAmount = BigInteger.valueOf(500).multiply(EXA);

        // 1000 bnUSD should give 10000 sICX with default slippage of 1%
        // we should have a min recv of 9900 sICX
        BigInteger expectedMinRecv = BigInteger.valueOf(9900).multiply(EXA);

        JsonObject swapParams = Json.object()
                .add("toToken", mockBalanced.sicx.getAddress().toString())
                .add("minimumReceive", expectedMinRecv.toString());
        JsonObject swapData = Json.object()
                .add("method", "_swap")
                .add("params", swapParams);

        JsonObject unstakeData = Json.object()
                .add("method", "unstake");

        when(mockBalanced.bnUSD.mock.balanceOf(burner.getAddress())).thenReturn(bnUSDBalance);
        when(mockBalanced.sicx.mock.balanceOf(burner.getAddress())).thenReturn(sICXBalance);
        when(mockBalanced.balancedOracle.mock.getPriceInUSD("sICX")).thenReturn(sICXPriceInUSD);
        burner.getAccount().addBalance("ICX", unstakedAmount);

        // Act
        burner.invoke(owner, "burn");

        // Assert
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.dex.getAddress(), bnUSDBalance, swapData.toString().getBytes());
        verify(mockBalanced.sicx.mock).transfer(mockBalanced.staking.getAddress(), sICXBalance, unstakeData.toString().getBytes());
        verify(mockBalanced.staking.mock).claimUnstakedICX(burner.getAddress());
        verify(spy).callBurn(unstakedAmount);

        assertEquals(unstakedAmount, burner.call("getBurnedAmount"));
    }

    @Test
    public void manualSwap() {
        // Arrange
        BigInteger bnUSDBalance = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sICXPriceInUSD = BigInteger.TEN.pow(17); // $0.1
        // 1000 bnUSD should give 10000 sICX with default slippage of 1%
        // we should have a min recv of 9900 sICX
        BigInteger expectedMinRecv = BigInteger.valueOf(9900).multiply(EXA);

        JsonObject swapParams = Json.object()
                .add("toToken", mockBalanced.sicx.getAddress().toString())
                .add("minimumReceive", expectedMinRecv.toString());
        JsonObject swapData = Json.object()
                .add("method", "_swap")
                .add("params", swapParams);

        when(mockBalanced.bnUSD.mock.balanceOf(burner.getAddress())).thenReturn(bnUSDBalance);
        when(mockBalanced.balancedOracle.mock.getPriceInUSD("sICX")).thenReturn(sICXPriceInUSD);

        // Act
        burner.invoke(owner, "swapBnUSD", bnUSDBalance);

        // Assert
        verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.dex.getAddress(), bnUSDBalance, swapData.toString().getBytes());
    }
}