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

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import network.balanced.score.core.loans.utils.LoansConstants.Standings;
import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.mock.MockContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.LOCKING_RATIO;
import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


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
        loans.invoke(governance.account, "setLiquidationReward", BigInteger.valueOf(6));
        loans.invoke(governance.account, "setRedemptionFee", BigInteger.valueOf(7));
        loans.invoke(governance.account, "setOriginationFee", BigInteger.valueOf(8));
        loans.invoke(governance.account, "setLiquidationRatio", "sICX", BigInteger.valueOf(9));
        loans.invoke(governance.account, "setLockingRatio", "sICX", BigInteger.valueOf(10));

        Map<String, Object> params = (Map<String, Object>) loans.call("getParameters");

        assertEquals(BigInteger.valueOf(2), params.get("retire percent max"));
        assertEquals(BigInteger.valueOf(3), params.get("time offset"));
        assertEquals(BigInteger.valueOf(5), params.get("new loan minimum"));
        assertEquals(BigInteger.valueOf(6), params.get("liquidation reward"));
        assertEquals(BigInteger.valueOf(7), params.get("redemption fee"));
        assertEquals(BigInteger.valueOf(8), params.get("origination fee"));
        assertEquals(BigInteger.valueOf(9), params.get("liquidation ratio"));
        assertEquals(BigInteger.valueOf(10), params.get("locking ratio"));
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
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assets = (Map<String, Map<String, BigInteger>>) position.get("holdings");
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>) position.get("standings");

        assertEquals(1, position.get("pos_id"));
        assertEquals(account.getAddress().toString(), position.get("address"));

        assertEquals(loan.add(expectedFee), standings.get("sICX").get("total_debt"));
        assertEquals(sICXCollateral, standings.get("sICX").get("collateral"));
        assertEquals(sICXCollateral.multiply(EXA).divide(loan.add(expectedFee)), standings.get("sICX").get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), standings.get("sICX").get("standing"));

        assertEquals(iETHloan.add(iETHExpectedFee), standings.get("iETH").get("total_debt"));
        assertEquals(iETHCollateral, standings.get("iETH").get("collateral"));
        assertEquals(iETHCollateral.multiply(EXA).divide(iETHloan.add(iETHExpectedFee)), standings.get("iETH").get(
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
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans"
                , account.getAddress());
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
        balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", account.getAddress());
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
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans"
                , zeroAccount.getAddress());

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
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger>>) position.get(
                "holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_LowerThanMinimum() {
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
    void DepositAndBorrow_OriginateLoan_ToLargeDebt() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(500).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger lockingRatio = ((BigInteger) getParam("locking ratio")).divide(POINTS);

        String expectedErrorMessage = "Reverted(0): " + TAG + collateral + " collateral is insufficient to originate " +
                "a loan of " + loan + " bnUSD " +
                "when max_debt_value = " + collateral.divide(lockingRatio) + ", new_debt_value = " + loan.add(expectedFee) + ", " +
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
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        loans.invoke(account, "borrow", "sICX", "bnUSD", loan);
        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        BigInteger sICXCollateralInLoop = collateral.multiply(rate);
        BigInteger iETHCollateralInLoop = collateral.multiply(rate);
        BigInteger bnUSDPriceInLoop = rate;
        BigInteger sICXLockingRatio = BigInteger.valueOf(30_000);
        BigInteger iETHLockingRatio = BigInteger.valueOf(15_000);

        BigInteger sICXMaxDebt = POINTS.multiply(sICXCollateralInLoop).divide(sICXLockingRatio);
        BigInteger sICXMaxLoan =
                sICXMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(bnUSDPriceInLoop);

        BigInteger iETHMaxDebt = POINTS.multiply(iETHCollateralInLoop).divide(iETHLockingRatio);
        BigInteger iETHMaxLoan =
                iETHMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(bnUSDPriceInLoop);

        // Act
        loans.invoke(governance.account, "setLockingRatio", "sICX", sICXLockingRatio);
        loans.invoke(governance.account, "setLockingRatio", "iETH", iETHLockingRatio);

        // Assert
        Executable borrowSICX = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan.add(BigInteger.ONE));
        Executable borrowIETH = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan.add(BigInteger.ONE));
        expectErrorMessage(borrowSICX, "collateral is insufficient to originate a loan of");
        expectErrorMessage(borrowIETH, "collateral is insufficient to originate a loan of");

        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan);
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan);

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
        loans.invoke(account, "borrow", "iETH", "bnUSD", loan);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        Executable overDebtCeilingiETH = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan);
        expectErrorMessage(overDebtCeilingiETH, expectedErrorMessage);

        expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral sICX";
        Executable overDebtCeilingsICX = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan);
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
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan);
        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan);

        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger debtCeiling = BigInteger.valueOf(300).multiply(EXA);

        when(bnusd.mock.balanceOf(liquidator.getAddress())).thenReturn(expectedBadDebt);

        loans.invoke(governance.account, "setDebtCeiling", "sICX", debtCeiling);

        takeLoanICX(liquidatedLoanTaker, "bnUSD", collateral, loan);
        verifyPosition(liquidatedLoanTaker.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", liquidatedLoanTaker.getAddress(), "sICX");
        mockOraclePrice("bnUSD", EXA);

        // Assert
        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));

        // Arrange
        BigInteger feePercentage = (BigInteger) getParam("origination fee");
        BigInteger expectedAvailableBnusd = debtCeiling.subtract(expectedBadDebt);
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

        // Act
        loans.invoke(liquidator, "retireBadDebt", "bnUSD", expectedBadDebt);

        // Assert
        takeLoanICX(account, "bnUSD", collateral, loan);
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
        Executable returnToMuch = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", loan);
        expectErrorMessage(returnToMuch, expectedErrorMessage);


        // Assert
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
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
        Executable loanWithoutLockingRatio = () -> loans.invoke(account, "borrow", "iBTC", "bnUSD", loan);
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
        Executable negativeLoan = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan);
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
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        loans.invoke(account, "returnAsset", "bnUSD", iETHloanToRepay, "iETH");

        // Assert
        verify(bnusd.mock).burnFrom(account.getAddress(), loanToRepay);
        verify(bnusd.mock).burnFrom(account.getAddress(), iETHloanToRepay);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyPosition(account.getAddress(), iETHCollateral, iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee),
                "iETH");
        BigInteger expectedTotal =
                loan.subtract(loanToRepay).add(expectedFee).add(iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee));
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
        loans.invoke(account, "returnAsset", "bnUSD", loan.add(expectedFee), "sICX");

        // Assert
        assertFalse((boolean) loans.call("hasDebt", account.getAddress()));
        verifyPosition(account.getAddress(), collateral, BigInteger.ZERO);

        // Act
        takeLoanICX(account, "bnUSD", BigInteger.ZERO, loan);

        // Assert
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));
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
        Executable returnToMuch = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
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
        Executable returnForiETH = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "iETH");
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
                "sICX");
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
        Executable returnWithNoPosition = () -> loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
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
        loans.invoke(loanRepayer, "returnAsset", "bnUSD", loanToRepay, "sICX");

        // Assert
        verify(bnusd.mock).burnFrom(loanRepayer.getAddress(), loanToRepay);
        verifyPosition(loanRepayer.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyTotalDebt(expectedDebt.multiply(BigInteger.TWO).subtract(loanToRepay));
    }

    @Test
    void sellCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(100).multiply(EXA);
        BigInteger minimumReceiveSicxCollateralSell = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHCollateralToSell = BigInteger.valueOf(80).multiply(EXA);
        BigInteger minimumReceiveiETHCollateralSell = BigInteger.valueOf(80).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger rate = EXA.divide(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        mockiETHBnusdPrice(rate);

        BigInteger expectedBnusdRepaidForSicx = collateralToSell.multiply(BigInteger.TWO);
        BigInteger expectedBnusdRepaidForiETH = iETHCollateralToSell.multiply(BigInteger.ONE);
        mockSwap(sicx, bnusd, collateralToSell, expectedBnusdRepaidForSicx);
        mockSwap(ieth, bnusd, iETHCollateralToSell, expectedBnusdRepaidForiETH);

        // Act
        loans.invoke(account, "sellCollateral", collateralToSell, "sICX", minimumReceiveSicxCollateralSell);
        loans.invoke(account, "sellCollateral", iETHCollateralToSell, "iETH", minimumReceiveiETHCollateralSell);

        //  Assert
        verifyPosition(account.getAddress(), collateral.subtract(collateralToSell),
                loan.add(expectedFee).subtract(expectedBnusdRepaidForSicx), "sICX");
        verifyPosition(account.getAddress(), collateral.subtract(iETHCollateralToSell),
                loan.add(expectedFee).subtract(expectedBnusdRepaidForiETH), "iETH");
    }

    @Test
    void sellCollateral_ZeroCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(0).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(50).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Sell amount must be more than zero.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable sellZeroCollateral = () -> loans.invoke(account, "sellCollateral", collateralToSell,
                "sICX", minimumReceiveAfterSell);
        expectErrorMessage(sellZeroCollateral, expectedErrorMessage);
    }

    @Test
    void sellCollateral_NoPosition() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateralToSell = BigInteger.valueOf(200).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(50).multiply(EXA);

        String expectedErrorMessage = "Reverted(0): " + TAG + "This address does not have a position on Balanced.";

        // Assert & Act
        Executable sellWithNoPosition = () -> loans.invoke(account, "sellCollateral", collateralToSell,
                "sICX", minimumReceiveAfterSell);
        expectErrorMessage(sellWithNoPosition, expectedErrorMessage);
    }

    @Test
    void sellCollateral_TooMuchCollateral() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(1100).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(50).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Position holds less collateral than the requested " +
                "sell.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable sellTooMuchCollateral = () -> loans.invoke(account, "sellCollateral", collateralToSell
                , "sICX", minimumReceiveAfterSell);
        expectErrorMessage(sellTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void sellCollateral_NoAvailablePool() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(300).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(300).multiply(EXA);
        String collateralSymbol = "sICX";
        String expectedErrorMessage =
                "Reverted(0): " + TAG + "There doesn't exist a bnUSD pool for " + collateralSymbol + ".";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable noAvailablePool = () -> loans.invoke(account, "sellCollateral", collateralToSell
                , collateralSymbol, minimumReceiveAfterSell);
        expectErrorMessage(noAvailablePool, expectedErrorMessage);
    }

    @Test
    void sellCollateral_TooMuchDebt() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(300).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(300).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Minimum receive cannot be greater than your debt.";

        BigInteger rate = EXA.divide(BigInteger.TWO);
        mockSicxBnusdPrice(rate);

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable sellTooMuchDebt = () -> loans.invoke(account, "sellCollateral", collateralToSell
                , "sICX", minimumReceiveAfterSell);
        expectErrorMessage(sellTooMuchDebt, expectedErrorMessage);
    }

    @Test
    void sellCollateral_TooMuchCollateralSell() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToSell = BigInteger.valueOf(400).multiply(EXA);
        BigInteger minimumReceiveAfterSell = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Cannot sell collateral worth more than your debt.";

        BigInteger rate = EXA.divide(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        BigInteger expectedBnusdRepaidForSicx = collateralToSell.multiply(BigInteger.TWO);
        mockSwap(sicx, bnusd, collateralToSell, expectedBnusdRepaidForSicx);

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable sellTooMuchCollateral = () -> loans.invoke(account, "sellCollateral", collateralToSell
                , "sICX", minimumReceiveAfterSell);
        expectErrorMessage(sellTooMuchCollateral, expectedErrorMessage);
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
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw
                , "sICX");
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
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw
                , "iETH");
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
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw
                , "sICX");
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

    @SuppressWarnings("unchecked")
    @Test
    void liquidate() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        // Assert
        verify(sicx.mock).transfer(eq(liquidator.getAddress()), eq(expectedReward), any(byte[].class));
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt.add(loan.add(expectedFee)),
                account.getAddress(), loan.add(expectedFee));
        verifyTotalDebt(originalTotalDebt);
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
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", BigInteger.ZERO.toString());
        mockOraclePrice("iBTC", EXA);

        loans.invoke(iBTC.account, "tokenFallback", account.getAddress(), collateral, data.toString().getBytes());
        loans.invoke(account, "borrow", "iBTC", "bnUSD", loan);

        // Act & Assert
        String expectedErrorMessage = "Reverted(0): Liquidation ratio for iBTC is not set";
        Executable liquidateWithoutLiquidationRatio = () -> loans.invoke(liquidator, "liquidate",
                account.getAddress(), "iBTC");
        expectErrorMessage(liquidateWithoutLiquidationRatio, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void liquidate_iETH() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);

        takeLoaniETH(account, collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee), "iETH");

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");

        // Assert
        verify(ieth.mock).transfer(eq(liquidator.getAddress()), eq(expectedReward), any(byte[].class));
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("iETH").get("liquidation_pool"));

        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt.add(loan.add(expectedFee)),
                account.getAddress(), loan.add(expectedFee));
        verifyTotalDebt(originalTotalDebt);
    }

    @Test
    void liquidate_differentLiquidationRatios() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger debt = loan.add(expectedFee);

        takeLoaniETH(account, collateral, loan);
        takeLoanSICX(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        BigInteger sICXLiquidationRatio = BigInteger.valueOf(12_000);
        BigInteger iETHLiquidationRatio = BigInteger.valueOf(10_000);

        BigInteger rate = EXA;
        mockOraclePrice("sICX", EXA);
        mockOraclePrice("iETH", EXA);

        BigInteger sICXCollateralInLoop = collateral.multiply(EXA);
        BigInteger iETHCollateralInLoop = collateral.multiply(EXA);

        BigInteger sICXLoanLiquidationRatio = sICXLiquidationRatio.multiply(EXA).divide(POINTS);
        BigInteger iETHLoanLiquidationRatio = iETHLiquidationRatio.multiply(EXA).divide(POINTS);
        BigInteger sICXLiquidationPrice =
                sICXCollateralInLoop.multiply(EXA).divide(debt.multiply(sICXLoanLiquidationRatio));
        BigInteger iETHLiquidationPrice =
                iETHCollateralInLoop.multiply(EXA).divide(debt.multiply(iETHLoanLiquidationRatio));

        loans.invoke(governance.account, "setLiquidationRatio", "sICX", sICXLiquidationRatio);
        loans.invoke(governance.account, "setLiquidationRatio", "iETH", iETHLiquidationRatio);

        // Act & Assert
        BigInteger bnusdPrice = sICXLiquidationPrice.subtract(BigInteger.TEN);
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), collateral, debt, "sICX");
        verifyPosition(account.getAddress(), collateral, debt, "iETH");

        bnusdPrice = sICXLiquidationPrice;
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "sICX");
        verifyPosition(account.getAddress(), collateral, debt, "iETH");

        // Act & Assert
        bnusdPrice = iETHLiquidationPrice.subtract(BigInteger.TEN);
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), collateral, debt, "iETH");

        mockOraclePrice("bnUSD", iETHLiquidationPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "iETH");
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void liquidate_wrongCollateralType() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");

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

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        // Act
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void liquidate_NoPosition() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): " + TAG + "This address does not have a position on Balanced.";

        // Assert & Act
        Executable liquidateAccountWithNoPosition = () -> loans.invoke(liquidator, "liquidate", account.getAddress(),
                "sICX");
        expectErrorMessage(liquidateAccountWithNoPosition, expectedErrorMessage);
    }

    @SuppressWarnings("unchecked")
    @Test
    void retireBadDebt_retireAll() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(expectedDebt.multiply(BigInteger.TWO));

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtSICX = expectedDebt;
        BigInteger badDebtIETH = expectedDebt;

        // Act
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtSICX.add(badDebtIETH));

        // Assert
        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = EXA;
        BigInteger iETHPrice = EXA;
        BigInteger debtInsICX = bonus.multiply(badDebtSICX).multiply(newPrice).divide(icxPrice.multiply(POINTS));
        BigInteger debtIniETH = bonus.multiply(badDebtIETH).multiply(newPrice).divide(iETHPrice.multiply(POINTS));

        verify(sicx.mock).transfer(eq(badDebtReedemer.getAddress()), eq(debtInsICX), any(byte[].class));
        verify(ieth.mock).transfer(eq(badDebtReedemer.getAddress()), eq(debtIniETH), any(byte[].class));
        verify(bnusd.mock, times(2)).burnFrom(badDebtReedemer.getAddress(), badDebtSICX);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("liquidation_pool"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retireBadDebt_retirePartial() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(expectedDebt.multiply(BigInteger.TWO));

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtSICX = expectedDebt;
        BigInteger badDebtIETH = expectedDebt;
        BigInteger badDebtToRedeem = badDebtSICX.divide(BigInteger.TWO).add(badDebtIETH);

        // Act
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtToRedeem);


        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, BigInteger>> bnusdDebtDetails =
                (Map<String, Map<String, BigInteger>>) bnusdAsset.get("debt_details");

        assertEquals(badDebtIETH.add(badDebtIETH).subtract(badDebtToRedeem),
                bnusdDebtDetails.get("sICX").get("bad_debt").add(bnusdDebtDetails.get("iETH").get("bad_debt")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retireBadDebt_UseReserve() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebtRedeemed = loan.add(expectedFee);

        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(badDebtRedeemed);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger pricePreLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        BigInteger pricePostLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(6));

        mockOraclePrice("bnUSD", pricePreLiquidation);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        mockOraclePrice("bnUSD", pricePostLiquidation);

        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = EXA;
        mockOraclePrice("sICX", icxPrice);
        BigInteger debtInsICX =
                bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));

        // Act

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtRedeemed);

        // Assert
        BigInteger valueNeededFromReserve = amountRedeemed.multiply(icxPrice).divide(EXA);
        verify(reserve.mock).redeem(badDebtReedemer.getAddress(), valueNeededFromReserve, "sICX");

        verify(bnusd.mock).burnFrom(badDebtReedemer.getAddress(), badDebtRedeemed);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retireBadDebtForCollateral_UseReserve_sICX() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebtRedeemed = loan.add(expectedFee);

        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(badDebtRedeemed);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger pricePreLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        BigInteger pricePostLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(6));

        mockOraclePrice("bnUSD", pricePreLiquidation);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        mockOraclePrice("bnUSD", pricePostLiquidation);

        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = EXA;
        mockOraclePrice("sICX", icxPrice);

        BigInteger debtInsICX =
                bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));

        // Act

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        loans.invoke(badDebtReedemer, "retireBadDebtForCollateral", "bnUSD", badDebtRedeemed, "sICX");

        // Assert
        BigInteger valueNeededFromReserve = amountRedeemed.multiply(icxPrice).divide(EXA);
        verify(reserve.mock).redeem(badDebtReedemer.getAddress(), valueNeededFromReserve, "sICX");

        verify(bnusd.mock).burnFrom(badDebtReedemer.getAddress(), badDebtRedeemed);
        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void retireBadDebtForCollateral_UseReserve_IETH() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);

        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(expectedDebt.multiply(BigInteger.TWO));

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidator, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtIETH = expectedDebt;

        // Act

        loans.invoke(badDebtReedemer, "retireBadDebtForCollateral", "bnUSD", badDebtIETH, "iETH");

        // Assert
        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger iETHPrice = EXA;
        BigInteger debtIniETH = bonus.multiply(badDebtIETH).multiply(newPrice).divide(iETHPrice.multiply(POINTS));

        verify(bnusd.mock).burnFrom(badDebtReedemer.getAddress(), badDebtIETH);
        verify(ieth.mock).transfer(eq(badDebtReedemer.getAddress()), eq(debtIniETH), any(byte[].class));

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>) loans.call("getAvailableAssets")).get(
                "bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get(
                "debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("liquidation_pool"));
    }

    @Test
    void retireBadDebt_ZeroAmount() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): " + TAG + "Amount retired must be greater than zero.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(loan.add(expectedFee));
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        Executable retireBadDebtZeroAmount = () -> loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD",
                BigInteger.ZERO);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_InsufficientBalance() {
        // Arrange
        Account account = sm.createAccount();
        Account liquidator = sm.createAccount();
        Account badDebtReedemer = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): " + TAG + "Insufficient balance.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);
        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(badDebt.subtract(BigInteger.ONE));

        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidator, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        Executable retireBadDebtZeroAmount = () -> loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_NoBadDebt() {
        // Arrange
        Account badDebtReedemer = sm.createAccount();
        BigInteger badDebt = BigInteger.valueOf(200).multiply(EXA);
        when(bnusd.mock.balanceOf(badDebtReedemer.getAddress())).thenReturn(badDebt);

        String expectedErrorMessage = "Reverted(0): " + TAG + "No bad debt for bnUSD";

        // Assert & Act
        Executable retireBadDebtZeroAmount = () -> loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
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

        BigInteger sICXRate = EXA;
        BigInteger bnUSDRate = BigInteger.TWO.multiply(EXA);
        mockOraclePrice("sICX", sICXRate);
        mockOraclePrice("bnUSD", bnUSDRate);

        BigInteger totalRedemptionFee = amountRedeemed.multiply(redemptionFee).divide(POINTS);
        BigInteger totalCollateralRedeemed =
                amountRedeemed.subtract(totalRedemptionFee).multiply(bnUSDRate).divide(sICXRate);

        BigInteger feeAccount1 = expectedRepaidAccount1.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount1 =
                expectedRepaidAccount1.subtract(feeAccount1).multiply(bnUSDRate).divide(sICXRate);
        BigInteger collateralRedeemedAccount2 = collateralRedeemedAccount1;

        BigInteger feeAccount3 = expectedRepaidAccount3.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount3 =
                expectedRepaidAccount3.subtract(feeAccount3).multiply(bnUSDRate).divide(sICXRate);

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
        BigInteger totalCollateralRedeemed =
                amountRedeemed.subtract(totalRedemptionFee).multiply(bnUSDRate).divide(iETHRate);

        BigInteger feeAccount1 = expectedRepaidAccount1.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount1 =
                expectedRepaidAccount1.subtract(feeAccount1).multiply(bnUSDRate).divide(iETHRate);
        BigInteger collateralRedeemedAccount2 = collateralRedeemedAccount1;

        BigInteger feeAccount3 = expectedRepaidAccount3.multiply(redemptionFee).divide(POINTS);
        BigInteger collateralRedeemedAccount3 =
                expectedRepaidAccount3.subtract(feeAccount3).multiply(bnUSDRate).divide(iETHRate);

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
}