package network.balanced.score.core;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.argThat;

import score.Context;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.BigDecimal;
import scorex.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import network.balanced.score.mock.DexMock;
import network.balanced.score.core.RewardsMock;
import network.balanced.score.core.StakingMock;
import network.balanced.score.core.ReserveMock;

import network.balanced.score.core.sICXMintBurn;
import network.balanced.score.core.bnUSDMintBurn;
import network.balanced.score.core.Loans;
import network.balanced.score.core.Constants;

import java.util.Random;

@DisplayName("Loans Tests")
class LoansTestContinuousRewards extends LoansTestsBase {

    static final String continuousRewardsErrorMessage = "The continuous rewards is already active.";   
    private RewardsMock rewardsSpy;

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
        enableContinuousRewards();
        rewardsSpy = (RewardsMock) spy(rewards.getInstance());
        rewards.setInstance(rewardsSpy);
    }

    @Test
    void getPositonStanding() {
        Account account = accounts.get(0);
        int lastDay = -1;
        Executable getPositionStanding = () -> loans.call("getPositionStanding", account.getAddress(), lastDay);
        expectErrorMessage(getPositionStanding, continuousRewardsErrorMessage);
    }


    @Test
    void getPositionByIndex() {
        int index = 1;
        int lastDay = -1;
        Executable getPositionByIndex = () -> loans.call("getPositionByIndex", index, lastDay);
        expectErrorMessage(getPositionByIndex, continuousRewardsErrorMessage);
    }

    @Test
    void getTotalValue() {
        int lastDay = -1;
        Executable getTotalValue = () -> loans.call("getTotalValue", "loans", lastDay);
        expectErrorMessage(getTotalValue, continuousRewardsErrorMessage);
    }

    @Test
    void getDataBatch_SameDay() {
        int lastDay = -1;
        //Does not throw error
        loans.call("getDataBatch", "loans", lastDay, 0, 0);
    }

    @Test
    void getDataBatch_DayAfter() {
        int day = (int)loans.call("getDay") + 1;

        Executable getDataBatch = () -> loans.call("getDataBatch", "loans", day, 0, 0);
        expectErrorMessage(getDataBatch, continuousRewardsErrorMessage);
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
        verify(rewardsSpy).updateRewardsData("Loans", totalSupply, account.getAddress(), intialDebt);
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
        verify(rewardsSpy).updateRewardsData("Loans", totalSupply, account.getAddress(), loan.add(expectedFee));
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

        BigInteger bnUSDDexLiquidity = (BigInteger) bnusd.call("balanceOf", dex.getAddress());
        BigInteger sICXDexLiquidity = (BigInteger) sicx.call("balanceOf", dex.getAddress());
        BigInteger expectedBnusdRecived = bnUSDDexLiquidity.multiply(rebalanceAmount).divide(sICXDexLiquidity.add(rebalanceAmount));

        // Act
        loans.invoke(admin, "raisePrice", rebalanceAmount);

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

        ArrayList<Map<String, Object>> rewardsBatchList = new ArrayList(3);
        Map<String, Object> accountZeroUpdate = Map.of(
            "_user",  accounts.get(0).getAddress(),
            "_balance", accountZeroDebt
        );
        Map<String, Object> accountOneUpdate = Map.of(
            "_user",  accounts.get(1).getAddress(),
            "_balance", accountOneDebt
        );
        Map<String, Object> accountTwoUpdate =  Map.of(
            "_user",  accounts.get(2).getAddress(),
            "_balance", accountTwoDebt
        );

        rewardsBatchList.add(accountZeroUpdate);
        rewardsBatchList.add(accountOneUpdate);
        rewardsBatchList.add(accountTwoUpdate);
  
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        verify(rewardsSpy).updateBatchRewardsData(eq("Loans"), eq(totalSupply), argThat(arg -> arg.containsAll(rewardsBatchList)));
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

        BigInteger bnUSDDexLiquidity = (BigInteger) bnusd.call("balanceOf", dex.getAddress());
        BigInteger sICXDexLiquidity = (BigInteger) sicx.call("balanceOf", dex.getAddress());
        BigInteger expectedSICXRecived = sICXDexLiquidity.multiply(rebalanceAmount).divide(bnUSDDexLiquidity.add(rebalanceAmount));

        // Act
        loans.invoke(admin, "lowerPrice", rebalanceAmount);

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

        ArrayList<Map<String, Object>> rewardsBatchList = new ArrayList(3);
        Map<String, Object> accountZeroUpdate = Map.of(
            "_user",  accounts.get(0).getAddress(),
            "_balance", accountZeroDebt
        );
        Map<String, Object> accountOneUpdate = Map.of(
            "_user",  accounts.get(1).getAddress(),
            "_balance", accountOneDebt
        );
        Map<String, Object> accountTwoUpdate =  Map.of(
            "_user",  accounts.get(2).getAddress(),
            "_balance", accountTwoDebt
        );

        rewardsBatchList.add(accountZeroUpdate);
        rewardsBatchList.add(accountOneUpdate);
        rewardsBatchList.add(accountTwoUpdate);
  
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        verify(rewardsSpy).updateBatchRewardsData(eq("Loans"), eq(totalSupply), argThat(arg -> arg.containsAll(rewardsBatchList)));
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
        verify(rewardsSpy).updateRewardsData("Loans", totalSupply, account.getAddress(), loan.add(expectedFee));
    }
}