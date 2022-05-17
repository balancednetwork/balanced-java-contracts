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
import network.balanced.score.lib.structs.RewardsDataEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@DisplayName("Loans Tests")
class LoansTestContinuousRewards extends LoansTestBase {

    static final String continuousRewardsErrorMessage = "The continuous rewards is already active.";

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
        enableContinuousRewards();
    }

    @Test
    void getPositonStanding() {
        Account account = accounts.get(0);
        BigInteger lastDay = BigInteger.valueOf(-1);
        Executable getPositionStanding = () -> loans.call("getPositionStanding", account.getAddress(), lastDay);
        expectErrorMessage(getPositionStanding, continuousRewardsErrorMessage);
    }

    @Test
    void getPositionByIndex() {
        int index = 1;
        BigInteger lastDay = BigInteger.valueOf(-1);
        Executable getPositionByIndex = () -> loans.call("getPositionByIndex", index, lastDay);
        expectErrorMessage(getPositionByIndex, continuousRewardsErrorMessage);
    }

    @Test
    void getTotalValue() {
        BigInteger lastDay = BigInteger.valueOf(-1);
        Executable getTotalValue = () -> loans.call("getTotalValue", "loans", lastDay);
        expectErrorMessage(getTotalValue, continuousRewardsErrorMessage);
    }

    @Test
    void getDataBatch_SameDay() {
        BigInteger lastDay = BigInteger.valueOf(-1);
        loans.call("getDataBatch", "loans", lastDay, 0, 0);
    }

    @Test
    void getDataBatch_DayAfter() {
        BigInteger day = ((BigInteger)loans.call("getDay")).add(BigInteger.ONE);

        Executable getDataBatch = () -> loans.call("getDataBatch", "loans", day, 0, 0);
        expectErrorMessage(getDataBatch, continuousRewardsErrorMessage);
    }

    @Test
    void migrateUserData_UserWithPosition() {
        // Arrange
        governanceCall("setContinuousRewardsDay", BigInteger.valueOf(100000));
        Account account = accounts.get(0);
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        BigInteger intialDebt = BigInteger.ZERO;

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));

        // Act
        enableContinuousRewards();
        loans.invoke(account, "migrateUserData", account.getAddress());

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));
    }

    @Test
    void migrateUserData_ChangePosition_beforeMigration() {
        // Arrange
        governanceCall("setContinuousRewardsDay", BigInteger.valueOf(100000));
        Account account = accounts.get(0);
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        BigInteger intialDebt = BigInteger.ZERO;

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));

        // Act
        enableContinuousRewards();
        BigInteger addedCollateral = BigInteger.valueOf(100).multiply(EXA);
        BigInteger addedLoan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedAddedFee = calculateFee(addedLoan);
        BigInteger expectedCollateral = addedCollateral.add(collateral);
        BigInteger expectedLoan = addedLoan.add(loan).add(expectedAddedFee).add(expectedFee);

        takeLoanICX(account, "bnUSD", addedCollateral, addedLoan);

        // Assert
        verifyPosition(account.getAddress(), expectedCollateral, expectedLoan);
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));
        loans.invoke(account, "migrateUserData", account.getAddress());
        verifyPosition(account.getAddress(), expectedCollateral, expectedLoan);
        assertTrue((boolean) loans.call("hasDebt", account.getAddress()));
    }

    @Test
    void DepositAndBorrow_rewardsUpdate_noInitalLoan() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        BigInteger intialDebt = BigInteger.ZERO;

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
        verify(rewards.mock).updateRewardsData("Loans", totalSupply, account.getAddress(), intialDebt);
    }

    @Test
    void DepositAndBorrow_rewardsUpdate_withInitalLoan() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger initalCollateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger initalLoan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger initalDebt = calculateFee(initalLoan).add(initalLoan);
        takeLoanICX(account, "bnUSD", initalCollateral, initalLoan);

        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger exceptedDebt = initalDebt.add(loan.add(expectedFee));

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert
        verifyPosition(account.getAddress(), collateral.add(initalCollateral), exceptedDebt);
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
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");

        // Act 
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);

        // Assert
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", account.getAddress());
        assertEquals(balancePre.subtract(loanToRepay), balancePost);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee));
        verify(rewards.mock).updateRewardsData("Loans", totalSupply, account.getAddress(), loan.add(expectedFee));
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
        mockSicxBnusdPrice(rate);
        BigInteger expectedBnusdRecived = rebalanceAmount.multiply(BigInteger.TWO);
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
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),
                accountOneDebt.subtract(accountOneExpectedDebtRepaid));

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

        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(totalSupply), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
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


        BigInteger rate = EXA.divide(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        BigInteger expectedSICXRecived = rebalanceAmount.divide(BigInteger.TWO);
        mockSwap(sicx, rebalanceAmount, expectedSICXRecived);

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

        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        verify(rewards.mock).updateBatchRewardsData(eq("Loans"), eq(totalSupply), argThat(arg -> compareRewardsData(rewardsBatchList, arg)));
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

        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        verify(rewards.mock).updateRewardsData("Loans", totalSupply, account.getAddress(), loan.add(expectedFee));
    }
}