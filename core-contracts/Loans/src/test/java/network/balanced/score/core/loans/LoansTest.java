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

import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static network.balanced.score.core.loans.utils.LoansConstants.LOCKING_RATIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;

import network.balanced.score.core.loans.utils.LoansConstants.Standings;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.structs.RewardsDataEntry;
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
        assertTrue(!loansOn);
    }

    @Test
    void getSetParameters() {
        loans.invoke(admin, "setRedeemBatchSize", 1);
        loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(2));
        governanceCall("setTimeOffset", BigInteger.valueOf(3));
        loans.invoke(admin, "setNewLoanMinimum", BigInteger.valueOf(5));
        loans.invoke(admin, "setLiquidationReward", BigInteger.valueOf(6));
        loans.invoke(admin, "setRedemptionFee", BigInteger.valueOf(7));
        loans.invoke(admin, "setOriginationFee", BigInteger.valueOf(8));
        loans.invoke(admin, "setLiquidationRatio", "sICX", BigInteger.valueOf(9));
        loans.invoke(admin, "setLockingRatio", "sICX", BigInteger.valueOf(10));

        loans.invoke(admin, "setStaking", staking.getAddress());
        loans.invoke(admin, "setRewards", rewards.getAddress());
        loans.invoke(admin, "setReserve", reserve.getAddress());
        loans.invoke(admin, "setDividends", dividends.getAddress());

        Map<String, Object> params = (Map<String, Object>)loans.call("getParameters");
        assertEquals(admin.getAddress(), params.get("admin"));
        assertEquals(governance.getAddress(), params.get("governance"));
        assertEquals(staking.getAddress(), params.get("staking"));
        assertEquals(rewards.getAddress(), params.get("rewards"));
        assertEquals(reserve.getAddress(), params.get("reserve_fund"));
        assertEquals(dividends.getAddress(), params.get("dividends"));

        assertEquals(1, params.get("redeem batch size"));
        assertEquals(BigInteger.valueOf(2), params.get("retire percent max"));
        assertEquals(BigInteger.valueOf(3), params.get("time offset"));
        assertEquals(BigInteger.valueOf(5), params.get("new loan minimum"));
        assertEquals(BigInteger.valueOf(6), params.get("liquidation reward"));
        assertEquals(BigInteger.valueOf(7), params.get("redemption fee"));
        assertEquals(BigInteger.valueOf(8), params.get("origination fee"));
        assertEquals(BigInteger.valueOf(9), params.get("liquidation ratio"));
        assertEquals(BigInteger.valueOf(10), params.get("locking ratio"));
        assertEquals(400, params.get("max div debt length"));
    }

    @Test
    void setMaxRetirePercent_ToLow() {
        Executable setMaxRetirePercent = () -> loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(-1));
        String expectedErrorMessage = "Reverted(0): Input parameter must be in the range 0 to 10000 points.";

        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void setMaxRetirePercent_ToHigh() {
        Executable setMaxRetirePercent = () -> loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(-2));
        String expectedErrorMessage = "Reverted(0): Input parameter must be in the range 0 to 10000 points.";

        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void setGetDebtCeiling() {
        BigInteger ceiling = BigInteger.TEN.pow(23);
        assertOnlyCallableByAdmin(loans, "setDebtCeiling", "sICX", ceiling);

        loans.invoke(admin, "setDebtCeiling", "sICX", ceiling);

        assertEquals(ceiling, loans.call("getDebtCeiling", "sICX"));
        assertEquals(null, loans.call("getDebtCeiling", "iETH"));
    }

    @Test
    void getAccountPositions() {
        // Arrange
        Account account = accounts.get(0);
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
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>)position.get("standings");

        assertEquals(1, position.get("pos_id"));
        assertEquals(account.getAddress().toString(), position.get("address"));
      
        assertEquals(loan.add(expectedFee), standings.get("sICX").get("total_debt"));
        assertEquals(sICXCollateral, standings.get("sICX").get("collateral"));
        assertEquals(sICXCollateral.multiply(EXA).divide(loan.add(expectedFee)), standings.get("sICX").get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), standings.get("sICX").get("standing"));

        assertEquals(iETHloan.add(iETHExpectedFee), standings.get("iETH").get("total_debt"));
        assertEquals(iETHCollateral, standings.get("iETH").get("collateral"));
        assertEquals(iETHCollateral.multiply(EXA).divide(iETHloan.add(iETHExpectedFee)), standings.get("iETH").get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), standings.get("iETH").get("standing"));

        assertEquals(loan.add(expectedFee), assets.get("sICX").get("bnUSD"));
        assertEquals(sICXCollateral, assets.get("sICX").get("sICX"));

        assertEquals(iETHloan.add(iETHExpectedFee), assets.get("iETH").get("bnUSD"));
        assertEquals(iETHCollateral, assets.get("iETH").get("iETH"));
    }

    @Test
    void getBalanceAndSupply() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger sICXdebt = loan.add(expectedFee);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", account.getAddress());
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

    @Test
    void getBalanceAndSupply_noPositition() {
        // Arrange
        Account loanTaker = accounts.get(0);
        Account zeroAccount = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanICX(loanTaker, "bnUSD", collateral, loan);

        // Act
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", zeroAccount.getAddress());

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
        Account account = accounts.get(0);
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
        Account account = accounts.get(0);
        BigInteger value = BigInteger.valueOf(0);
        String expectedErrorMessage = "Reverted(0): " + TAG +  "Token value should be a positive number";

        // Assert & Act
        Executable transferToken = () -> sicx.invoke(account, "transfer", loans.getAddress(), value, new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_NonSupportedCollateral() {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + "bnUSD is not a supported collateral type.";

        // Assert & Act
        Executable transferToken = () -> bnusd.invoke(account, "transfer", loans.getAddress(), value, new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_NonSupportedCollateral_SameSymbol() throws Exception {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): BalancedLoans: The Balanced Loans contract does not accept that token type.";
        MockContract<IRC2> fakesICX = new MockContract<IRC2>(IRC2ScoreInterface.class, sm, admin);
        when(fakesICX.mock.symbol()).thenReturn((String)sicx.call("symbol"));

        // Assert & Act
        Executable transferToken = () -> loans.invoke(fakesICX.account, "tokenFallback", account.getAddress(), value, new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_EmptyData() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Token Fallback: Data can't be empty";
        byte[] data = new byte[0];

        // Assert & Act
        Executable transferToken = () -> sicx.invoke(account, "transfer", loans.getAddress(), value, data);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void depositAndBorrow_ICX() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyTotalDebt(loan.add(expectedFee));
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void depositAndBorrow_StakeOnly() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.ZERO;

        // Act
        takeLoanICX(account, "", collateral, loan);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_NotBorrowable() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);;
        String expectedErrorMessage = "Reverted(0): BalancedLoansAssets sICX is not a supported asset.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "sICX", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_NotActive() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Loans of inactive assets are not allowed.";
        loans.invoke(admin, "toggleAssetActive", "bnUSD");

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_LowerThanMinimum() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger newLoanMinimum = (BigInteger) getParam("new loan minimum");

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger toSmallLoan = newLoanMinimum.subtract(BigInteger.ONE.multiply(EXA));
        String expectedErrorMessage = "Reverted(0): " + TAG + "The initial loan of any " +
                "asset must have a minimum value " +
                "of "+ newLoanMinimum.divide(EXA) +" dollars.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, toSmallLoan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_ToLargeDebt() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(500).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger lockingRatio = ((BigInteger)getParam("locking ratio")).divide(POINTS);

        String expectedErrorMessage = "Reverted(0): " + TAG + collateral + " collateral is insufficient to originate a loan of " + loan + " bnUSD " +
                "when max_debt_value = "+ collateral.divide(lockingRatio) + ", new_debt_value = " + loan.add(expectedFee) + ", " +
                "which includes a fee of " + expectedFee +" bnUSD, given an existing loan value of 0.";
        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }


    @Test
    void depositCollateral_sICX() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);

        // Act
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void depositCollateral_iETH() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);

        // Act
        takeLoaniETH(account, collateral, BigInteger.ZERO);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("iETH").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void borrow_sICX() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        // Act 
        loans.invoke(account, "borrow", "sICX", "bnUSD", loan);
        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(expectedDebt, assetHoldings.get("sICX").get("bnUSD"));
        verifyPosition(account.getAddress(), collateral, expectedDebt, "sICX");
        verifyTotalDebt(expectedDebt);
    }

    @Test
    void borrow_differentLockingRatios() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger feePercentage = (BigInteger)getParam("origination fee");
     
        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        BigInteger sICXCollateralInLoop = collateral.multiply((BigInteger) sicx.call("priceInLoop"));        
        BigInteger iETHCollateralInLoop = collateral.multiply((BigInteger) ieth.call("priceInLoop"));        
        BigInteger bnUSDPriceInLoop = (BigInteger) bnusd.call("lastPriceInLoop");        
        BigInteger sICXLockingRatio = BigInteger.valueOf(30_000);        
        BigInteger iETHLockingRatio = BigInteger.valueOf(15_000);

        BigInteger sICXMaxDebt = POINTS.multiply(sICXCollateralInLoop).divide(sICXLockingRatio);
        BigInteger sICXMaxLoan = sICXMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(bnUSDPriceInLoop);

        BigInteger iETHMaxDebt = POINTS.multiply(iETHCollateralInLoop).divide(iETHLockingRatio);
        BigInteger iETHMaxLoan = iETHMaxDebt.multiply(POINTS).divide(feePercentage.add(POINTS)).divide(bnUSDPriceInLoop);

        // Act
        loans.invoke(admin, "setLockingRatio", "sICX", sICXLockingRatio);
        loans.invoke(admin, "setLockingRatio", "iETH", iETHLockingRatio);
        
        // Assert
        Executable borrowSICX = () -> loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan.add(BigInteger.ONE));
        Executable borrowIETH = () -> loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan.add(BigInteger.ONE));
        expectErrorMessage(borrowSICX, "collateral is insufficient to originate a loan of");
        expectErrorMessage(borrowIETH, "collateral is insufficient to originate a loan of");

        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXMaxLoan);
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHMaxLoan);

        verifyTotalDebt(iETHMaxDebt.add(sICXMaxDebt).divide(EXA));
    }

    @Test
    void borrow_iETH() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        takeLoaniETH(account, collateral, BigInteger.ZERO);

        // Act 
        loans.invoke(account, "borrow", "iETH", "bnUSD", loan);
        
        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(expectedDebt, assetHoldings.get("iETH").get("bnUSD"));
        verifyPosition(account.getAddress(), collateral, expectedDebt, "iETH");
        verifyTotalDebt(expectedDebt);
    }

    @Test
    void borrow_withDebtCeilings_toLow() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA);

        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        loans.invoke(admin, "setDebtCeiling", "iETH", iETHLoan);
        loans.invoke(admin, "setDebtCeiling", "sICX", sICXLoan);
        
        // Act & Assert
        String expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral iETH";
        Executable overDebtCeilingiETH = () ->  loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan);
        expectErrorMessage(overDebtCeilingiETH, expectedErrorMessage);

        expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral sICX";
        Executable overDebtCeilingsICX = () ->  loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan);
        expectErrorMessage(overDebtCeilingsICX, expectedErrorMessage);
    }

    @Test
    void borrow_withDebtCeilings() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger iETHLoan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger sICXLoan = BigInteger.valueOf(300).multiply(EXA);
        BigInteger iETHExpectedFee = calculateFee(iETHLoan);
        BigInteger sICXExpectedFee = calculateFee(sICXLoan);

        takeLoaniETH(account, collateral, BigInteger.ZERO);
        takeLoanSICX(account, collateral, BigInteger.ZERO);

        loans.invoke(admin, "setDebtCeiling", "iETH", iETHLoan.add(iETHExpectedFee));
        loans.invoke(admin, "setDebtCeiling", "sICX", sICXLoan.add(sICXExpectedFee));

        // Act   
        loans.invoke(account, "borrow", "iETH", "bnUSD", iETHLoan);
        loans.invoke(account, "borrow", "sICX", "bnUSD", sICXLoan);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("iETH").get("iETH"));
        assertEquals(iETHLoan.add(iETHExpectedFee), assetHoldings.get("iETH").get("bnUSD"));

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(sICXLoan.add(sICXExpectedFee), assetHoldings.get("sICX").get("bnUSD"));

        verifyTotalDebt(sICXLoan.add(sICXExpectedFee).add(iETHLoan.add(iETHExpectedFee)));
    }

    @Test
    void borrow_withDebtCeilings_badDebt() {
        // Arrange
        Account liquidatedLoanTaker = accounts.get(0);
        Account liquidator = accounts.get(1);
        Account account = accounts.get(2);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger debtCeiling = BigInteger.valueOf(300).multiply(EXA);
        bnusd.invoke(admin, "transfer", liquidator.getAddress(), expectedBadDebt, new byte[0]);
        loans.invoke(admin, "setDebtCeiling", "sICX", debtCeiling);

        takeLoanICX(liquidatedLoanTaker, "bnUSD", collateral, loan);
        verifyPosition(liquidatedLoanTaker.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
       
        // Act
        loans.invoke(liquidator, "liquidate", liquidatedLoanTaker.getAddress(), "sICX");
        mockOraclePrice("bnUSD", EXA);

        // Assert
        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));

        // Arrange
        BigInteger feePercentage = (BigInteger)getParam("origination fee");
        BigInteger expectedAvailableBnusd = debtCeiling.subtract(expectedBadDebt);
        BigInteger allowedLoan = expectedAvailableBnusd.multiply(POINTS).divide(POINTS.add(feePercentage));

        // Act
        String expectedErrorMessage = "BalancedLoansPositions: Cannot mint more bnUSD on collateral sICX";
        Executable overDebtCeilingSICX = () -> takeLoanICX(account, "bnUSD", collateral, allowedLoan.add(BigInteger.TWO));
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
    
    @Test
    void borrow_fromWrongCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanSICX(account, collateral, BigInteger.ZERO);


        // Assert & Act
        String expectedErrorMessage = "Reverted(0): " + TAG + "0 collateral is insufficient to originate a loan of " +
                                        loan + " bnUSD when max_debt_value = 0," +
                                        " new_debt_value = " + loan.add(expectedFee) + ", which includes a fee of " + 
                                        expectedFee + " bnUSD, given an existing loan value of 0.";
        Executable returnToMuch = () ->  loans.invoke(account, "borrow", "iETH", "bnUSD", loan);
        expectErrorMessage(returnToMuch, expectedErrorMessage);
      
       
        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, Map<String, BigInteger>> assetHoldings = (Map<String, Map<String, BigInteger> >) position.get("holdings");

        assertEquals(collateral, assetHoldings.get("sICX").get("sICX"));
        assertEquals(BigInteger.ZERO, assetHoldings.get("sICX").get("bnUSD"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void borrow_collateralWithoutLockingRation() throws Exception {
        // Arrange
        MockContract<IRC2> iBTC = new MockContract<IRC2>(IRC2ScoreInterface.class, sm, admin);
        when(iBTC.mock.symbol()).thenReturn("iBTC");
        loans.invoke(admin, "addAsset", iBTC.getAddress(), true, true);

        Account account = accounts.get(0);
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
    void getTotalDebts() {
        // Arrange
        Account account1 = accounts.get(0);
        Account account2 = accounts.get(0);
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
        BigInteger totalDebt = (BigInteger)loans.call("getTotalDebt", "bnUSD");
        BigInteger totalsICXDebt = (BigInteger)loans.call("getTotalCollateralDebt", "sICX", "bnUSD");
        BigInteger totaliETHDebt = (BigInteger)loans.call("getTotalCollateralDebt", "iETH", "bnUSD");
        assertEquals(expectedTotalDebt, totalDebt);
        assertEquals(sICXExpectedDebt, totalsICXDebt);
        assertEquals(iETHExpectedDebt, totaliETHDebt);
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = accounts.get(0);
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
        BigInteger balancePre = (BigInteger) bnusd.call("balanceOf", account.getAddress());

        // Act 
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        loans.invoke(account, "returnAsset", "bnUSD", iETHloanToRepay, "iETH");

        // Assert
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", account.getAddress());
        BigInteger expectedBalance = balancePre.subtract(loanToRepay).subtract(iETHloanToRepay);
        assertEquals(expectedBalance, balancePost);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyPosition(account.getAddress(), iETHCollateral, iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee), "iETH");
        BigInteger expectedTotal = loan.subtract(loanToRepay).add(expectedFee).add(iETHLoan.subtract(iETHloanToRepay).add(iETHExpectedFee));
        verifyTotalDebt(expectedTotal);
    }

    @Test
    void returnAssetAndReopenPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        bnusd.invoke(admin, "transfer", account.getAddress(), expectedFee, new byte[0]);
        loans.invoke(account, "returnAsset", "bnUSD", loan.add(expectedFee), "sICX");

        // Assert
        assertFalse((boolean)loans.call("hasDebt", account.getAddress()));
        verifyPosition(account.getAddress(), collateral, BigInteger.ZERO);

        // Act 
        takeLoanICX(account, "bnUSD", BigInteger.ZERO, loan);

        // Assert
        assertTrue((boolean)loans.call("hasDebt", account.getAddress()));
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        verifyTotalDebt(loan.add(expectedFee));
    }

    @Test
    void returnAsset_MoreThanHoldings() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = loan.add(expectedFee).add(BigInteger.ONE);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Repaid amount is greater than the amount in the position of " + account.getAddress();

        bnusd.invoke(admin, "transfer", account.getAddress(), loanToRepay, new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnToMuch = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        expectErrorMessage(returnToMuch, expectedErrorMessage);
    }

    @Test
    void returnAsset_WrongCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Repaid amount is greater than the amount in the position of " + account.getAddress();

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnForiETH = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "iETH");
        expectErrorMessage(returnForiETH, expectedErrorMessage);
    }

    @Test
    void returnAsset_returnCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): BalancedLoansAssets sICX is not a supported asset.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnCollateral = () ->  loans.invoke(account, "returnAsset", "sICX", collateralToRepay, "sICX");
        expectErrorMessage(returnCollateral, expectedErrorMessage);
    }

    @Test
    void returnAsset_returnNonActiveAssest() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "bnUSD is not an active, borrowable asset on Balanced.";

        takeLoanICX(account, "bnUSD", collateral, loan);
        loans.invoke(admin, "toggleAssetActive", "bnUSD");

        // Assert & Act
        Executable nonActiveAsset = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        expectErrorMessage(nonActiveAsset, expectedErrorMessage);
    }

    @Test
    void returnAsset_InsufficientBalance() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        String expectedErrorMessage = "Reverted(0): " + TAG + "Insufficient balance.";

        takeLoanICX(account, "bnUSD", collateral, loan);
        bnusd.invoke(account, "transfer", admin.getAddress(), loan, new byte[0]);

        // Assert & Act
        Executable returnWithInsufficientBalance = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        expectErrorMessage(returnWithInsufficientBalance, expectedErrorMessage);
    }

    @Test
    void returnAsset_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        bnusd.invoke(admin, "transfer", account.getAddress(), loanToRepay, new byte[0]);

        String expectedErrorMessage = "Reverted(0): " + TAG + "No debt repaid because, " + account.getAddress() + " does not have a position " +
                "in Balanced";

        // Assert & Act
        Executable returnWithNoPosition = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, "sICX");
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void returnAsset_AlreadyAboveCeiling() {
        Account loanTaker = accounts.get(0);
        Account loanRepayer = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        loans.invoke(admin, "setDebtCeiling", "sICX", BigInteger.valueOf(1000).multiply(EXA));
        
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);


        takeLoanICX(loanTaker, "bnUSD", collateral, loan);
        takeLoanICX(loanRepayer, "bnUSD", collateral, loan);
        loans.invoke(admin, "setDebtCeiling", "sICX", BigInteger.valueOf(200).multiply(EXA));
        verifyTotalDebt(expectedDebt.multiply(BigInteger.TWO));

        // Act 
        loans.invoke(loanRepayer, "returnAsset", "bnUSD", loanToRepay, "sICX");
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", loanRepayer.getAddress());

        // Assert
        BigInteger expectedBalance = loan.subtract(loanToRepay);
        assertEquals(expectedBalance, balancePost);
        verifyPosition(loanRepayer.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee), "sICX");
        verifyTotalDebt(expectedDebt.multiply(BigInteger.TWO).subtract(loanToRepay));
    }

    @Test
    void withdrawCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        BigInteger iETHCollateralToWithdraw = BigInteger.valueOf(80).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", account.getAddress());
        BigInteger iETHBalancePre = (BigInteger) ieth.call("balanceOf", account.getAddress());

        // Act
        loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        loans.invoke(account, "withdrawCollateral", iETHCollateralToWithdraw, "iETH");

        // Assert
        BigInteger sICXbalancePost = (BigInteger) sicx.call("balanceOf", account.getAddress());
        BigInteger iETHBalancePost = (BigInteger) ieth.call("balanceOf", account.getAddress());

        assertEquals(sICXBalancePre.add(collateralToWithdraw), sICXbalancePost);
        assertEquals(iETHBalancePre.add(iETHCollateralToWithdraw), iETHBalancePost);
        verifyPosition(account.getAddress(), collateral.subtract(collateralToWithdraw), loan.add(expectedFee), "sICX");
        verifyPosition(account.getAddress(), collateral.subtract(iETHCollateralToWithdraw), loan.add(expectedFee), "iETH");
        verifyTotalDebt(loan.add(expectedFee).add(loan.add(expectedFee)));
    }

    @Test
    void withdrawCollateral_ZeroCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(0);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Withdraw amount must be more than zero.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawZeroCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        expectErrorMessage(withdrawZeroCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateralToWithdraw = BigInteger.valueOf(200).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG +  "This address does not have a position on Balanced.";

        // Assert & Act
        Executable withdrawWithNoPosition = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        expectErrorMessage(withdrawWithNoPosition, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_TooMuchCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(1100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Position holds less collateral than the requested withdrawal.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_WrongCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Position holds less collateral than the requested withdrawal.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "iETH");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NewLockingRatioToLow() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(800).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger lockingRatio = ((BigInteger)getParam("locking ratio")).divide(POINTS);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Requested withdrawal is more than available collateral. " +
                "total debt value: " + loan.add(expectedFee) + " ICX " +
                "remaining collateral value: " + collateral.subtract(collateralToWithdraw) + " ICX " +
                "locking value (max debt): " + lockingRatio.multiply(loan.add(expectedFee)) + " ICX";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw, "sICX");
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawAndUnstake() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);

        // Act
        takeLoanICX(account, "bnUSD", collateral, BigInteger.ZERO);
        loans.invoke(account, "withdrawAndUnstake", collateral);

        // Assert
        JsonObject data = new JsonObject()
            .add("method", "unstake")
            .add("user", account.getAddress().toString());
        verify(staking.mock).tokenFallback(loans.getAddress(), collateral, data.toString().getBytes());
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "sICX");
    }

    @Test
    void liquidate() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        BigInteger liquidaterBalancePre = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
       
        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert
        BigInteger liquidaterBalancePost = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());
        assertEquals(liquidaterBalancePre.add(expectedReward), liquidaterBalancePost);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt.add(loan.add(expectedFee)), account.getAddress(),  loan.add(expectedFee));
        verifyTotalDebt(originalTotalDebt);
    }

    @Test
    void liquidate_liquidationRatioNotSet() throws Exception {
        // Arrange
        MockContract<IRC2> iBTC = new MockContract<IRC2>(IRC2ScoreInterface.class, sm, admin);
        when(iBTC.mock.symbol()).thenReturn("iBTC");
        loans.invoke(admin, "addAsset", iBTC.getAddress(), true, true);
        loans.invoke(admin, "setLockingRatio", "iBTC", LOCKING_RATIO);

        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
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
        Executable liquidateWithoutLiquidationRatio = () -> loans.invoke(liquidater, "liquidate", account.getAddress(), "iBTC");
        expectErrorMessage(liquidateWithoutLiquidationRatio, expectedErrorMessage);
    }

    @Test
    void liquidate_iETH() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger originalTotalDebt = getTotalDebt();

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        BigInteger liquidaterBalancePre = (BigInteger) ieth.call("balanceOf", liquidater.getAddress());

        takeLoaniETH(account, collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee), "iETH");

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
       
        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");

        // Assert
        BigInteger liquidaterBalancePost = (BigInteger) ieth.call("balanceOf", liquidater.getAddress());
        assertEquals(liquidaterBalancePre.add(expectedReward), liquidaterBalancePost);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("iETH").get("liquidation_pool"));

        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt, account.getAddress(), BigInteger.ZERO);
        verify(rewards.mock).updateRewardsData("Loans", originalTotalDebt.add(loan.add(expectedFee)), account.getAddress(),  loan.add(expectedFee));
        verifyTotalDebt(originalTotalDebt);
    }

    @Test
    void liquidate_differentLiquidationRatios() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
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

        BigInteger sICXCollateralInLoop = collateral.multiply((BigInteger) sicx.call("priceInLoop"));        
        BigInteger iETHCollateralInLoop = collateral.multiply((BigInteger) ieth.call("priceInLoop"));     

        BigInteger sICXLoanLiquidationRatio = sICXLiquidationRatio.multiply(EXA).divide(POINTS);
        BigInteger iETHLoanLiquidationRatio = iETHLiquidationRatio.multiply(EXA).divide(POINTS);
        BigInteger sICXLiquidationPrice = sICXCollateralInLoop.multiply(EXA).divide(debt.multiply(sICXLoanLiquidationRatio));
        BigInteger iETHLiquidationPrice = iETHCollateralInLoop.multiply(EXA).divide(debt.multiply(iETHLoanLiquidationRatio));

        loans.invoke(admin, "setLiquidationRatio", "sICX", sICXLiquidationRatio);
        loans.invoke(admin, "setLiquidationRatio", "iETH", iETHLiquidationRatio);

        // Act & Assert
        BigInteger bnusdPrice = sICXLiquidationPrice.subtract(BigInteger.TEN);
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), collateral, debt.multiply(bnusdPrice).divide(EXA), "sICX");
        verifyPosition(account.getAddress(), collateral, debt.multiply(bnusdPrice).divide(EXA), "iETH");

        bnusdPrice = sICXLiquidationPrice;
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "sICX");
        verifyPosition(account.getAddress(), collateral, debt.multiply(bnusdPrice).divide(EXA), "iETH");

        // Act & Assert
        bnusdPrice = iETHLiquidationPrice.subtract(BigInteger.TEN);
        mockOraclePrice("bnUSD", bnusdPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), collateral, debt.multiply(bnusdPrice).divide(EXA), "iETH");

        mockOraclePrice("bnUSD", iETHLiquidationPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO, "iETH");
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void liquidate_wrongCollateralType() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
       
        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee).multiply(newPrice).divide(EXA));
    }

    @Test
    void liquidate_PostionNotInLiquidateStanding() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void liquidate_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        String expectedErrorMessage = "Reverted(0): " + TAG + "This address does not have a position on Balanced.";

        // Assert & Act
        Executable liquidateAccountWithNoPosition = () ->  loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        expectErrorMessage(liquidateAccountWithNoPosition, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_retireAll() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), expectedDebt.multiply(BigInteger.TWO), new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtSICX = expectedDebt;
        BigInteger badDebtIETH = expectedDebt;
        BigInteger sICXLiquidationPool = collateral.subtract(expectedReward);
        BigInteger iETHLiquidationPool = collateral.subtract(expectedReward);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger iETHBalancePre = (BigInteger) ieth.call("balanceOf", badDebtReedemer.getAddress());

        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtSICX.add(badDebtIETH));

        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePost = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger iETHBalancePost = (BigInteger) ieth.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = EXA;
        BigInteger iETHPrice = EXA;
        BigInteger debtInsICX = bonus.multiply(badDebtSICX).multiply(newPrice).divide(icxPrice.multiply(POINTS));
        BigInteger debtIniETH = bonus.multiply(badDebtIETH).multiply(newPrice).divide(iETHPrice.multiply(POINTS));

        assertEquals(bnUSDBalancePre.subtract(badDebtSICX.add(badDebtIETH)), bnUSDBalancePost);
        assertEquals(sICXBalancePre.add(debtInsICX), sICXBalancePost);
        assertEquals(iETHBalancePre.add(debtIniETH), iETHBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        BigInteger expectedLiquidationPool = sICXLiquidationPool.subtract(debtInsICX);
        BigInteger expectedLiquidationPooliETH = iETHLiquidationPool.subtract(debtIniETH);
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
        assertEquals(expectedLiquidationPooliETH, bnusdDebtDetails.get("iETH").get("liquidation_pool"));
    }

    @Test
    void retireBadDebt_retirePartial() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), expectedDebt.multiply(BigInteger.TWO), new byte[0]);

        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtSICX = expectedDebt;
        BigInteger badDebtIETH = expectedDebt;
        BigInteger badDebtToRedeem = badDebtSICX.divide(BigInteger.TWO).add(badDebtIETH);

        // Act
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtToRedeem);


        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, BigInteger>> bnusdDebtDetails = (Map<String, Map<String, BigInteger>>) bnusdAsset.get("debt_details");

        assertEquals(badDebtIETH.add(badDebtIETH).subtract(badDebtToRedeem), 
                    bnusdDebtDetails.get("sICX").get("bad_debt").add(bnusdDebtDetails.get("iETH").get("bad_debt")));
    }

    @Test
    void retireBadDebt_UseReserve() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebtRedeemed = loan.add(expectedFee);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), badDebtRedeemed, new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger pricePreLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        BigInteger pricePostLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(6));

        mockOraclePrice("bnUSD", pricePreLiquidation);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        mockOraclePrice("bnUSD", pricePostLiquidation);

        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));
        sicx.invoke(admin, "transfer", reserve.getAddress(), debtInsICX, new byte[0]);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtRedeemed);

        // Assert
        BigInteger valueNeededFromReserve = amountRedeemed.multiply(icxPrice).divide(EXA);
        verify(reserve.mock).redeem(badDebtReedemer.getAddress(), valueNeededFromReserve, "sICX");
        
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        assertEquals(bnUSDBalancePre.subtract(badDebtRedeemed), bnUSDBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
    }

    @Test
    void retireBadDebtForCollateral_UseReserve_sICX() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebtRedeemed = loan.add(expectedFee);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), badDebtRedeemed, new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger pricePreLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        BigInteger pricePostLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(6));

        mockOraclePrice("bnUSD", pricePreLiquidation);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        mockOraclePrice("bnUSD", pricePostLiquidation);

        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));
        sicx.invoke(admin, "transfer", reserve.getAddress(), debtInsICX, new byte[0]);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        loans.invoke(badDebtReedemer, "retireBadDebtForCollateral", "bnUSD", badDebtRedeemed, "sICX");

        // Assert
        BigInteger valueNeededFromReserve = amountRedeemed.multiply(icxPrice).divide(EXA);
        verify(reserve.mock).redeem(badDebtReedemer.getAddress(), valueNeededFromReserve, "sICX");
        
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        assertEquals(bnUSDBalancePre.subtract(badDebtRedeemed), bnUSDBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("sICX").get("liquidation_pool"));
    }

    @Test
    void retireBadDebtForCollateral_UseReserve_IETH() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), expectedDebt.multiply(BigInteger.TWO), new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);
        takeLoaniETH(account, collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");
        loans.invoke(liquidater, "liquidate", account.getAddress(), "iETH");

        BigInteger badDebtSICX = expectedDebt;
        BigInteger badDebtIETH = expectedDebt;
        BigInteger iETHLiquidationPool = collateral.subtract(expectedReward);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger iETHBalancePre = (BigInteger) ieth.call("balanceOf", badDebtReedemer.getAddress());

        loans.invoke(badDebtReedemer, "retireBadDebtForCollateral", "bnUSD", badDebtIETH, "iETH");

        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger iETHBalancePost = (BigInteger) ieth.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger iETHPrice = EXA;
        BigInteger debtIniETH = bonus.multiply(badDebtIETH).multiply(newPrice).divide(iETHPrice.multiply(POINTS));

        assertEquals(bnUSDBalancePre.subtract(badDebtIETH), bnUSDBalancePost);
        assertEquals(iETHBalancePre.add(debtIniETH), iETHBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        Map<String, Map<String, Object>> bnusdDebtDetails = (Map<String, Map<String, Object>>) bnusdAsset.get("debt_details");

        BigInteger expectedLiquidationPooliETH = iETHLiquidationPool.subtract(debtIniETH);
        assertEquals(BigInteger.ZERO, bnusdDebtDetails.get("iETH").get("bad_debt"));
        assertEquals(expectedLiquidationPooliETH, bnusdDebtDetails.get("iETH").get("liquidation_pool"));
    }

    @Test
    void retireBadDebt_ZeroAmount() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Amount retired must be greater than zero.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", BigInteger.ZERO);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_Collateral() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Reverted(0): BalancedLoansAssets sICX is not a supported asset.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "sICX", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_AssetNotActive() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Reverted(0): " + TAG + "bnUSD is not an active, borrowable asset on Balanced.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        loans.invoke(admin, "toggleAssetActive", "bnUSD");
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_InsufficientBalance() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Reverted(0): " + TAG + "Insufficient balance.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);

        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        mockOraclePrice("bnUSD", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress(), "sICX");

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_NoBadDebt() {
        // Arrange
        Account badDebtReedemer =  accounts.get(0);
        BigInteger badDebt = BigInteger.valueOf(200).multiply(EXA);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), badDebt, new byte[0]);

        String expectedErrorMessage = "Reverted(0): " + TAG + "No bad debt for bnUSD";

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void raisePrice() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger rate = EXA.divide(BigInteger.TWO);
        BigInteger expectedBnusdRecived = rebalanceAmount.multiply(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        mockSwap(bnusd, rebalanceAmount, expectedBnusdRecived);

        // Act
        loans.invoke(rebalancing, "raisePrice", sicx.getAddress(), rebalanceAmount);

        // Assert
        BigInteger remainingBnusd = expectedBnusdRecived;

        BigInteger accountZeroExpectedCollateralSold = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtRepaid = remainingBnusd.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.subtract(accountZeroExpectedCollateralSold),  accountZeroDebt.subtract(accountZeroExpectedDebtRepaid));

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountZeroExpectedDebtRepaid);

        BigInteger accountOneExpectedCollateralSold = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtRepaid = remainingBnusd.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),  accountOneDebt.subtract(accountOneExpectedDebtRepaid));

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountOneExpectedDebtRepaid);

        BigInteger accountTwoExpectedCollateralSold = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtRepaid = remainingBnusd.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.subtract(accountTwoExpectedCollateralSold), accountTwoDebt.subtract(accountTwoExpectedDebtRepaid));
    
        RewardsDataEntry accountZeroUpdate = new RewardsDataEntry();
        accountZeroUpdate._user = accounts.get(0).getAddress();
        accountZeroUpdate._balance = accountZeroDebt;

        RewardsDataEntry accountOneUpdate = new RewardsDataEntry();
        accountOneUpdate._user = accounts.get(1).getAddress();
        accountOneUpdate._balance = accountOneDebt;

        RewardsDataEntry accountTwoUpdate = new RewardsDataEntry();
        accountTwoUpdate._user = accounts.get(2).getAddress();
        accountTwoUpdate._balance = accountTwoDebt;

        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[] {accountZeroUpdate, accountOneUpdate, accountTwoUpdate};

        verifyTotalDebt(originalTotalDebt.subtract(expectedBnusdRecived)); 
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(originalTotalDebt), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
    }

    @Test
    void raisePrice_iETH() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);

        takeLoaniETH(accounts.get(0), accountZeroCollateral, accountZeroLoan);
        takeLoaniETH(accounts.get(1), accountOneCollateral, accountOneLoan);
        takeLoaniETH(accounts.get(2), accountTwoCollateral, accountTwoLoan);

        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger rate = EXA.divide(BigInteger.TWO);
        BigInteger expectedBnusdRecived = rebalanceAmount.multiply(BigInteger.TWO);
        mockiETHBnusdPrice(rate);
        mockSwap(bnusd, rebalanceAmount, expectedBnusdRecived);

        // Act
        loans.invoke(rebalancing, "raisePrice", ieth.getAddress(), rebalanceAmount);

        // Assert
        BigInteger remainingBnusd = expectedBnusdRecived;

        BigInteger accountZeroExpectedCollateralSold = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtRepaid = remainingBnusd.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.subtract(accountZeroExpectedCollateralSold),  accountZeroDebt.subtract(accountZeroExpectedDebtRepaid), "iETH");

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountZeroExpectedDebtRepaid);

        BigInteger accountOneExpectedCollateralSold = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtRepaid = remainingBnusd.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),  accountOneDebt.subtract(accountOneExpectedDebtRepaid), "iETH");

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountOneExpectedDebtRepaid);

        BigInteger accountTwoExpectedCollateralSold = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtRepaid = remainingBnusd.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.subtract(accountTwoExpectedCollateralSold), accountTwoDebt.subtract(accountTwoExpectedDebtRepaid), "iETH");
    
        RewardsDataEntry accountZeroUpdate = new RewardsDataEntry();
        accountZeroUpdate._user = accounts.get(0).getAddress();
        accountZeroUpdate._balance = accountZeroDebt.multiply(BigInteger.TWO);

        RewardsDataEntry accountOneUpdate = new RewardsDataEntry();
        accountOneUpdate._user = accounts.get(1).getAddress();
        accountOneUpdate._balance = accountOneDebt.multiply(BigInteger.TWO);

        RewardsDataEntry accountTwoUpdate = new RewardsDataEntry();
        accountTwoUpdate._user = accounts.get(2).getAddress();
        accountTwoUpdate._balance = accountTwoDebt;

        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[] {accountZeroUpdate, accountOneUpdate, accountTwoUpdate};

        verifyTotalDebt(originalTotalDebt.subtract(expectedBnusdRecived)); 
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(originalTotalDebt), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
    }

    @Test
    void raisePrice_OverMaxRetirePercent() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        BigInteger totalTokenRequired = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger maxRetirePercent = (BigInteger) getParam("retire percent max");
        BigInteger rate = EXA;
        mockSicxBnusdPrice(rate);
        BigInteger rebalanceAmount = maxRetirePercent.multiply(totalDebt).multiply(EXA).divide(POINTS.multiply(rate));

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger expectedBnusdRecived = rebalanceAmount;
        mockSwap(bnusd, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(rebalancing, "raisePrice", sicx.getAddress(), totalTokenRequired);

        // Assert
        BigInteger remainingBnusd = expectedBnusdRecived;

        BigInteger accountZeroExpectedCollateralSold = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtRepaid = remainingBnusd.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.subtract(accountZeroExpectedCollateralSold),  accountZeroDebt.subtract(accountZeroExpectedDebtRepaid));

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountZeroExpectedDebtRepaid);

        BigInteger accountOneExpectedCollateralSold = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtRepaid = remainingBnusd.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),
                accountOneDebt.subtract(accountOneExpectedDebtRepaid));

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountOneExpectedDebtRepaid);

        BigInteger accountTwoExpectedCollateralSold = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtRepaid = remainingBnusd.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.subtract(accountTwoExpectedCollateralSold), accountTwoDebt.subtract(accountTwoExpectedDebtRepaid));
    }

    @Test
    void lowerPrice() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);
        
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA;
        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger expectedSICXRecived = rebalanceAmount;
        mockSicxBnusdPrice(rate);
        mockSwap(sicx, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(rebalancing, "lowerPrice", sicx.getAddress(), rebalanceAmount);

        // Assert
        BigInteger remainingSicx = expectedSICXRecived;

        BigInteger accountZeroExpectedCollateralAdded = remainingSicx.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtAdded = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.add(accountZeroExpectedCollateralAdded),  accountZeroDebt.add(accountZeroExpectedDebtAdded));

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountZeroExpectedCollateralAdded);

        BigInteger accountOneExpectedCollateralAdded = remainingSicx.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtAdded = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.add(accountOneExpectedCollateralAdded),  accountOneDebt.add(accountOneExpectedDebtAdded));

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountOneExpectedCollateralAdded);

        BigInteger accountTwoExpectedCollateralAdded = remainingSicx.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtAdded = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.add(accountTwoExpectedCollateralAdded), accountTwoDebt.add(accountTwoExpectedDebtAdded));
    
        RewardsDataEntry accountZeroUpdate = new RewardsDataEntry();
        accountZeroUpdate._user = accounts.get(0).getAddress();
        accountZeroUpdate._balance = accountZeroDebt;

        RewardsDataEntry accountOneUpdate = new RewardsDataEntry();
        accountOneUpdate._user = accounts.get(1).getAddress();
        accountOneUpdate._balance = accountOneDebt;

        RewardsDataEntry accountTwoUpdate = new RewardsDataEntry();
        accountTwoUpdate._user = accounts.get(2).getAddress();
        accountTwoUpdate._balance = accountTwoDebt;

        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[] {accountZeroUpdate, accountOneUpdate, accountTwoUpdate};

        verifyTotalDebt(originalTotalDebt.add(expectedSICXRecived)); 
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(originalTotalDebt), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
    }

    @Test
    void lowerPrice_iETH() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);


        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);

        takeLoaniETH(accounts.get(0), accountZeroCollateral, accountZeroLoan);
        takeLoaniETH(accounts.get(1), accountOneCollateral, accountOneLoan);
        takeLoaniETH(accounts.get(2), accountTwoCollateral, accountTwoLoan);

        BigInteger originalTotalDebt = getTotalDebt();
        BigInteger rate = EXA;
        BigInteger expectedIETHRecived = rebalanceAmount;
        mockiETHBnusdPrice(rate);
        mockSwap(ieth, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(rebalancing, "lowerPrice", ieth.getAddress(), rebalanceAmount);

        // Assert
        BigInteger remainingIETH = expectedIETHRecived;

        BigInteger accountZeroExpectedCollateralAdded = remainingIETH.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtAdded = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.add(accountZeroExpectedCollateralAdded),  accountZeroDebt.add(accountZeroExpectedDebtAdded), "iETH");

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedDebtAdded);
        remainingIETH = remainingIETH.subtract(accountZeroExpectedCollateralAdded);

        BigInteger accountOneExpectedCollateralAdded = remainingIETH.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtAdded = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.add(accountOneExpectedCollateralAdded),  accountOneDebt.add(accountOneExpectedDebtAdded), "iETH");

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedDebtAdded);
        remainingIETH = remainingIETH.subtract(accountOneExpectedCollateralAdded);

        BigInteger accountTwoExpectedCollateralAdded = remainingIETH.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtAdded = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.add(accountTwoExpectedCollateralAdded), accountTwoDebt.add(accountTwoExpectedDebtAdded), "iETH");
    
        RewardsDataEntry accountZeroUpdate = new RewardsDataEntry();
        accountZeroUpdate._user = accounts.get(0).getAddress();
        accountZeroUpdate._balance = accountZeroDebt.multiply(BigInteger.TWO);

        RewardsDataEntry accountOneUpdate = new RewardsDataEntry();
        accountOneUpdate._user = accounts.get(1).getAddress();
        accountOneUpdate._balance = accountOneDebt.multiply(BigInteger.TWO);;

        RewardsDataEntry accountTwoUpdate = new RewardsDataEntry();
        accountTwoUpdate._user = accounts.get(2).getAddress();
        accountTwoUpdate._balance = accountTwoDebt;


        RewardsDataEntry[] rewardsBatchList = new RewardsDataEntry[] {accountZeroUpdate, accountOneUpdate, accountTwoUpdate};

        verifyTotalDebt(originalTotalDebt.add(expectedIETHRecived)); 
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(originalTotalDebt), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
    }


    @Test
    void lowerPrice_OverMaxRetirePercent() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        BigInteger totalTokenRequired = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger maxRetirePercent = (BigInteger) getParam("retire percent max");
        BigInteger rebalanceAmount = maxRetirePercent.multiply(totalDebt).divide(POINTS);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA;
        BigInteger expectedSICXRecived = rebalanceAmount;
        mockSicxBnusdPrice(rate);
        mockSwap(sicx, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(rebalancing, "lowerPrice", sicx.getAddress(), totalTokenRequired);

        // Assert
        BigInteger remainingSicx = expectedSICXRecived;

        BigInteger accountZeroExpectedCollateralAdded = remainingSicx.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtAdded = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.add(accountZeroExpectedCollateralAdded),  accountZeroDebt.add(accountZeroExpectedDebtAdded));

        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountZeroExpectedCollateralAdded);

        BigInteger accountOneExpectedCollateralAdded = remainingSicx.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtAdded = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.add(accountOneExpectedCollateralAdded),  accountOneDebt.add(accountOneExpectedDebtAdded));

        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountOneExpectedCollateralAdded);

        BigInteger accountTwoExpectedCollateralAdded = remainingSicx.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtAdded = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.add(accountTwoExpectedCollateralAdded), accountTwoDebt.add(accountTwoExpectedDebtAdded));
    }
}