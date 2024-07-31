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

package network.balanced.score.core.loans;

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Loans Tests")
class LoansTestRewards extends LoansTestBase {

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
    }

    @Test
    void depositAndBorrow_rewardsUpdate_noInitialLoan() {
        // Arrange
        Account account = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        BigInteger expectedDebt = loan.add(expectedFee);
        verifyPosition(account.getAddress(), collateral, expectedDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", expectedDebt, account.getAddress().toString(), expectedDebt);
    }

    @Test
    void depositAndBorrow_rewardsUpdate_withInitialLoan() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger initialCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger initialLoan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger initialDebt = calculateFee(initialLoan).add(initialLoan);
        takeLoanICX(account, "bnUSD", initialCollateral, initialLoan);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger exceptedDebt = initialDebt.add(loan.add(expectedFee));

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyTotalDebt(exceptedDebt);
        verifyPosition(account.getAddress(), collateral.add(initialCollateral), exceptedDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", initialDebt, account.getAddress().toString(), initialDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", exceptedDebt, account.getAddress().toString(), exceptedDebt);
    }

    @Test
    void depositAndBorrow_mutliCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger sICXCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger sICXDebt = calculateFee(sICXLoan).add(sICXLoan);
        takeLoanICX(account, "bnUSD", sICXCollateral, sICXLoan);

        BigInteger iETHCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger iETHloan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHExpectedFee = calculateFee(iETHloan);
        BigInteger IETHDebt = iETHloan.add(iETHExpectedFee);

        // Act
        takeLoaniETH(account, iETHCollateral, iETHloan);

        // Assert
        verifyTotalDebt(IETHDebt.add(sICXDebt));
        verifyPosition(account.getAddress(), iETHCollateral, IETHDebt, "iETH");
        verifyPosition(account.getAddress(), sICXCollateral, sICXDebt, "sICX");
        verify(rewards.mock).updateBalanceAndSupply("Loans", sICXDebt, account.getAddress().toString(), sICXDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", IETHDebt.add(sICXDebt), account.getAddress().toString(), IETHDebt.add(sICXDebt));
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        takeLoanICX(account, "bnUSD", collateral, loan);
        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(loan.subtract(expectedFee));

        // Act
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");

        // Assert
        verify(bnusd.mock).burnFrom(account.getAddress(), loanToRepay);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee));

        verifyTotalDebt(loan.add(expectedFee).subtract(loanToRepay));
        verify(rewards.mock).updateBalanceAndSupply("Loans", loan.add(expectedFee), account.getAddress().toString(),
                loan.add(expectedFee));
        verify(rewards.mock).updateBalanceAndSupply("Loans", loan.add(expectedFee).subtract(loanToRepay), account.getAddress().toString(),
                loan.add(expectedFee).subtract(loanToRepay));
    }

    @Test
    void returnAsset_MultiCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger sICXCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sICXloan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXFee = calculateFee(sICXloan);
        BigInteger sICXLoanToRepay = BigInteger.valueOf(100).multiply(EXA);
        BigInteger sICXDebt = sICXloan.add(sICXFee);

        BigInteger iETHCollateral = BigInteger.valueOf(1300).multiply(EXA);
        BigInteger iETHloan = BigInteger.valueOf(150).multiply(EXA);
        BigInteger iETHFee = calculateFee(iETHloan);
        BigInteger iETHLoanToRepay = BigInteger.valueOf(80).multiply(EXA);
        BigInteger iETHDebt = iETHloan.add(iETHFee);

        takeLoaniETH(account, iETHCollateral, iETHloan);
        takeLoanICX(account, "bnUSD", sICXCollateral, sICXloan);

        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(sICXloan.add(iETHloan));

        // Act
        loans.invoke(account, "returnAsset", "bnUSD", sICXLoanToRepay, "sICX");
        loans.invoke(account, "returnAsset", "bnUSD", iETHLoanToRepay, "iETH");

        // Assert
        BigInteger expectedTotalDebt = iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay).subtract(iETHLoanToRepay);
        verifyPosition(account.getAddress(), sICXCollateral, sICXDebt.subtract(sICXLoanToRepay), "sICX");
        verifyPosition(account.getAddress(), iETHCollateral, iETHDebt.subtract(iETHLoanToRepay), "iETH");
        verifyTotalDebt(expectedTotalDebt);

        verify(rewards.mock).updateBalanceAndSupply("Loans", iETHDebt, account.getAddress().toString(), iETHDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", iETHDebt.add(sICXDebt), account.getAddress().toString(),
                iETHDebt.add(sICXDebt));
        verify(rewards.mock).updateBalanceAndSupply("Loans", iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay),
                account.getAddress().toString(), iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay));
        verify(rewards.mock).updateBalanceAndSupply("Loans", expectedTotalDebt, account.getAddress().toString(), expectedTotalDebt);

    }

    @SuppressWarnings("unchecked")
    @Test
    void liquidate() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateral_symbol = "sICX";
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);
        System.out.println("total debt: " + originalTotalDebt);

        liquidateSetup(collateral_symbol, BigInteger.valueOf(8500), BigInteger.valueOf(400), BigInteger.valueOf(100), BigInteger.valueOf(10).multiply(EXA));

        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger totalLoan = loan.add(expectedFee);
        verifyPosition(account.getAddress(), collateral, totalLoan);

        BigInteger price = EXA.divide(BigInteger.valueOf(5));
        BigInteger liquidationPrice = price.multiply(POINTS.subtract(BigInteger.valueOf(400).add(BigInteger.valueOf(100)))).divide(POINTS);

        mockOraclePrice("sICX", price);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "sICX");

        //Since the liquidation threshold is 85%, mock price is 1/5 which makes collateral value 200, and liquidation value 190(95% of 200)
        //since liquidation ratio is 202/200 so all collateral will be liquidated for the value of 190

        BigInteger liquidatorFee = BigInteger.valueOf(400).multiply(collateral).divide(POINTS);
        BigInteger daoFundFee = BigInteger.valueOf(100).multiply(collateral).divide(POINTS);
        BigInteger expectedLiquidation = collateral.subtract(liquidatorFee).subtract(daoFundFee);

        // Assert
        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)), any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), BigInteger.valueOf(190).multiply(EXA));
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        BigInteger toralAmountSpent = collateral.multiply(liquidationPrice).divide(EXA);

        BigInteger expectedBadDebt = totalLoan.subtract(toralAmountSpent);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));
        verify(rewards.mock).updateBalanceAndSupply("Loans", BigInteger.ZERO, account.getAddress().toString(), BigInteger.ZERO);
    }

}