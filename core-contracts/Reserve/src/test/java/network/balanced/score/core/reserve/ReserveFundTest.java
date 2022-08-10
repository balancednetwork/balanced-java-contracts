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

package network.balanced.score.core.reserve;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

import org.junit.jupiter.api.Assertions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static network.balanced.score.lib.utils.Constants.EXA;
import static  network.balanced.score.core.reserve.ReserveFund.TAG;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.structs.Disbursement;


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

    private void setRate(String symbol, BigInteger rateInLoop) {
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(rateInLoop);
    }

    @BeforeEach
    public void setupContract() throws Exception {
        super.setup();
        reserve.invoke(governanceScore, "setAdmin", admin.getAddress());
        setupCollaterals();
    }

    @Test
    void redeem_sicxOnly() {
        // Arrange
        BigInteger loopValueToRedeem = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sicxRate = BigInteger.TWO.multiply(EXA);
        BigInteger expectedSicxSent = loopValueToRedeem.multiply(EXA).divide(sicxRate);
        BigInteger sicxBalance = BigInteger.valueOf(1200).multiply(EXA);
        Account redeemer = sm.createAccount();;

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        // Act 
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), loopValueToRedeem);

        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), expectedSicxSent, new byte[0]);
    }

    @Test
    void redeem_mutliCollateral() {
        // Arrange
        BigInteger loopValueToRedeem = BigInteger.valueOf(1400).multiply(EXA);
        BigInteger sicxRate = BigInteger.TWO.multiply(EXA);
        BigInteger iethRate = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(400).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(10).multiply(EXA);

        BigInteger valueRemaning = loopValueToRedeem.subtract(sicxBalance.multiply(sicxRate).divide(EXA));
        BigInteger expectedIethSent = valueRemaning.multiply(EXA).divide(iethRate);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        // Act 
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), loopValueToRedeem);
    
        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), sicxBalance, new byte[0]);
        verify(ieth.mock).transfer(redeemer.getAddress(), expectedIethSent, new byte[0]);
    }

    @Test
    void redeem_useBaln() {
        // Arrange
        BigInteger loopValueToRedeem = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger sicxRate = EXA;
        BigInteger iethRate = BigInteger.valueOf(500).multiply(EXA);
        BigInteger balnRate = BigInteger.TWO.multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(500).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(1).multiply(EXA);
        BigInteger balnBalance = BigInteger.valueOf(1000).multiply(EXA);

        BigInteger valueRemaning = loopValueToRedeem.subtract(sicxBalance.multiply(sicxRate).divide(EXA));
        valueRemaning = valueRemaning.subtract(iethBalance.multiply(iethRate).divide(EXA));
        BigInteger expectedBalnSent = valueRemaning.multiply(EXA).divide(balnRate);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        setBalance(baln, balnBalance);
        setRate("BALN", balnRate);

        // Act 
        reserve.invoke(loans.account, "redeem", redeemer.getAddress(), loopValueToRedeem);
    
        // Assert
        verify(sicx.mock).transfer(redeemer.getAddress(), sicxBalance, new byte[0]);
        verify(ieth.mock).transfer(redeemer.getAddress(), iethBalance, new byte[0]);
        verify(baln.mock).transfer(redeemer.getAddress(), expectedBalnSent, new byte[0]);
    }

    @Test
    void redeem_notEnoughBalance() {
        // Arrange
        BigInteger loopValueToRedeem = BigInteger.valueOf(5000).multiply(EXA);
        BigInteger sicxRate = EXA;
        BigInteger iethRate = BigInteger.valueOf(500).multiply(EXA);
        BigInteger balnRate = BigInteger.TWO.multiply(EXA);
        BigInteger sicxBalance = BigInteger.valueOf(500).multiply(EXA);
        BigInteger iethBalance = BigInteger.valueOf(1).multiply(EXA);
        BigInteger balnBalance = BigInteger.valueOf(1000).multiply(EXA);

        Account redeemer = sm.createAccount();

        setBalance(sicx, sicxBalance);
        setRate("sICX", sicxRate);

        setBalance(ieth, iethBalance);
        setRate("iETH", iethRate);

        setBalance(baln, balnBalance);
        setRate("BALN", balnRate);

        // Act
        String expectedErrorMesssage = TAG +": Unable to process request at this time.";
        Executable withToHighValue = () -> reserve.invoke(loans.account, "redeem", redeemer.getAddress(), loopValueToRedeem);
        expectErrorMessage(withToHighValue, expectedErrorMesssage);

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
        setBalance(sicx,  BigInteger.TEN.pow(21));

        // Act
        reserve.invoke(governanceScore, "disburse", target.getAddress(), disbursements);

        // Assert
        reserve.invoke(target, "claim");
        reserve.invoke(target, "claim");
        verify(sicx.mock, times(1)).transfer(target.getAddress(), BigInteger.TEN.pow(20), new byte[0]);
    }
}
