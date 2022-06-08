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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.valueOf;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import foundation.icon.score.client.RevertedException;
import javafx.scene.effect.Light.Point;

import org.junit.jupiter.api.Order;

import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.Address;
import score.UserRevertedException;

class DexUpdate implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static String dexJavaPath;
    private static String loansJavaPath;
    private static String rewardsJavaPath;
    private static String dividendsJavaPath;
    private static BigInteger EXA = BigInteger.TEN.pow(18);

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
        owner = balanced.ownerClient;

        System.setProperty("Rewards", rewardsJavaPath);
        System.setProperty("Dex", dexJavaPath);
        System.setProperty("Loans", loansJavaPath);
        System.setProperty("Dividends", dividendsJavaPath);
        
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
        balanced.rewards._update(rewardsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.rewards.addDataProvider(balanced.stakedLp._address());
        owner.rewards.addDataProvider(balanced.dex._address());
        owner.rewards.addDataProvider(balanced.loans._address());
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));
    }

    @Test
    void updateDex() throws Exception {
        BalancedClient loansClient = balanced.newClient();
        BalancedClient lpClient = balanced.newClient();
        BalancedClient loansAndlpClient = balanced.newClient();
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        
        loansClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansAndlpClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", loanAmount, null, null);
        loansClient.bnUSD.transfer(lpClient.getAddress(), loanAmount, null);
        joinsICXBnusdLP(lpClient, loanAmount, loanAmount);
        joinsICXBnusdLP(loansAndlpClient, loanAmount, loanAmount);
        loansAndlpClient.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);
        nextDay();

        byte[] swapIcx = "{\"method\":\"_swap_icx\",\"params\":{\"none\":\"none\"}}".getBytes();
        loansClient.staking.stakeICX(BigInteger.TEN.pow(21), null, null);
        loansClient.sicx.transfer(balanced.dex._address(), loansClient.sicx.balanceOf(loansClient.getAddress()), swapIcx);

        verifyRewards(loansClient);
        verifyRewards(lpClient);
        BigInteger rewards = verifyRewards(loansAndlpClient);

        joinsICXBalnLP(loansAndlpClient, rewards, rewards);

        nextDay();

        verifyExternalAndUpdateDex(loansAndlpClient);
        assertEquals(balanced.stakedLp._address().toString(), owner.dex.getStakedLp().toString());

        nextDay();

        verifyRewards(loansClient);
        verifyRewards(lpClient);
        verifyRewards(loansAndlpClient);
    }

    private void verifyExternalAndUpdateDex(BalancedClient clientInFocus) throws Exception {
        BigInteger day = owner.governance.getDay();
        BigInteger deposit1Pre = owner.dex.getDeposit(balanced.sicx._address(), clientInFocus.getAddress());
        BigInteger deposit2Pre = owner.dex.getDeposit(balanced.baln._address(), clientInFocus.getAddress());
        BigInteger deposit3Pre = owner.dex.getDeposit(balanced.bnusd._address(), clientInFocus.getAddress());
        BigInteger sicxEarningsPre = owner.dex.getSicxEarnings(clientInFocus.getAddress());
        BigInteger withdrawLock1Pre = owner.dex.getWithdrawLock(BigInteger.ONE, clientInFocus.getAddress());
        BigInteger withdrawLock2Pre = owner.dex.getWithdrawLock(BigInteger.TWO, clientInFocus.getAddress());
        BigInteger poolId1Pre = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address()); 
        BigInteger poolId2Pre = owner.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        BigInteger noncePre = owner.dex.getNonce();
        List<String> namedPoolsPre = owner.dex.getNamedPools();
        BigInteger poolTotal1Pre = owner.dex.getPoolTotal(poolId1Pre, balanced.sicx._address());
        BigInteger poolTotal2Pre = owner.dex.getPoolTotal(poolId1Pre, balanced.bnusd._address());
        BigInteger poolTotal3Pre = owner.dex.getPoolTotal(poolId2Pre, balanced.baln._address());
        BigInteger poolTotal4Pre = owner.dex.getPoolTotal(poolId2Pre, balanced.sicx._address());
        BigInteger totalSupply1Pre = owner.dex.totalSupply(poolId1Pre);
        BigInteger totalSupply2Pre = owner.dex.totalSupply(poolId1Pre);
        Map<String, BigInteger> feesPre = owner.dex.getFees();
        Address poolBase1Pre = owner.dex.getPoolBase(poolId1Pre);
        Address poolBase2Pre = owner.dex.getPoolBase(poolId2Pre);
        Address poolQuote1Pre = owner.dex.getPoolQuote(poolId1Pre);
        Address poolQuote2Pre = owner.dex.getPoolQuote(poolId2Pre);
        BigInteger basePriceInQuote1Pre = owner.dex.getBasePriceInQuote(poolId1Pre);
        BigInteger basePriceInQuote2Pre = owner.dex.getBasePriceInQuote(poolId2Pre);
        BigInteger price1Pre = owner.dex.getPrice(poolId1Pre);
        BigInteger price2Pre = owner.dex.getPrice(poolId2Pre);
        BigInteger balnPricePre = owner.dex.getBalnPrice();
        BigInteger sicxBnusdPricePre = owner.dex.getSicxBnusdPrice();
        String poolName1Pre = namedPoolsPre.get(1);//owner.dex.getPoolName(poolId1Pre);
        String poolName2Pre = namedPoolsPre.get(3);//owner.dex.getPoolName(poolId2Pre);
        BigInteger bnusdValue1Pre = owner.dex.getBnusdValue(poolName1Pre);
        BigInteger bnusdValue2Pre = owner.dex.getBnusdValue(poolName2Pre);
        BigInteger priceByName1Pre = owner.dex.getPriceByName(poolName1Pre);
        BigInteger priceByName2Pre = owner.dex.getPriceByName(poolName2Pre);
        BigInteger iCXBalancePre = owner.dex.getICXBalance(clientInFocus.getAddress());
        Map<String, Object> poolStats1Pre = owner.dex.getPoolStats(poolId1Pre);
        Map<String, Object> poolStats2Pre = owner.dex.getPoolStats(poolId2Pre);
        BigInteger totalDexAddressesPre = owner.dex.totalDexAddresses(poolId1Pre);
        BigInteger totalDexAddresses2Pre = owner.dex.totalDexAddresses(poolId2Pre);
        BigInteger balanceOfAt1Pre = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId1Pre, day, false);
        BigInteger balanceOfAt2Pre = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId2Pre, day, false);
        BigInteger balanceOfAt3Pre = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId1Pre, day.subtract(BigInteger.ONE), false);
        BigInteger balanceOfAt4Pre = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId2Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalSupplyAt1Pre = owner.dex.totalSupplyAt(poolId1Pre, day, false);
        BigInteger totalSupplyAt2Pre = owner.dex.totalSupplyAt(poolId2Pre, day, false);
        BigInteger totalSupplyAt3Pre = owner.dex.totalSupplyAt(poolId1Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalSupplyAt4Pre = owner.dex.totalSupplyAt(poolId2Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalBalnAt1Pre = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt2Pre = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt3Pre = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt4Pre = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalValue1Pre = owner.dex.getTotalValue(poolName1Pre, day);
        BigInteger totalValue2Pre = owner.dex.getTotalValue(poolName2Pre, day);
        BigInteger totalValue3Pre = owner.dex.getTotalValue(poolName1Pre, day.subtract(BigInteger.ONE));
        BigInteger totalValue4Pre = owner.dex.getTotalValue(poolName2Pre, day.subtract(BigInteger.ONE));
        BigInteger balnSnapshot1Pre = owner.dex.getBalnSnapshot(poolName1Pre, day);
        BigInteger balnSnapshot2Pre = owner.dex.getBalnSnapshot(poolName2Pre, day);
        BigInteger balnSnapshot3Pre = owner.dex.getBalnSnapshot(poolName1Pre, day.subtract(BigInteger.ONE));
        BigInteger balnSnapshot4Pre = owner.dex.getBalnSnapshot(poolName2Pre, day.subtract(BigInteger.ONE));
        
        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setAddressesOnContract("dex");

        BigInteger deposit1Post = owner.dex.getDeposit(balanced.sicx._address(), clientInFocus.getAddress());
        BigInteger deposit2Post = owner.dex.getDeposit(balanced.baln._address(), clientInFocus.getAddress());
        BigInteger deposit3Post = owner.dex.getDeposit(balanced.bnusd._address(), clientInFocus.getAddress());
        BigInteger sicxEarningsPost = owner.dex.getSicxEarnings(clientInFocus.getAddress());
        BigInteger withdrawLock1Post = owner.dex.getWithdrawLock(BigInteger.ONE, clientInFocus.getAddress());
        BigInteger withdrawLock2Post = owner.dex.getWithdrawLock(BigInteger.TWO, clientInFocus.getAddress());
        BigInteger poolId1Post = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolId2Post = owner.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        BigInteger noncePost = owner.dex.getNonce();
        List<String> namedPoolsPost = owner.dex.getNamedPools();
        BigInteger poolTotal1Post = owner.dex.getPoolTotal(poolId1Pre, balanced.sicx._address());
        BigInteger poolTotal2Post = owner.dex.getPoolTotal(poolId1Pre, balanced.bnusd._address());
        BigInteger poolTotal3Post = owner.dex.getPoolTotal(poolId2Pre, balanced.baln._address());
        BigInteger poolTotal4Post = owner.dex.getPoolTotal(poolId2Pre, balanced.sicx._address());
        BigInteger totalSupply1Post = owner.dex.totalSupply(poolId1Pre);
        BigInteger totalSupply2Post = owner.dex.totalSupply(poolId1Pre);
        Map<String, BigInteger> feesPost = owner.dex.getFees();
        Address poolBase1Post = owner.dex.getPoolBase(poolId1Pre);
        Address poolBase2Post = owner.dex.getPoolBase(poolId2Pre);
        Address poolQuote1Post = owner.dex.getPoolQuote(poolId1Pre);
        Address poolQuote2Post = owner.dex.getPoolQuote(poolId2Pre);
        BigInteger basePriceInQuote1Post = owner.dex.getBasePriceInQuote(poolId1Pre);
        BigInteger basePriceInQuote2Post = owner.dex.getBasePriceInQuote(poolId2Pre);
        BigInteger price1Post = owner.dex.getPrice(poolId1Pre);
        BigInteger price2Post = owner.dex.getPrice(poolId2Pre);
        BigInteger balnPricePost = owner.dex.getBalnPrice();
        BigInteger sicxBnusdPricePost = owner.dex.getSicxBnusdPrice();
        String poolName1Post = owner.dex.getPoolName(poolId1Pre);
        String poolName2Post = owner.dex.getPoolName(poolId2Pre);
        BigInteger bnusdValue1Post = owner.dex.getBnusdValue(poolName1Pre);
        BigInteger bnusdValue2Post = owner.dex.getBnusdValue(poolName2Pre);
        BigInteger priceByName1Post = owner.dex.getPriceByName(poolName1Pre);
        BigInteger priceByName2Post = owner.dex.getPriceByName(poolName2Pre);
        BigInteger iCXBalancePost = owner.dex.getICXBalance(clientInFocus.getAddress());
        Map<String, Object> poolStats1Post = owner.dex.getPoolStats(poolId1Pre);
        Map<String, Object> poolStats2Post = owner.dex.getPoolStats(poolId2Pre);
        BigInteger totalDexAddressesPost = owner.dex.totalDexAddresses(poolId1Pre);
        BigInteger totalDexAddresses2Post = owner.dex.totalDexAddresses(poolId2Pre);
        BigInteger balanceOfAt1Post = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId1Pre, day, false);
        BigInteger balanceOfAt2Post = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId2Pre, day, false);
        BigInteger balanceOfAt3Post = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId1Pre, day.subtract(BigInteger.ONE), false);
        BigInteger balanceOfAt4Post = owner.dex.balanceOfAt(clientInFocus.getAddress(), poolId2Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalSupplyAt1Post = owner.dex.totalSupplyAt(poolId1Pre, day, false);
        BigInteger totalSupplyAt2Post = owner.dex.totalSupplyAt(poolId2Pre, day, false);
        BigInteger totalSupplyAt3Post = owner.dex.totalSupplyAt(poolId1Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalSupplyAt4Post = owner.dex.totalSupplyAt(poolId2Pre, day.subtract(BigInteger.ONE), false);
        BigInteger totalBalnAt1Post = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt2Post = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt3Post = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalBalnAt4Post = owner.dex.totalBalnAt(poolId1Pre, day, false);
        BigInteger totalValue1Post = owner.dex.getTotalValue(poolName1Pre, day);
        BigInteger totalValue2Post = owner.dex.getTotalValue(poolName2Pre, day);
        BigInteger totalValue3Post = owner.dex.getTotalValue(poolName1Pre, day.subtract(BigInteger.ONE));
        BigInteger totalValue4Post = owner.dex.getTotalValue(poolName2Pre, day.subtract(BigInteger.ONE));
        BigInteger balnSnapshot1Post = owner.dex.getBalnSnapshot(poolName1Pre, day);
        BigInteger balnSnapshot2Post = owner.dex.getBalnSnapshot(poolName2Pre, day);
        BigInteger balnSnapshot3Post = owner.dex.getBalnSnapshot(poolName1Pre, day.subtract(BigInteger.ONE));
        BigInteger balnSnapshot4Post = owner.dex.getBalnSnapshot(poolName2Pre, day.subtract(BigInteger.ONE));

        assertEquals(deposit1Pre, deposit1Post);
        assertEquals(deposit2Pre, deposit2Post);
        assertEquals(deposit3Pre, deposit3Post);
        assertEquals(sicxEarningsPre, sicxEarningsPost);
        assertEquals(withdrawLock1Pre, withdrawLock1Post);
        assertEquals(withdrawLock2Pre, withdrawLock2Post);
        assertEquals(poolId1Pre, poolId1Post);
        assertEquals(poolId2Pre, poolId2Post);
        assertEquals(noncePre, noncePost);
        assertEquals(namedPoolsPre, namedPoolsPost);
        assertEquals(poolTotal1Pre, poolTotal1Post);
        assertEquals(poolTotal2Pre, poolTotal2Post);
        assertEquals(poolTotal3Pre, poolTotal3Post);
        assertEquals(poolTotal4Pre, poolTotal4Post);
        assertEquals(totalSupply1Pre, totalSupply1Post);
        assertEquals(totalSupply2Pre, totalSupply2Post);
        assertEquals(feesPre, feesPost);
        assertEquals(poolBase1Pre, poolBase1Post);
        assertEquals(poolBase2Pre, poolBase2Post);
        assertEquals(poolQuote1Pre, poolQuote1Post);
        assertEquals(poolQuote2Pre, poolQuote2Post);
        assertEquals(basePriceInQuote1Pre, basePriceInQuote1Post);
        assertEquals(basePriceInQuote2Pre, basePriceInQuote2Post);
        assertEquals(price1Pre, price1Post);
        assertEquals(price2Pre, price2Post);
        assertEquals(balnPricePre, balnPricePost);
        assertEquals(sicxBnusdPricePre, sicxBnusdPricePost);
        assertEquals(poolName1Pre, poolName1Post);
        assertEquals(poolName2Pre, poolName2Post);
        assertEquals(bnusdValue1Pre, bnusdValue1Post);
        assertEquals(bnusdValue2Pre, bnusdValue2Post);
        assertEquals(priceByName1Pre, priceByName1Post);
        assertEquals(priceByName2Pre, priceByName2Post);
        assertEquals(iCXBalancePre, iCXBalancePost);
        assertEquals(poolStats1Pre, poolStats1Post);
        assertEquals(poolStats2Pre, poolStats2Post);
        assertEquals(totalDexAddressesPre, totalDexAddressesPost);
        assertEquals(totalDexAddresses2Pre, totalDexAddresses2Post);
        // assertEquals(balanceOfAt1Pre, balanceOfAt1Post);
        // assertEquals(balanceOfAt2Pre, balanceOfAt2Post); 
        // assertEquals(balanceOfAt3Pre, balanceOfAt3Post);
        // assertEquals(balanceOfAt4Pre, balanceOfAt4Post);
        assertEquals(totalSupplyAt1Pre, totalSupplyAt1Post);
        assertEquals(totalSupplyAt2Pre, totalSupplyAt2Post);
        assertEquals(totalSupplyAt3Pre, totalSupplyAt3Post);
        assertEquals(totalSupplyAt4Pre, totalSupplyAt4Post);
        assertEquals(totalBalnAt1Pre, totalBalnAt1Post);
        assertEquals(totalBalnAt2Pre, totalBalnAt2Post);
        assertEquals(totalBalnAt3Pre, totalBalnAt3Post);
        assertEquals(totalBalnAt4Pre, totalBalnAt4Post);
        assertEquals(totalValue1Pre, totalValue1Post);
        assertEquals(totalValue2Pre, totalValue2Post);
        assertEquals(totalValue3Pre, totalValue3Post);
        assertEquals(totalValue4Pre, totalValue4Post);
        assertEquals(balnSnapshot1Pre, balnSnapshot1Post);
        assertEquals(balnSnapshot2Pre, balnSnapshot2Post);
        assertEquals(balnSnapshot3Pre, balnSnapshot3Post);
        assertEquals(balnSnapshot4Pre, balnSnapshot4Post);
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

            System.out.println("rebalanced");
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