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

    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);
    public static void setup() {
        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.baln.toggleEnableSnapshot();

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });

        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setVoteDefinitionFee(voteDefinitionFee);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        ethAddress = createIRC2Token(owner, "ICON ETH", "iETH");
        owner.irc2(ethAddress).setMinter(owner.getAddress());
    }

    @Test
    @Order(1)
    void takeLoans() throws Exception {
        addCollateralType(owner, ethAddress, BigInteger.TEN.pow(22), BigInteger.TEN.pow(24)); 

        // Arrange
        BalancedClient loantakerICX = balanced.newClient();
        BalancedClient loantakerSICX = balanced.newClient();
        BalancedClient twoStepLoanTaker = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger totalDebt = getTotalDebt();

        // Act
        loantakerICX.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(collateral, "bnUSD", BigInteger.ZERO, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);
        loantakerSICX.stakeDepositAndBorrow(collateral, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        totalDebt = debt.multiply(BigInteger.valueOf(3)).add(totalDebt);

        Map<String, BigInteger> loantakerIcxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerICX.getAddress());
        Map<String, BigInteger> loantakerSicxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());
        Map<String, BigInteger> twoStepLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());

        assertEquals(totalDebt, loantakerIcxBaS.get("_totalSupply"));
        assertEquals(debt, loantakerIcxBaS.get("_balance"));
        assertEquals(debt, loantakerSicxBaS.get("_balance"));
        assertEquals(debt, twoStepLoanTakerBaS.get("_balance"));
    }

    @Test
    @Order(2)
    void repyDebt() throws Exception {
        // Arrange
        BalancedClient loanTakerFullRepay = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullRepay.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.stakeDepositAndBorrow(collateral, loanAmount);

        loanTakerPartialRepay.loans.returnAsset("sICX", "bnUSD", debt.divide(BigInteger.TWO));
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullRepay.getAddress(), fee, null);
        loanTakerFullRepay.loans.returnAsset("sICX", "bnUSD", debt);

        BigInteger outstandingNewDebt = debt.divide(BigInteger.TWO);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(outstandingNewDebt);
        Map<String, BigInteger> loanTakerPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerPartialRepay.getAddress());
        Map<String, BigInteger> loanTakerFullRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerFullRepay.getAddress());

        assertEquals(expectedTotalDebt, loanTakerPartialRepayBaS.get("_totalSupply"));
        assertEquals(BigInteger.ZERO, loanTakerFullRepayBaS.get("_balance"));
        assertEquals(debt.divide(BigInteger.TWO), loanTakerPartialRepayBaS.get("_balance"));
    }

    @Test
    @Order(3)
    void withdrawCollateral() throws Exception {
        // Arrange
        BalancedClient loanTakerFullWithdraw = balanced.newClient();
        BalancedClient loanTakerPartialWithdraw = balanced.newClient();
        BalancedClient zeroLoanWithdrawFull = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullWithdraw.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialWithdraw.stakeDepositAndBorrow(collateral, loanAmount);
        zeroLoanWithdrawFull.stakeDepositAndBorrow(collateral, BigInteger.ZERO);

        loanTakerPartialWithdraw.bnUSD.transfer(loanTakerFullWithdraw.getAddress(), fee, null);
        loanTakerFullWithdraw.loans.returnAsset("sICX", "bnUSD", debt);
        BigInteger sICXCollateral = loanTakerFullWithdraw.getLoansAssetPosition("sICX");
        loanTakerFullWithdraw.loans.withdrawCollateral("sICX", sICXCollateral);

        assertThrows(UserRevertedException.class, () -> 
            loanTakerPartialWithdraw.loans.withdrawCollateral("sICX", sICXCollateral));
        BigInteger amountWithdrawn = BigInteger.TEN.pow(20);
        loanTakerPartialWithdraw.loans.withdrawCollateral("sICX", amountWithdrawn);

        zeroLoanWithdrawFull.loans.withdrawCollateral("sICX", sICXCollateral);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt);

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTakerFullWithdraw.getLoansAssetPosition("sICX"));
        assertEquals(sICXCollateral.subtract(amountWithdrawn), loanTakerPartialWithdraw.getLoansAssetPosition("sICX"));
        assertEquals(BigInteger.ZERO, zeroLoanWithdrawFull.getLoansAssetPosition("sICX"));
    }

    @Test
    @Order(4)
    void reOpenPosition() throws Exception {
        // Arrange
        BalancedClient loanTakerCloseLoanOnly = balanced.newClient();
        BalancedClient loanTakerCloseLoanOnly2 = balanced.newClient();
        BalancedClient loanTakerFullClose = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerCloseLoanOnly.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerCloseLoanOnly2.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerFullClose.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.stakeDepositAndBorrow(collateral, loanAmount);

        loanTakerPartialRepay.bnUSD.transfer(loanTakerCloseLoanOnly.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerCloseLoanOnly2.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullClose.getAddress(), fee, null);

        loanTakerCloseLoanOnly.loans.returnAsset("sICX", "bnUSD", debt);
        loanTakerCloseLoanOnly2.loans.returnAsset("sICX", "bnUSD", debt);
        loanTakerFullClose.loans.returnAsset("sICX", "bnUSD", debt);
        BigInteger amountRepaid = BigInteger.TEN.pow(21);
        loanTakerPartialRepay.loans.returnAsset("sICX", "bnUSD", amountRepaid);

        loanTakerFullClose.loans.withdrawCollateral("sICX", loanTakerFullClose.getLoansAssetPosition("sICX"));

        loanTakerCloseLoanOnly.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);
        loanTakerCloseLoanOnly2.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerFullClose.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", amountRepaid, null, null);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt.multiply(BigInteger.valueOf(4))).add(amountRepaid.multiply(feePercent).divide(POINTS));
        assertEquals(expectedTotalDebt, getTotalDebt());
    }


    @Test
    @Order(5)
    void takeLoans_multiCollateral() throws Exception {
        // Arrange
        BalancedClient loantakerSICX = balanced.newClient();
        BalancedClient loantakerMulti = balanced.newClient();
        BalancedClient loantakerIETH = balanced.newClient();
        
        BigInteger collateralICX = BigInteger.TEN.pow(23);
        BigInteger collateralETH = BigInteger.TEN.pow(20);

        owner.irc2(ethAddress).mintTo(loantakerMulti.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loantakerIETH.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger totalDebt = getTotalDebt();
        // Act
        loantakerSICX.stakeDepositAndBorrow(collateralICX, loanAmount);
        loantakerMulti.stakeDepositAndBorrow(collateralICX, loanAmount);
        loantakerMulti.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loantakerIETH.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        totalDebt = debt.multiply(BigInteger.valueOf(4)).add(totalDebt);

        // TODO assert more
        assertEquals(totalDebt, getTotalDebt());
    }

    @Test
    @Order(21)
    void rebalancing_raisePrice() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        reducePriceBelowThreshold();
        rebalance();

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) > 0);
    }

    @Test
    @Order(22)
    void rebalancing_lowerPrice() throws Exception {
        BigInteger initialTotalDebt = getTotalDebt();

        raisePriceAboveThreshold();
        rebalance();

        assertTrue(initialTotalDebt.compareTo(getTotalDebt()) < 0);
    }

    @Test
    @Order(31)
    void liquidate_throughGovernanceVote_noReserve() throws Exception {
        // Arrange
        balanced.increaseDay(1);
        claimAllRewards();

        BalancedClient voter = balanced.newClient();
        BigInteger expectedTotalDebt = getTotalDebt();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(13000);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup");

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);

        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio 31");

        // Act
        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate("sICX", loanTaker.getAddress());
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
        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("bnUSD"));
        assertEquals(BigInteger.ZERO, LiquidatedUserBaS.get("_balance"));
    }

    protected void rebalance() throws Exception {
        BalancedClient rebalancer = balanced.newClient();
        while (true) {
            BigInteger threshold = calculateThreshold();
            owner.rebalancing.rebalance(balanced.sicx._address());
            if (threshold.equals(calculateThreshold())) {
                return;
            }
        }
    }

    protected void reducePriceBelowThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
       while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.multiply(BigInteger.valueOf(105))) < 0) {
            reduceBnusdPrice();
        }
    }

    protected void raisePriceAboveThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.negate().multiply(BigInteger.valueOf(105))) > 0) {
            raiseBnusdPrice();
        }
    }

    protected BigInteger calculateThreshold() {
        BigInteger bnusdPriceInIcx = owner.bnUSD.lastPriceInLoop();
        BigInteger sicxPriceInIcx = owner.sicx.lastPriceInLoop();

        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(icxBnusdPoolId);
        BigInteger sicxLiquidity = hexObjectToBigInteger(poolStats.get("base"));
        BigInteger bnusdLiquidity = hexObjectToBigInteger(poolStats.get("quote"));

        BigInteger actualBnusdPriceInSicx = bnusdPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
        BigInteger bnusdPriceInSicx = sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage = (actualBnusdPriceInSicx.subtract(bnusdPriceInSicx)).multiply(EXA).divide(actualBnusdPriceInSicx);

        return priceDifferencePercentage;
    }

    protected void reduceBnusdPrice() throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        depositToStabilityContract(sellerClient, BigInteger.TEN.pow(22));
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.sicx._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.bnUSD.transfer(balanced.dex._address(), amountToSell, swapData.toString().getBytes());
    }

    protected void raiseBnusdPrice() throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        sellerClient.staking.stakeICX(amountToSell.multiply(BigInteger.TWO), null, null);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.sicx.transfer(balanced.dex._address(), amountToSell, swapData.toString().getBytes());
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

    protected void setLockingRatio(BalancedClient voter, BigInteger ratio, String name) throws Exception {
        JsonObject setLockingRatioParameters = new JsonObject()
        .add("_value", ratio.intValue());
        
        JsonArray setLockingRatioCall = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParameters);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCall);
        executeVoteActions(balanced, voter, name, actions);
    }

    private void addCollateralType(BalancedClient minter, Address collateralAddress, BigInteger tokenAmount, BigInteger bnUSDAmount) {
        minter.irc2(collateralAddress).mintTo(owner.getAddress(), tokenAmount, new byte[0]);
        depositToStabilityContract(owner, bnUSDAmount);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        owner.irc2(collateralAddress).transfer(balanced.dex._address(), tokenAmount, depositData.toString().getBytes());

        BigInteger bnusdDeposit = owner.bnUSD.balanceOf(owner.getAddress());
        owner.bnUSD.transfer(balanced.dex._address(), bnusdDeposit, depositData.toString().getBytes());
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnusdDeposit, false);

        owner.governance.addCollateral(collateralAddress, true);
    }
}
