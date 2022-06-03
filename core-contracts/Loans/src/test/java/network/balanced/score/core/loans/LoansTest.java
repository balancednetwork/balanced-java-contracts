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
import network.balanced.score.core.loans.utils.LoansConstants.Standings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

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
        loans.invoke(admin, "setLiquidationRatio", BigInteger.valueOf(9));
        loans.invoke(admin, "setLockingRatio", BigInteger.valueOf(10));

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
    void getAccountPositions() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        BigInteger day = (BigInteger) loans.call("getDay");
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
        Map<String, BigInteger> assets = (Map<String, BigInteger>) position.get("assets");
        assertEquals(1, position.get("pos_id"));
        assertEquals(account.getAddress().toString(), position.get("address"));
        assertEquals(loan.add(expectedFee), position.get("total_debt"));
        assertEquals(collateral, position.get("collateral"));
        assertEquals(collateral.multiply(EXA).divide(loan.add(expectedFee)), position.get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), position.get("standing"));

        assertEquals(loan.add(expectedFee), assets.get("bnUSD"));
        assertEquals(collateral, assets.get("sICX"));
    }

    @Test
    void getBalanceAndSupply() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Act
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", account.getAddress());

        // Assert
        assertEquals(loan.add(expectedFee), balanceAndSupply.get("_balance"));
        assertEquals(loan.add(expectedFee), balanceAndSupply.get("_totalSupply"));
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
        int loan = 100;
        BigInteger bigLoan = BigInteger.valueOf(loan).multiply(EXA);

        // Assert
        doNothing().when(loansSpy).depositAndBorrow("bnUSD", bigLoan, account.getAddress(), collateral);

        // Act
        takeLoanSICX(account, collateral, loan);
        verify(loansSpy).depositAndBorrow("bnUSD", bigLoan, account.getAddress(), collateral);
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
    void tokenFallback_NotSICX() {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + bnusd.call("symbol") + " is not a supported collateral type.";

        // Assert & Act
        Executable transferToken = () -> bnusd.invoke(account, "transfer", loans.getAddress(), value, new byte[0]);
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
    void DepositAndBorrow_ICX() {
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
    void DepositAndBorrow_StakeOnly() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.ZERO;

        // Act
        takeLoanICX(account, "", collateral, loan);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, BigInteger> assetHoldings = (Map<String, BigInteger>) position.get("assets");

        assertEquals(collateral, assetHoldings.get("sICX"));
        assertEquals(false, assetHoldings.containsKey("bnUSD"));
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
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);;
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
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);

        // Assert
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", account.getAddress());
        assertEquals(balancePre.subtract(loanToRepay), balancePost);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee));
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
        Executable returnToMuch = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(returnToMuch, expectedErrorMessage);
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
        Executable returnCollateral = () ->  loans.invoke(account, "returnAsset", "sICX", collateralToRepay, true);
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
        Executable nonActiveAsset = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
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
        Executable returnWithInsufficientBalance = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
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
        Executable returnWithNoPosition = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void returnAsset_doNotRepay() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        takeLoanICX(account, "bnUSD", collateral, loan);
        String expectedErrorMessage = "Reverted(0): " + TAG + "No debt repaid because, repay=false";

        // Assert & Act
        Executable returnWithNoPosition = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, false);
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger balancePre = (BigInteger) sicx.call("balanceOf", account.getAddress());

        // Act
        loans.invoke(account, "withdrawCollateral", collateralToWithdraw);

        // Assert
        BigInteger balancePost = (BigInteger) sicx.call("balanceOf", account.getAddress());
        assertEquals(balancePre.add(collateralToWithdraw), balancePost);
        verifyPosition(account.getAddress(), collateral.subtract(collateralToWithdraw), loan.add(expectedFee));
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
        Executable withdrawZeroCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawZeroCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateralToWithdraw = BigInteger.valueOf(200).multiply(EXA);
        String expectedErrorMessage = "Reverted(0): " + TAG +  "This address does not have a position on Balanced.";

        // Assert & Act
        Executable withdrawWithNoPosition = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
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
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
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
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void liquidate() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        BigInteger liquidaterBalancePre = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);

        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert
        BigInteger liquidaterBalancePost = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());
        assertEquals(liquidaterBalancePre.add(expectedReward), liquidaterBalancePost);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");

        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdAsset.get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdAsset.get("liquidation_pool"));
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
        loans.invoke(liquidater, "liquidate", account.getAddress());

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
        Executable liquidateAccountWithNoPosition = () ->  loans.invoke(liquidater, "liquidate", account.getAddress());
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
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        BigInteger badDebt = loan.add(expectedFee);
        BigInteger liquidationPool = collateral.subtract(expectedReward);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());

        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);

        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePost = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebt).multiply(newPrice).divide(icxPrice.multiply(POINTS));

        assertEquals(bnUSDBalancePre.subtract(badDebt), bnUSDBalancePost);
        assertEquals(sICXBalancePre.add(debtInsICX), sICXBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        BigInteger expectedBadDebt = loan.add(expectedFee);

        BigInteger expectedLiquidationPool = liquidationPool.subtract(debtInsICX);
        assertEquals(BigInteger.ZERO, bnusdAsset.get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdAsset.get("liquidation_pool"));
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

        bnusd.invoke(admin, "setPrice", pricePreLiquidation);
        loans.invoke(liquidater, "liquidate", account.getAddress());
        bnusd.invoke(admin, "setPrice", pricePostLiquidation);

        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));
        sicx.invoke(admin, "transfer", reserve.getAddress(), debtInsICX, new byte[0]);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        mockRedeemFromReserve(badDebtReedemer.getAddress(), amountRedeemed, icxPrice);
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtRedeemed);

        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePost = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());


        assertEquals(bnUSDBalancePre.subtract(badDebtRedeemed), bnUSDBalancePost);
        assertEquals(sICXBalancePre.add(debtInsICX), sICXBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        BigInteger expectedBadDebt = loan.add(expectedFee);

        BigInteger expectedLiquidationPool = liquidationPool.subtract(debtInsICX);
        assertEquals(BigInteger.ZERO, bnusdAsset.get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdAsset.get("liquidation_pool"));
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
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

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
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

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
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

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
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

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
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA.divide(BigInteger.TWO);
        BigInteger expectedBnusdRecived = rebalanceAmount.multiply(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        mockSwap(bnusd, rebalanceAmount, expectedBnusdRecived);

        // Act
        loans.invoke(rebalancing, "raisePrice", rebalanceAmount);

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
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);

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
        loans.invoke(rebalancing, "raisePrice", totalTokenRequired);

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
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA;
        BigInteger expectedSICXRecived = rebalanceAmount;
        mockSicxBnusdPrice(rate);
        mockSwap(sicx, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(rebalancing, "lowerPrice", rebalanceAmount);

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

        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
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
        loans.invoke(rebalancing, "lowerPrice", totalTokenRequired);

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