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

package network.balanced.score.core.reserve;

import static network.balanced.score.lib.test.integration.BalancedUtils.createIRC2Token;
import static network.balanced.score.lib.test.integration.BalancedUtils.executeVoteActions;
import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import foundation.icon.jsonrpc.Address;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;

class ReserveIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;
    protected static Address ethAddress;

    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);
    @BeforeAll
    public static void contractSetup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        
        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

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

        BigInteger pid = owner.dex.getPoolId(balanced.baln._address(), balanced.bnusd._address());
        owner.balancedOracle.getPriceInLoop((txr)->{}, "ETH");
        owner.balancedOracle.getPriceInLoop((txr)->{}, "BALN");

    }

    @Test
    @Order(-1)
    void setupBaseLoans() throws Exception {
        BalancedClient loantakerICX1 = balanced.newClient();
        BalancedClient loantakerICX2 = balanced.newClient();
        BalancedClient loantakerICX3 = balanced.newClient();
        BalancedClient loantakerICX4 = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);

        loantakerICX1.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX2.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX3.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX4.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger ethAmount =  BigInteger.TEN.pow(18);
        
        BigInteger bnusdAmount = ethAmount.multiply(reader.balancedOracle.getLastPriceInLoop("ETH"))
            .divide(reader.balancedOracle.getLastPriceInLoop("bnUSD"));

        addCollateralType(owner, ethAddress, ethAmount, bnusdAmount, "ETH"); 
    }

    @Test
    @Order(1)
    void liquidate_useReserve_multicollateral() throws Exception {
        // Arrange
        balanced.increaseDay(1);
        claimAllRewards();
        BigInteger BAD_DEBT_RETIREMENT_BONUS = BigInteger.valueOf(1_000);
        BalancedClient voter = balanced.newClient();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup with reserve multiCollateral");

        BigInteger sICXCollateral = BigInteger.TEN.pow(23);
        BigInteger iETHCollateral = BigInteger.TEN.pow(18);
        owner.staking.stakeICX(sICXCollateral, null, null);
        owner.sicx.transfer(balanced.reserve._address(), sICXCollateral, null);
        owner.irc2(ethAddress).mintTo(balanced.reserve._address(), iETHCollateral, null);

        BigInteger collateralValue = sICXCollateral.multiply(owner.balancedOracle.getLastPriceInLoop("sICX")).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.balancedOracle.getLastPriceInLoop("bnUSD"));
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        BigInteger iETHcollateralValue = iETHCollateral.multiply(owner.balancedOracle.getLastPriceInLoop("iETH")).divide(EXA);
        BigInteger iETHfeePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger iETHmaxDebt = POINTS.multiply(iETHcollateralValue).divide(lockingRatio);
        BigInteger iETHmaxFee = iETHmaxDebt.multiply(iETHfeePercent).divide(POINTS);
        BigInteger iETHloan  = (iETHmaxDebt.subtract(iETHmaxFee)).multiply(EXA).divide(owner.balancedOracle.getLastPriceInLoop("bnUSD"));
        BigInteger iETHfee = iETHloan.multiply(iETHfeePercent).divide(POINTS);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), iETHCollateral, null);
        loanTaker.depositAndBorrow(ethAddress, iETHCollateral, iETHloan);
        loanTaker.stakeDepositAndBorrow(sICXCollateral, loan);
        setLockingRatio(voter, initalLockingRatio, "restore Locking ratio multicollateral");

        // Act
        BigInteger iETHBalancePreLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "iETH");
        BigInteger iETHBalancePostLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        assertTrue(iETHBalancePreLiquidation.compareTo(iETHBalancePostLiquidation) < 0);

        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "sICX");
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        Map<String,Object> bnusdDetails = reader.loans.getAvailableAssets().get("bnUSD");
        BigInteger reserveSICXBalance = reader.reserve.getBalances().get("sICX");
        BigInteger reserveIETHBalance = reader.reserve.getBalances().get("iETH");
        depositToStabilityContract(liquidator, loan.add(fee).add(iETHloan).add(iETHfee).multiply(BigInteger.valueOf(101)).divide(BigInteger.valueOf(100)));

        BigInteger bnUSDBalancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        BigInteger iETHBalancePreRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee).add(iETHloan).add(iETHfee));
        BigInteger bnUSDBalancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        BigInteger iETHBalancePostRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());

        // Assert
        assertTrue(bnUSDBalancePreRetire.compareTo(bnUSDBalancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);
        assertTrue(iETHBalancePreRetire.compareTo(iETHBalancePostRetire) < 0);

        Map<String, BigInteger> LiquidatedUserBaS = reader.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("iETH"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("IETH", "bnUSD"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(BigInteger.ZERO, LiquidatedUserBaS.get("_balance"));

        BigInteger reserveSICXBalanceAfterRedeem = reader.reserve.getBalances().get("sICX");
        BigInteger reserveIETHBalanceAfterRedeem = reader.reserve.getBalances().get("iETH");
        BigInteger reserveBALNBalanceAfterRedeem = reader.reserve.getBalances().get("BALN");
        BigInteger iETHPriceInLoop = reader.balancedOracle.getLastPriceInLoop("iETH");
        BigInteger sICXPriceInLoop = reader.balancedOracle.getLastPriceInLoop("sICX");
        BigInteger bnUSDPriceInLoop = reader.balancedOracle.getLastPriceInLoop("bnUSD");
        
        Map<String, Map<String, Object>> bnusdDebtDetails =( Map<String, Map<String, Object>>) bnusdDetails.get("debt_details");
        
        BigInteger totalSICXBadDebtValueInLoop = hexObjectToBigInteger(bnusdDebtDetails.get("sICX").get("bad_debt")).multiply(bnUSDPriceInLoop).divide(EXA);
        totalSICXBadDebtValueInLoop = totalSICXBadDebtValueInLoop.multiply(BAD_DEBT_RETIREMENT_BONUS.add(POINTS)).divide(POINTS);

        BigInteger totalIETHBadDebtValueInLoop = hexObjectToBigInteger(bnusdDebtDetails.get("iETH").get("bad_debt")).multiply(bnUSDPriceInLoop).divide(EXA);
        totalIETHBadDebtValueInLoop = totalIETHBadDebtValueInLoop.multiply(BAD_DEBT_RETIREMENT_BONUS.add(POINTS)).divide(POINTS);

        BigInteger totalValueInSICXPool = hexObjectToBigInteger(bnusdDebtDetails.get("sICX").get("liquidation_pool")).multiply(sICXPriceInLoop).divide(EXA);
        BigInteger totalValueInIETHPool = hexObjectToBigInteger(bnusdDebtDetails.get("iETH").get("liquidation_pool")).multiply(iETHPriceInLoop).divide(EXA);

        BigInteger valueNeededFromIETHReserve = totalIETHBadDebtValueInLoop.subtract(totalValueInIETHPool);
        BigInteger amountOfiETHRedeemed = valueNeededFromIETHReserve.multiply(EXA).divide(iETHPriceInLoop);

        BigInteger valueNeededFromSICXReserve = totalSICXBadDebtValueInLoop.subtract(totalValueInSICXPool);
        BigInteger amountOfSICXRedeemed = valueNeededFromSICXReserve.multiply(EXA).divide(sICXPriceInLoop);
        
        assertEquals(reserveIETHBalance.subtract(amountOfiETHRedeemed).divide(BigInteger.TEN), reserveIETHBalanceAfterRedeem.divide(BigInteger.TEN));
        assertEquals(reserveSICXBalance.subtract(amountOfSICXRedeemed).divide(BigInteger.TEN), reserveSICXBalanceAfterRedeem.divide(BigInteger.TEN));
    }

    @Test
    @Order(2)
    void liquidate_useReserve() throws Exception {
        // Arrange
        balanced.increaseDay(1);
        claimAllRewards();
        BalancedClient voter = balanced.newClient();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initialLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup with reserve");

        BigInteger collateral = BigInteger.TEN.pow(23);
        owner.staking.stakeICX(collateral, null, null);
        owner.sicx.transfer(balanced.reserve._address(), owner.sicx.balanceOf(owner.getAddress()), null);
        BigInteger reserveSICXBalance = reader.reserve.getBalances().get("sICX");
        assertTrue(reserveSICXBalance.compareTo(BigInteger.ZERO) > 0);

        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);

        setLockingRatio(voter, initialLockingRatio, "restore Locking ratio 32");

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
        assertEquals(BigInteger.ZERO, loanTaker.getLoansCollateralPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX", "bnUSD"));
        assertEquals(BigInteger.ZERO, LiquidatedUserBaS.get("_balance"));

        BigInteger reserveSICXBalanceAfterRedeem = reader.reserve.getBalances().get("sICX");
        assertTrue(reserveSICXBalanceAfterRedeem.compareTo(reserveSICXBalance) < 0);
    }

    @Test
    @Order(3)
    void liquidate_useReserve_specificCollateral() throws Exception {
        // Arrange
        balanced.increaseDay(1);
        claimAllRewards();
        BigInteger BAD_DEBT_RETIREMENT_BONUS = BigInteger.valueOf(1_000);
        BalancedClient voter = balanced.newClient();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup with reserve specificCollateral");

        BigInteger sICXCollateral = BigInteger.TEN.pow(23);
        BigInteger iETHCollateral = BigInteger.TEN.pow(18);
        owner.irc2(ethAddress).mintTo(balanced.reserve._address(), iETHCollateral, null);

        BigInteger collateralValue = sICXCollateral.multiply(owner.balancedOracle.getLastPriceInLoop("sICX")).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.balancedOracle.getLastPriceInLoop("bnUSD"));
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        BigInteger iETHcollateralValue = iETHCollateral.multiply(owner.balancedOracle.getLastPriceInLoop("iETH")).divide(EXA);
        BigInteger iETHfeePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger iETHmaxDebt = POINTS.multiply(iETHcollateralValue).divide(lockingRatio);
        BigInteger iETHmaxFee = iETHmaxDebt.multiply(iETHfeePercent).divide(POINTS);
        BigInteger iETHloan  = (iETHmaxDebt.subtract(iETHmaxFee)).multiply(EXA).divide(owner.balancedOracle.getLastPriceInLoop("bnUSD"));
        BigInteger iETHfee = iETHloan.multiply(iETHfeePercent).divide(POINTS);

        owner.irc2(ethAddress).mintTo(loanTaker.getAddress(), iETHCollateral, null);
        loanTaker.depositAndBorrow(ethAddress, iETHCollateral, iETHloan);
        loanTaker.stakeDepositAndBorrow(sICXCollateral, loan);
        setLockingRatio(voter, initalLockingRatio, "restore Locking ratio specificCollateral");

        // Act
        BigInteger iETHBalancePreLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "iETH");
        BigInteger iETHBalancePostLiquidation = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        assertTrue(iETHBalancePreLiquidation.compareTo(iETHBalancePostLiquidation) < 0);

        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress(), "sICX");
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        Map<String,Object> bnusdDetails = reader.loans.getAvailableAssets().get("bnUSD");
        BigInteger reserveSICXBalance = reader.reserve.getBalances().get("sICX");
        BigInteger reserveIETHBalance = reader.reserve.getBalances().get("iETH");
        depositToStabilityContract(liquidator, iETHloan.add(iETHfee).multiply(BigInteger.valueOf(101)).divide(BigInteger.valueOf(100)));

        BigInteger bnUSDBalancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        BigInteger iETHBalancePreRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebtForCollateral("bnUSD", iETHloan.add(iETHfee), "iETH");
        BigInteger bnUSDBalancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        BigInteger iETHBalancePostRetire = liquidator.irc2(ethAddress).balanceOf(liquidator.getAddress());

        // Assert
        assertTrue(bnUSDBalancePreRetire.compareTo(bnUSDBalancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.equals(sICXBalancePostRetire));
        assertTrue(iETHBalancePreRetire.compareTo(iETHBalancePostRetire) < 0);

        BigInteger reserveIETHBalanceAfterRedeem = reader.reserve.getBalances().get("iETH");
        BigInteger iETHPriceInLoop = reader.balancedOracle.getLastPriceInLoop("iETH");
        BigInteger bnUSDPriceInLoop = reader.balancedOracle.getLastPriceInLoop("bnUSD");
        
        Map<String, Map<String, Object>> bnusdDebtDetails =( Map<String, Map<String, Object>>) bnusdDetails.get("debt_details");
        
        BigInteger totalSICXBadDebtValueInLoop = hexObjectToBigInteger(bnusdDebtDetails.get("sICX").get("bad_debt")).multiply(bnUSDPriceInLoop).divide(EXA);
        totalSICXBadDebtValueInLoop = totalSICXBadDebtValueInLoop.multiply(BAD_DEBT_RETIREMENT_BONUS.add(POINTS)).divide(POINTS);

        BigInteger totalIETHBadDebtValueInLoop = hexObjectToBigInteger(bnusdDebtDetails.get("iETH").get("bad_debt")).multiply(bnUSDPriceInLoop).divide(EXA);
        totalIETHBadDebtValueInLoop = totalIETHBadDebtValueInLoop.multiply(BAD_DEBT_RETIREMENT_BONUS.add(POINTS)).divide(POINTS);

        BigInteger totalValueInIETHPool = hexObjectToBigInteger(bnusdDebtDetails.get("iETH").get("liquidation_pool")).multiply(iETHPriceInLoop).divide(EXA);

        BigInteger valueNeededFromIETHReserve = totalIETHBadDebtValueInLoop.subtract(totalValueInIETHPool);
        BigInteger amountOfiETHRedeemed = valueNeededFromIETHReserve.multiply(EXA).divide(iETHPriceInLoop);

        assertEquals(reserveIETHBalance.subtract(amountOfiETHRedeemed).divide(BigInteger.TEN), reserveIETHBalanceAfterRedeem.divide(BigInteger.TEN));
    }
    
    protected void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
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
        JsonObject setLockingRatioParametersSICX = new JsonObject()
            .add("_symbol", "sICX")
            .add("_value", ratio.intValue());
        
        JsonArray setLockingRatioCallSICX = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParametersSICX);

        JsonObject setLockingRatioParametersIETH = new JsonObject()
            .add("_symbol", "iETH")
            .add("_value", ratio.intValue());
        
        JsonArray setLockingRatioCallIETH = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParametersIETH);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCallSICX)
            .add(setLockingRatioCallIETH);
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

        owner.governance.addCollateral(collateralAddress, true, peg, BigInteger.ZERO);
    }
}
