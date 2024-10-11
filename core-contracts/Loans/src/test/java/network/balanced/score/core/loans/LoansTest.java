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

import static network.balanced.score.core.loans.utils.LoansConstants.LOCKING_RATIO;
import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;

import network.balanced.score.core.loans.LoansImpl.LiquidationResult;
import network.balanced.score.core.loans.utils.LoansConstants.Standings;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.mock.MockContract;

@DisplayName("Loans Tests")
class LoansTest extends LoansTestBase {

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
    }

    @Test
    void getLoansOn() {
        Boolean loansOn = (Boolean) loans.call("getLoansOn");
        assertTrue(loansOn);

        governanceCall("toggleLoansOn");
        loansOn = (Boolean) loans.call("getLoansOn");
        assertFalse(loansOn);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getSetParameters() {
        loans.invoke(governance.account, "setMaxRetirePercent", BigInteger.valueOf(2));
        governanceCall("setTimeOffset", BigInteger.valueOf(3));
        loans.invoke(governance.account, "setNewLoanMinimum", BigInteger.valueOf(5));
        loans.invoke(governance.account, "setRedemptionFee", BigInteger.valueOf(7));
        loans.invoke(governance.account, "setOriginationFee", BigInteger.valueOf(8));
        loans.invoke(governance.account, "setLiquidationRatio", "sICX", BigInteger.valueOf(6500));
        loans.invoke(governance.account, "setLockingRatio", "sICX", BigInteger.valueOf(10));

        Map<String, Object> params = (Map<String, Object>) loans.call("getParameters");

        assertEquals(BigInteger.valueOf(2), params.get("retire percent max"));
        assertEquals(BigInteger.valueOf(3), params.get("time offset"));
        assertEquals(BigInteger.valueOf(5), params.get("new loan minimum"));
        assertEquals(BigInteger.valueOf(7), params.get("redemption fee"));
        assertEquals(BigInteger.valueOf(8), params.get("origination fee"));
        assertEquals(BigInteger.valueOf(10), params.get("locking ratio"));
        assertEquals(BigInteger.valueOf(6500), params.get("Liquidation ratio "));
    }

    @Test
    void setMaxRetirePercent_ToLow() {
        Executable setMaxRetirePercent = () -> loans.invoke(governance.account, "setMaxRetirePercent",
                BigInteger.valueOf(-1));
        String expectedErrorMessage = "Reverted(0): Input parameter must be in the range 0 to 10000 points.";

        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void setMaxRetirePercent_ToHigh() {
        Executable setMaxRetirePercent = () -> loans.invoke(governance.account, "setMaxRetirePercent",
                BigInteger.valueOf(-2));
        String expectedErrorMessage = "Reverted(0): Input parameter must be in the range 0 to 10000 points.";

        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void setGetDebtCeiling() {
        BigInteger ceiling = BigInteger.TEN.pow(23);
        assertOnlyCallableBy(governance.getAddress(), loans, "setDebtCeiling", "sICX", ceiling);

        loans.invoke(governance.account, "setDebtCeiling", "sICX", ceiling);

        assertEquals(ceiling, loans.call("getDebtCeiling", "sICX"));
        assertNull(loans.call("getDebtCeiling", "iETH"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAccountPositions() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger sICXCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger iETHCollateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHloan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger iETHExpectedFee = calculateFee(iETHloan);

        // Act
        takeLoanICX(account, "bnUSD", sICXCollateral, loan);
        takeLoaniETH(account, iETHCollateral, iETHloan);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assets = (Map<String, Map<String, BigInteger>>) position.get("holdings");
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>) position.get("standings");

        assertEquals(1, position.get("pos_id"));
        assertEquals(account.getAddress().toString(), position.get("address"));

        assertEquals(loan.add(expectedFee), standings.get("sICX").get("total_debt_in_USD"));
        assertEquals(sICXCollateral, standings.get("sICX").get("collateral_in_USD"));
        assertEquals(sICXCollateral.multiply(EXA).divide(loan.add(expectedFee)),
                standings.get("sICX").get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), standings.get("sICX").get("standing"));

        assertEquals(iETHloan.add(iETHExpectedFee), standings.get("iETH").get("total_debt_in_USD"));
        assertEquals(iETHCollateral, standings.get("iETH").get("collateral_in_USD"));
        assertEquals(iETHCollateral.multiply(EXA).divide(iETHloan.add(iETHExpectedFee)),
                standings.get("iETH").get(
                        "ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), standings.get("iETH").get("standing"));

        assertEquals(loan.add(expectedFee), assets.get("sICX").get("bnUSD"));
        assertEquals(sICXCollateral, assets.get("sICX").get("sICX"));

        assertEquals(iETHloan.add(iETHExpectedFee), assets.get("iETH").get("bnUSD"));
        assertEquals(iETHCollateral, assets.get("iETH").get("iETH"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalanceAndSupply() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger sICXdebt = loan.add(expectedFee);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans",
                account.getAddress().toString());
        assertEquals(sICXdebt, balanceAndSupply.get("_balance"));
        assertEquals(sICXdebt, balanceAndSupply.get("_totalSupply"));

        // ArrangeQ
        BigInteger iETHCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(50).multiply(EXA);
        expectedFee = calculateFee(iETHLoan);
        BigInteger iETHDebt = iETHLoan.add(expectedFee);

        // Act
        takeLoaniETH(account, iETHCollateral, iETHLoan);

        // Assert
        balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans",
                account.getAddress().toString());
        assertEquals(iETHDebt.add(sICXdebt), balanceAndSupply.get("_balance"));
        assertEquals(iETHDebt.add(sICXdebt), balanceAndSupply.get("_totalSupply"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getBalanceAndSupply_noPosition() {
        // Arrange
        Account loanTaker = sm.createAccount();
        Account zeroAccount = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanICX(loanTaker, "bnUSD", collateral, loan);

        // Act
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans",
                zeroAccount.getAddress().toString());

        // Assert
        assertEquals(BigInteger.ZERO, balanceAndSupply.get("_balance"));
        assertEquals(loan.add(expectedFee), balanceAndSupply.get("_totalSupply"));
    }

    @Test
    void getDay() {
        BigInteger currentDay = (BigInteger) loans.call("getDay");
        sm.getBlock().increase(DAY);

        assertEquals(currentDay.add(BigInteger.ONE), loans.call("getDay"));
    }

    @Test
    void tokenFallback_DepositAndBorrow() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanSICX(account, collateral, loan);

        // Assert
        verifyTotalDebt(loan.add(expectedFee));
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void tokenFallback_ZeroValue() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger value = BigInteger.valueOf(0);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Token value should be a positive number";

        // Assert & Act
        Executable transferToken = () -> loans.invoke(sicx.account, "tokenFallback", account.getAddress(), value,
                new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_NonSupportedCollateral() {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + bnusd.getAddress() + " is not a supported collateral type.";

        // Assert & Act
        Executable transferToken = () -> loans.invoke(bnusd.account, "tokenFallback", account.getAddress(), value,
                new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_NonSupportedCollateral_SameSymbol() throws Exception {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);

        MockContract<IRC2> fakesICX = new MockContract<>(IRC2ScoreInterface.class, sm, admin);
        when(fakesICX.mock.symbol()).thenReturn((String) "sICX");
        String expectedErrorMessage = "Reverted(0): " + fakesICX.getAddress() + " is not a supported collateral type.";

        // Assert & Act
        Executable transferToken = () -> loans.invoke(fakesICX.account, "tokenFallback", account.getAddress(), value,
                new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_EmptyData() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Token Fallback: Data can't be empty";
        byte[] data = new byte[0];

        // Assert & Act
        Executable transferToken = () -> loans.invoke(sicx.account, "tokenFallback", account.getAddress(), value, data);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void depositAndBorrow_ICX() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyTotalDebt(loan.add(expectedFee));
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @SuppressWarnings("unchecked")
    @Test
    void depositAndBorrow_StakeOnly() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.ZERO;

        // Act
        takeLoanICX(account, "", collateral, loan);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void depositAndBorrow_OriginateLoan_LowerThanMinimum() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger newLoanMinimum = (BigInteger) getParam("new loan minimum");

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger toSmallLoan = newLoanMinimum.subtract(BigInteger.ONE.multiply(EXA));
        String expectedErrorMessage = "Reverted(0): " + TAG + "The initial loan of any " +
                "asset must have a minimum value " +
                "of " + newLoanMinimum.divide(EXA) + " dollars.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, toSmallLoan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void depositAndBorrow_OriginateLoan_ToLargeDebt() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(500).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger lockingRatio = ((BigInteger) getParam("locking ratio")).divide(POINTS);

        String expectedErrorMessage = "Reverted(0): " + TAG + collateral + " collateral is insufficient to originate " +
                "a loan of " + loan + " bnUSD " +
                "when max_debt_value = " + collateral.divide(lockingRatio) + ", new_debt_value = "
                + loan.add(expectedFee) + ", " +
                "which includes a fee of " + expectedFee + " bnUSD, given an existing loan value of 0.";
        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void depositCollateral_sICX() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);

        // Act
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @SuppressWarnings("unchecked")
    @Test
    void depositCollateral_iETH() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);

        // Act
        takeLoaniETH(account, collateral, BigInteger.ZERO);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("iETH").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @SuppressWarnings("unchecked")
    @Test
    void borrow_sICX() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Act
        loans.invoke(account, "borrow", "sICX", "bnUSD", loan, "", new byte[0]);
        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(expectedDebt, assetHoldings.get("sICX").get("bnUSD"));
        verifyPosition(account.getAddress(), collateral, expectedDebt, "sICX");
        verifyTotalDebt(expectedDebt);
    }

    @Test
    void borrow_differentLockingRatios() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger feePercentage = (BigInteger) getParam("origination fee");

        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);
        BigInteger rate = EXA;
        mockOraclePrice("sICX", rate);
        mockOraclePrice("iETH", rate);
        mockOraclePrice("bnUSD", rate);
        BigInteger sICXCollateralInUSD = collateral.multiply(rate);
        BigInteger iETHCollateralInUSD = collateral.multiply(rate);
        BigInteger sICXLockingRatio = BigInteger.valueOf(30_000);
        BigInteger iETHLockingRatio = BigInteger.valueOf(15_000);

        BigInteger sICXMaxDebt = POINTS.multiply(sICXCollateralInUSD).divide(sICXLockingRatio);
        BigInteger sICXMaxLoan = sICXMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(EXA);

        BigInteger iETHMaxDebt = POINTS.multiply(iETHCollateralInUSD).divide(iETHLockingRatio);
        BigInteger iETHMaxLoan = iETHMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(EXA);

        // Act
        loans.invoke(governance.account, "setLockingRatio", "sICX", sICXLockingRatio);
        loans.invoke(governance.account, "setLockingRatio", "iETH", iETHLockingRatio);

        // Assert
        Executable borrowSICX = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan.add(BigInteger.ONE),
                "", new byte[0]);
        Executable borrowIETH = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan.add(BigInteger.ONE),
                "", new byte[0]);
        expectErrorMessage(borrowSICX, "collateral is insufficient to originate a loan of");
        expectErrorMessage(borrowIETH, "collateral is insufficient to originate a loan of");

        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan, "", new byte[0]);
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan, "", new byte[0]);

        verifyTotalDebt(iETHMaxDebt.add(sICXMaxDebt).divide(EXA));
    }

    @SuppressWarnings("unchecked")
    @Test
    void borrow_iETH() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        takeLoaniETH(account, collateral, BigInteger.ZERO);

        // Act
        loans.invoke(account, "borrow", "iETH", "bnUSD", loan, "", new byte[0]);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(expectedDebt, assetHoldings.get("iETH").get("bnUSD"));
        verifyPosition(account.getAddress(), collateral, expectedDebt, "iETH");
        verifyTotalDebt(expectedDebt);
    }

    @Test
    void borrow_withDebtCeilings_toLow() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA);

        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        loans.invoke(governance.account, "setDebtCeiling", "iETH", iETHLoan);
        loans.invoke(governance.account, "setDebtCeiling", "sICX", sICXLoan);

        // Act & Assert
        String expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral iETH";
        Executable overDebtCeilingiETH = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan, "",
                new byte[0]);
        expectErrorMessage(overDebtCeilingiETH, expectedErrorMessage);

        expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral sICX";
        Executable overDebtCeilingsICX = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan, "",
                new byte[0]);
        expectErrorMessage(overDebtCeilingsICX, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void borrow_withDebtCeilings() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA);
        BigInteger iETHExpectedFee = calculateFee(iETHLoan);
        BigInteger sICXExpectedFee = calculateFee(sICXLoan);

        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        loans.invoke(governance.account, "setDebtCeiling", "iETH", iETHLoan.add(iETHExpectedFee));
        loans.invoke(governance.account, "setDebtCeiling", "sICX", sICXLoan.add(sICXExpectedFee));

        // Act
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan, "", new byte[0]);
        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan, "", new byte[0]);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(iETHLoan.add(iETHExpectedFee), assetHoldings.get("iETH").get("bnUSD"));

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(sICXLoan.add(sICXExpectedFee), assetHoldings.get("sICX").get("bnUSD"));

        verifyTotalDebt(sICXLoan.add(sICXExpectedFee).add(iETHLoan.add(iETHExpectedFee)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void borrow_withDebtCeilings_badDebt() {
        // Arrange
        Account liquidatedLoanTaker = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account account = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger totalLoan = loan.add(expectedFee);
        BigInteger debtCeiling = BigInteger.valueOf(300).multiply(EXA);
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);

        // Since this data will liquidate all collareral and then remaining debt will be
        // bad debt so
        BigInteger totalAmountSpent = BigInteger.valueOf(190).multiply(EXA);
        BigInteger expectedBadDebt = totalLoan.subtract(totalAmountSpent);

        loans.invoke(governance.account, "setDebtCeiling", "sICX", debtCeiling);

        takeLoanICX(liquidatedLoanTaker, "bnUSD", collateral, loan);
        verifyPosition(liquidatedLoanTaker.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = EXA.divide(BigInteger.valueOf(5));
        mockOraclePrice("sICX", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", liquidatedLoanTaker.getAddress().toString(), liquidateAmount, "sICX");
        mockOraclePrice("sICX", EXA);

        // Assert
        verifyBadDebt(liquidatedLoanTaker.getAddress(), expectedBadDebt);

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Arrange
        BigInteger feePercentage = (BigInteger) getParam("origination fee");
        BigInteger expectedAvailableBnusd = debtCeiling.subtract(expectedBadDebt).subtract(totalLoan);

        BigInteger allowedLoan = expectedAvailableBnusd.multiply(POINTS).divide(POINTS.add(feePercentage));

        // Act
        String expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral sICX";
        Executable overDebtCeilingSICX = () -> takeLoanICX(account, "bnUSD", collateral,
                allowedLoan.add(BigInteger.TWO));
        expectErrorMessage(overDebtCeilingSICX, expectedErrorMessage);

        // Assert
        takeLoanICX(account, "bnUSD", collateral, allowedLoan);

        overDebtCeilingSICX = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(overDebtCeilingSICX, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void borrow_fromWrongCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Assert & Act
        String expectedErrorMessage = "Reverted(0): " + TAG + "0 collateral is insufficient to originate a loan of " +
                loan + " bnUSD when max_debt_value = 0," +
                " new_debt_value = " + loan.add(expectedFee) + ", which includes a fee of " +
                expectedFee + " bnUSD, given an existing loan value of 0.";
        Executable borrowWrong = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", loan, "", new byte[0]);
        expectErrorMessage(borrowWrong, expectedErrorMessage);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                account.getAddress().toString());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void borrow_collateralWithoutLockingRation() throws Exception {
        // Arrange
        MockContract<IRC2> iBTC = new MockContract<>(IRC2ScoreInterface.class, sm, admin);
        when(iBTC.mock.symbol()).thenReturn("iBTC");
        when(iBTC.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        loans.invoke(governance.account, "addAsset", iBTC.getAddress(), true, true);

        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", BigInteger.ZERO.toString());

        loans.invoke(iBTC.account, "tokenFallback", account.getAddress(), collateral, data.toString().getBytes());

        // Act & Assert
        String expectedErrorMessage = "Reverted(0): Locking ratio for iBTC is not set";
        Executable loanWithoutLockingRatio = () -> loans.invoke(account, "borrow", "iBTC", "bnUSD", loan, "",
                new byte[0]);
        expectErrorMessage(loanWithoutLockingRatio, expectedErrorMessage);
    }

    @Test
    void borrow_negativeAmount() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA).negate();

        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Act & Assert
        String expectedErrorMessage = "_amountToBorrow needs to be larger than 0";
        Executable negativeLoan = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan, "", new byte[0]);
        expectErrorMessage(negativeLoan, expectedErrorMessage);
    }

    @Test
    void getTotalDebts() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA);
        BigInteger iETHExpectedFee = calculateFee(iETHLoan);
        BigInteger sICXExpectedFee = calculateFee(sICXLoan);
        BigInteger iETHExpectedDebt = iETHLoan.add(iETHExpectedFee);
        BigInteger sICXExpectedDebt = sICXLoan.add(sICXExpectedFee).multiply(BigInteger.TWO);
        BigInteger expectedTotalDebt = iETHExpectedDebt.add(sICXExpectedDebt);

        // Act
        takeLoaniETH(account1, collateral, iETHLoan);
        takeLoanSICX(account1, collateral, sICXLoan);
        takeLoanSICX(account2, collateral, sICXLoan);

        // Assert
        BigInteger totalDebt = (BigInteger) loans.call("getTotalDebt", "bnUSD");
        BigInteger totalsICXDebt = (BigInteger) loans.call("getTotalCollateralDebt", "sICX", "bnUSD");
        BigInteger totaliETHDebt = (BigInteger) loans.call("getTotalCollateralDebt", "iETH", "bnUSD");
        assertEquals(expectedTotalDebt, totalDebt);
        assertEquals(sICXExpectedDebt, totalsICXDebt);
        assertEquals(iETHExpectedDebt, totaliETHDebt);
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger iETHCollateral = BigInteger.valueOf(1200).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(250).multiply(EXA);
        BigInteger iETHExpectedFee = calculateFee(iETHLoan);

        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHloanToRepay = BigInteger.valueOf(200).multiply(EXA);

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, iETHCollateral, iETHLoan);

        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(iETHLoan.add(loan));

        // Act
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX", "");
        loans.invoke(account, "returnAsset", "bnUSD", iETHloanToRepay, "iETH", "");

        // Assert
        verify(bnusd.mock).burnFrom(account.getAddress(), loanToRepay);
        verify(bnusd.mock).burnFrom(account.getAddress(), iETHloanToRepay);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyPosition(account.getAddress(), iETHCollateral, iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee),
                "iETH");
        BigInteger expectedTotal = loan.subtract(loanToRepay).add(expectedFee)
                .add(iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee));
        verifyTotalDebt(expectedTotal);
    }

    @Test
    void returnAsset_to() {
        // Arrange
        Account account = sm.createAccount();
        Account repayer = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        takeLoanICX(account, "bnUSD", collateral, loan);
        when(bnusd.mock.balanceOf(repayer.getAddress())).thenReturn(loanToRepay);

        // Act
        loans.invoke(repayer, "returnAsset", "bnUSD", loanToRepay, "sICX", account.getAddress().toString());

        // Assert
        verify(bnusd.mock).burnFrom(repayer.getAddress(), loanToRepay);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        BigInteger expectedTotal = loan.subtract(loanToRepay).add(expectedFee);
        verifyTotalDebt(expectedTotal);
    }

    @Test
    void returnAssetAndReopenPosition() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(expectedFee.add(loan));
        loans.invoke(account, "returnAsset", "bnUSD", loan.add(expectedFee), "sICX", "");

        // Assert
        assertFalse((boolean) loans.call("hasDebt", account.getAddress().toString()));
        verifyPosition(account.getAddress(), collateral, BigInteger.ZERO);

        // Act
        takeLoanICX(account, "bnUSD", BigInteger.ZERO, loan);

        // Assert
        assertTrue((boolean) loans.call("hasDebt", account.getAddress().toString()));
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        verifyTotalDebt(loan.add(expectedFee));
    }

    @Test
    void returnAsset_MoreThanHoldings() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = loan.add(expectedFee).add(BigInteger.ONE);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Repaid amount is greater than the amount in the " +
                "position of " + account.getAddress();

        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(loanToRepay);

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnToMuch = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX", "");
        expectErrorMessage(returnToMuch, expectedErrorMessage);
    }

    @Test
    void returnAsset_WrongCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Repaid amount is greater than the amount in the " +
                "position of " + account.getAddress();

        takeLoanICX(account, "bnUSD", collateral, loan);
        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(loan);

        // Assert & Act
        Executable returnForiETH = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "iETH", "");
        expectErrorMessage(returnForiETH, expectedErrorMessage);
    }

    @Test
    void returnAsset_InsufficientBalance() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        String expectedErrorMessage = "Reverted(0): " + TAG + "Insufficient balance.";

        takeLoanICX(account, "bnUSD", collateral, loan);
        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(loanToRepay.subtract(BigInteger.ONE));

        // Assert & Act
        Executable returnWithInsufficientBalance = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay,
                "sICX", "");
        expectErrorMessage(returnWithInsufficientBalance, expectedErrorMessage);
    }

    @Test
    void returnAsset_NoPosition() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(loanToRepay);

        String expectedErrorMessage = "Reverted(0): " + TAG + "No debt repaid because, " + account.getAddress() + " " +
                "does not have a position in Balanced";

        // Assert & Act
        Executable returnWithNoPosition = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX", "");
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void returnAsset_AlreadyAboveCeiling() {
        Account loanTaker = sm.createAccount();
        Account loanRepayer = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        loans.invoke(governance.account, "setDebtCeiling", "sICX", BigInteger.valueOf(1000).multiply(EXA));

        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        when(bnusd.mock.balanceOf(loanRepayer.getAddress())).thenReturn(loan);

        takeLoanICX(loanTaker, "bnUSD", collateral, loan);
        takeLoanICX(loanRepayer, "bnUSD", collateral, loan);
        loans.invoke(governance.account, "setDebtCeiling", "sICX", BigInteger.valueOf(200).multiply(EXA));
        verifyTotalDebt(expectedDebt.multiply(BigInteger.TWO));

        // Act
        loans.invoke(loanRepayer, "returnAsset", "bnUSD", loanToRepay, "sICX", "");

        // Assert
        verify(bnusd.mock).burnFrom(loanRepayer.getAddress(), loanToRepay);
        verifyPosition(loanRepayer.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyTotalDebt(expectedDebt.multiply(BigInteger.TWO).subtract(loanToRepay));
    }

    @Test
    void withdrawCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHCollateralToWithdraw = BigInteger.valueOf(80).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        // Act
        loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        loans.invoke(account, "withdrawCollateral", iETHCollateralToWithdraw, "iETH");

        // Assert
        verify(sicx.mock).transfer(eq(account.getAddress()), eq(collateralToWithdraw), any(byte[].class));
        verify(ieth.mock).transfer(eq(account.getAddress()), eq(iETHCollateralToWithdraw), any(byte[].class));
        verifyPosition(account.getAddress(), collateral.subtract(collateralToWithdraw), loan.add(expectedFee), "sICX");
        verifyPosition(account.getAddress(), collateral.subtract(iETHCollateralToWithdraw), loan.add(expectedFee),
                "iETH");
        verifyTotalDebt(loan.add(expectedFee).add(loan.add(expectedFee)));
    }

    @Test
    void withdrawCollateral_ZeroCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(0);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Withdraw amount must be more than zero.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawZeroCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw,
                "sICX");
        expectErrorMessage(withdrawZeroCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NoPosition() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateralToWithdraw = BigInteger.valueOf(200).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "This address does not have a position on Balanced.";

        // Assert & Act
        Executable withdrawWithNoPosition = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw,
                "sICX");
        expectErrorMessage(withdrawWithNoPosition, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_TooMuchCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(1100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Position holds less collateral than the requested " +
                "withdrawal.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw,
                "sICX");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_WrongCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Position holds less collateral than the requested " +
                "withdrawal.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw,
                "iETH");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NewLockingRatioToLow() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(800).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger lockingRatio = ((BigInteger) getParam("locking ratio")).divide(POINTS);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Requested withdrawal is more than available collateral" +
                ". total debt value: " + loan.add(expectedFee) + " ICX remaining collateral value: " +
                collateral.subtract(collateralToWithdraw) + " ICX locking value (max debt): " +
                lockingRatio.multiply(loan.add(expectedFee)) + " ICX";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw,
                "sICX");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawAndUnstake() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);

        // Act
        takeLoanICX(account, "bnUSD", collateral, BigInteger.ZERO);
        loans.invoke(account, "withdrawAndUnstake", collateral);

        // Assert
        JsonObject data = new JsonObject()
                .add("method", "unstake")
                .add("user", account.getAddress().toString());
        verify(sicx.mock).transfer(staking.getAddress(), collateral, data.toString().getBytes());
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "sICX");
    }

    @Test
    void testSuccessfulLiquidationWithDefaultCollateral() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();

        String collateral_symbol = "sICX";
        BigInteger collateral = BigInteger.valueOf(1000).multiply(ICX);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateral_symbol, BigInteger.valueOf(12000), BigInteger.valueOf(400),
                BigInteger.valueOf(100), minimumDebtThreshold);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger price = EXA.divide(BigInteger.valueOf(5));
        BigInteger liquidationPrice = price
                .multiply(POINTS.subtract(BigInteger.valueOf(400).add(BigInteger.valueOf(100))))
                .divide(POINTS);

        mockOraclePrice("sICX", price);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "sICX");

        // Calculate expected result
        BigInteger totalCollateralLiquidated = (liquidateAmount.multiply(EXA)).divide(liquidationPrice);
        BigInteger totalAmountSpend = liquidateAmount;

        // Assert
        verifyPosition(account.getAddress(), collateral.subtract(totalCollateralLiquidated),
                loan.add(expectedFee).subtract(totalAmountSpend), "sICX");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSuccessfulLiquidation() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();

        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(200).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(12000); // 85%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(400); // 4%
        BigInteger daoFundFeePercent = BigInteger.valueOf(100); // 1%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);

        BigInteger expectedFee = calculateFee(loanAmount);
        BigInteger originalTotalDebt = getTotalDebt();

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);

        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);

        BigInteger liquidationPrice = oraclePriceValue
                .multiply(POINTS.subtract(liquidatorFeePercent.add(daoFundFeePercent))).divide(POINTS);

        mockOraclePrice(collateralSymbol, oraclePriceValue);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);

        // Assert
        BigInteger liquidatorFee = liquidatorFeePercent.multiply(collateralAmount).divide(POINTS);
        BigInteger daoFundFee = daoFundFeePercent.multiply(collateralAmount).divide(POINTS);
        BigInteger expectedLiquidation = collateralAmount.subtract(liquidatorFee).subtract(daoFundFee);

        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), BigInteger.valueOf(190).multiply(EXA));
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets"))
                .get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset
                .get("debt_details");

        BigInteger totalAmountSpent = collateralAmount.multiply(liquidationPrice).divide(EXA);

        BigInteger expectedBadDebt = totalLoan.subtract(totalAmountSpent);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));
        verify(rewards.mock).updateBalanceAndSupply("Loans", BigInteger.ZERO, account.getAddress().toString(),
                BigInteger.ZERO);
        verifyTotalDebt(originalTotalDebt);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testLiquidationBelowThreshold() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();

        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(12000); // 85%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(400); // 4%
        BigInteger daoFundFeePercent = BigInteger.valueOf(100); // 1%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(50).multiply(EXA);

        BigInteger expectedFee = calculateFee(loanAmount);

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);

        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);

        mockOraclePrice(collateralSymbol, oraclePriceValue);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);

        // Assert
        verifyPosition(account.getAddress(), collateralAmount, totalLoan); // No liquidation should occur
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCollateralZeroWithRemainingDebt() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();

        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(220).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(12000); // 85%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(400); // 4%
        BigInteger daoFundFeePercent = BigInteger.valueOf(100); // 1%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);

        BigInteger expectedFee = calculateFee(loanAmount);

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);

        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);

        BigInteger liquidationPrice = oraclePriceValue
                .multiply(POINTS.subtract(liquidatorFeePercent.add(daoFundFeePercent))).divide(POINTS);

        mockOraclePrice(collateralSymbol, oraclePriceValue);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);

        // Assert
        BigInteger remainingDebt = totalLoan.subtract(collateralAmount.multiply(liquidationPrice).divide(EXA));
        verifyBadDebt(account.getAddress(), remainingDebt);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPartialLiquidation() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();

        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(180).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(12000); // 85%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(400); // 4%
        BigInteger daoFundFeePercent = BigInteger.valueOf(100); // 1%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);

        BigInteger expectedFee = calculateFee(loanAmount);

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);

        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);

        BigInteger liquidationPrice = oraclePriceValue
                .multiply(POINTS.subtract(liquidatorFeePercent.add(daoFundFeePercent))).divide(POINTS);

        mockOraclePrice(collateralSymbol, oraclePriceValue);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);

        // Assert
        BigInteger liquidatedCollateral = liquidateAmount.multiply(EXA).divide(liquidationPrice);
        BigInteger remainingCollateral = collateralAmount.subtract(liquidatedCollateral);
        BigInteger remainingDebt = totalLoan.subtract(liquidateAmount);

        verifyPosition(account.getAddress(), remainingCollateral, remainingDebt);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testLiquidationWithDifferentFeesAndThresholds() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(600).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(120).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(14000); // 70%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(500); // 5%
        BigInteger daoFundFeePercent = BigInteger.valueOf(200); // 2%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(4)); // 1/4
        BigInteger liquidateAmount = BigInteger.valueOf(120).multiply(EXA);
        BigInteger expectedFee = calculateFee(loanAmount);
        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        liquidationRatio = liquidationRatio.add(BigInteger.TEN); // 70%
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);
        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);
        BigInteger liquidationPrice = oraclePriceValue
                .multiply(POINTS.subtract(liquidatorFeePercent.add(daoFundFeePercent))).divide(POINTS);
        mockOraclePrice(collateralSymbol, oraclePriceValue);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);
        // It will liquidate until the Liquidation ratio is reached,
        BigInteger maxLiquidatedAmount = calculateThresholdPoint(collateralAmount, liquidationRatio, oraclePriceValue,
                liquidationPrice, totalLoan);
        BigInteger collateralLiquidated = maxLiquidatedAmount.multiply(EXA).divide(liquidationPrice);
        BigInteger totalAmountSpent = maxLiquidatedAmount;
        BigInteger remainingCollateral = collateralAmount.subtract(collateralLiquidated);
        BigInteger remainingDebt = totalLoan.subtract(totalAmountSpent);
        // Assert
        BigInteger liquidatorFee = liquidatorFeePercent.multiply(collateralLiquidated).divide(POINTS);
        BigInteger daoFundFee = daoFundFeePercent.multiply(collateralLiquidated).divide(POINTS);
        BigInteger expectedLiquidation = collateralLiquidated.subtract(liquidatorFee).subtract(daoFundFee);

        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), totalAmountSpent);
        verifyPosition(account.getAddress(), remainingCollateral, remainingDebt);
        verifyBadDebt(account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateBalanceAndSupply("Loans", remainingDebt, account.getAddress().toString(),
                remainingDebt);
        verifyTotalDebt(remainingDebt);
    }

    BigInteger calculateThresholdPoint(BigInteger collateral, BigInteger liquidationRatio,
            BigInteger collateralPrice, BigInteger liquidationPrice, BigInteger totalDebt) {
        BigInteger liquidationRatioValue = totalDebt.multiply(liquidationRatio).divide(POINTS);
        BigInteger extraCollateral = liquidationRatioValue.subtract(collateralPrice.multiply(collateral).divide(EXA));
        BigInteger collateralLiquidatedPerUnitDebtPay = collateralPrice.multiply(EXA).divide(liquidationPrice);
        BigInteger collateralNeededPerUnitDebt = liquidationRatio.multiply(EXA).divide(POINTS);
        BigInteger effectiveCollateralValue = collateralNeededPerUnitDebt.subtract(collateralLiquidatedPerUnitDebtPay);
        if (effectiveCollateralValue.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger maxAmountToSpendToMaintainThreshold = extraCollateral.multiply(EXA)
                .divide(effectiveCollateralValue);
        return maxAmountToSpendToMaintainThreshold;
    }

    @SuppressWarnings("unchecked")
    @Test
    void testLiquidationWithHighFees() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(500).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(12000); // 85%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(1000); // 10%
        BigInteger daoFundFeePercent = BigInteger.valueOf(500); // 5%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loanAmount);
        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);
        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);
        mockOraclePrice(collateralSymbol, oraclePriceValue);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);
        // Assert
        BigInteger liquidatorFee = liquidatorFeePercent.multiply(collateralAmount).divide(POINTS);
        BigInteger daoFundFee = daoFundFeePercent.multiply(collateralAmount).divide(POINTS);
        BigInteger expectedLiquidation = collateralAmount.subtract(liquidatorFee).subtract(daoFundFee);
        // here, due to fees, to liquidate 500 sICX, liquidator only need to spend 100 -
        // 10% - 5% = 85 bnUSD
        BigInteger actualLiquidatedAmount = BigInteger.valueOf(85).multiply(EXA);
        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), actualLiquidatedAmount);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);
        verifyBadDebt(account.getAddress(), totalLoan.subtract(actualLiquidatedAmount));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testLiquidationWithDifferentThreshold() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateralSymbol = "sICX";
        BigInteger collateralAmount = BigInteger.valueOf(800).multiply(EXA);
        BigInteger loanAmount = BigInteger.valueOf(150).multiply(EXA);
        BigInteger liquidationRatio = BigInteger.valueOf(11111); // 90%
        BigInteger liquidatorFeePercent = BigInteger.valueOf(400); // 4%
        BigInteger daoFundFeePercent = BigInteger.valueOf(100); // 1%
        BigInteger oraclePriceValue = EXA.divide(BigInteger.valueOf(5)); // 1/5
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loanAmount);
        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateralSymbol, liquidationRatio, liquidatorFeePercent, daoFundFeePercent,
                minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateralAmount, loanAmount);
        BigInteger totalLoan = loanAmount.add(expectedFee);
        verifyPosition(account.getAddress(), collateralAmount, totalLoan);
        BigInteger liquidationPrice = oraclePriceValue
                .multiply(POINTS.subtract(liquidatorFeePercent.add(daoFundFeePercent))).divide(POINTS);
        mockOraclePrice(collateralSymbol, oraclePriceValue);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount,
                collateralSymbol);
        // This will liquidate amount upto threshold, after which remaining debt will be
        // about 8 bnusd which is less
        // than 10 bnusd threshold, so it will liquidate all debt
        BigInteger maxLiquidateAmount = totalLoan;
        BigInteger maxCollateralToLiquidate = maxLiquidateAmount.multiply(EXA).divide(liquidationPrice);
        // Assert
        BigInteger liquidatorFee = liquidatorFeePercent.multiply(maxCollateralToLiquidate).divide(POINTS);
        BigInteger daoFundFee = daoFundFeePercent.multiply(maxCollateralToLiquidate).divide(POINTS);
        BigInteger expectedLiquidation = maxCollateralToLiquidate.subtract(liquidatorFee).subtract(daoFundFee);
        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), maxLiquidateAmount);
        verifyPosition(account.getAddress(), collateralAmount.subtract(maxCollateralToLiquidate),
                totalLoan.subtract(maxLiquidateAmount));
    }

    @SuppressWarnings("unchecked")
    @Test
    void liquidate_withCancelBadDebt() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateral_symbol = "sICX";
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger price = EXA.divide(BigInteger.valueOf(5));
        BigInteger liquidateAmount = BigInteger.valueOf(200).multiply(EXA);

        BigInteger minimumDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateral_symbol, BigInteger.valueOf(12000), BigInteger.valueOf(400),
                BigInteger.valueOf(100), minimumDebtThreshold);
        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger totalLoan = loan.add(expectedFee);
        verifyPosition(account.getAddress(), collateral, totalLoan);
        BigInteger liquidationPrice = price
                .multiply(POINTS.subtract(BigInteger.valueOf(400).add(BigInteger.valueOf(100))))
                .divide(POINTS);
        mockOraclePrice("sICX", price);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "sICX");
        // Since the Liquidation ratio is 85%, mock price is 1/5 which makes
        // collateral value 200, and liquidation value 190(95% of 200)
        // since liquidation ratio is 202/200 so all collateral will be liquidated for
        // the value of 190
        BigInteger liquidatorFee = BigInteger.valueOf(400).multiply(collateral).divide(POINTS);
        BigInteger daoFundFee = BigInteger.valueOf(100).multiply(collateral).divide(POINTS);
        BigInteger expectedLiquidation = collateral.subtract(liquidatorFee).subtract(daoFundFee);
        // Assert
        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(sicx.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), BigInteger.valueOf(190).multiply(EXA));
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);
        BigInteger totalAmountSpent = collateral.multiply(liquidationPrice).divide(EXA);
        BigInteger expectedBadDebt = totalLoan.subtract(totalAmountSpent);
        verifyBadDebt(account.getAddress(), expectedBadDebt);
        verify(rewards.mock).updateBalanceAndSupply("Loans", BigInteger.ZERO, account.getAddress().toString(),
                BigInteger.ZERO);
        verifyTotalDebt(originalTotalDebt);
        // Cancel bad debt
        loans.invoke(governance.account, "cancelBadDebt", "sICX", BigInteger.valueOf(100).multiply(EXA));
        verify(bnusd.mock).burnFrom(governance.account.getAddress(), expectedBadDebt);
        verifyBadDebt(account.getAddress(), BigInteger.ZERO);
    }

    @Test
    void liquidate_liquidationRatioNotSet() throws Exception {
        // Arrange
        MockContract<IRC2> iBTC = new MockContract<>(IRC2ScoreInterface.class, sm, admin);
        when(iBTC.mock.symbol()).thenReturn("iBTC");
        when(iBTC.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        loans.invoke(governance.account, "addAsset", iBTC.getAddress(), true, true);
        loans.invoke(governance.account, "setLockingRatio", "iBTC", LOCKING_RATIO);
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", BigInteger.ZERO.toString());
        mockOraclePrice("iBTC", EXA);
        loans.invoke(iBTC.account, "tokenFallback", account.getAddress(), collateral,
                data.toString().getBytes());
        loans.invoke(account, "borrow", "iBTC", "bnUSD", loan, "", new byte[0]);

        // Act & Assert
        String expectedErrorMessage = "Reverted(0): Liquidation ratio for iBTC is not set";
        Executable liquidateWithoutLiquidationRatio = () -> loans.invoke(liquidator, "liquidate",
                account.getAddress().toString(), liquidateAmount, "iBTC");
        expectErrorMessage(liquidateWithoutLiquidationRatio, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void liquidate_iETH() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String collateral_symbol = "iETH";
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger liquidateAmount = BigInteger.valueOf(80).multiply(EXA);
        BigInteger totalLoan = loan.add(expectedFee);
        takeLoaniETH(account, collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee), "iETH");
        BigInteger price = EXA.divide(BigInteger.valueOf(10));
        BigInteger liquidationPrice = price
                .multiply(POINTS.subtract(BigInteger.valueOf(400).add(BigInteger.valueOf(100))))
                .divide(POINTS);
        mockOraclePrice("iETH", price);
        BigInteger minDebtThreshold = BigInteger.valueOf(10).multiply(EXA);
        liquidateSetup(collateral_symbol, BigInteger.valueOf(12000), BigInteger.valueOf(400),
                BigInteger.valueOf(100), minDebtThreshold);
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "iETH");
        BigInteger totalCollateralLiquidated = (liquidateAmount.multiply(EXA)).divide(liquidationPrice);
        BigInteger liquidatorFee = BigInteger.valueOf(400).multiply(totalCollateralLiquidated).divide(POINTS);
        BigInteger daoFundFee = BigInteger.valueOf(100).multiply(totalCollateralLiquidated).divide(POINTS);
        BigInteger expectedLiquidation = totalCollateralLiquidated.subtract(liquidatorFee).subtract(daoFundFee);
        verify(ieth.mock).transfer(eq(liquidator.getAddress()), eq(expectedLiquidation.add(liquidatorFee)),
                any(byte[].class));
        verify(ieth.mock).transfer(eq(mockBalanced.daofund.getAddress()), eq(daoFundFee), any(byte[].class));
        verify(bnusd.mock).burnFrom(liquidator.getAddress(), liquidateAmount);
        // Assert
        verifyPosition(account.getAddress(), collateral.subtract(totalCollateralLiquidated),
                totalLoan.subtract(liquidateAmount), "iETH");
        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets"))
                .get(
                        "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");
        BigInteger expectedBadDebt = BigInteger.ZERO;
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("iETH").get("bad_debt"));
        verify(rewards.mock).updateBalanceAndSupply("Loans", totalLoan.subtract(liquidateAmount),
                account.getAddress().toString(), totalLoan.subtract(liquidateAmount));
        verifyTotalDebt(totalLoan.subtract(liquidateAmount));
    }

    @Test
    void liquidate_wrongCollateralType() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);
        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        mockOraclePrice("sICX", EXA.divide(BigInteger.valueOf(4)));
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "iETH");
        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void liquidate_PositionNotInLiquidateStanding() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);
        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress().toString(), liquidateAmount, "sICX");
        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void liquidate_NoPosition() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger liquidateAmount = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG
                + "This address does not have a position on Balanced.";
        // Assert & Act
        Executable liquidateAccountWithNoPosition = () -> loans.invoke(liquidator, "liquidate",
                account.getAddress().toString(), liquidateAmount,
                "sICX");
        expectErrorMessage(liquidateAccountWithNoPosition, expectedErrorMessage);
    }

    @Test
    void redeemCollateral_sICX() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account account3 = sm.createAccount();
        Account account4 = sm.createAccount();
        Account redeemer = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(4000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(400).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger debt = loan.add(expectedFee);

        BigInteger maxRedemptionPercentage = (BigInteger) loans.call("getMaxRetirePercent");
        BigInteger redemptionFee = (BigInteger) loans.call("getRedemptionFee");
        BigInteger daoFeePercentage = (BigInteger) loans.call("getRedemptionDaoFee");

        BigInteger amountToRedeem = BigInteger.valueOf(10).multiply(EXA);
        BigInteger expectedDaoFee = daoFeePercentage.multiply(amountToRedeem).divide(POINTS);
        BigInteger amountRedeemed = amountToRedeem.subtract(expectedDaoFee);
        when(bnusd.mock.balanceOf(redeemer.getAddress())).thenReturn(amountToRedeem);

        BigInteger maxRetire = maxRedemptionPercentage.multiply(debt).divide(POINTS);
        BigInteger expectedRepaidAccount1 = maxRetire;
        BigInteger expectedRepaidAccount2 = maxRetire;
        BigInteger expectedRepaidAccount3 = amountRedeemed.subtract(maxRetire).subtract(maxRetire);

        BigInteger sICXRate = EXA.divide(BigInteger.TWO);
        mockOraclePrice("sICX", sICXRate);

        BigInteger totalRedemptionFee = amountRedeemed.multiply(redemptionFee).divide(POINTS);
        BigInteger totalCollateralRedeemed = amountRedeemed.subtract(totalRedemptionFee).multiply(EXA).divide(sICXRate);

        BigInteger feeAccount1 = expectedRepaidAccount1.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount1 = expectedRepaidAccount1.subtract(feeAccount1).multiply(EXA)
                .divide(sICXRate);
        BigInteger collateralRedeemedAccount2 = collateralRedeemedAccount1;

        BigInteger feeAccount3 = expectedRepaidAccount3.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount3 = expectedRepaidAccount3.subtract(feeAccount3).multiply(EXA)
                .divide(sICXRate);

        // Act
        takeLoanICX(account1, "bnUSD", collateral, loan);
        takeLoanICX(account2, "bnUSD", collateral, loan);
        takeLoanICX(account3, "bnUSD", collateral, loan);
        takeLoanICX(account4, "bnUSD", collateral, loan);

        BigInteger redeemableAmount = (BigInteger) loans.call("getRedeemableAmount", sicx.getAddress(), 4);
        loans.invoke(redeemer, "redeemCollateral", sicx.getAddress(), amountToRedeem);

        // Assert
        verify(bnusd.mock).burnFrom(redeemer.getAddress(), amountToRedeem);
        verify(bnusd.mock).mintTo(mockBalanced.daofund.getAddress(), expectedDaoFee, new byte[0]);

        verifyPosition(account1.getAddress(), collateral.subtract(collateralRedeemedAccount1),
                debt.subtract(expectedRepaidAccount1), "sICX");
        verifyPosition(account2.getAddress(), collateral.subtract(collateralRedeemedAccount2),
                debt.subtract(expectedRepaidAccount2), "sICX");
        verifyPosition(account3.getAddress(), collateral.subtract(collateralRedeemedAccount3),
                debt.subtract(expectedRepaidAccount3), "sICX");
        verifyPosition(account4.getAddress(), collateral, debt, "sICX");
        assertEquals(totalCollateralRedeemed,
                collateralRedeemedAccount1.add(collateralRedeemedAccount2).add(collateralRedeemedAccount3));
        verify(sicx.mock).transfer(eq(redeemer.getAddress()), eq(totalCollateralRedeemed), any(byte[].class));

        assertEquals(redeemableAmount,
                debt.multiply(BigInteger.valueOf(4)).multiply(maxRedemptionPercentage).divide(POINTS));
    }

    @Test
    void redeemCollateral_redeemAboveMax() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account redeemer = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(4000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(400).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger debt = loan.add(expectedFee);

        BigInteger sICXRate = EXA.divide(BigInteger.TWO);
        mockOraclePrice("sICX", sICXRate);

        // Act && Assert
        takeLoanICX(account1, "bnUSD", collateral, loan);
        takeLoanICX(account2, "bnUSD", collateral, loan);

        String expectedErrorMessage = "Reached end of list";
        Executable checkAboveMaxSize = () -> loans.call("getRedeemableAmount", sicx.getAddress(), 3);
        Executable redeemAboveMaxSize = () -> loans.invoke(redeemer, "redeemCollateral", sicx.getAddress(), debt);
        expectErrorMessage(checkAboveMaxSize, expectedErrorMessage);
        expectErrorMessage(redeemAboveMaxSize, expectedErrorMessage);
    }

    @Test
    void redeemCollateral_iETH() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account account3 = sm.createAccount();
        Account account4 = sm.createAccount();
        Account redeemer = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(4000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(400).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger debt = loan.add(expectedFee);

        BigInteger maxRedemptionPercentage = (BigInteger) loans.call("getMaxRetirePercent");
        BigInteger redemptionFee = (BigInteger) loans.call("getRedemptionFee");
        BigInteger daoFeePercentage = (BigInteger) loans.call("getRedemptionDaoFee");

        BigInteger amountToRedeem = BigInteger.valueOf(10).multiply(EXA);
        BigInteger expectedDaoFee = daoFeePercentage.multiply(amountToRedeem).divide(POINTS);
        BigInteger amountRedeemed = amountToRedeem.subtract(expectedDaoFee);
        when(bnusd.mock.balanceOf(redeemer.getAddress())).thenReturn(amountToRedeem);

        BigInteger maxRetire = maxRedemptionPercentage.multiply(debt).divide(POINTS);
        BigInteger expectedRepaidAccount1 = maxRetire;
        BigInteger expectedRepaidAccount2 = maxRetire;
        BigInteger expectedRepaidAccount3 = amountRedeemed.subtract(maxRetire).subtract(maxRetire);

        BigInteger iETHRate = EXA;
        BigInteger bnUSDRate = BigInteger.ONE.multiply(EXA);
        mockOraclePrice("iETH", iETHRate);
        mockOraclePrice("bnUSD", bnUSDRate);

        BigInteger totalRedemptionFee = amountRedeemed.multiply(redemptionFee).divide(POINTS);
        BigInteger totalCollateralRedeemed = amountRedeemed.subtract(totalRedemptionFee).multiply(bnUSDRate)
                .divide(iETHRate);

        BigInteger feeAccount1 = expectedRepaidAccount1.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount1 = expectedRepaidAccount1.subtract(feeAccount1).multiply(bnUSDRate)
                .divide(iETHRate);
        BigInteger collateralRedeemedAccount2 = collateralRedeemedAccount1;

        BigInteger feeAccount3 = expectedRepaidAccount3.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount3 = expectedRepaidAccount3.subtract(feeAccount3).multiply(bnUSDRate)
                .divide(iETHRate);

        // Act
        takeLoaniETH(account1, collateral, loan);
        takeLoaniETH(account2, collateral, loan);
        takeLoaniETH(account3, collateral, loan);
        takeLoaniETH(account4, collateral, loan);

        BigInteger redeemableAmount = (BigInteger) loans.call("getRedeemableAmount", ieth.getAddress(), 4);
        loans.invoke(redeemer, "redeemCollateral", ieth.getAddress(), amountToRedeem);

        // Assert
        verify(bnusd.mock).burnFrom(redeemer.getAddress(), amountToRedeem);
        verify(bnusd.mock).mintTo(mockBalanced.daofund.getAddress(), expectedDaoFee, new byte[0]);

        verifyPosition(account1.getAddress(), collateral.subtract(collateralRedeemedAccount1),
                debt.subtract(expectedRepaidAccount1), "iETH");
        verifyPosition(account2.getAddress(), collateral.subtract(collateralRedeemedAccount2),
                debt.subtract(expectedRepaidAccount2), "iETH");
        verifyPosition(account3.getAddress(), collateral.subtract(collateralRedeemedAccount3),
                debt.subtract(expectedRepaidAccount3), "iETH");
        verifyPosition(account4.getAddress(), collateral, debt, "iETH");
        assertEquals(totalCollateralRedeemed,
                collateralRedeemedAccount1.add(collateralRedeemedAccount2).add(collateralRedeemedAccount3));
        verify(ieth.mock).transfer(eq(redeemer.getAddress()), eq(totalCollateralRedeemed), any(byte[].class));

        assertEquals(redeemableAmount,
                debt.multiply(BigInteger.valueOf(4)).multiply(maxRedemptionPercentage).divide(POINTS));
    }

    @Test
    void getBorrowerData() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account account3 = sm.createAccount();
        Account account4 = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(4000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(400).multiply(EXA);

        // Act
        takeLoanICX(account1, "bnUSD", collateral, loan);
        takeLoanICX(account2, "bnUSD", collateral, loan);
        takeLoanICX(account3, "bnUSD", collateral, loan);
        takeLoanICX(account4, "bnUSD", collateral, loan);
        takeLoaniETH(account3, collateral, loan);
        takeLoaniETH(account2, collateral, loan);

        // Assert
        int icxCount = (int) loans.call("getBorrowerCount", sicx.getAddress());
        int iETHCount = (int) loans.call("getBorrowerCount", ieth.getAddress());
        int sICXHead = (int) loans.call("getBorrowerHead", sicx.getAddress());
        int iETHHead = (int) loans.call("getBorrowerHead", ieth.getAddress());
        int sICXTail = (int) loans.call("getBorrowerTail", sicx.getAddress());
        int iETHTail = (int) loans.call("getBorrowerTail", ieth.getAddress());

        List<Map<String, Object>> sICXBorrowers = (List<Map<String, Object>>) loans.call("getBorrowers",
                sicx.getAddress(), 3, 0);
        List<Map<String, Object>> sICXTailBorrower = (List<Map<String, Object>>) loans.call("getBorrowers",
                sicx.getAddress(), 1, sICXBorrowers.get(2).get("nextId"));
        List<Map<String, Object>> iETHBorrowers = (List<Map<String, Object>>) loans.call("getBorrowers",
                ieth.getAddress(), iETHCount, iETHTail);

        assertEquals(4, icxCount);
        assertEquals(2, iETHCount);
        assertEquals(1, sICXHead);
        assertEquals(3, iETHHead);
        assertEquals(4, sICXTail);
        assertEquals(2, iETHTail);

        assertEquals(account1.getAddress().toString(), sICXBorrowers.get(0).get("address"));
        assertEquals(account2.getAddress().toString(), sICXBorrowers.get(1).get("address"));
        assertEquals(account3.getAddress().toString(), sICXBorrowers.get(2).get("address"));
        assertEquals(3, sICXBorrowers.size());

        assertEquals(account4.getAddress().toString(), sICXTailBorrower.get(0).get("address"));
        assertEquals(1, sICXTailBorrower.size());

        assertEquals(account2.getAddress().toString(), iETHBorrowers.get(0).get("address"));
        assertEquals(account3.getAddress().toString(), iETHBorrowers.get(1).get("address"));
        assertEquals(2, iETHBorrowers.size());
    }

    @ParameterizedTest
    @MethodSource("liquidationData")
    void liquidate_calculations(BigInteger amount, BigInteger collateral, BigInteger totalDebt, BigInteger fee,
            BigInteger collateralDecimals, BigInteger collateralPrice, BigInteger liquidationRatio,
            BigInteger minDebtThreshold, BigInteger expectedRatio) {

        LiquidationResult res = (LiquidationResult) loans.call("_liquidate", amount, collateral, totalDebt, fee,
                collateralDecimals, collateralPrice, liquidationRatio, minDebtThreshold);

        BigInteger remainingCollateral = collateral.subtract(res.collateralToLiquidate).multiply(collateralPrice).divide(collateralDecimals);
        BigInteger remainingDebt = totalDebt.subtract(res.liquidationAmount);

        BigInteger ratio = BigInteger.ZERO;
        if (remainingDebt.compareTo(BigInteger.ZERO) > 0) {
            ratio = remainingCollateral.multiply(EXA).divide(remainingDebt).multiply(POINTS).divide(EXA);
        }

        BigInteger liquidationPrice = collateralPrice.multiply(POINTS.subtract(fee)).divide(POINTS);
        BigInteger expectedCollateralValue = res.collateralToLiquidate.multiply(collateralPrice).divide(collateralDecimals);
        expectedCollateralValue = expectedCollateralValue.multiply(POINTS.subtract(fee)).divide(POINTS);

        // Round the USD so that it only buys whole collateral tokens. This mean the liquidator can be a partial collateral decimal short
        res.liquidationAmount = res.liquidationAmount.multiply(collateralDecimals).divide(liquidationPrice).multiply(liquidationPrice).divide(collateralDecimals);

        // Avoid small rounding errors
        assertTrue(expectedRatio.compareTo(ratio.add(BigInteger.ONE)) <= 0 && expectedRatio.compareTo(ratio.subtract(BigInteger.ONE)) >= 0 );
        assertTrue(expectedCollateralValue.compareTo(res.liquidationAmount.add(BigInteger.ONE)) <= 0 && expectedCollateralValue.compareTo(res.liquidationAmount.subtract(BigInteger.ONE)) >= 0 );
    }

    static Stream<Arguments> liquidationData() {
        return List.of(simpleLiquidations(18), simpleLiquidations(6), simpleLiquidations(9), simpleLiquidations(22),
                       calculatedLiquidations(18),calculatedLiquidations(6),calculatedLiquidations(9),calculatedLiquidations(22)
                ).stream()
        .reduce(Stream::concat)
        .orElseGet(Stream::empty);

    }

    private static Stream<Arguments> calculatedLiquidations(int decimals) {
        Arguments[] arguments = new Arguments[1];

        // Partial liquidation simple
        BigInteger amount = createAmount(5);
        BigInteger collateral = createCollateral(100, decimals);
        BigInteger collateralValue = collateral.multiply(EXA).divide(BigInteger.TEN.pow(decimals));
        BigInteger debt = createTotalDebt(86);
        BigInteger newRatio = collateralValue.subtract(amount).multiply(EXA).multiply(POINTS).divide(debt.subtract(amount)).divide(EXA);
        arguments[0] = Arguments.of(
                amount,
                collateral,
                debt,
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                newRatio
            );


        return Stream.of(arguments);
    }

    private static Stream<Arguments> simpleLiquidations(int decimals) {
        return Stream.of(
            // Normal liquidation goes back to limit
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(86),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(11750)
            ),

            // Normal liquidation goes back to limit
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(100),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(0)
            ),

            // Liquidation goes into bad debt, all collateral gets liquidated
            Arguments.of(
                createAmount(105),
                createCollateral(100, decimals),
                createTotalDebt(105),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(0)
            ),

            // The fee makes it so that the whole position is liquidated
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(96),
                createFee(500),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(0)
            ),

            // Fee is taken but liquidated to threshold with a 1% margin
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(94),
                createFee(500),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(11750)
            ),

            // Normal liquidation but the minimum debt threshold is hit and the whole position is liquidated
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(86),
                createFee(500),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(11750),
                createMinDebtThreshold(80),
                createExpectedRatio(0)
            ),

            // Normal liquidation with new ratio (~71%)
            Arguments.of(
                createAmount(100),
                createCollateral(100, decimals),
                createTotalDebt(74),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA),
                createLiquidationRatio(14500),
                createMinDebtThreshold(0),
                createExpectedRatio(14500)
            ),

            // Normal liquidation goes back to limit with lower price
            Arguments.of(
                createAmount(100),
                createCollateral(1000, decimals),
                createTotalDebt(86),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA.divide(BigInteger.TEN)),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(11750)
            ),

            // Normal liquidation goes back to limit with higher price
            Arguments.of(
                createAmount(100),
                createCollateral(1, decimals),
                createTotalDebt(86),
                createFee(0),
                createCollateralDecimals(decimals),
                createCollateralPrice(EXA.multiply(BigInteger.valueOf(100))),
                createLiquidationRatio(11750),
                createMinDebtThreshold(0),
                createExpectedRatio(11750)
            )
        );
    }

    // Helper methods to improve readability
    private static BigInteger createAmount(long value) {
        return BigInteger.valueOf(value).multiply(EXA);
    }

    private static BigInteger createCollateral(long value, int decimal) {
        return BigInteger.valueOf(value).multiply(BigInteger.TEN.pow(decimal));
    }

    private static BigInteger createTotalDebt(long value) {
        return BigInteger.valueOf(value).multiply(EXA);
    }

    private static BigInteger createFee(long value) {
        return BigInteger.valueOf(value);
    }

    private static BigInteger createCollateralDecimals(int value) {
        return BigInteger.TEN.pow(value);
    }

    private static BigInteger createCollateralPrice(BigInteger value) {
        return value;
    }

    private static BigInteger createLiquidationRatio(long value) {
        return BigInteger.valueOf(value);
    }

    private static BigInteger createMinDebtThreshold(long value) {
        return BigInteger.valueOf(value).multiply(EXA);
    }

    private static BigInteger createExpectedRatio(long value) {
        return BigInteger.valueOf(value);
    }


}