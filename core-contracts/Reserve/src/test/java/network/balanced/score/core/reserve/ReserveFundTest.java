/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.core.reserve;

import com.iconloop.score.test.Account;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.test.mock.MockContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.core.reserve.ReserveFund.TAG;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


public class ReserveFundTest extends ReserveFundTestBase {

    private void setupCollaterals() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("iETH", ieth.getAddress().toString());
        tokens.put("sICX", sicx.getAddress().toString());


        when(loans.mock.getCollateralTokens()).thenReturn(tokens);
    }

    private void setBalance(MockContract<? extends IRC2Mintable> token, BigInteger amount) {
        when(token.mock.balanceOf(reserve.getAddress())).thenReturn(amount);
    }

    private void setRate(String symbol, BigInteger rateInUSD) {
        when(balancedOracle.mock.getPriceInUSD(symbol)).thenReturn(rateInUSD);
    }

    @BeforeEach
    public void setupContract() throws Exception {
        super.setup();
        setupCollaterals();
    }

    @Test
    void name() {
        String contractName = "Balanced Reserve Fund";
        assertEquals(contractName, reserve.call("name"));
    }

    @Test
    void redeem_sicxOnly() {
        // Arrange
        BigInteger dollarValueToRedeem = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sicxRate = BigInteger.TWO.multiply(EXA);
        BigInteger expectedSicxSent = dollarValueToRedeem.multiply(EXA).divide(sicxRate);
        BigInteger sicxBalance = BigInteger.valueOf(1200).multiply(EXA);
        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        // Act
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), dollarValueToRedeem, "sICX");

        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), expectedSicxSent, new byte[0]);
    }

    @Test
    void redeem_multiCollateral() {
        // Arrange
        BigInteger dollarValueToRedeem = BigInteger.valueOf(1400).multiply(EXA);
        BigInteger sicxRate = BigInteger.TWO.multiply(EXA);
        BigInteger iethRate = BigInteger.valueOf(300).multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(400).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(3).multiply(iETHDecimals);

        BigInteger valueRemaining = dollarValueToRedeem.subtract(iethBalance.multiply(iethRate).divide(iETHDecimals));
        BigInteger expectedSICXSent = valueRemaining.multiply(EXA).divide(sicxRate);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        // Act
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), dollarValueToRedeem, "iETH");

        // Assert
        verify(ieth.mock).transfer(redeemer.getAddress(), iethBalance, new byte[0]);
        verify(sicx.mock).transfer(redeemer.getAddress(), expectedSICXSent, new byte[0]);
    }

    @Test
    void redeem_useBaln_iETH() {
        // Arrange
        BigInteger dollarValueToRedeem = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger sicxRate = EXA;
        BigInteger iethRate = BigInteger.valueOf(500).multiply(EXA);
        BigInteger balnRate = BigInteger.TWO.multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(500).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(1).multiply(iETHDecimals);
        BigInteger balnBalance = BigInteger.valueOf(1000).multiply(EXA);

        BigInteger valueRemaining = dollarValueToRedeem.subtract(iethBalance.multiply(iethRate).divide(iETHDecimals));
        valueRemaining = valueRemaining.subtract(sicxBalance.multiply(sicxRate).divide(EXA));
        BigInteger expectedBalnSent = valueRemaining.multiply(EXA).divide(balnRate);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        setBalance(baln, balnBalance);
        setRate("BALN", balnRate);

        // Act
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), dollarValueToRedeem, "iETH");

        // Assert
        verify(ieth.mock).transfer(redeemer.getAddress(), iethBalance, new byte[0]);
        verify(sicx.mock).transfer(redeemer.getAddress(), sicxBalance, new byte[0]);
        verify(baln.mock).transfer(redeemer.getAddress(), expectedBalnSent, new byte[0]);
    }

    @Test
    void redeem_useBaln_sICX() {
        // Arrange
        BigInteger dollarValueToRedeem = BigInteger.valueOf(1500).multiply(EXA);
        BigInteger sicxRate = EXA;
        BigInteger iethRate = BigInteger.valueOf(500).multiply(EXA);
        BigInteger balnRate = BigInteger.TWO.multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(500).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(1).multiply(EXA);
        BigInteger balnBalance = BigInteger.valueOf(1000).multiply(EXA);

        BigInteger valueRemaining = dollarValueToRedeem.subtract(sicxBalance.multiply(sicxRate).divide(EXA));
        BigInteger expectedBalnSent = valueRemaining.multiply(EXA).divide(balnRate);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        setBalance(baln, balnBalance);
        setRate("BALN", balnRate);

        // Act
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), dollarValueToRedeem, "sICX");

        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), sicxBalance, new byte[0]);
        verify(baln.mock).transfer(redeemer.getAddress(), expectedBalnSent, new byte[0]);
    }

    @Test
    void redeem_notEnoughBalance() {
        // Arrange
        BigInteger dollarValueToRedeem = BigInteger.valueOf(5000).multiply(EXA);
        BigInteger sicxRate = EXA;
        BigInteger iethRate = BigInteger.valueOf(500).multiply(EXA);
        BigInteger balnRate = BigInteger.TWO.multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(500).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(1).multiply(iETHDecimals);
        BigInteger balnBalance = BigInteger.valueOf(1000).multiply(EXA);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        setBalance(baln, balnBalance);
        setRate("BALN", balnRate);

        // Act
        String expectedErrorMessage = TAG + ": Unable to process request at this time.";
        Executable withToHighValue = () -> reserve.invoke(loans.account, "redeem", redeemer.getAddress(),
                dollarValueToRedeem, "iETH");
        expectErrorMessage(withToHighValue, expectedErrorMessage);

        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), sicxBalance, new byte[0]);
        verify(ieth.mock).transfer(redeemer.getAddress(), iethBalance, new byte[0]);
    }

    @Test
    void disburseSicx() {
        //Arrange
        Account target = sm.createAccount();
        Disbursement[] disbursements = new Disbursement[]{new
                Disbursement()};
        disbursements[0].address = sicx.getAddress();
        disbursements[0].amount = BigInteger.TEN.pow(20);
        setBalance(sicx, BigInteger.TEN.pow(21));

        // Act
        reserve.invoke(mockBalanced.governance.account, "disburse", target.getAddress(), disbursements);

        // Assert
        reserve.invoke(target, "claim");
        reserve.invoke(target, "claim");
        verify(sicx.mock, times(1)).transfer(target.getAddress(), BigInteger.TEN.pow(20), new byte[0]);
    }

    @Test
    void transfer() {
        BigInteger amount = BigInteger.valueOf(10);
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), reserve, "transfer", sicx.getAddress(),
                loans.getAddress(), amount);
        reserve.invoke(mockBalanced.governance.account, "transfer", sicx.getAddress(), loans.getAddress(), amount);

        verify(sicx.mock).transfer(loans.getAddress(), amount, new byte[0]);
    }
}
