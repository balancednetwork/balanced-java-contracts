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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;

import static  network.balanced.score.lib.utils.Constants.*;
import static  network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

abstract class LoansIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;
    protected static Address ethAddress;

    private static BigInteger initalLockingRatio;
    private static BigInteger lockingRatio;
    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);

    public static void setup() {
        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setVoteDefinitionFee(voteDefinitionFee);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        ethAddress = createIRC2Token(owner, "ICON ETH", "iETH");
        owner.balancedOracle.getPriceInLoop((txr)->{}, "ETH");
        owner.irc2(ethAddress).setMinter(owner.getAddress());
    }

    @Test
    @Order(1)
    void takeLoans() throws Exception {
        BigInteger ethAmount =  BigInteger.TEN.pow(18);
        
        BigInteger bnusdAmount = ethAmount.multiply(reader.balancedOracle.getLastPriceInLoop("ETH"))
            .divide(reader.balancedOracle.getLastPriceInLoop("bnUSD"));

        addCollateralType(owner, ethAddress, ethAmount, bnusdAmount, "ETH"); 

        // Arrange
        BalancedClient loantakerICX = balanced.newClient();
        BalancedClient loantakerSICX = balanced.newClient();
        BalancedClient twoStepLoanTaker = balanced.newClient();
        BalancedClient loantakerMulti = balanced.newClient();
        BalancedClient loantakerIETH = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loantakerMulti.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loantakerIETH.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger totalDebt = getTotalDebt();

        // Act
        loantakerICX.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(collateral, "bnUSD", BigInteger.ZERO, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);
        loantakerSICX.stakeDepositAndBorrow(collateral, loanAmount);
        loantakerMulti.stakeDepositAndBorrow(collateral, loanAmount);
        loantakerMulti.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loantakerIETH.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        totalDebt = debt.multiply(BigInteger.valueOf(6)).add(totalDebt);

        Map<String, BigInteger> loantakerIcxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerICX.getAddress());
        Map<String, BigInteger> loantakerSicxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());
        Map<String, BigInteger> twoStepLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());
        Map<String, BigInteger> loantakerMultiBaS = reader.loans.getBalanceAndSupply("Loans", loantakerMulti.getAddress());
        Map<String, BigInteger> loantakerIETHBaS = reader.loans.getBalanceAndSupply("Loans", loantakerIETH.getAddress());

        assertEquals(totalDebt, loantakerIcxBaS.get("_totalSupply"));
        assertEquals(debt, loantakerIcxBaS.get("_balance"));
        assertEquals(debt, loantakerSicxBaS.get("_balance"));
        assertEquals(debt, twoStepLoanTakerBaS.get("_balance"));
        assertEquals(debt.multiply(BigInteger.TWO), loantakerMultiBaS.get("_balance"));
        assertEquals(debt, loantakerIETHBaS.get("_balance"));

        assertEquals(collateralETH, loantakerMulti.getLoansCollateralPosition("iETH"));
        assertEquals(collateral, loantakerMulti.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH, loantakerIETH.getLoansCollateralPosition("iETH"));
    }

    @Test
    @Order(2)
    void repyDebt() throws Exception {
        // Arrange
        BalancedClient loanTakerFullRepay = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BalancedClient loantakerIETHFullRepay = balanced.newClient();
        BalancedClient loantakerMultiPartialRepay = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loantakerIETHFullRepay.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loantakerMultiPartialRepay.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullRepay.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.stakeDepositAndBorrow(collateral, loanAmount);
        loantakerMultiPartialRepay.stakeDepositAndBorrow(collateral, loanAmount);
        loantakerIETHFullRepay.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loantakerMultiPartialRepay.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        loanTakerPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX");
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullRepay.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loantakerIETHFullRepay.getAddress(), fee, null);
        loanTakerFullRepay.loans.returnAsset("bnUSD", debt, "sICX");

        assertThrows(UserRevertedException.class, () -> 
            loantakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "sICX"));

        loantakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "iETH");
        loantakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX");
        loantakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "iETH");

        BigInteger outstandingNewDebt = BigInteger.valueOf(3).multiply(debt.divide(BigInteger.TWO));

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(outstandingNewDebt);
        Map<String, BigInteger> loanTakerPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerPartialRepay.getAddress());
        Map<String, BigInteger> loanTakerFullRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerFullRepay.getAddress());
        Map<String, BigInteger> loantakerIETHFullRepayBaS = reader.loans.getBalanceAndSupply("Loans", loantakerIETHFullRepay.getAddress());
        Map<String, BigInteger> loantakerMultiPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans", loantakerMultiPartialRepay.getAddress());

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTakerFullRepayBaS.get("_balance"));
        assertEquals(debt.divide(BigInteger.TWO), loanTakerPartialRepayBaS.get("_balance"));
        assertEquals(BigInteger.ZERO, loantakerIETHFullRepayBaS.get("_balance"));
        assertEquals(debt, loantakerMultiPartialRepayBaS.get("_balance"));
    }

    @Test
    @Order(3)
    void withdrawCollateral() throws Exception {
        // Arrange
        BalancedClient loanTakerFullWithdraw = balanced.newClient();
        BalancedClient loanTakerPartialWithdraw = balanced.newClient();
        BalancedClient loanTakerETHFullWithdraw = balanced.newClient();
        BalancedClient loanTakerETHPartialWithdraw = balanced.newClient();
        BalancedClient zeroLoanWithdrawFull = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loanTakerETHFullWithdraw.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerETHPartialWithdraw.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullWithdraw.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialWithdraw.stakeDepositAndBorrow(collateral, loanAmount);
        zeroLoanWithdrawFull.stakeDepositAndBorrow(collateral, BigInteger.ZERO);
        loanTakerETHFullWithdraw.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerETHPartialWithdraw.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        loanTakerPartialWithdraw.bnUSD.transfer(loanTakerFullWithdraw.getAddress(), fee, null);
        loanTakerPartialWithdraw.bnUSD.transfer(loanTakerETHFullWithdraw.getAddress(), fee, null);
       
        loanTakerFullWithdraw.loans.returnAsset("bnUSD", debt, "sICX");
        loanTakerFullWithdraw.loans.withdrawCollateral(collateral, "sICX");

        loanTakerETHFullWithdraw.loans.returnAsset("bnUSD", debt, "iETH");
        loanTakerETHFullWithdraw.loans.withdrawCollateral(collateralETH, "iETH");

        BigInteger amountToWithdraw = BigInteger.TEN.pow(20);
        BigInteger amountiETHToWithdraw = BigInteger.TEN.pow(18);

        assertThrows(UserRevertedException.class, () -> 
            loanTakerPartialWithdraw.loans.withdrawCollateral(collateral, "sICX"));
        loanTakerPartialWithdraw.loans.withdrawCollateral(amountToWithdraw, "sICX");

        assertThrows(UserRevertedException.class, () -> 
            loanTakerETHPartialWithdraw.loans.withdrawCollateral(amountToWithdraw, "sICX"));
        loanTakerETHPartialWithdraw.loans.withdrawCollateral(amountiETHToWithdraw, "iETH");

        zeroLoanWithdrawFull.loans.withdrawCollateral(collateral, "sICX");

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt).add(debt);

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTakerFullWithdraw.getLoansCollateralPosition("sICX"));
        assertEquals(collateral.subtract(amountToWithdraw), loanTakerPartialWithdraw.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, zeroLoanWithdrawFull.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH.subtract(amountiETHToWithdraw), loanTakerETHPartialWithdraw.getLoansCollateralPosition("iETH"));
        assertEquals(BigInteger.ZERO, loanTakerETHPartialWithdraw.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTakerETHFullWithdraw.getLoansCollateralPosition("iETH"));
    }

    @Test
    @Order(4)
    void reOpenPosition() throws Exception {
        // Arrange
        BalancedClient loanTakerCloseLoanOnly = balanced.newClient();
        BalancedClient loanTakerFullClose = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BalancedClient loanTakerETHFullClose = balanced.newClient();
        BalancedClient loanTakerETHPartialRepay = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loanTakerETHFullClose.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerETHPartialRepay.getAddress(), collateralETH, null);
        
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerCloseLoanOnly.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerFullClose.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerETHFullClose.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerETHPartialRepay.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        loanTakerPartialRepay.bnUSD.transfer(loanTakerCloseLoanOnly.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullClose.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerETHFullClose.getAddress(), fee, null);

        loanTakerCloseLoanOnly.loans.returnAsset("bnUSD", debt, "sICX");
        loanTakerFullClose.loans.returnAsset("bnUSD", debt, "sICX");
        loanTakerETHFullClose.loans.returnAsset("bnUSD", debt, "iETH");

        BigInteger amountRepaid = BigInteger.TEN.pow(21);
        BigInteger amountETHRepaid = BigInteger.TEN.pow(18);
        loanTakerPartialRepay.loans.returnAsset("bnUSD", amountRepaid, "sICX");
        loanTakerETHPartialRepay.loans.returnAsset("bnUSD", amountETHRepaid, "iETH");

        loanTakerFullClose.loans.withdrawCollateral(loanTakerFullClose.getLoansCollateralPosition("sICX"), null);
        loanTakerETHFullClose.loans.withdrawCollateral(loanTakerETHFullClose.getLoansCollateralPosition("iETH"), "iETH");

        loanTakerCloseLoanOnly.borrowFrom("sICX", loanAmount);
        loanTakerFullClose.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.borrowFrom("sICX", amountRepaid);
        loanTakerETHFullClose.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerETHPartialRepay.borrowFrom("iETH", amountETHRepaid);

        // Assert
        BigInteger extraFees = amountRepaid.multiply(feePercent).divide(POINTS);
        BigInteger extraFeesETHLoan = amountETHRepaid.multiply(feePercent).divide(POINTS);
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt.multiply(BigInteger.valueOf(5))).add(extraFees).add(extraFeesETHLoan);
        assertEquals(expectedTotalDebt, getTotalDebt());
    }

    @Test
    @Order(5)
    void withdrawAndUnstake() throws Exception {
        // Arrange
        BalancedClient loantakerSICX = balanced.newClient();
        BalancedClient staker = balanced.newClient();
    
        BigInteger collateral = BigInteger.TEN.pow(23);

        BigInteger totalDebt = getTotalDebt();

        // Act
        loantakerSICX.stakeDepositAndBorrow(collateral, BigInteger.ZERO);
        loantakerSICX.loans.withdrawAndUnstake(collateral);
        staker.staking.stakeICX(collateral, null, null);

        // Assert
        assertEquals(collateral, loantakerSICX.staking.claimableICX(loantakerSICX.getAddress()));
    }

    @Test
    @Order(21)
    void rebalancing_raisePrice() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        reducePriceBelowThreshold(balanced.sicx._address());
        rebalance(balanced.sicx._address());

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) > 0);
    }

    @Test
    @Order(22)
    void rebalancing_lowerPrice() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        raisePriceAboveThreshold(balanced.sicx._address());
        rebalance(balanced.sicx._address());

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) < 0);
    }

    @Test
    @Order(23)
    void rebalancing_raisePrice_ETH() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        reducePriceBelowThreshold(ethAddress);
        rebalance(ethAddress);

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) > 0);
    }

    @Test
    @Order(24)
    void rebalancing_lowerPrice_ETH() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        raisePriceAboveThreshold(ethAddress);
        rebalance(ethAddress);

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) < 0);
    }

    @Test
    @Order(31)
    void setupLockingRatio() throws Exception {
        balanced.increaseDay(1);
        claimAllRewards();
        BalancedClient voter = balanced.newClient();
        initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        lockingRatio = BigInteger.valueOf(13000);
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));
        
        setLockingRatio(voter, "sICX", lockingRatio, "Liquidation setup ICX");
        setLockingRatio(voter, "iETH", lockingRatio, "Liquidation setup IETH");
    }

    @Test
    @Order(32)
    void liquidateSICX_throughGovernanceVote() throws Exception {
        // Arrange
        BigInteger initalDebt = getTotalDebt();
        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), collateralETH, null);
        
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
        BigInteger ethLoan = BigInteger.TEN.pow(22);
        BigInteger ethDebt = ethLoan.add(ethLoan.multiply(feePercent).divide(POINTS));
        loanTaker.depositAndBorrow(ethAddress, collateralETH, ethLoan);
        // Act
        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "sICX");
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        BigInteger bnUSDBalancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger bnUSDBalancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());

        // Assert
        assertTrue(bnUSDBalancePreRetire.compareTo(bnUSDBalancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);

        Map<String, BigInteger> LiquidatedUserBaS = reader.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertEquals(initalDebt.add(ethDebt), getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH,  loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(ethDebt, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
        assertEquals(ethDebt, LiquidatedUserBaS.get("_balance"));
    }

    @Test
    @Order(32)
    void liquidateIETH_throughGovernanceVote() throws Exception {
        // Arrange
        BigInteger initalDebt = getTotalDebt();
        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(19);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), collateralETH, null);
        
        BigInteger collateralValue = collateralETH.multiply(reader.balancedOracle.getLastPriceInLoop("iETH")).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(reader.balancedOracle.getLastPriceInLoop("bnUSD"));
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.depositAndBorrow(ethAddress, collateralETH, loan);
    
        BigInteger sICXLoan = BigInteger.TEN.pow(21);
        BigInteger sICXdebt = sICXLoan.add(sICXLoan.multiply(feePercent).divide(POINTS));

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", sICXLoan,  null, null);

        // Act
        BigInteger balancePreLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "iETH");
        BigInteger balancePostLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        loanTaker.bnUSD.transfer(liquidator.getAddress(), loan, null);
        depositToStabilityContract(liquidator, fee.multiply(BigInteger.TWO));

        BigInteger bnUSDBalancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger bnUSDBalancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());

        // Assert
        assertTrue(bnUSDBalancePreRetire.compareTo(bnUSDBalancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);

        Map<String, BigInteger> LiquidatedUserBaS = reader.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertEquals(initalDebt.add(sICXdebt), getTotalDebt());
        assertEquals(collateral, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO,  loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(sICXdebt, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
        assertEquals(sICXdebt, LiquidatedUserBaS.get("_balance"));
    }

    @Test
    @Order(39)
    void resetLockingRatio() throws Exception {
        BalancedClient voter = balanced.newClient();
        initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));
        setLockingRatio(voter, "sICX", initalLockingRatio, "restore locking ratio sICX");
        setLockingRatio(voter, "iETH", initalLockingRatio, "restore locking ratio iETH");
    }
  
    protected void rebalance(Address address) throws Exception {
        BalancedClient rebalancer = balanced.newClient();
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (true) {
            if (threshold.abs().compareTo(calculateThreshold(address).abs()) > 0) {
                return;
            }

            owner.rebalancing.rebalance(address);
        }
    }

    protected void reducePriceBelowThreshold(Address address) throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
       while (calculateThreshold(address).multiply(BigInteger.valueOf(100)).compareTo(threshold.multiply(BigInteger.valueOf(105))) < 0) {
            reducePrice(address);
        }
    }

    protected void raisePriceAboveThreshold(Address address) throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold(address).multiply(BigInteger.valueOf(100)).compareTo(threshold.negate().multiply(BigInteger.valueOf(105))) > 0) {
            raisePrice(address);
        }
    }

    protected BigInteger calculateThreshold(Address collateralAddress) {
        BigInteger bnusdPriceInIcx = owner.balancedOracle.getLastPriceInLoop("bnUSD");
        BigInteger collateralPriceInIcx = owner.balancedOracle.getLastPriceInLoop(reader.irc2(collateralAddress).symbol());

        BigInteger poolId = owner.dex.getPoolId(collateralAddress, balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(poolId);
        BigInteger collateralLiquidity = hexObjectToBigInteger(poolStats.get("base"));
        BigInteger bnusdLiquidity = hexObjectToBigInteger(poolStats.get("quote"));

        BigInteger actualBnusdPriceInCollateral = bnusdPriceInIcx.multiply(EXA).divide(collateralPriceInIcx);
        BigInteger bnusdPriceInCollateral = collateralLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage = (actualBnusdPriceInCollateral.subtract(bnusdPriceInCollateral)).multiply(EXA).divide(actualBnusdPriceInCollateral);

        return priceDifferencePercentage;
    }

    protected void reducePrice(Address collateralAddress) throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(22);
        depositToStabilityContract(sellerClient, amountToSell);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", collateralAddress.toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.bnUSD.transfer(balanced.dex._address(), sellerClient.bnUSD.balanceOf(sellerClient.getAddress()), swapData.toString().getBytes());
    }

    protected void raisePrice(Address collateralAddress) throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        getTokens(sellerClient, collateralAddress, amountToSell);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.irc2(collateralAddress).transfer(balanced.dex._address(), amountToSell, swapData.toString().getBytes());
    }

    protected void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }

    protected BigInteger getTotalDebt() {
        return reader.loans.getBalanceAndSupply("Loans", reader.getAddress()).get("_totalSupply");
    }

    protected void claimAllRewards() {
        for (BalancedClient client : balanced.balancedClients.values()) {
            if(client.rewards.getBalnHolding(client.getAddress()).compareTo(EXA) < 0) {
                continue;
            }

            client.rewards.claimRewards();
            BigInteger balance = client.baln.balanceOf(client.getAddress());
            if (balance.compareTo(EXA) > 0) {
                client.baln.stake(balance);
            }
        }
    }

    protected void setLockingRatio(BalancedClient voter, String symbol, BigInteger ratio, String name) throws Exception {
        JsonObject setLockingRatioParameters = new JsonObject()
            .add("_symbol", symbol)
            .add("_value", ratio.intValue());
            
        JsonArray setLockingRatioCall = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParameters);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCall);
        executeVoteActions(balanced, voter, name, actions);
    }

    private void addCollateralType(BalancedClient minter, Address collateralAddress, BigInteger tokenAmount, BigInteger bnUSDAmount, String peg) {
        minter.irc2(collateralAddress).mintTo(owner.getAddress(), tokenAmount, new byte[0]);
        depositToStabilityContract(owner, bnUSDAmount);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        owner.irc2(collateralAddress).transfer(balanced.dex._address(), tokenAmount, depositData.toString().getBytes());

        BigInteger bnusdDeposit = owner.bnUSD.balanceOf(owner.getAddress());
        owner.bnUSD.transfer(balanced.dex._address(), bnusdDeposit, depositData.toString().getBytes());
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnusdDeposit, false);

        owner.governance.addCollateral(collateralAddress, true, peg, null);
    }

    private void getTokens(BalancedClient client, Address address, BigInteger amount) {
        if (address.equals(balanced.sicx._address())) {
            client.staking.stakeICX(amount.multiply(reader.staking.getTodayRate()).divide(EXA), null, null);
        } else if (address.equals(ethAddress)) {
            owner.irc2(ethAddress).mintTo(client.getAddress(), amount, null);
        }
    }
}
