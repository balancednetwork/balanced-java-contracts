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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.RevertedException;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;

import score.UserRevertedException;
import foundation.icon.xcall.NetworkAddress;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.*;

import static network.balanced.score.core.loans.LoansVariables.governance;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.valueOf;

abstract class LoansIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;
    protected static Address ethAddress;
    protected static BigInteger iethNumberOfDecimals = BigInteger.valueOf(24);
    protected static BigInteger iethDecimals = BigInteger.TEN.pow(iethNumberOfDecimals.intValue());
    protected static BigInteger sicxDecimals = EXA;

    protected static BalancedClient nativeLoanTaker;
    private static BigInteger initialLockingRatio;
    private static BigInteger lockingRatio;
    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);

    public static void setup() throws Exception {
        whitelistToken(balanced, balanced.sicx._address(), BigInteger.TEN.pow(10));
        owner.governance.setVoteDefinitionFee(voteDefinitionFee);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        JsonArray setMaxRetirePercentParameters = new JsonArray().add(createParameter(BigInteger.valueOf(1000)));
        JsonArray setMaxRetirePercent = new JsonArray().add(createTransaction(balanced.loans._address(),
                "setMaxRetirePercent", setMaxRetirePercentParameters));
        owner.governance.execute(setMaxRetirePercent.toString());

        ethAddress = createIRC2Token(owner, "ICON ETH", "iETH", iethNumberOfDecimals);
        owner.irc2(ethAddress).setMinter(owner.getAddress());
        addCollateral(balanced.bscBaseAsset, "ETH");
        addCollateral(balanced.ethBaseAsset, "ETH");

        nativeLoanTaker = balanced.newClient();
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
        BigInteger ethPrice = reader.balancedOracle.getLastPriceInUSD("ETH");
        BigInteger bnusdAmount = ethAmount.multiply(ethPrice).divide(iethDecimals);

        addCollateralAndLiquidity(owner, ethAddress, ethAmount, bnusdAmount, "ETH");

        loanTakerMulti.depositAndBorrow(ethAddress, collateralETH, loanAmount);
        loanTakerIETH.depositAndBorrow(ethAddress, collateralETH, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        totalDebt = debt.multiply(BigInteger.valueOf(6)).add(totalDebt);

        Map<String, BigInteger> loanTakerIcxBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerICX.getAddress().toString());
        Map<String, BigInteger> loanTakerSicxBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerSICX.getAddress().toString());
        Map<String, BigInteger> twoStepLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerSICX.getAddress().toString());
        Map<String, BigInteger> loanTakerMultiBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerMulti.getAddress().toString());
        Map<String, BigInteger> loanTakerIETHBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerIETH.getAddress().toString());

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

    @Nested
    @DisplayName("Crosschain Loans")
    @TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
    class CrosschainTests {
        NetworkAddress ethUser = new NetworkAddress(balanced.ETH_NID, "0x11");
        NetworkAddress bscUser = new NetworkAddress(balanced.BSC_NID, "0x12");
        NetworkAddress bscHubUser = new NetworkAddress(balanced.BSC_NID, "0x13");
        NetworkAddress bscBridgeUser = new NetworkAddress(balanced.BSC_NID, "0x14");
        String loansNetAddress = new NetworkAddress(balanced.ICON_NID, balanced.loans._address()).toString();
        BigInteger collateral = BigInteger.TEN.multiply(sicxDecimals);
        BigInteger loanAmount = BigInteger.TEN.pow(22);



        @Test
        @Order(1)
        void crossChainDepositAndBorrow() throws Exception {
            // Arrange
            BigInteger totalDebt = getTotalDebt();

            // Act
            // Borrow directly trough deposit
            JsonObject loanData = new JsonObject()
                .add("_amount", loanAmount.toString());
            byte[] depositAndBorrowETH = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethUser.account().toString(), loansNetAddress, collateral, loanData.toString().getBytes());
            owner.xcall.recvCall(balanced.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), depositAndBorrowETH);

            // Deposit first then borrow
            byte[] depositBSC = AssetManagerMessages.deposit(balanced.BSC_TOKEN_ADDRESS, bscUser.account().toString(), loansNetAddress, collateral, "{}".getBytes());
            owner.xcall.recvCall(balanced.assetManager._address(), new NetworkAddress(balanced.BSC_NID, balanced.BSC_ASSET_MANAGER).toString(), depositBSC);
            byte[] borrowBSC = LoansMessages.xBorrow(balanced.BSC_TOKEN_SYMBOL, loanAmount, "", new byte[0]);
            owner.xcall.recvCall(balanced.loans._address(), bscUser.toString(), borrowBSC);

            // Bridge collateral to hub wallet first then borrow
            byte[] transferBSC = AssetManagerMessages.deposit(balanced.BSC_TOKEN_ADDRESS, bscHubUser.account().toString(), bscHubUser.toString(), collateral, new byte[0]);
            owner.xcall.recvCall(balanced.assetManager._address(), new NetworkAddress(balanced.BSC_NID, balanced.BSC_ASSET_MANAGER).toString(), transferBSC);

            byte[] depositAndBorrowTransfer = SpokeTokenMessages.xHubTransfer(loansNetAddress, collateral, loanData.toString().getBytes());
            owner.xcall.recvCall(balanced.bscBaseAsset, bscHubUser.toString(), depositAndBorrowTransfer);

            // Bridge collateral to ICON wallet first then borrow
            byte[] transferBSCToICON = AssetManagerMessages.deposit(balanced.BSC_TOKEN_ADDRESS, bscHubUser.account().toString(), new NetworkAddress(balanced.ICON_NID, nativeLoanTaker.getAddress()).toString(), collateral, new byte[0]);
            owner.xcall.recvCall(balanced.assetManager._address(), new NetworkAddress(balanced.BSC_NID, balanced.BSC_ASSET_MANAGER).toString(), transferBSCToICON);
            nativeLoanTaker.spokeToken(balanced.bscBaseAsset).transfer(balanced.loans._address(), collateral, loanData.toString().getBytes());

            // Assert
            BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
            BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
            BigInteger debt = loanAmount.add(fee);
            totalDebt = debt.multiply(BigInteger.valueOf(4)).add(totalDebt);

            Map<String, BigInteger> loanTakerETH = reader.loans.getBalanceAndSupply("Loans", ethUser.toString());
            Map<String, BigInteger> loanTakerBSC= reader.loans.getBalanceAndSupply("Loans", bscUser.toString());
            Map<String, BigInteger> loanTakerHUBBSC = reader.loans.getBalanceAndSupply("Loans", bscHubUser.toString());
            Map<String, BigInteger> nativeLoanTakerBSC = reader.loans.getBalanceAndSupply("Loans", nativeLoanTaker.getAddress().toString());

            assertEquals(totalDebt, loanTakerETH.get("_totalSupply"));
            assertEquals(debt, loanTakerETH.get("_balance"));
            assertEquals(debt, loanTakerBSC.get("_balance"));
            assertEquals(debt, loanTakerHUBBSC.get("_balance"));
            assertEquals(debt, nativeLoanTakerBSC.get("_balance"));

            Map<String, Map<String, String>> assetsETH = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(ethUser.toString()).get("holdings");
            Map<String, Map<String, String>> assetsBSC = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(bscUser.toString()).get("holdings");
            Map<String, Map<String, String>> assetsBSCHUB = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(bscHubUser.toString()).get("holdings");

            assertEquals(collateral, hexObjectToBigInteger(assetsETH.get(balanced.ETH_TOKEN_SYMBOL).get(balanced.ETH_TOKEN_SYMBOL)));
            assertEquals(collateral, hexObjectToBigInteger(assetsBSC.get(balanced.BSC_TOKEN_SYMBOL).get(balanced.BSC_TOKEN_SYMBOL)));
            assertEquals(collateral, hexObjectToBigInteger(assetsBSCHUB.get(balanced.BSC_TOKEN_SYMBOL).get(balanced.BSC_TOKEN_SYMBOL)));
            assertEquals(collateral, nativeLoanTaker.getLoansCollateralPosition(balanced.BSC_TOKEN_SYMBOL));
        }

        @Test
        @Order(3)
        void crossChainRepayAndWithdraw() throws Exception {
            // Arrange
            BigInteger totalDebt = getTotalDebt();
            BigInteger amountToWithdraw = collateral.divide(BigInteger.TWO);
            BigInteger amountToRepay = loanAmount;

            // Act
            // Repay and withdraw through croschain transfer
            JsonObject repayData = new JsonObject()
                .add("_collateral", balanced.ETH_TOKEN_SYMBOL)
                .add("_withdrawAmount", amountToWithdraw.toString());
            byte[] repayAndWithdraw = HubTokenMessages.xCrossTransfer(ethUser.toString(), loansNetAddress, amountToRepay, repayData.toString().getBytes());
            owner.xcall.recvCall(balanced.bnusd._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_BNUSD_ADDRESS).toString(), repayAndWithdraw);

            // Repay through transfer, then withdraw via xCall
            repayData = new JsonObject()
                .add("_collateral", balanced.BSC_TOKEN_SYMBOL)
                .add("_withdrawAmount", "0");
            byte[] repay = HubTokenMessages.xCrossTransfer(bscUser.toString(), loansNetAddress, amountToRepay, repayData.toString().getBytes());
            owner.xcall.recvCall(balanced.bnusd._address(), new NetworkAddress(balanced.BSC_NID, balanced.BSC_BNUSD_ADDRESS).toString(), repay);

            byte[] withdraw = LoansMessages.xWithdraw(amountToWithdraw, balanced.BSC_TOKEN_SYMBOL, "");
            owner.xcall.recvCall(balanced.loans._address(), bscUser.toString(), withdraw);

            // Repay and withdraw with bnUSD on the hub.
            byte[] deposit = HubTokenMessages.xCrossTransfer(bscHubUser.toString(), bscHubUser.toString(), amountToRepay, new byte[0]);
            owner.xcall.recvCall(balanced.bnusd._address(), new NetworkAddress(balanced.BSC_NID, balanced.BSC_BNUSD_ADDRESS).toString(), deposit);
            repayData = new JsonObject()
                .add("_collateral", balanced.BSC_TOKEN_SYMBOL)
                .add("_withdrawAmount", amountToWithdraw.toString());

            byte[] repayTransfer = HubTokenMessages.xHubTransfer(loansNetAddress, amountToRepay, repayData.toString().getBytes());
            owner.xcall.recvCall(balanced.bnusd._address(), bscHubUser.toString(), repayTransfer);

            // Repay and withdraw with bnUSD on the ICON wallet.
            nativeLoanTaker.loans.returnAsset("bnUSD", amountToRepay, balanced.BSC_TOKEN_SYMBOL, "");
            nativeLoanTaker.loans.withdrawCollateral(amountToWithdraw, balanced.BSC_TOKEN_SYMBOL);

            // Assert
            BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
            BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
            BigInteger initialDebt = loanAmount.add(fee);

            Map<String, BigInteger> loanTakerETH = reader.loans.getBalanceAndSupply("Loans", ethUser.toString());
            Map<String, BigInteger> loanTakerBSC = reader.loans.getBalanceAndSupply("Loans", bscUser.toString());
            Map<String, BigInteger> loanTakerHUBBSC = reader.loans.getBalanceAndSupply("Loans", bscHubUser.toString());
            Map<String, BigInteger> nativeLoanTakerBSC = reader.loans.getBalanceAndSupply("Loans", nativeLoanTaker.getAddress().toString());

            assertEquals(totalDebt.subtract(amountToRepay.multiply(BigInteger.valueOf(4))), loanTakerETH.get("_totalSupply"));
            assertEquals(initialDebt.subtract(amountToRepay), loanTakerETH.get("_balance"));
            assertEquals(initialDebt.subtract(amountToRepay), loanTakerBSC.get("_balance"));
            assertEquals(initialDebt.subtract(amountToRepay), loanTakerHUBBSC.get("_balance"));
            assertEquals(initialDebt.subtract(amountToRepay), nativeLoanTakerBSC.get("_balance"));

            Map<String, Map<String, String>> assetsETH = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(ethUser.toString()).get("holdings");
            Map<String, Map<String, String>> assetsBSC = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(bscUser.toString()).get("holdings");
            Map<String, Map<String, String>> assetsBSCHUB = (Map<String, Map<String, String>>) reader.loans.getAccountPositions(bscHubUser.toString()).get("holdings");

            assertEquals(collateral.subtract(amountToWithdraw), hexObjectToBigInteger(assetsETH.get(balanced.ETH_TOKEN_SYMBOL).get(balanced.ETH_TOKEN_SYMBOL)));
            assertEquals(collateral.subtract(amountToWithdraw), hexObjectToBigInteger(assetsBSC.get(balanced.BSC_TOKEN_SYMBOL).get(balanced.BSC_TOKEN_SYMBOL)));
            assertEquals(collateral.subtract(amountToWithdraw), hexObjectToBigInteger(assetsBSCHUB.get(balanced.BSC_TOKEN_SYMBOL).get(balanced.BSC_TOKEN_SYMBOL)));
            assertEquals(collateral.subtract(amountToWithdraw), nativeLoanTaker.getLoansCollateralPosition(balanced.BSC_TOKEN_SYMBOL));
        }
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

        loanTakerPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX", "");
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullRepay.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerIETHFullRepay.getAddress(), fee, null);
        loanTakerFullRepay.loans.returnAsset("bnUSD", debt, "sICX", "");

        assertThrows(UserRevertedException.class, () ->
                loanTakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "sICX", ""));

        loanTakerIETHFullRepay.loans.returnAsset("bnUSD", debt, "iETH", "");
        loanTakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "sICX", "");
        loanTakerMultiPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), "iETH", "");

        BigInteger outstandingNewDebt = BigInteger.valueOf(3).multiply(debt.divide(BigInteger.TWO));

        // Assert
        BigInteger expectedTotalDebt = initialTotalDebt.add(outstandingNewDebt);
        BigInteger expectedSumDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD")
                .add(reader.loans.getTotalCollateralDebt("iBTC", "bnUSD"))
                .add(reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));
        Map<String, BigInteger> loanTakerPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerPartialRepay.getAddress().toString());
        Map<String, BigInteger> loanTakerFullRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerFullRepay.getAddress().toString());
        Map<String, BigInteger> loanTakerIETHFullRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerIETHFullRepay.getAddress().toString());
        Map<String, BigInteger> loanTakerMultiPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans",
                loanTakerMultiPartialRepay.getAddress().toString());

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(expectedTotalDebt, expectedSumDebt);
        assertEquals(BigInteger.ZERO, loanTakerFullRepayBaS.get("_balance"));
        assertEquals(debt.divide(BigInteger.TWO), loanTakerPartialRepayBaS.get("_balance"));
        assertEquals(BigInteger.ZERO, loanTakerIETHFullRepayBaS.get("_balance"));
        assertEquals(debt, loanTakerMultiPartialRepayBaS.get("_balance"));
    }

    @Test
    @Order(4)
    void rateLimits() throws Exception {
        // Arrange
        BalancedClient loanTaker = balanced.newClient();

        BigInteger totalCollateral = reader.sicx.balanceOf(balanced.loans._address());
        BigInteger collateral = totalCollateral.divide(BigInteger.TEN); // increase total collateral by 10%

        // Act
        loanTaker.stakeDepositAndBorrow(collateral, BigInteger.ZERO);

        JsonArray setPercentageParameters = new JsonArray()
            .add(createParameter(balanced.sicx._address()))
            .add(createParameter(BigInteger.valueOf(500)));//5%
        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.loans._address(), "setFloorPercentage", setPercentageParameters));
        owner.governance.execute(actions.toString());

        JsonArray setTimeDelay = new JsonArray()
        .add(createParameter(balanced.sicx._address()))
        .add(createParameter(MICRO_SECONDS_IN_A_DAY)); // 1 day delay
        actions = new JsonArray()
                .add(createTransaction(balanced.loans._address(), "setTimeDelayMicroSeconds", setTimeDelay));
        owner.governance.execute(actions.toString());

        // Assert
        assertThrows(UserRevertedException.class, () ->
            loanTaker.loans.withdrawCollateral(collateral, "sICX"));

        BigInteger floor = reader.loans.getCurrentFloor(balanced.sicx._address());
        assertEquals(totalCollateral.add(collateral).multiply(BigInteger.valueOf(9500)).divide(POINTS), floor);
        loanTaker.loans.withdrawCollateral(collateral.divide(BigInteger.TWO), "sICX");

        BigInteger newFloor = reader.loans.getCurrentFloor(balanced.sicx._address());
        assertTrue(floor.compareTo(newFloor) > 0);
        // Assert floor is decreasing
        Thread.sleep(1000);
        BigInteger newFloor2 = reader.loans.getCurrentFloor(balanced.sicx._address());
        assertTrue(newFloor.compareTo(newFloor2) > 0);


        setPercentageParameters = new JsonArray()
            .add(createParameter(balanced.sicx._address()))
            .add(createParameter(BigInteger.ZERO));
        actions = new JsonArray()
                .add(createTransaction(balanced.loans._address(), "setFloorPercentage", setPercentageParameters));
        owner.governance.execute(actions.toString());

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

        loanTakerFullWithdraw.loans.returnAsset("bnUSD", debt, "sICX", "");
        loanTakerFullWithdraw.loans.withdrawCollateral(collateral, "sICX");

        loanTakerETHFullWithdraw.loans.returnAsset("bnUSD", debt, "iETH", "");
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

        loanTakerCloseLoanOnly.loans.returnAsset("bnUSD", debt, "sICX", "");
        loanTakerFullClose.loans.returnAsset("bnUSD", debt, "sICX", "");
        loanTakerETHFullClose.loans.returnAsset("bnUSD", debt, "iETH", "");

        BigInteger amountRepaid = BigInteger.TEN.pow(21);
        BigInteger amountETHRepaid = BigInteger.TEN.pow(18);
        loanTakerPartialRepay.loans.returnAsset("bnUSD", amountRepaid, "sICX", "");
        loanTakerETHPartialRepay.loans.returnAsset("bnUSD", amountETHRepaid, "iETH", "");

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

        setDebtCeiling("sICX", initialsICXDebt.add(debt1).add(badDebt));

        // Act
        loanTaker1.stakeDepositAndBorrow(sICXCollateral, loanAmount1);
        assertThrows(RevertedException.class, () -> loanTaker2.stakeDepositAndBorrow(sICXCollateral, loanAmount2));

        loanTaker1.loans.returnAsset("bnUSD", debt2, "sICX", "");
        loanTaker2.stakeDepositAndBorrow(sICXCollateral, loanAmount2);
        assertThrows(UserRevertedException.class, () -> loanTaker1.borrowFrom("sICX", debt2));

        BigInteger outstandingNewDebt = debt1;

        // Assert
        BigInteger expectedTotalDebt = initialTotalDebt.add(outstandingNewDebt);

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(initialsICXDebt.add(outstandingNewDebt), reader.loans.getTotalCollateralDebt("sICX", "bnUSD"));
        assertEquals(initialIETHDebt, reader.loans.getTotalCollateralDebt("iETH", "bnUSD"));
        setDebtCeiling("sICX", BigInteger.TEN.pow(28));
    }

    @Test
    @Order(21)
    void redeemCollateral_sICX() throws Exception {
        BigInteger daoFee = reader.loans.getRedemptionDaoFee();

        BalancedClient loanTaker = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(5).multiply(sicxDecimals);
        BigInteger redeemAmount = BigInteger.TEN.pow(22);
        loanTaker.stakeDepositAndBorrow(collateral, redeemAmount);

        BigInteger initialTotalDebt = getTotalDebt();
        BigInteger initialsICXDebt = reader.loans.getTotalCollateralDebt("sICX", "bnUSD");

        // Act
        loanTaker.loans.redeemCollateral(balanced.sicx._address(), redeemAmount);

        // Assert
        BigInteger expectedFee = redeemAmount.multiply(daoFee).divide(POINTS);
        BigInteger expectedDebtRepaid = redeemAmount.subtract(expectedFee);
        assertEquals(initialTotalDebt.subtract(expectedDebtRepaid), getTotalDebt());
        assertEquals(initialsICXDebt.subtract(expectedDebtRepaid), reader.loans.getTotalCollateralDebt("sICX", "bnUSD"
        ));
    }

    @Test
    @Order(22)
    void redeemCollateral_iETH() throws Exception {
        // Arrange
        BigInteger daoFee = reader.loans.getRedemptionDaoFee();

        BalancedClient loanTaker = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.multiply(iethDecimals);
        BigInteger redeemAmount = BigInteger.TEN.pow(21);
        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), collateral, null);
        loanTaker.depositAndBorrow(ethAddress, collateral, redeemAmount);

        BigInteger initialTotalDebt = getTotalDebt();
        BigInteger initialETHDebt = reader.loans.getTotalCollateralDebt("iETH", "bnUSD");

        // Act
        loanTaker.loans.redeemCollateral(ethAddress, redeemAmount);

        // Assert
        BigInteger expectedFee = redeemAmount.multiply(daoFee).divide(POINTS);
        BigInteger expectedDebtRepaid = redeemAmount.subtract(expectedFee);
        assertEquals(initialTotalDebt.subtract(expectedDebtRepaid), getTotalDebt());
        assertEquals(initialETHDebt.subtract(expectedDebtRepaid), reader.loans.getTotalCollateralDebt("iETH", "bnUSD"));
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
        BigInteger icxPrice = reader.balancedOracle.getLastPriceInUSD("sICX");
        BigInteger collateralValue = icxCollateral.multiply(icxPrice).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan = maxDebt.subtract(maxFee);
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(icxCollateral, "bnUSD", loan, null, null);

        BigInteger ethLoan = BigInteger.TEN.pow(22);
        BigInteger ethDebt = ethLoan.add(ethLoan.multiply(feePercent).divide(POINTS));
        loanTaker.depositAndBorrow(ethAddress, collateralETH, ethLoan);

        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        // Act
        BigInteger bnUSDBalancePreLiquidation = liquidator.bnUSD.balanceOf(liquidator.getAddress());

        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress().toString(), loan.add(fee), "sICX");
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);


        BigInteger bnUSDBalancePostLiquidation = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger amountLiquidated = bnUSDBalancePreLiquidation.subtract(bnUSDBalancePostLiquidation);
        assertTrue(amountLiquidated.compareTo(loan.add(fee)) < 0);

        BigInteger remainingSicxDebt = loan.add(fee).subtract(amountLiquidated);
        BigInteger remainingTotalDebt = initialDebt.add(ethDebt).add(remainingSicxDebt);

        assertEquals(remainingTotalDebt, getTotalDebt());
        assertEquals(collateralETH, loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(remainingSicxDebt, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(ethDebt, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
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
                collateralETH.multiply(reader.balancedOracle.getLastPriceInUSD("iETH")).divide(iethDecimals);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan = maxDebt.subtract(maxFee);
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.depositAndBorrow(ethAddress, collateralETH, loan);

        BigInteger sICXLoan = BigInteger.TEN.pow(21);
        BigInteger sICXDebt = sICXLoan.add(sICXLoan.multiply(feePercent).divide(POINTS));

        loanTaker.stakeDepositAndBorrow(icxCollateral,  sICXLoan);

        loanTaker.bnUSD.transfer(liquidator.getAddress(), loan, null);
        depositToStabilityContract(liquidator, fee.multiply(BigInteger.TWO));

        // Act
        BigInteger bnUSDBalancePreLiquidation = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger balancePreLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress().toString(), loan.add(fee), "iETH");
        BigInteger balancePostLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        BigInteger bnUSDBalancePostLiquidation = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger amountLiquidated = bnUSDBalancePreLiquidation.subtract(bnUSDBalancePostLiquidation);

        System.out.println("amount liquidated"+ amountLiquidated);
        assertTrue(amountLiquidated.compareTo(loan.add(fee)) < 0);

        BigInteger remainingIethDebt = loan.add(fee).subtract(amountLiquidated);
        BigInteger remainingTotalDebt = initialDebt.add(sICXDebt).add(remainingIethDebt);

        assertEquals(remainingTotalDebt, getTotalDebt());
        assertEquals(icxCollateral, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(remainingIethDebt, loanTaker.getLoansAssetPosition("iETH", "bnUSD"));
        assertEquals(sICXDebt, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
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

    protected void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }

    protected BigInteger getTotalDebt() {
        return reader.loans.getBalanceAndSupply("Loans", reader.getAddress().toString()).get("_totalSupply");
    }

    protected void claimAllRewards() {
        for (BalancedClient client : balanced.balancedClients.values()) {
            if (client.rewards.getBalnHolding(client.getAddress().toString()).compareTo(EXA) < 0) {
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
        JsonArray setLockingRatioParameters = new JsonArray()
                .add(createParameter(symbol))
                .add(createParameter(ratio));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.loans._address(), "setLockingRatio", setLockingRatioParameters));
        executeVote(balanced, voter, name, actions);
    }

    private void addCollateralAndLiquidity(BalancedClient minter, Address collateralAddress, BigInteger tokenAmount,
                                   BigInteger bnUSDAmount, String peg) {
        minter.irc2(collateralAddress).mintTo(owner.getAddress(), tokenAmount, new byte[0]);
        depositToStabilityContract(owner, bnUSDAmount.add(voteDefinitionFee).multiply(BigInteger.TWO));
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        owner.irc2(collateralAddress).transfer(balanced.dex._address(), tokenAmount, depositData.toString().getBytes());

        owner.bnUSD.transfer(balanced.dex._address(), bnUSDAmount, depositData.toString().getBytes());
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnUSDAmount, false, BigInteger.valueOf(100));
        addCollateral(collateralAddress, peg);
    }

    private static void addCollateral(Address collateralAddress, String peg) {
        BigInteger lockingRatio = BigInteger.valueOf(40_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(30);

        BigInteger liquidationRatio = BigInteger.valueOf(14_000);
        BigInteger liquidatorFee = BigInteger.valueOf(800);
        BigInteger daofundFee = BigInteger.valueOf(200);

        JsonArray addCollateralParameters = new JsonArray()
                .add(createParameter(collateralAddress))
                .add(createParameter(true))
                .add(createParameter(peg))
                .add(createParameter(lockingRatio))
                .add(createParameter(debtCeiling))
                .add(createParameter(liquidationRatio))
                .add(createParameter(liquidatorFee))
                .add(createParameter(daofundFee));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "addCollateral", addCollateralParameters));

        String symbol = reader.irc2(collateralAddress).symbol();
        owner.governance.execute(actions.toString());

        assertEquals(lockingRatio, reader.loans.getLockingRatio(symbol));
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
        owner.dex.add(collateralAddress, balanced.bnusd._address(), tokenAmount, bnUSDAmount, false, BigInteger.valueOf(100));
        BigInteger lockingRatio = BigInteger.valueOf(40_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(30);
        BigInteger liquidationRatio = BigInteger.valueOf(14_000);
        BigInteger liquidatorFee = BigInteger.valueOf(800);
        BigInteger daofundFee = BigInteger.valueOf(200);

        JsonArray addCollateralParameters = new JsonArray()
                .add(createParameter(collateralAddress))
                .add(createParameter(true))
                .add(createParameter(lockingRatio))
                .add(createParameter(debtCeiling))
                .add(createParameter(liquidationRatio))
                .add(createParameter(liquidatorFee))
                .add(createParameter(daofundFee));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.governance._address(), "addDexPricedCollateral",
                        addCollateralParameters));

        String symbol = reader.irc2(collateralAddress).symbol();
        executeVote(balanced, owner, "add dex collateral " + symbol, actions);

        assertEquals(lockingRatio, reader.loans.getLockingRatio(symbol));
        assertEquals(debtCeiling, reader.loans.getDebtCeiling(symbol));
    }

    private void getTokens(BalancedClient client, Address address, BigInteger amount) {
        if (address.equals(balanced.sicx._address())) {
            client.staking.stakeICX(amount.multiply(reader.staking.getTodayRate()).divide(EXA), null, null);
        } else if (address.equals(ethAddress)) {
            owner.irc2(ethAddress).mintTo(client.getAddress(), amount, null);
        }
    }

    private static void setRebalancingThreshold(BigInteger threshold) {
        JsonArray rebalancingThresholdParameter = new JsonArray()
                .add(createParameter(threshold));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.rebalancing._address(), "setPriceDiffThreshold",
                        rebalancingThresholdParameter));

        owner.governance.execute(actions.toString());
    }

    public static void setDebtCeiling(String symbol, BigInteger ceiling) {
        JsonArray setDebtCeilingParameter = new JsonArray()
                .add(createParameter(symbol))
                .add(createParameter(ceiling));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.loans._address(), "setDebtCeiling", setDebtCeilingParameter));

        owner.governance.execute(actions.toString());
    }
}
