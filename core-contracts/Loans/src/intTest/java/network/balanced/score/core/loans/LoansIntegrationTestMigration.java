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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

class LoansIntegrationTestMigration extends LoansIntegrationTest {
    static String loansPath;

    @BeforeAll
    public static void contractSetup() throws Exception {
        loansPath = System.getProperty("Loans");
        System.setProperty("Loans", System.getProperty("mainnet"));

        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        
        LoansIntegrationTest.setup();
    }

    @Test
    @Order(-1)
    void updateLoans() throws Exception {
        setupLoans();
        liquidateUser();

        Map<String, Object> availableAssetsPreUpdate = reader.loans.getAvailableAssets().get("bnUSD");
        Map<String, Object> availableAssetsEdited = new HashMap<String, Object>(availableAssetsPreUpdate);
        Map<String, String> collateralTokensPre = reader.loans.getCollateralTokens();
        BigInteger totalCollateralPre = reader.loans.getTotalCollateral();
        BigInteger totalDebtPre = reader.loans.getBalanceAndSupply("Loans", reader.getAddress()).get("_totalSupply");

        List<Map<String, Object>> positionsPre = new ArrayList<>();
        for (BalancedClient client :  balanced.balancedClients.values()) {    
            try {
                Map<String, Object> position = new HashMap<String, Object>(reader.loans.getAccountPositions(client.getAddress()));
                positionsPre.add(position);
            } catch (Exception e) {
            }
        }

        balanced.loans._update(loansPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setAddressesOnContract("loans");

        Map<String, Object>  availableAssetsPostUpdate = reader.loans.getAvailableAssets().get("bnUSD");;
        Map<String, String> collateralTokensPost = reader.loans.getCollateralTokens();
        BigInteger totalCollateralPost = reader.loans.getTotalCollateral();
        BigInteger balanceAndSupplyTotalDebtPost = reader.loans.getBalanceAndSupply("Loans", reader.getAddress()).get("_totalSupply");
        BigInteger totalDebtPost = reader.loans.getTotalDebt("bnUSD");
        BigInteger totalSICXDebtPost = reader.loans.getTotalCollateralDebt("sICX", "bnUSD");

        List<Map<String, Object>> positionsPost = new ArrayList<>();
        for (BalancedClient client :  balanced.balancedClients.values()) {
            try {
                positionsPost.add(reader.loans.getAccountPositions(client.getAddress()));
            } catch (Exception e) {
            }
        }

        compareAssets(availableAssetsPostUpdate, availableAssetsEdited);
        assertEquals(collateralTokensPre, collateralTokensPost);
        assertEquals(totalCollateralPre, totalCollateralPost);
        comparePositions(positionsPost, positionsPre);

        assertEquals(totalDebtPre, balanceAndSupplyTotalDebtPost);
        assertEquals(totalDebtPre, totalDebtPost);
        assertEquals(totalDebtPre, totalSICXDebtPost);

    }

    private void setupLoans() throws Exception {
        BalancedClient loanTaker1 = balanced.newClient();
        BalancedClient loanTaker2 = balanced.newClient();
        BalancedClient loanTaker3 = balanced.newClient();
        BalancedClient loanTaker4 = balanced.newClient();
        BalancedClient loanTaker5 = balanced.newClient();
        BalancedClient nonLoanTaker = balanced.newClient();
        
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loan  = BigInteger.TEN.pow(20);

        loanTaker1.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
        loanTaker2.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
        loanTaker3.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
        loanTaker4.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
        loanTaker5.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);
    }

    private void comparePositions(List<Map<String, Object>> positions, List<Map<String, Object>> refPositions) {
        for (int i = 0; i < positions.size(); i++) {
            Map<String, Map<String, Object>> assetsDetails = (Map<String, Map<String, Object>>) positions.get(i).get("holdings");
            Map<String, Map<String, Object>> standingDetails = (Map<String, Map<String, Object>>) positions.get(i).get("standings");
            Map<String, Object> refAssetsDetails = (Map<String, Object>) refPositions.get(i).get("assets");

            assertEquals(positions.get(i).get("total_debt"), refPositions.get(i).get("total_debt"));
            assertEquals(positions.get(i).get("address"), refPositions.get(i).get("address"));
            assertEquals(positions.get(i).get("pos_id"), refPositions.get(i).get("pos_id"));
            assertEquals(positions.get(i).get("created"), refPositions.get(i).get("created"));
            if (!refAssetsDetails.isEmpty()) {
                assertEquals(assetsDetails.get("sICX").get("sICX"), refAssetsDetails.get("sICX"));
                assertEquals(assetsDetails.get("sICX").get("bnUSD"), refAssetsDetails.get("bnUSD"));
            }
          
            assertEquals(standingDetails.get("sICX").get("standing"), refPositions.get(i).get("standing"));
            assertEquals(standingDetails.get("sICX").get("ratio"), refPositions.get(i).get("ratio"));
            assertEquals(standingDetails.get("sICX").get("collateral"), refPositions.get(i).get("collateral"));
            assertEquals(standingDetails.get("sICX").get("total_debt"), refPositions.get(i).get("total_debt"));
        }
    }

    private void compareAssets(Map<String, Object> assets, Map<String, Object> refAssets) {
        Map<String, Map<String, Object>> debtDetails = (Map<String, Map<String, Object>>) assets.get("debt_details");
        assertEquals(assets.get("total_burned"), refAssets.get("total_burned"));
        assertEquals(assets.get("symbol"), refAssets.get("symbol"));
        assertEquals(assets.get("active"), refAssets.get("active"));
        assertEquals(assets.get("address"), refAssets.get("address"));
        assertEquals(assets.get("total_supply"), refAssets.get("total_supply"));
        assertEquals(debtDetails.get("sICX").get("borrowers"), refAssets.get("borrowers"));
        assertEquals(debtDetails.get("sICX").get("bad_debt"), refAssets.get("bad_debt"));
        assertEquals(debtDetails.get("sICX").get("liquidation_pool"), refAssets.get("liquidation_pool"));
    }
    
    private void liquidateUser() throws Exception {
        balanced.increaseDay(1);
        claimAllRewards();

        BalancedClient voter = balanced.newClient();
        BigInteger expectedTotalDebt = getTotalDebt();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(14000);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup for asset migration");

        BigInteger collateral = BigInteger.TEN.pow(21);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);

        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio for asset migration");

        Map<String,Object> params = new HashMap<>();
        params.put("_owner",loanTaker.getAddress());
        liquidator.loans._send("liquidate", params);
    }

    protected void setLockingRatio(BalancedClient voter, BigInteger ratio, String name) throws Exception {
        JsonArray setLockingRatioParameters = new JsonArray()
         .add(createParameter("Number", ratio));

        JsonObject setLockingRatioList = new JsonObject()
            .add("contract_address", balanced.loans._address().toString())
            .add("method", "setLockingRatio")
            .add("parameters", setLockingRatioParameters);
    
        JsonArray setLockingRatio = new JsonArray()
            .add("call")
            .add(setLockingRatioList);

        JsonArray actions = new JsonArray()
            .add(setLockingRatio);

        executeVoteActions(balanced, voter, name, actions);
    }

    protected JsonObject createParameter(String type, BigInteger value) {

        return new JsonObject()
            .add("type", type)
            .add("value", value.intValue());
    }
}