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
       takeLoans();
    //    reOpenPosition();
       repyDebt();
    //    rebalancing_lowerPrice();
    //    rebalancing_raisePrice();
       liquidateUser();

       Map<String, Object> availableAssetsPreUpdate = reader.loans.getAvailableAssets().get("bnUSD");
       Map<String, Object> availableAssetsEdited = new HashMap<String, Object>(availableAssetsPreUpdate);
       availableAssetsEdited.remove("is_collateral");
       Map<String, String> collateralTokensPre = reader.loans.getCollateralTokens();
       BigInteger totalCollateralPre = reader.loans.getTotalCollateral();

       List<Object> positionsPre = new ArrayList<>();
       for (BalancedClient client :  balanced.balancedClients.values()) {    
            try {
                Map<String, Object> positionEdited = new HashMap<String, Object>(reader.loans.getAccountPositions(client.getAddress()));
                positionEdited.remove("first day");
                positionEdited.remove("last_snap");
                positionEdited.remove("snap_id");
                positionEdited.remove("snaps_length");
                positionsPre.add(positionEdited);
            } catch (Exception e) {
            }
       }

       balanced.loans._update(loansPath, Map.of("_governance", balanced.governance._address()));
       owner.governance.setAddressesOnContract("loans");
       owner.balancedOracle.getPriceInLoop((txr) -> {}, "sICX");
       owner.balancedOracle.getPriceInLoop((txr) -> {}, "USD");

       Map<String, Object>  availableAssetsPostUpdate = reader.loans.getAvailableAssets().get("bnUSD");;
       Map<String, String> collateralTokensPost = reader.loans.getCollateralTokens();
       BigInteger totalCollateralPost = reader.loans.getTotalCollateral();

       List<Object> positionsPost = new ArrayList<>();
       for (BalancedClient client :  balanced.balancedClients.values()) {
            try {
                positionsPost.add(reader.loans.getAccountPositions(client.getAddress()));
            } catch (Exception e) {
            }
       }

       assertEquals(availableAssetsEdited, availableAssetsPostUpdate);
       assertEquals(collateralTokensPre, collateralTokensPost);
       assertEquals(totalCollateralPre, totalCollateralPost);
       assertEquals(positionsPre, positionsPost);
    }

    private void liquidateUser() throws Exception {
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
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup for asset migration");

        BigInteger collateral = BigInteger.TEN.pow(22);
        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);

        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio -1");

        // Act
        liquidator.loans.liquidate(loanTaker.getAddress());
    }
}