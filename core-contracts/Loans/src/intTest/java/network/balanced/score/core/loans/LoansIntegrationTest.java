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
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.RevertedException;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class LoansIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;
    protected static Address ethAddress;
    protected static BigInteger iethNumberOfDecimals = BigInteger.valueOf(24);
    protected static BigInteger iethDecimals = BigInteger.TEN.pow(iethNumberOfDecimals.intValue());
    protected static BigInteger sicxDecimals = EXA;

    protected static Address btcAddress;

    private static BigInteger initialLockingRatio;
    private static BigInteger lockingRatio;
    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);

    public static void setup() {
        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDefinitionFee(voteDefinitionFee);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        owner.loans.removeBALN();
        ethAddress = createIRC2Token(owner, "ICON ETH", "iETH", iethNumberOfDecimals);
        owner.balancedOracle.getPriceInLoop((txr) -> {
        }, "ETH");
        owner.irc2(ethAddress).setMinter(owner.getAddress());

        btcAddress = createIRC2Token(owner, "ICON BTC", "iBTC");
        owner.irc2(btcAddress).setMinter(owner.getAddress());
    }

    @Test
    @Order(1)
    void takeLoans() throws Exception {
        // Arrange
        BalancedClient loanTakerICX = balanced.newClient();
        BalancedClient loanTakerSICX = balanced.newClient();
        BalancedClient twoStepLoanTaker = balanced.newClient();
        BalancedClient loanTakerMulti = balanced.newClient();
        BalancedClient loanTakerIETH = balanced.newClient();
        BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTakerMulti.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerIETH.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger totalDebt = getTotalDebt();

        // Act
        loanTakerICX.loans.depositAndBorrow(icxCollateral, "bnUSD", loanAmount, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(icxCollateral, "bnUSD", BigInteger.ZERO, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);
        loanTakerSICX.stakeDepositAndBorrow(icxCollateral, loanAmount);
        loanTakerMulti.stakeDepositAndBorrow(icxCollateral, loanAmount);

        // get enough baln to vote through a new collateral
        balanced.increaseDay(3);
        BigInteger ethAmount = BigInteger.valueOf(2).multiply(iethDecimals);
        BigInteger ethPriceInLoop = reader.balancedOracle.getLastPriceInLoop("ETH");
        BigInteger bnusdPriceInLoop = reader.balancedOracle.getLastPriceInLoop("bnUSD");
        BigInteger ethValue = ethAmount.multiply(ethPriceInLoop).divide(iethDecimals);
        BigInteger bnusdAmount = ethValue.multiply(EXA).divide(bnusdPriceInLoop);

        addCollateralType(owner, ethAddress, ethAmount, bnusdAmount, "ETH");

        loanTakerMulti.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerIETH.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        totalDebt = debt.multiply(BigInteger.valueOf(6)).add(totalDebt);

        Map<String, BigInteger> loanTakerIcxBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerICX.getAddress());
        Map<String, BigInteger> loanTakerSicxBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerSICX.getAddress());
        Map<String, BigInteger> twoStepLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerSICX.getAddress());
        Map<String, BigInteger> loanTakerMultiBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerMulti.getAddress());
        Map<String, BigInteger> loanTakerIETHBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerIETH.getAddress());

        assertEquals(totalDebt, loanTakerIcxBaS.get("_totalSupply"));
        assertEquals(debt, loanTakerIcxBaS.get("_balance"));
        assertEquals(debt, loanTakerSicxBaS.get("_balance"));
        assertEquals(debt, twoStepLoanTakerBaS.get("_balance"));
        assertEquals(debt.multiply(BigInteger.TWO), loanTakerMultiBaS.get("_balance"));
        assertEquals(debt, loanTakerIETHBaS.get("_balance"));

        assertEquals(collateralETH, loanTakerMulti.getLoansCollateralPosition("iETH"));
        assertEquals(icxCollateral, loanTakerMulti.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH, loanTakerIETH.getLoansCollateralPosition("iETH"));
    }

    @Test
    @Order(2)
    void takeLoan_dexPricedCollateral() throws Exception {
        // Arrange
        BigInteger btcAmount = BigInteger.TEN.pow(18);
        BigInteger bnusdAmount = btcAmount.multiply(reader.balancedOracle.getLastPriceInLoop("BTC"))
                .divide(reader.balancedOracle.getLastPriceInLoop("USD"));
        addDexCollateralType(owner, btcAddress, btcAmount, bnusdAmount);

        BalancedClient btcLoanTaker = balanced.newClient();
        BigInteger collateralBTC = BigInteger.TEN.pow(19);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        owner.irc2(btcAddress).mintTo(btcLoanTaker.getAddress(), collateralBTC, null);

        // Act
        btcLoanTaker.depositAndBorrow(btcAddress, collateralBTC, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        Map<String, BigInteger> btcLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans", btcLoanTaker.getAddress());
        assertEquals(debt, btcLoanTakerBaS.get("_balance"));
        assertEquals(collateralBTC, btcLoanTaker.getLoansCollateralPosition("iBTC"));
    }

    @Test
    @Order(3)
    void repayDebt() throws Exception {
        // Arrange
        BalancedClient loanTakerFullRepay = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BalancedClient loanTakerIETHFullRepay = balanced.newClient();
        BalancedClient loanTakerMultiPartialRepay = balanced.newClient();
        BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTakerIETHFullRepay.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerMultiPartialRepay.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initialTotalDebt = getTotalDebt();
        BigInteger initialsICXDebt = reader.loans.getTotalCollateralDebt("sICX", "bnUSD");
        BigInteger initialIETHDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD");

        // Act
        loanTakerFullRepay.stakeDepositAndBorrow(icxCollateral, loanAmount);
        loanTakerPartialRepay.stakeDepositAndBorrow(icxCollateral, loanAmount);
        loanTakerMultiPartialRepay.stakeDepositAndBorrow(icxCollateral, loanAmount);
        loanTakerIETHFullRepay.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerMultiPartialRepay.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        loanTakerPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX");
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullRepay.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerIETHFullRepay.getAddress(), fee, null);
        loanTakerFullRepay.loans.returnAsset("bnUSD", debt, "sICX");

        assertThrows(UserRevertedException.class, () ->
                loanTakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "sICX"));

        loanTakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "iETH");
        loanTakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX");
        loanTakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "iETH");

        BigInteger outstandingNewDebt = BigInteger.valueOf(3).multiply(debt.divide(BigInteger.TWO));

        // Assert
        BigInteger expectedTotalDebt = initialTotalDebt.add(outstandingNewDebt);
        BigInteger expectedSumDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD")
                .add(reader.loans.getTotalCollateralDebt("iBTC", "bnUSD"))
                .add(reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));
        Map<String, BigInteger> loanTakerPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerPartialRepay.getAddress());
        Map<String, BigInteger> loanTakerFullRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerFullRepay.getAddress());
        Map<String, BigInteger> loanTakerIETHFullRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerIETHFullRepay.getAddress());
        Map<String, BigInteger> loanTakerMultiPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerMultiPartialRepay.getAddress());

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(expectedTotalDebt, expectedSumDebt);
        assertEquals(BigInteger.ZERO, loanTakerFullRepayBaS.get("_balance"));
        assertEquals(debt.divide(BigInteger.TWO), loanTakerPartialRepayBaS.get("_balance"));
        assertEquals(BigInteger.ZERO, loanTakerIETHFullRepayBaS.get("_balance"));
        assertEquals(debt, loanTakerMultiPartialRepayBaS.get("_balance"));
    }

    @Test
    @Order(4)
    void sellCollateral() throws Exception {
        // Arrange
        BalancedClient loanTakerFullDebtSell = balanced.newClient();
        BalancedClient loanTakerPartialDebtSell = balanced.newClient();
        BalancedClient loanTakerETHFullSell = balanced.newClient();
        BalancedClient loanTakerETHPartialSell = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTakerETHFullSell.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerETHPartialSell.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.valueOf(17).multiply(EXA);
        BigInteger ethLoanAmount = BigInteger.TEN.pow(23);

        BigInteger collateralToSellFullDebt = BigInteger.TEN.pow(19);
        BigInteger ETHCollateralToSellFullDebt = BigInteger.valueOf(28).multiply(BigInteger.TEN.pow(22));
        BigInteger collateralToSellPartialDebt = BigInteger.valueOf(5).multiply(EXA);
        BigInteger ETHCollateralToSellPartialDebt = BigInteger.valueOf(2).multiply(BigInteger.TEN.pow(23));

        BigInteger minimumReceiveToSellFullDebt = BigInteger.valueOf(16).multiply(EXA);
        BigInteger minimumReceiveToSellPartialDebt = BigInteger.valueOf(8).multiply(EXA);

        // Act
        loanTakerFullDebtSell.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialDebtSell.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerETHFullSell.depositAndBorrow(ethAddress, collateralETH, ethLoanAmount);
        loanTakerETHPartialSell.depositAndBorrow(ethAddress, collateralETH, ethLoanAmount);

        //  requested collateral sell worth is more than debt
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateral, "sICX", minimumReceiveToSellFullDebt));
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateralETH, "iETH", minimumReceiveToSellFullDebt));

        //  minimum receive is more than users debt
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateral, "sICX", loanAmount.add(BigInteger.TEN.pow(19))));
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateralETH, "iETH",
                        ethLoanAmount.add(BigInteger.TEN.pow(19))));

        BigInteger loanTakerFullDebtSellCollateralPre = loanTakerFullDebtSell.getLoansCollateralPosition("sICX");
        BigInteger loanTakerETHFullDebtSellCollateralPre = loanTakerETHFullSell.getLoansCollateralPosition("iETH");
        BigInteger loanTakerPartialDebtSellCollateralPre = loanTakerPartialDebtSell.getLoansCollateralPosition("sICX");
        BigInteger loanTakerETHPartialDebtSellCollateralPre = loanTakerETHPartialSell.getLoansCollateralPosition(
                "iETH");

        BigInteger loanTakerFullDebtSellDebtPositionPre = loanTakerFullDebtSell.getLoansAssetPosition("sICX", "bnUSD");
        BigInteger loanTakerETHFullDebtSellDebtPositionPre = loanTakerETHFullSell.getLoansAssetPosition("iETH",
                "bnUSD");
        BigInteger loanTakerPartialDebtSellDebtPositionPre = loanTakerPartialDebtSell.getLoansAssetPosition("sICX",
                "bnUSD");
        BigInteger loanTakerETHPartialDebtSellDebtPositionPre = loanTakerETHPartialSell.getLoansAssetPosition("iETH",
                "bnUSD");

        loanTakerFullDebtSell.loans.sellCollateral(collateralToSellFullDebt, "sICX", minimumReceiveToSellFullDebt);
        loanTakerETHFullSell.loans.sellCollateral(ETHCollateralToSellFullDebt, "iETH", minimumReceiveToSellFullDebt);
        loanTakerPartialDebtSell.loans.sellCollateral(collateralToSellPartialDebt, "sICX",
                minimumReceiveToSellPartialDebt);
        loanTakerETHPartialSell.loans.sellCollateral(ETHCollateralToSellPartialDebt, "iETH",
                minimumReceiveToSellPartialDebt);

        BigInteger loanTakerFullDebtSellCollateralPost = loanTakerFullDebtSell.getLoansCollateralPosition("sICX");
        BigInteger loanTakerETHFullDebtSellCollateralPost = loanTakerETHFullSell.getLoansCollateralPosition("iETH");
        BigInteger loanTakerPartialDebtSellCollateralPost = loanTakerPartialDebtSell.getLoansCollateralPosition("sICX");
        BigInteger loanTakerETHPartialDebtSellCollateralPost = loanTakerETHPartialSell.getLoansCollateralPosition(
                "iETH");

        BigInteger loanTakerFullDebtSellDebtPositionPost = loanTakerFullDebtSell.getLoansAssetPosition("sICX", "bnUSD");
        BigInteger loanTakerETHFullDebtSellDebtPositionPost = loanTakerETHFullSell.getLoansAssetPosition("iETH",
                "bnUSD");
        BigInteger loanTakerPartialDebtSellDebtPositionPost = loanTakerPartialDebtSell.getLoansAssetPosition("sICX",
                "bnUSD");
        BigInteger loanTakerETHPartialDebtSellDebtPositionPost = loanTakerETHPartialSell.getLoansAssetPosition("iETH"
                , "bnUSD");

        //  Assert
        //  requested collateral sell more than available collateral
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateral, "sICX", minimumReceiveToSellFullDebt));
        assertThrows(UserRevertedException.class, () ->
                loanTakerFullDebtSell.loans.sellCollateral(collateralETH, "iETH", minimumReceiveToSellFullDebt));

        assertEquals(loanTakerFullDebtSellCollateralPost,
                loanTakerFullDebtSellCollateralPre.subtract(collateralToSellFullDebt));
        assertEquals(loanTakerETHFullDebtSellCollateralPost,
                loanTakerETHFullDebtSellCollateralPre.subtract(ETHCollateralToSellFullDebt));
        assertEquals(loanTakerPartialDebtSellCollateralPost,
                loanTakerPartialDebtSellCollateralPre.subtract(collateralToSellPartialDebt));
        assertEquals(loanTakerETHPartialDebtSellCollateralPost,
                loanTakerETHPartialDebtSellCollateralPre.subtract(ETHCollateralToSellPartialDebt));

        assertTrue(loanTakerFullDebtSellDebtPositionPost.compareTo(loanTakerFullDebtSellDebtPositionPre) < 0);
        assertTrue(loanTakerETHFullDebtSellDebtPositionPost.compareTo(loanTakerETHFullDebtSellDebtPositionPre) < 0);
        assertTrue(loanTakerPartialDebtSellDebtPositionPost.compareTo(loanTakerPartialDebtSellDebtPositionPre) < 0);
        assertTrue(loanTakerETHPartialDebtSellDebtPositionPost.compareTo(loanTakerETHPartialDebtSellDebtPositionPre) < 0);
    }

    @Test
    @Order(4)
    void withdrawCollateral() throws Exception {
        // Arrange
        BalancedClient loanTakerFullWithdraw = balanced.newClient();
        BalancedClient loanTakerPartialWithdraw = balanced.newClient();
        BalancedClient loanTakerETHFullWithdraw = balanced.newClient();
        BalancedClient loanTakerETHPartialWithdraw = balanced.newClient();
        BalancedClient zeroLoanWithdrawFull = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTakerETHFullWithdraw.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerETHPartialWithdraw.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initialTotalDebt = getTotalDebt();

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
        BigInteger amountiETHToWithdraw = BigInteger.TEN.pow(6);

        assertThrows(UserRevertedException.class, () ->
                loanTakerPartialWithdraw.loans.withdrawCollateral(collateral, "sICX"));
        loanTakerPartialWithdraw.loans.withdrawCollateral(amountToWithdraw, "sICX");

        assertThrows(UserRevertedException.class, () ->
                loanTakerETHPartialWithdraw.loans.withdrawCollateral(amountToWithdraw, "sICX"));
        loanTakerETHPartialWithdraw.loans.withdrawCollateral(amountiETHToWithdraw, "iETH");

        zeroLoanWithdrawFull.loans.withdrawCollateral(collateral, "sICX");

        // Assert
        BigInteger expectedTotalDebt = initialTotalDebt.add(debt).add(debt);
        BigInteger expectedSumDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD")
                .add(reader.loans.getTotalCollateralDebt("iBTC", "bnUSD"))
                .add(reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(expectedTotalDebt, expectedSumDebt);
        assertEquals(BigInteger.ZERO, loanTakerFullWithdraw.getLoansCollateralPosition("sICX"));
        assertEquals(collateral.subtract(amountToWithdraw), loanTakerPartialWithdraw.getLoansCollateralPosition("sICX"
        ));
        assertEquals(BigInteger.ZERO, zeroLoanWithdrawFull.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH.subtract(amountiETHToWithdraw),
                loanTakerETHPartialWithdraw.getLoansCollateralPosition("iETH"));
        assertEquals(BigInteger.ZERO, loanTakerETHPartialWithdraw.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTakerETHFullWithdraw.getLoansCollateralPosition("iETH"));
    }

    @Test
    @Order(5)
    void reOpenPosition() throws Exception {
        // Arrange
        BalancedClient loanTakerCloseLoanOnly = balanced.newClient();
        BalancedClient loanTakerFullClose = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BalancedClient loanTakerETHFullClose = balanced.newClient();
        BalancedClient loanTakerETHPartialRepay = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTakerETHFullClose.getAddress(), collateralETH, null);
        owner.irc2(ethAddress).mintTo(loanTakerETHPartialRepay.getAddress(), collateralETH, null);

        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initialTotalDebt = getTotalDebt();

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
        loanTakerETHFullClose.loans.withdrawCollateral(loanTakerETHFullClose.getLoansCollateralPosition("iETH"),
                "iETH");

        loanTakerCloseLoanOnly.borrowFrom("sICX", loanAmount);
        loanTakerFullClose.stakeDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.borrowFrom("sICX", amountRepaid);
        loanTakerETHFullClose.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerETHPartialRepay.borrowFrom("iETH", amountETHRepaid);

        // Assert
        BigInteger extraFees = amountRepaid.multiply(feePercent).divide(POINTS);
        BigInteger extraFeesETHLoan = amountETHRepaid.multiply(feePercent).divide(POINTS);
        BigInteger expectedTotalDebt =
                initialTotalDebt.add(debt.multiply(BigInteger.valueOf(5))).add(extraFees).add(extraFeesETHLoan);
        BigInteger expectedSumDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD")
                .add(reader.loans.getTotalCollateralDebt("iBTC", "bnUSD"))
                .add(reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));
        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(expectedTotalDebt, expectedSumDebt);
    }

    @Test
    @Order(6)
    void withdrawAndUnstake() throws Exception {
        // Arrange
        BalancedClient loanTakerSICX = balanced.newClient();
        BalancedClient staker = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);

        BigInteger totalDebt = getTotalDebt();

        // Act
        loanTakerSICX.stakeDepositAndBorrow(collateral, BigInteger.ZERO);
        loanTakerSICX.loans.withdrawAndUnstake(collateral);
        staker.staking.stakeICX(collateral, null, null);

        // Assert
        assertEquals(collateral, loanTakerSICX.staking.claimableICX(loanTakerSICX.getAddress()));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(7)
    void debtCeilings() throws Exception {
        // Arrange
        BalancedClient loanTaker1 = balanced.newClient();
        BalancedClient loanTaker2 = balanced.newClient();
        BigInteger sICXCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);

        BigInteger loanAmount1 = BigInteger.TEN.pow(22);
        BigInteger loanAmount2 = BigInteger.TEN.pow(21);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee1 = loanAmount1.multiply(feePercent).divide(POINTS);
        BigInteger fee2 = loanAmount2.multiply(feePercent).divide(POINTS);
        BigInteger debt1 = loanAmount1.add(fee1);
        BigInteger debt2 = loanAmount2.add(fee2);

        BigInteger initialTotalDebt = getTotalDebt();
        BigInteger initialsICXDebt = reader.loans.getTotalCollateralDebt("sICX", "bnUSD");
        BigInteger initialIETHDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD");
        Map<String, Map<String, Object>> debtDetails =
                (Map<String, Map<String, Object>>) reader.loans.getAvailableAssets().get("bnUSD").get("debt_details");
        BigInteger badDebt = hexObjectToBigInteger(debtDetails.get("sICX").get("bad_debt"));
        owner.governance.setDebtCeiling("sICX", initialsICXDebt.add(debt1).add(badDebt));

        // Act
        loanTaker1.stakeDepositAndBorrow(sICXCollateral, loanAmount1);
        assertThrows(RevertedException.class, () -> loanTaker2.stakeDepositAndBorrow(sICXCollateral, loanAmount2));

        loanTaker1.loans.returnAsset("bnUSD", debt2, "sICX");
        loanTaker2.stakeDepositAndBorrow(sICXCollateral, loanAmount2);
        assertThrows(UserRevertedException.class, () -> loanTaker1.borrowFrom("sICX", debt2));

        BigInteger outstandingNewDebt = debt1;

        // Assert
        BigInteger expectedTotalDebt = initialTotalDebt.add(outstandingNewDebt);

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(initialsICXDebt.add(outstandingNewDebt), reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));
        assertEquals(initialIETHDebt, reader.loans.getTotalCollateralDebt("iETH", "bnUSD"));
        owner.governance.setDebtCeiling("sICX", BigInteger.TEN.pow(28));
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
        initialLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        lockingRatio = BigInteger.valueOf(13000);
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        setLockingRatio(voter, "sICX", lockingRatio, "Liquidation setup ICX");
        setLockingRatio(voter, "iETH", lockingRatio, "Liquidation setup IETH");
    }

    @Test
    @Order(32)
    void liquidateSICX_throughGovernanceVote() throws Exception {
        // Arrange
        BigInteger initialDebt = getTotalDebt();
        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();

        BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), collateralETH, null);

        BigInteger collateralValue = icxCollateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(icxCollateral, "bnUSD", loan, null, null);
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
        assertEquals(initialDebt.add(ethDebt), getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(collateralETH, loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(ethDebt, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
        assertEquals(ethDebt, LiquidatedUserBaS.get("_balance"));
    }

    @Test
    @Order(32)
    void liquidateIETH_throughGovernanceVote() throws Exception {
        // Arrange
        BigInteger initialDebt = getTotalDebt();
        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();

        BigInteger icxCollateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger collateralETH = BigInteger.TEN.multiply(iethDecimals);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), collateralETH, null);

        BigInteger collateralValue =
                collateralETH.multiply(reader.balancedOracle.getLastPriceInLoop("iETH")).divide(iethDecimals);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan = (maxDebt.subtract(maxFee)).multiply(EXA).divide(reader.balancedOracle.getLastPriceInLoop(
                "bnUSD"));
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.depositAndBorrow(ethAddress, collateralETH, loan);

        BigInteger sICXLoan = BigInteger.TEN.pow(21);
        BigInteger sICXDebt = sICXLoan.add(sICXLoan.multiply(feePercent).divide(POINTS));

        loanTaker.loans.depositAndBorrow(icxCollateral, "bnUSD", sICXLoan, null, null);
        BigInteger expectedsICXCollateral = icxCollateral.multiply(EXA).divide(reader.balancedOracle.getLastPriceInLoop(
                "sICX"));

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
        assertEquals(initialDebt.add(sICXDebt), getTotalDebt());
        assertEquals(expectedsICXCollateral, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(sICXDebt, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
        assertEquals(sICXDebt, LiquidatedUserBaS.get("_balance"));
    }

    @Test
    @Order(39)
    void resetLockingRatio() throws Exception {
        BalancedClient voter = balanced.newClient();
        initialLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));
        setLockingRatio(voter, "sICX", initialLockingRatio, "restore locking ratio sICX");
        setLockingRatio(voter, "iETH", initialLockingRatio, "restore locking ratio iETH");
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
        BigInteger collateralPriceInIcx =
                owner.balancedOracle.getLastPriceInLoop(reader.irc2(collateralAddress).symbol());

        BigInteger poolId = owner.dex.getPoolId(collateralAddress, balanced.bnusd._address());
        BigInteger decimals = BigInteger.TEN.pow(reader.irc2(collateralAddress).decimals().intValue());
        Map<String, Object> poolStats = owner.dex.getPoolStats(poolId);
        BigInteger collateralLiquidity = hexObjectToBigInteger(poolStats.get("base"));
        BigInteger bnusdLiquidity = hexObjectToBigInteger(poolStats.get("quote"));

        BigInteger actualBnusdPriceInCollateral = bnusdPriceInIcx.multiply(decimals).divide(collateralPriceInIcx);
        BigInteger bnusdPriceInCollateral = collateralLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage =
                (actualBnusdPriceInCollateral.subtract(bnusdPriceInCollateral)).multiply(EXA).divide(actualBnusdPriceInCollateral);

        return priceDifferencePercentage;
    }

    protected void reducePrice(Address collateralAddress) throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger poolId = owner.dex.getPoolId(collateralAddress, balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(poolId);
        BigInteger bnusdLiquidity = hexObjectToBigInteger(poolStats.get("quote"));
        BigInteger amountToSell = bnusdLiquidity.divide(BigInteger.valueOf(100));
        depositToStabilityContract(sellerClient, amountToSell);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", collateralAddress.toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.bnUSD.transfer(balanced.dex._address(), sellerClient.bnUSD.balanceOf(sellerClient.getAddress()),
                swapData.toString().getBytes());
    }

    protected void raisePrice(Address collateralAddress) throws Exception {
        BalancedClient sellerClient = balanced.newClient();
        BigInteger poolId = owner.dex.getPoolId(collateralAddress, balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(poolId);
        BigInteger collateralLiquidity = hexObjectToBigInteger(poolStats.get("base"));
        BigInteger amountToSell = collateralLiquidity.divide(BigInteger.valueOf(100));
        getTokens(sellerClient, collateralAddress, amountToSell);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", balanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.irc2(collateralAddress).transfer(balanced.dex._address(), amountToSell,
                swapData.toString().getBytes());
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
            if (client.rewards.getBalnHolding(client.getAddress()).compareTo(EXA) < 0) {
                continue;
            }

            client.rewards.claimRewards(null);
            BigInteger balance = client.baln.availableBalanceOf(client.getAddress());
            BigInteger boostedBalance = client.boostedBaln.balanceOf(client.getAddress(), BigInteger.ZERO);
            if (boostedBalance.equals(BigInteger.ZERO) && balance.compareTo(EXA) > 0) {
                long unlockTime =
                        (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(52).multiply(MICRO_SECONDS_IN_A_DAY).multiply(BigInteger.valueOf(7))).longValue();
                String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
                client.baln.transfer(owner.boostedBaln._address(), balance, data.getBytes());
            }
        }
    }

    protected void setLockingRatio(BalancedClient voter, String symbol, BigInteger ratio, String name) {
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

    private void addCollateralType(BalancedClient minter, Address collateralAddress, BigInteger tokenAmount,
                                   BigInteger bnUSDAmount, String peg) {
        minter.irc2(collateralAddress).mintTo(owner.getAddress(), tokenAmount, new byte[0]);
        depositToStabilityContract(owner, bnUSDAmount.add(voteDefinitionFee).multiply(BigInteger.TWO));
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        owner.irc2(collateralAddress).transfer(balanced.dex._address(), tokenAmount, depositData.toString().getBytes());

        owner.bnUSD.transfer(balanced.dex._address(), bnUSDAmount, depositData.toString().getBytes());
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnUSDAmount, false);
        BigInteger lockingRatio = BigInteger.valueOf(40_000);
        BigInteger liquidationRatio = BigInteger.valueOf(15_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(30);

        JsonObject addCollateralParameters = new JsonObject()
                .add("_token_address", collateralAddress.toString())
                .add("_active", true)
                .add("_peg", peg)
                .add("_lockingRatio", lockingRatio.toString())
                .add("_liquidationRatio", liquidationRatio.toString())
                .add("_debtCeiling", debtCeiling.toString());

        JsonArray addCollateral = new JsonArray()
                .add("addCollateral")
                .add(addCollateralParameters);

        JsonArray actions = new JsonArray()
                .add(addCollateral);

        String symbol = reader.irc2(collateralAddress).symbol();
        claimAllRewards();
        executeVoteActions(balanced, owner, "add collateral " + symbol, actions);

        assertEquals(lockingRatio, reader.loans.getLockingRatio(symbol));
        assertEquals(liquidationRatio, reader.loans.getLiquidationRatio(symbol));
        assertEquals(debtCeiling, reader.loans.getDebtCeiling(symbol));
    }

    private void addDexCollateralType(BalancedClient minter, Address collateralAddress, BigInteger tokenAmount,
                                      BigInteger bnUSDAmount) {
        minter.irc2(collateralAddress).mintTo(owner.getAddress(), tokenAmount, new byte[0]);
        depositToStabilityContract(owner, bnUSDAmount.add(voteDefinitionFee).multiply(BigInteger.TWO));
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        owner.irc2(collateralAddress).transfer(balanced.dex._address(), tokenAmount, depositData.toString().getBytes());

        owner.bnUSD.transfer(balanced.dex._address(), bnUSDAmount, depositData.toString().getBytes());
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnUSDAmount, false);
        BigInteger lockingRatio = BigInteger.valueOf(40_000);
        BigInteger liquidationRatio = BigInteger.valueOf(15_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(30);

        JsonObject addCollateralParameters = new JsonObject()
                .add("_token_address", collateralAddress.toString())
                .add("_active", true)
                .add("_lockingRatio", lockingRatio.toString())
                .add("_liquidationRatio", liquidationRatio.toString())
                .add("_debtCeiling", debtCeiling.toString());

        JsonArray addCollateral = new JsonArray()
                .add("addDexPricedCollateral")
                .add(addCollateralParameters);

        JsonArray actions = new JsonArray()
                .add(addCollateral);

        String symbol = reader.irc2(collateralAddress).symbol();
        executeVoteActions(balanced, owner, "add dex collateral " + symbol, actions);

        assertEquals(lockingRatio, reader.loans.getLockingRatio(symbol));
        assertEquals(liquidationRatio, reader.loans.getLiquidationRatio(symbol));
        assertEquals(debtCeiling, reader.loans.getDebtCeiling(symbol));
    }

    private void getTokens(BalancedClient client, Address address, BigInteger amount) {
        if (address.equals(balanced.sicx._address())) {
            client.staking.stakeICX(amount.multiply(reader.staking.getTodayRate()).divide(EXA), null, null);
        } else if (address.equals(ethAddress)) {
            owner.irc2(ethAddress).mintTo(client.getAddress(), amount, null);
        }
    }

    private BigInteger getAmountToSell(Address address) {
        if (address.equals(balanced.sicx._address())) {
            return BigInteger.TEN.pow(21);
        } else if (address.equals(ethAddress)) {
            return iethDecimals;
        }
        return BigInteger.ZERO;
    }
}
