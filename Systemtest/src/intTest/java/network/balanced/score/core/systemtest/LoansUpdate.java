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

package network.balanced.score.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.interfaces.Loans;
import network.balanced.score.lib.test.integration.Balanced;
import static network.balanced.score.lib.test.integration.BalancedUtils.executeVoteActions;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

class LoansUpdate implements ScoreIntegrationTest {
    private Balanced balanced;
    private BalancedClient owner;
    private String dexJavaPath;
    private String loansJavaPath;
    private String rewardsJavaPath;
    private String dividendsJavaPath;
    private BigInteger EXA = BigInteger.TEN.pow(18);
    private final BigInteger POINTS = BigInteger.valueOf(10_000);

    @BeforeEach    
    void setup() throws Exception {
        dexJavaPath = System.getProperty("Dex");
        loansJavaPath = System.getProperty("Loans");
        rewardsJavaPath = System.getProperty("Rewards");
        dividendsJavaPath = System.getProperty("Dividends");

        System.setProperty("Rewards", System.getProperty("rewardsPython"));
        System.setProperty("Dex", System.getProperty("dexPython"));
        System.setProperty("Loans", System.getProperty("loansPython"));
        System.setProperty("Dividends", System.getProperty("dividendsPython"));
        
        balanced = new Balanced();
        balanced.deployBalanced();

        System.setProperty("Rewards", rewardsJavaPath);
        System.setProperty("Dex", dexJavaPath);
        System.setProperty("Loans", loansJavaPath);
        System.setProperty("Dividends", dividendsJavaPath);

        owner = balanced.ownerClient;
        
        balanced.increaseDay(1);
        owner.baln.toggleEnableSnapshot();

        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });
        owner.governance.setFeeProcessingInterval(BigInteger.ZERO);
        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        nextDay();
        balanced.dividends._update(dividendsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setDividendsOnlyToStakedBalnDay(owner.dividends.getSnapshotId().add(BigInteger.ONE));
        updateRewards();

        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setAddressesOnContract("dex");
    }

    @Test
    void updateLoans_Simple() throws Exception {
        BalancedClient loansClient1 = balanced.newClient();
        BalancedClient loansClient2 = balanced.newClient();
        BalancedClient loansClient3 = balanced.newClient();
        BalancedClient loansClient4 = balanced.newClient();
        BalancedClient loansClient5 = balanced.newClient();
        BalancedClient stabilityDepositor = balanced.newClient();
        
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        BigInteger stabiityFundMint = BigInteger.TEN.pow(22);

        BigInteger feePercent = Balanced.hexObjectToInt(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger totalDebt = loanAmount.add(fee).multiply(BigInteger.valueOf(5));

        Map<String, Object> governacePosition = owner.loans.getAccountPositions(balanced.governance._address());
        totalDebt = totalDebt.add(balanced.hexObjectToInt(((Map<String,Object>)governacePosition.get("assets")).get("bnUSD")));
        loansClient1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient3.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient4.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient5.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        depositToStabilityContract(stabilityDepositor, stabiityFundMint);
        
        nextDay();

        verifyRewards(loansClient1);
        verifyRewards(loansClient2);
        verifyRewards(loansClient3);
        verifyRewards(loansClient4);
        verifyRewards(loansClient5);
        verifyNoRewards(stabilityDepositor);

        verifyExternalAndUpdateLoans(loansClient1);

        Map<String, BigInteger> balanceAndSupply = owner.loans.getBalanceAndSupply("Loans", loansClient1.getAddress());
        Map<String, Object> position = owner.loans.getAccountPositions(loansClient1.getAddress());
        assertEquals(
            balanced.hexObjectToInt(((Map<String,Object>)position.get("assets")).get("bnUSD")), 
            balanceAndSupply.get("_balance")
        );

        assertEquals(
            BigInteger.ZERO, 
            balanceAndSupply.get("_totalSupply")
        );

        nextDay();

        balanceAndSupply = owner.loans.getBalanceAndSupply("Loans", loansClient1.getAddress());
        assertEquals(
            balanced.hexObjectToInt(((Map<String,Object>)position.get("assets")).get("bnUSD")), 
            balanceAndSupply.get("_balance")
        );

        assertEquals(
            loanAmount.add(fee), 
            balanceAndSupply.get("_balance")
        );


        assertEquals(
            totalDebt,
            balanceAndSupply.get("_totalSupply")
        );

        verifyRewards(loansClient1);
        verifyRewards(loansClient2);
        verifyRewards(loansClient3);
        verifyRewards(loansClient4);
        verifyRewards(loansClient5);
        verifyNoRewards(stabilityDepositor);
    }

    @Test
    void updateLoans_WithBadDebt() throws Exception {
        Map<String, Object> governacePosition = owner.loans.getAccountPositions(balanced.governance._address());
        BigInteger governanceDebt = balanced.hexObjectToInt(((Map<String,Object>)governacePosition.get("assets")).get("bnUSD"));
        BalancedClient voter = balanced.newClient();
        BigInteger feePercent = Balanced.hexObjectToInt(owner.loans.getParameters().get("origination fee"));
        BigInteger voterLoan = BigInteger.TEN.pow(22);
        BigInteger voterDebt = voterLoan.add(voterLoan.multiply(feePercent).divide(POINTS));
        voter.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", voterLoan, null, null);
        nextDay();
        nextDay();
        verifyRewards(voter);
        stakeBaln(voter);

        depositToStabilityContract(voter, BigInteger.TEN.pow(18).multiply(BigInteger.TWO));
        BigInteger initalLockingRatio = Balanced.hexObjectToInt(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);
        setLockingRatio(voter, lockingRatio, "Liquidation setup");

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger loan = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan, null, null);
        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio");
        
        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());

        liquidator.loans.liquidate(loanTaker.getAddress());
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        verifyExternalAndUpdateLoans(loanTaker);

        nextDay();

        Map<String, Map<String, Object>> assets = owner.loans.getAvailableAssets();

        BigInteger bnusdBadDebt = balanced.hexObjectToInt(assets.get("bnUSD").get("bad_debt"));
        assertEquals(bnusdBadDebt, loan.add(fee));
        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        BigInteger balancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger balancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());

        assertTrue(balancePreRetire.compareTo(balancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);


        Map<String, BigInteger> balanceAndSupply = owner.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());

        assertEquals(
            BigInteger.ZERO, 
            balanceAndSupply.get("_balance")
        );
        assertEquals(
            governanceDebt.add(voterDebt), 
            balanceAndSupply.get("_totalSupply")
        );
    }

    @Test
    void updateLoans_WithPositionReadyToBeLiquidated() throws Exception {
        BalancedClient voter = balanced.newClient();
        voter.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        nextDay();
        nextDay();
        verifyRewards(voter);
        stakeBaln(voter);

        depositToStabilityContract(voter, BigInteger.TEN.pow(18).multiply(BigInteger.TWO));
        BigInteger initalLockingRatio = Balanced.hexObjectToInt(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);
        setLockingRatio(voter, lockingRatio, "Liquidation setup");

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = Balanced.hexObjectToInt(owner.loans.getParameters().get("origination fee"));
        BigInteger loan = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);
        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan, null, null);
        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio");

        verifyExternalAndUpdateLoans(loanTaker);
 
        nextDay();

        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress());
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        BigInteger balancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger balancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());

        assertTrue(balancePreRetire.compareTo(balancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);
    }

    @Test
    void updateLoans_verifyRestrictions() throws Exception {
        BalancedClient loansTaker = balanced.newClient();
        BalancedClient abuseClient = balanced.newClient();

        BigInteger lockingRatio = Balanced.hexObjectToInt(owner.loans.getParameters().get("locking ratio"));

        BigInteger loanAmount = BigInteger.TEN.pow(21);
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger feePercent = Balanced.hexObjectToInt(owner.loans.getParameters().get("origination fee"));
        
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        loansTaker.loans.depositAndBorrow(collateralAmount, "bnUSD", loanAmount, null, null);

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.depositAndBorrow(collateralAmount, "bnUSD", collateralAmount, null, null));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.withdrawCollateral(BigInteger.ONE));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.withdrawCollateral(BigInteger.valueOf(-1)));
    
        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.tokenFallback(abuseClient.getAddress(), BigInteger.TEN.pow(24), createBorrowData(BigInteger.TEN.pow(22))));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.retireBadDebt("bnUSD", BigInteger.valueOf(1)));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.retireBadDebt("bnUSD", BigInteger.valueOf(-1)));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            loansTaker.loans.withdrawCollateral(collateralAmount));
        
        Assertions.assertThrows(UserRevertedException.class, () -> 
            loansTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", collateralAmount, null, null));

        Map<String, Object> positionPre = owner.loans.getAccountPositions(loansTaker.getAddress());
        abuseClient.loans.liquidate(loansTaker.getAddress());
        Map<String, Object> positionPost = owner.loans.getAccountPositions(loansTaker.getAddress());
        assertEquals(positionPre, positionPost);

        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.depositAndBorrow(collateralAmount, "bnUSD", collateralAmount, null, null));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.withdrawCollateral(BigInteger.ONE));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.withdrawCollateral(BigInteger.valueOf(-1)));
    
        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.tokenFallback(abuseClient.getAddress(), BigInteger.TEN.pow(24), createBorrowData(BigInteger.TEN.pow(22))));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.retireBadDebt("bnUSD", BigInteger.valueOf(1)));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            abuseClient.loans.retireBadDebt("bnUSD", BigInteger.valueOf(-1)));

        Assertions.assertThrows(UserRevertedException.class, () -> 
            loansTaker.loans.withdrawCollateral(collateralAmount));
        
        Assertions.assertThrows(UserRevertedException.class, () -> 
            loansTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", collateralAmount, null, null));

        positionPre = owner.loans.getAccountPositions(loansTaker.getAddress());
        abuseClient.loans.liquidate(loansTaker.getAddress());
        positionPost = owner.loans.getAccountPositions(loansTaker.getAddress());
        assertEquals(positionPre, positionPost);


    }

    private void verifyExternalAndUpdateLoans(BalancedClient clientInFocus) throws Exception {
        Map<String, Object> positionPre = removeZeroAssets(owner.loans.getAccountPositions(clientInFocus.getAddress()));
        int id =  balanced.hexObjectToInt(positionPre.get("pos_id")).intValue();
        Map<String, Object> postitionByIndexPre = removeZeroAssets(owner.loans.getPositionByIndex(id, BigInteger.valueOf(-1)));
        Map<String, Object> postitionByIndexLastDayPre = removeZeroAssets(owner.loans.getPositionByIndex(id, BigInteger.valueOf(-2)));
        Map<String, Object> snapshotPre = owner.loans.getSnapshot(BigInteger.valueOf(-1));
        Map<String, Object> snapshotPreLastDay = owner.loans.getSnapshot(BigInteger.valueOf(-2));
        Map<String, Map<String, Object>> assetsPre = owner.loans.getAvailableAssets();
        int borrowerCountPre = owner.loans.borrowerCount();
        Map<String, String> collateralTokensPre = owner.loans.getCollateralTokens();
        BigInteger totalCollateralPre = owner.loans.getTotalCollateral();
        Map<String, Map<String, Object>> availableAssetsPre = owner.loans.getAvailableAssets();
        
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));

        Map<String, Object> positionPost = owner.loans.getAccountPositions(clientInFocus.getAddress());
        Map<String, Object> postitionByIndexPost = owner.loans.getPositionByIndex(id, BigInteger.valueOf(-1));
        Map<String, Object> postitionByIndexLastDayPost = owner.loans.getPositionByIndex(id, BigInteger.valueOf(-2));
        Map<String, Object> snapshotPost = owner.loans.getSnapshot(BigInteger.valueOf(-1));
        Map<String, Object> snapshotPostLastDay = owner.loans.getSnapshot(BigInteger.valueOf(-2));
        Map<String, Map<String, Object>> assetsPost = owner.loans.getAvailableAssets();
        int borrowerCountPost = owner.loans.borrowerCount();
        Map<String, String> collateralTokensPost = owner.loans.getCollateralTokens();
        BigInteger totalCollateralPost = owner.loans.getTotalCollateral();
        Map<String, Map<String, Object>> availableAssetsPost = owner.loans.getAvailableAssets();

        assertEquals(positionPre.toString(), positionPost.toString());
        assertEquals(postitionByIndexPre.toString(), postitionByIndexPost.toString());
        assertEquals(postitionByIndexLastDayPre.toString(), postitionByIndexLastDayPost.toString());
        assertEquals(snapshotPre.toString(), snapshotPost.toString());
        assertEquals(snapshotPreLastDay.toString(), snapshotPostLastDay.toString());
        assertEquals(assetsPre.toString(), assetsPost.toString());
        assertEquals(borrowerCountPre, borrowerCountPost);
        assertEquals(collateralTokensPre.toString(), collateralTokensPost.toString());
        assertEquals(totalCollateralPre, totalCollateralPost);
        assertEquals(availableAssetsPre.toString(), availableAssetsPost.toString());
    }

    private Map<String, Object> removeZeroAssets(Map<String, Object> position) {
        if (position.isEmpty()){
            return position;
        }
        Map<String, Object> assets = (Map<String,Object>) position.get("assets");
        Map<String, Object> newAssets = new HashMap<>();
        for (Map.Entry<String, Object> entry : assets.entrySet()) {
            if (!balanced.hexObjectToInt(entry.getValue()).equals(BigInteger.ZERO)) {
                newAssets.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> newPosition = new HashMap<String, Object>(position);
        position.put("assets", newAssets);

        return position;
    }

    private void setLockingRatio(BalancedClient voter, BigInteger ratio, String name) throws Exception {
        JsonObject setLockingRatioParameters = new JsonObject()
        .add("_value", ratio.intValue());
        
        JsonArray setLockingRatioCall = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParameters);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCall);
        executeVoteActions(balanced, voter, name, actions);
    }
 
    private void updateRewards() {
        balanced.rewards._update(rewardsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.rewards.addDataProvider(balanced.stakedLp._address());
        owner.rewards.addDataProvider(balanced.dex._address());
        owner.rewards.addDataProvider(balanced.loans._address());
    }

    private byte[] createBorrowData(BigInteger amount) {
        JsonObject data = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", amount.toString());

        return data.toString().getBytes();
    }

    private void sICXDepositAndBorrow(BalancedClient client, BigInteger collateral, BigInteger amount) {
        client.staking.stakeICX(collateral.multiply(BigInteger.TWO), null, null);
        byte[] params = createBorrowData(amount);
        client.sicx.transfer(balanced.loans._address(), collateral, params);
    }
    
    private void closeLoansPostionAndVerifyNoRewards(BalancedClient client) throws Exception {
        Map<String, String> assets = (Map<String, String>) client.loans.getAccountPositions(client.getAddress()).get("assets");
        BigInteger debt = Balanced.hexObjectToInt(assets.get("bnUSD"));
        BigInteger balance = client.bnUSD.balanceOf(client.getAddress());
        BigInteger bnusdNeeded = debt.subtract(balance);
        if (bnusdNeeded.compareTo(BigInteger.ZERO) > 0) {
            client.staking.stakeICX(bnusdNeeded.multiply(BigInteger.TWO), null, null);
            client.sicx.transfer(balanced.stability._address(), client.sicx.balanceOf(client.getAddress()), null);
        }

        client.loans.returnAsset("bnUSD", debt, true);
        client.rewards.claimRewards();
        Thread.sleep(100);
        verifyNoRewards(client);
    }

    private void verifyContractRewards() throws Exception {
        BigInteger bwtBalancePreDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePreDist = owner.baln.balanceOf(balanced.reserve._address());
        balanced.increaseDay(1);
        owner.rewards.distribute((txr) -> {});
        BigInteger bwtBalancePostDist = owner.baln.balanceOf(balanced.bwt._address());
        BigInteger daoFundBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger reserveBalancePostDist = owner.baln.balanceOf(balanced.reserve._address());

        assertTrue(bwtBalancePostDist.compareTo(bwtBalancePreDist) > 0);
        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
        assertTrue(reserveBalancePostDist.compareTo(reserveBalancePreDist) > 0);
    }

    private void verifyDaofundBnusdDividends() throws Exception {
        BigInteger daoFundBalancePreDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        owner.dividends.transferDaofundDividends(0, 0);
        BigInteger daoFundBalancePostDist = owner.bnUSD.balanceOf(balanced.daofund._address());

        assertTrue(daoFundBalancePostDist.compareTo(daoFundBalancePreDist) > 0);
    }

    private void verifyDaofundAllDivTypes() throws Exception {
        BigInteger daoFundBnusdBalancePreDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundBalnBalancePreDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger daoFundSicxBalancePreDist = owner.sicx.balanceOf(balanced.daofund._address());
        owner.dividends.transferDaofundDividends(0, 0);
        BigInteger daoFundBnusdBalancePostDist = owner.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoFundBalnBalancePostDist = owner.baln.balanceOf(balanced.daofund._address());
        BigInteger daoFundSicxBalancePostDist = owner.sicx.balanceOf(balanced.daofund._address());

        assertTrue(daoFundBnusdBalancePostDist.compareTo(daoFundBnusdBalancePreDist) > 0);
        assertTrue(daoFundBalnBalancePostDist.compareTo(daoFundBalnBalancePreDist) > 0);
        assertTrue(daoFundSicxBalancePostDist.compareTo(daoFundSicxBalancePreDist) > 0);
    }

    private void joinsICXBnusdLP(BalancedClient client, BigInteger icxAmount, BigInteger bnusdAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.bnUSD.transfer(balanced.dex._address(), bnusdAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(balanced.sicx._address(), balanced.bnusd._address(), sicxDeposit, bnusdAmount, false);
    }

    private void leaveICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.remove(icxBnusdPoolId, poolBalance, true);
    }

    private void joinsICXBalnLP(BalancedClient client, BigInteger icxAmount, BigInteger balnAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.baln.transfer(balanced.dex._address(), balnAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(balanced.baln._address(), balanced.sicx._address(), balnAmount, sicxDeposit, false);
    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }

    private void unstakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.stakedLp.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.stakedLp.unstake(icxBnusdPoolId, poolBalance);
    }

    private void stakeICXBalnLP(BalancedClient client) {
        BigInteger icxBalnPoolId = owner.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBalnPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBalnPoolId, null);
    }

    private void stakeBaln(BalancedClient client) {
        BigInteger balance = client.baln.balanceOf(client.getAddress());
        client.baln.stake(balance);
    }

    private void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }
    
    private BigInteger verifyRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);

        return balancePostClaim.subtract(balancePreClaim);
    }

    private void verifyNoRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }

    private void verifyBnusdFees(BalancedClient client) {
        BigInteger balancePreClaim = client.bnUSD.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger balancePostClaim = client.bnUSD.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);
    }

    private void verifyNoBnusdFees(BalancedClient client) {
        BigInteger balancePreClaim = client.bnUSD.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger balancePostClaim = client.bnUSD.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.equals(balancePreClaim));
    }

    private void verifyAllFees(BalancedClient client) {
        BigInteger bnusdBalancePreDist = owner.bnUSD.balanceOf(client.getAddress());
        BigInteger balnBalancePreDist = owner.baln.balanceOf(client.getAddress());
        BigInteger sicxBalancePreDist = owner.sicx.balanceOf(client.getAddress());
        client.dividends.claim(0, 0);
        BigInteger bnusdBalancePostDist = owner.bnUSD.balanceOf(client.getAddress());
        BigInteger balnBalancePostDist = owner.baln.balanceOf(client.getAddress());
        BigInteger sicxBalancePostDist = owner.sicx.balanceOf(client.getAddress());
    
        assertTrue(bnusdBalancePostDist.compareTo(bnusdBalancePreDist) > 0);
        assertTrue(balnBalancePostDist.compareTo(balnBalancePreDist) > 0);
        assertTrue(sicxBalancePostDist.compareTo(sicxBalancePreDist) > 0);
    }
   
    private void nextDay() {
        balanced.increaseDay(1);
        balanced.syncDistributions();
    }

    private void rebalance() throws Exception {
        BalancedClient rebalancer = balanced.newClient();
        while (true) {
            BigInteger threshold = calculateThreshold();
            owner.rebalancing.rebalance();
            if (threshold.equals(calculateThreshold())) {
                return;
            }

        }
    }

    private void reducePriceBelowThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.multiply(BigInteger.valueOf(105))) < 0) {
            reduceBnusdPrice();
        }
    }

    private void raisePriceAboveThreshold() throws Exception {
        BigInteger threshold = owner.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold().multiply(BigInteger.valueOf(100)).compareTo(threshold.negate().multiply(BigInteger.valueOf(105))) > 0) {
            raiseBnusdPrice();
        }
    }

    private BigInteger calculateThreshold() {
        BigInteger bnusdPriceInIcx = owner.bnUSD.lastPriceInLoop();
        BigInteger sicxPriceInIcx = owner.sicx.lastPriceInLoop();

        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        Map<String, Object> poolStats = owner.dex.getPoolStats(icxBnusdPoolId);
        BigInteger sicxLiquidity = Balanced.hexObjectToInt(poolStats.get("base"));
        BigInteger bnusdLiquidity = Balanced.hexObjectToInt(poolStats.get("quote"));

        BigInteger actualBnusdPriceInSicx = bnusdPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
        BigInteger bnusdPriceInSicx = sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage = (actualBnusdPriceInSicx.subtract(bnusdPriceInSicx)).multiply(EXA).divide(actualBnusdPriceInSicx);

        return priceDifferencePercentage;
    }

    private void reduceBnusdPrice() throws Exception {
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

    private void raiseBnusdPrice() throws Exception {
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
}