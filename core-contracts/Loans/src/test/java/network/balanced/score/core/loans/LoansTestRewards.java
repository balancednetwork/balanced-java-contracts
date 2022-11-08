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

package network.balanced.score.core.loans;

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@DisplayName("Loans Tests")
class LoansTestRewards extends LoansTestBase {

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
    }

    @Test
    void depositAndBorrow_rewardsUpdate_noInitialLoan() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger initialDebt = BigInteger.ZERO;

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), initialDebt);
    }

    @Test
    void depositAndBorrow_rewardsUpdate_withInitialLoan() {
        // Arrange
        Account account = accounts.get(0);
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
        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", initialDebt, account.getAddress(), initialDebt);
    }

    @Test
    void depositAndBorrow_mutliCollateral() {
        // Arrange
        Account account = accounts.get(0);
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
        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", sICXDebt, account.getAddress(), sICXDebt);
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger balancePre = (BigInteger) bnusd.call("balanceOf", account.getAddress());

        // Act 
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");

        // Assert
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", account.getAddress());
        assertEquals(balancePre.subtract(loanToRepay), balancePost);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee));

        verifyTotalDebt(loan.add(expectedFee).subtract(loanToRepay));
        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", loan.add(expectedFee), account.getAddress(),
                loan.add(expectedFee));
    }

    @Test
    void returnAsset_MultiCollateral() {
        // Arrange
        Account account = accounts.get(0);
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

        // Act 
        loans.invoke(account, "returnAsset", "bnUSD", sICXLoanToRepay, "sICX");
        loans.invoke(account, "returnAsset", "bnUSD", iETHLoanToRepay, "iETH");

        // Assert
        verifyPosition(account.getAddress(), sICXCollateral, sICXDebt.subtract(sICXLoanToRepay), "sICX");
        verifyPosition(account.getAddress(), iETHCollateral, iETHDebt.subtract(iETHLoanToRepay), "iETH");
        verifyTotalDebt(iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay).subtract(iETHLoanToRepay));

        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", iETHDebt, account.getAddress(), iETHDebt);
        verify(rewards.mock).updateRewardsData("Loans", iETHDebt.add(sICXDebt), account.getAddress(),
                iETHDebt.add(sICXDebt));
        verify(rewards.mock).updateRewardsData("Loans", iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay),
                account.getAddress(), iETHDebt.add(sICXDebt).subtract(sICXLoanToRepay));
    }

    @SuppressWarnings("unchecked")
    @Test
    void liquidate() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidator = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        BigInteger liquidatorBalancePre = (BigInteger) sicx.call("balanceOf", liquidator.getAddress());

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        // Assert
        BigInteger liquidatorBalancePost = (BigInteger) sicx.call("balanceOf", liquidator.getAddress());
        assertEquals(liquidatorBalancePre.add(expectedReward), liquidatorBalancePost);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("sICX").get("liquidation_pool"));

        verifyTotalDebt(BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", BigInteger.ZERO, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", loan.add(expectedFee), account.getAddress(),
                loan.add(expectedFee));
    }
}