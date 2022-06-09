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
import static org.mockito.Answers.valueOf;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import net.bytebuddy.asm.Advice.PostProcessor;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

class LoansDataMigration implements ScoreIntegrationTest {
    private Balanced balanced;
    private Balanced referenceBalanced;
    private BalancedClient owner;
    private BalancedClient referenceOwner;
    private String dexJavaPath;
    private String loansJavaPath;
    private String rewardsJavaPath;
    private String dividendsJavaPath;
    private BigInteger EXA = BigInteger.TEN.pow(18);

    private BigInteger referenceTotalDebt = BigInteger.ZERO;


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
        referenceBalanced = new Balanced();
        balanced.deployBalanced();
        referenceBalanced.deployBalanced();

        System.setProperty("Rewards", rewardsJavaPath);
        System.setProperty("Dex", dexJavaPath);
        System.setProperty("Loans", loansJavaPath);
        System.setProperty("Dividends", dividendsJavaPath);

        owner = balanced.ownerClient;
        referenceOwner = referenceBalanced.ownerClient;

        balanced.increaseDay(1);
        referenceBalanced.increaseDay(1);
        owner.baln.toggleEnableSnapshot();
        referenceOwner.baln.toggleEnableSnapshot();

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

        referenceOwner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        referenceOwner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        referenceOwner.governance.addAcceptedTokens(balanced.baln._address().toString());
        referenceOwner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        referenceOwner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });
        referenceOwner.governance.setFeeProcessingInterval(BigInteger.ZERO);
        referenceOwner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
    
        nextDay();
        balanced.dividends._update(dividendsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setDividendsOnlyToStakedBalnDay(owner.dividends.getSnapshotId().add(BigInteger.ONE));
        updateRewards();
    }

    @Test
    void dataMigration() throws Exception {
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        
        compareAllPositions();
        rebalanceAll();
        compareAllPositions();
        
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));

        compareAllPositions();
        rebalanceAll();
        compareAllPositions();
        
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        rebalanceAll();
        compareAllPositions();
        compareAllPositions();
        
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        compareAllPositions();

        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setContractAddresses();
        BigInteger daysToMigration = BigInteger.TWO;
        owner.governance.setContinuousRewardsDay(owner.governance.getDay().add(daysToMigration));

        nextDay();

        compareAllPositions();
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        compareAllPositions();

        //migrate
        nextDay();

        compareAllPositionsContinuous();
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        compareAllPositionsContinuous();

        balanced.increaseDay(1);
        referenceBalanced.increaseDay(1);
        referenceBalanced.syncDistributions();

        compareAllPositionsContinuous();
        setupPositions(balanced);
        setupPositions(referenceBalanced);
        compareAllPositionsContinuous();
    }

    private void setupPositions(Balanced targetBalanced) throws Exception {
        BalancedClient emptyClient = targetBalanced.newClient();
        BalancedClient loanTakerICX = targetBalanced.newClient();
        BalancedClient zeroLoanTakerICX = targetBalanced.newClient();
        loanTakerICX.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        zeroLoanTakerICX.loans.depositAndBorrow(BigInteger.TEN.pow(21), "bnUSD", BigInteger.ZERO, null, null);

        BalancedClient positionCloser = targetBalanced.newClient();
        positionCloser.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(21), null, null);
        loanTakerICX.bnUSD.transfer(positionCloser.getAddress(), BigInteger.TEN.pow(21), null);
        Map<String, String> assets = (Map<String, String>) positionCloser.loans.getAccountPositions(positionCloser.getAddress()).get("assets");
        BigInteger debt = Balanced.hexObjectToInt(assets.get("bnUSD"));
        BigInteger collteral = Balanced.hexObjectToInt(assets.get("sICX"));
        positionCloser.loans.returnAsset("bnUSD", debt, true);
        positionCloser.loans.withdrawCollateral(collteral);

        BalancedClient returnAssetClient = targetBalanced.newClient();
        returnAssetClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        returnAssetClient.loans.returnAsset("bnUSD", BigInteger.TEN.pow(21), true);

        BalancedClient withdrawCollateralClient = targetBalanced.newClient();
        withdrawCollateralClient.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        withdrawCollateralClient.loans.withdrawCollateral(BigInteger.TEN.pow(21));

        BalancedClient stakingClient = targetBalanced.newClient();
        stakingClient.staking.stakeICX(BigInteger.TEN.pow(23),  null, null);
    }

    private void rebalanceAll() throws Exception {

        raisePriceAboveThreshold(balanced);
        raisePriceAboveThreshold(referenceBalanced);
        rebalance(balanced);
        rebalance(referenceBalanced);

    }

    private void rebalance(Balanced targetBalanced) throws Exception {
        BalancedClient rebalancer = targetBalanced.newClient();
        while (true) {
            BigInteger threshold = calculateThreshold(targetBalanced);
            targetBalanced.ownerClient.rebalancing.rebalance();
            if (threshold.equals(calculateThreshold(targetBalanced))) {
                return;
            }
        }
    }

    private void raisePriceAboveThreshold(Balanced targetBalanced) throws Exception {
        BigInteger threshold = targetBalanced.ownerClient.rebalancing.getPriceChangeThreshold();
        while (calculateThreshold(targetBalanced).multiply(BigInteger.valueOf(100)).compareTo(threshold.negate().multiply(BigInteger.valueOf(105))) > 0) {
            raiseBnusdPrice(targetBalanced);
        }
    }

    private BigInteger calculateThreshold(Balanced targetBalanced) {
        BigInteger bnusdPriceInIcx = targetBalanced.ownerClient.bnUSD.lastPriceInLoop();
        BigInteger sicxPriceInIcx = targetBalanced.ownerClient.sicx.lastPriceInLoop();

        BigInteger icxBnusdPoolId = targetBalanced.ownerClient.dex.getPoolId(targetBalanced.sicx._address(), targetBalanced.bnusd._address());
        Map<String, Object> poolStats = targetBalanced.ownerClient.dex.getPoolStats(icxBnusdPoolId);
        BigInteger sicxLiquidity = Balanced.hexObjectToInt(poolStats.get("base"));
        BigInteger bnusdLiquidity = Balanced.hexObjectToInt(poolStats.get("quote"));

        BigInteger actualBnusdPriceInSicx = bnusdPriceInIcx.multiply(EXA).divide(sicxPriceInIcx);
        BigInteger bnusdPriceInSicx = sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
        BigInteger priceDifferencePercentage = (actualBnusdPriceInSicx.subtract(bnusdPriceInSicx)).multiply(EXA).divide(actualBnusdPriceInSicx);

        return priceDifferencePercentage;
    }

    private void raiseBnusdPrice(Balanced targetBalanced) throws Exception {
        BalancedClient sellerClient = targetBalanced.newClient();
        BigInteger amountToSell = BigInteger.TEN.pow(21);
        sellerClient.staking.stakeICX(amountToSell.multiply(BigInteger.TWO), null, null);
        JsonObject swapData = Json.object();
        JsonObject swapParams = Json.object();
        swapParams.add("toToken", targetBalanced.bnusd._address().toString());
        swapData.add("method", "_swap");
        swapData.add("params", swapParams);

        sellerClient.sicx.transfer(targetBalanced.dex._address(), amountToSell, swapData.toString().getBytes());
    }

    private void compareAllPositions() throws Exception {
        for (int i = 0; i < balanced.balancedClientsList.size(); i++) {
            BalancedClient client = balanced.getClient(balanced.balancedClientsList.get(i));
            BalancedClient referenceclient = referenceBalanced.getClient(referenceBalanced.balancedClientsList.get(i));
            comparePositions(client, referenceclient);
        }

        compareSnapshots();
    }

    private void compareAllPositionsContinuous() throws Exception {
        referenceTotalDebt = BigInteger.ZERO;
        for (int i = 0; i < balanced.balancedClientsList.size(); i++) {
            BalancedClient client = balanced.getClient(balanced.balancedClientsList.get(i));
            BalancedClient referenceclient = referenceBalanced.getClient(referenceBalanced.balancedClientsList.get(i));
            comparePositionsContinuous(client, referenceclient);
        }

        BigInteger totalDebt = owner.loans.getBalanceAndSupply("Loans", owner.getAddress()).get("_totalSupply");
        Map<String, Object> refGovAssets = (Map<String, Object>)referenceOwner.loans.getAccountPositions(referenceBalanced.governance._address()).get("assets");
        BigInteger govHoldings = Balanced.hexObjectToInt(refGovAssets.get("bnUSD"));
        assertEquals(referenceTotalDebt.add(govHoldings), totalDebt);
    }

    private void compareSnapshots() throws Exception {
        Map<String, Object> snap1 = owner.loans.getSnapshot(BigInteger.valueOf(-1));
        Map<String, Object> snap2 = owner.loans.getSnapshot(BigInteger.valueOf(-2));
        Map<String, Object> snap3 = owner.loans.getSnapshot(BigInteger.valueOf(-3));

        Map<String, Object> refSnap1 = referenceOwner.loans.getSnapshot(BigInteger.valueOf(-1));
        Map<String, Object> refSnap2 = referenceOwner.loans.getSnapshot(BigInteger.valueOf(-2));
        Map<String, Object> refSnap3 = referenceOwner.loans.getSnapshot(BigInteger.valueOf(-3));

        assertEquals(snap1.get("snap_day"), refSnap1.get("snap_day"));
        assertEquals(snap1.get("total_mining_debt"), refSnap1.get("total_mining_debt"));
        assertEquals(snap1.get("prices"), refSnap1.get("prices"));
        assertEquals(snap1.get("mining_count"), refSnap1.get("mining_count"));
        assertEquals(snap1.get("precompute_index"), refSnap1.get("precompute_index"));
        assertEquals(snap1.get("add_to_nonzero_count"), refSnap1.get("add_to_nonzero_count"));
        assertEquals(snap1.get("remove_from_nonzero_count"), refSnap1.get("remove_from_nonzero_count"));

        assertEquals(snap2.get("snap_day"), refSnap2.get("snap_day"));
        assertEquals(snap2.get("total_mining_debt"), refSnap2.get("total_mining_debt"));
        assertEquals(snap2.get("prices"), refSnap2.get("prices"));
        assertEquals(snap2.get("mining_count"), refSnap2.get("mining_count"));
        assertEquals(snap2.get("precompute_index"), refSnap2.get("precompute_index"));
        assertEquals(snap2.get("add_to_nonzero_count"), refSnap2.get("add_to_nonzero_count"));
        assertEquals(snap2.get("remove_from_nonzero_count"), refSnap2.get("remove_from_nonzero_count"));
        
        assertEquals(snap3.get("snap_day"), refSnap3.get("snap_day"));
        assertEquals(snap3.get("total_mining_debt"), refSnap3.get("total_mining_debt"));
        assertEquals(snap3.get("prices"), refSnap3.get("prices"));
        assertEquals(snap3.get("mining_count"), refSnap3.get("mining_count"));
        assertEquals(snap3.get("precompute_index"), refSnap3.get("precompute_index"));
        assertEquals(snap3.get("add_to_nonzero_count"), refSnap3.get("add_to_nonzero_count"));
        assertEquals(snap3.get("remove_from_nonzero_count"), refSnap3.get("remove_from_nonzero_count"));  
    }

    private void comparePositions(BalancedClient client, BalancedClient referenceclient) throws Exception {
        Map<String, Object> position = new HashMap<String, Object>();
        Map<String, Object> refPosition = new HashMap<String, Object>();
        try {
            position = removeZeroAssets(owner.loans.getAccountPositions(client.getAddress()));
            refPosition = removeZeroAssets(referenceOwner.loans.getAccountPositions(referenceclient.getAddress()));    
        } catch (Exception e) {
            Assertions.assertThrows(Exception.class, () -> 
                owner.loans.getAccountPositions(client.getAddress())
            );
            Assertions.assertThrows(Exception.class, () -> 
                referenceOwner.loans.getAccountPositions(referenceclient.getAddress())
            );
        }
      
        if (position.containsKey("message") && refPosition.containsKey("message")) {
            assertEquals(position.get("message"), refPosition.get("message"));
            return;
        }

        int id =  balanced.hexObjectToInt(position.get("pos_id")).intValue();
        int refId =  balanced.hexObjectToInt(refPosition.get("pos_id")).intValue();

        Map<String, Object> postitionByIndex = new HashMap<String, Object>();
        Map<String, Object> refPostitionByIndex = new HashMap<String, Object>();
        try {
            postitionByIndex = removeZeroAssets(owner.loans.getPositionByIndex(id, BigInteger.valueOf(-1)));
            refPostitionByIndex = removeZeroAssets(referenceOwner.loans.getPositionByIndex(refId, BigInteger.valueOf(-1)));
        } catch (Exception e) {
            Assertions.assertThrows(UserRevertedException.class, () -> 
                owner.loans.getPositionByIndex(id, BigInteger.valueOf(-1))
            );
            Assertions.assertThrows(UserRevertedException.class, () -> 
                referenceOwner.loans.getPositionByIndex(refId, BigInteger.valueOf(-1))
            );
        }
        
        Map<String, Object> postitionByIndexLastDay = new HashMap<String, Object>();
        Map<String, Object> refPostitionByIndexLastDay = new HashMap<String, Object>();
        try {
            postitionByIndexLastDay = removeZeroAssets(owner.loans.getPositionByIndex(id, BigInteger.valueOf(-2)));
            refPostitionByIndexLastDay = removeZeroAssets(referenceOwner.loans.getPositionByIndex(refId, BigInteger.valueOf(-2)));
        } catch (Exception e) {
            Assertions.assertThrows(UserRevertedException.class, () -> 
                owner.loans.getPositionByIndex(id, BigInteger.valueOf(-2))
            );
            Assertions.assertThrows(UserRevertedException.class, () -> 
                referenceOwner.loans.getPositionByIndex(refId, BigInteger.valueOf(-2))
            );
        }

        assertEquals(id, refId);
        if (!position.isEmpty() || !refPosition.isEmpty()) {
            assertEquals(position.get("snap_id"), refPosition.get("snap_id"));
            assertEquals(position.get("snaps_length"), refPosition.get("snaps_length"));
            assertEquals(position.get("last_snap"), refPosition.get("last_snap"));
            assertEquals(position.get("first day"), refPosition.get("first day"));
            assertEquals(position.get("assets").toString(), refPosition.get("assets").toString());
            assertEquals(position.get("total_debt"), refPosition.get("total_debt"));
            assertEquals(position.get("collateral"), refPosition.get("collateral"));
            assertEquals(position.get("ratio"), refPosition.get("ratio"));
            assertEquals(position.get("standing"), refPosition.get("standing"));
        }
      
        if (!postitionByIndex.isEmpty() || !refPostitionByIndex.isEmpty()) {
            assertEquals(postitionByIndex.get("snap_id"), refPostitionByIndex.get("snap_id"));
            assertEquals(postitionByIndex.get("snaps_length"), refPostitionByIndex.get("snaps_length"));
            assertEquals(postitionByIndex.get("last_snap"), refPostitionByIndex.get("last_snap"));
            assertEquals(postitionByIndex.get("first day"), refPostitionByIndex.get("first day"));
            assertEquals(postitionByIndex.get("assets").toString(), refPostitionByIndex.get("assets").toString());
            assertEquals(postitionByIndex.get("total_debt"), refPostitionByIndex.get("total_debt"));
            assertEquals(postitionByIndex.get("collateral"), refPostitionByIndex.get("collateral"));
            assertEquals(postitionByIndex.get("ratio"), refPostitionByIndex.get("ratio"));
            assertEquals(postitionByIndex.get("standing"), refPostitionByIndex.get("standing"));
        }

        if (!postitionByIndexLastDay.isEmpty() || !refPostitionByIndexLastDay.isEmpty()) {
            assertEquals(postitionByIndexLastDay.get("snap_id"), refPostitionByIndexLastDay.get("snap_id"));
            assertEquals(postitionByIndexLastDay.get("snaps_length"), refPostitionByIndexLastDay.get("snaps_length"));
            assertEquals(postitionByIndexLastDay.get("last_snap"), refPostitionByIndexLastDay.get("last_snap"));
            assertEquals(postitionByIndexLastDay.get("first day"), refPostitionByIndexLastDay.get("first day"));
            assertEquals(postitionByIndexLastDay.get("assets").toString(), refPostitionByIndexLastDay.get("assets").toString());
            assertEquals(postitionByIndexLastDay.get("total_debt"), refPostitionByIndexLastDay.get("total_debt"));
            assertEquals(postitionByIndexLastDay.get("collateral"), refPostitionByIndexLastDay.get("collateral"));
            assertEquals(postitionByIndexLastDay.get("ratio"), refPostitionByIndexLastDay.get("ratio"));
            assertEquals(postitionByIndexLastDay.get("standing"), refPostitionByIndexLastDay.get("standing"));
        }
    }

    private void comparePositionsContinuous(BalancedClient client, BalancedClient referenceclient) throws Exception {
        Map<String, Object> position = new HashMap<String, Object>();
        Map<String, Object> refPosition = new HashMap<String, Object>();
        try {
            position = removeZeroAssets(owner.loans.getAccountPositions(client.getAddress()));
            refPosition = removeZeroAssets(referenceOwner.loans.getAccountPositions(referenceclient.getAddress()));    
        } catch (Exception e) {
            Assertions.assertThrows(Exception.class, () -> 
                owner.loans.getAccountPositions(client.getAddress())
            );
            Assertions.assertThrows(Exception.class, () -> 
                referenceOwner.loans.getAccountPositions(referenceclient.getAddress())
            );
        }
      
        if (position.containsKey("message") && refPosition.containsKey("message")) {
            assertEquals(position.get("message"), refPosition.get("message"));
            return;
        }

        if (!position.isEmpty() || !refPosition.isEmpty()) {
            try {
            referenceTotalDebt = referenceTotalDebt.add(balanced.hexObjectToInt(((Map<String, Object>)refPosition.get("assets")).get("bnUSD")));
            } catch (Exception e) {}
            assertEquals(position.get("assets").toString(), refPosition.get("assets").toString());
            assertEquals(position.get("total_debt"), refPosition.get("total_debt"));
            assertEquals(position.get("collateral"), refPosition.get("collateral"));
            assertEquals(position.get("ratio"), refPosition.get("ratio"));
        }


    }
    
    private Map<String, Object> removeZeroAssets(Map<String, Object> position) {
        if (position.isEmpty() || position.containsKey("message")){
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
        newPosition.put("assets", newAssets);

        return newPosition;
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

    
    private void nextDay() {
        balanced.increaseDay(1);
        balanced.syncDistributions();

        referenceBalanced.increaseDay(1);
        referenceBalanced.syncDistributions();
    }
}