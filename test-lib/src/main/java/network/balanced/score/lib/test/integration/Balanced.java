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

package network.balanced.score.lib.test.integration;

import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.model.Hash;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.BalancedAddresses;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;

import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;

public class Balanced {
    public KeyWallet owner;
    public BalancedClient ownerClient;
    public DefaultScoreClient governance;
    public DefaultScoreClient baln;
    public DefaultScoreClient bwt;
    public DefaultScoreClient dex;
    public DefaultScoreClient feehandler;
    public DefaultScoreClient loans;
    public DefaultScoreClient rebalancing;
    public DefaultScoreClient rewards;
    public DefaultScoreClient sicx;
    public DefaultScoreClient bnusd;
    public DefaultScoreClient daofund;
    public DefaultScoreClient dividends;
    public DefaultScoreClient oracle;
    public DefaultScoreClient reserve;
    public DefaultScoreClient router;
    public DefaultScoreClient staking;
    public DefaultScoreClient stakedLp;

    public Balanced() {
    }

    public void deployBalanced() throws Exception {
        owner = createWalletWithBalance(BigInteger.TEN.pow(24));
        deployPrep();

        governance = deploy(owner, "Governance", null);
        deployContracts();
        ownerClient = new BalancedClient(this, owner);
    
        setupAddresses();
        setupContracts();
        // delegate(adminWallet);
        ownerClient = new BalancedClient(this, owner);
        ownerClient.governance.createBnusdMarket(BigInteger.valueOf(210).multiply(BigInteger.TEN.pow(18)));
    }
    
    protected void deployPrep() {
        try {
            systemScore.registerPRep(BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), "test", "kokoa@example.com", "USA", "New York", "https://icon.kokoa.com", "https://icon.kokoa.com/json/details.json", "localhost:9082");
        } catch (Exception e) {
            //Already registerd
        }
    }

    protected void deployContracts() {
        Hash balnTx = deployAsync(owner, "Baln", Map.of("_governance", governance._address()));
        Hash bwtTx = deployAsync(owner, "Bwt", Map.of("_governance", governance._address()));
        Hash dexTx = deployAsync(owner, "Dex", Map.of("_governance", governance._address()));
        Hash feehandlerTx = deployAsync(owner, "Feehandler", Map.of("_governance", governance._address()));
        Hash loansTx = deployAsync(owner, "Loans", Map.of("_governance", governance._address()));
        Hash rebalancingTx = deployAsync(owner, "Rebalancing", Map.of("_governance", governance._address()));
        Hash rewardsTx = deployAsync(owner, "Rewards", Map.of("_governance", governance._address()));
        Hash stakingTx = deployAsync(owner, "Staking", null);
        Hash bnusdTx = deployAsync(owner, "BalancedDollar", Map.of("_governance", governance._address()));
        Hash daofundTx = deployAsync(owner, "DAOfund", Map.of("_governance", governance._address()));
        Hash dividendsTx = deployAsync(owner, "Dividends", Map.of("_governance", governance._address()));
        Hash oracleTx = deployAsync(owner, "Oracle",null);
        Hash reserveTx = deployAsync(owner, "Reserve", Map.of("governance", governance._address()));
        Hash routerTx = deployAsync(owner, "Router", Map.of("_governance", governance._address()));
        Hash stakedLpTx = deployAsync(owner, "StakedLP", Map.of("governance", governance._address()));
        staking = getDeploymentResult(owner, stakingTx);
        Hash sicxTx = deployAsync(owner, "Sicx", Map.of("_admin", staking._address()));

        baln = getDeploymentResult(owner, balnTx);
        bwt = getDeploymentResult(owner, bwtTx);
        dex = getDeploymentResult(owner, dexTx);
        feehandler = getDeploymentResult(owner, feehandlerTx);
        loans = getDeploymentResult(owner, loansTx);
        rebalancing = getDeploymentResult(owner, rebalancingTx);
        rewards = getDeploymentResult(owner, rewardsTx);
        bnusd = getDeploymentResult(owner, bnusdTx);
        daofund = getDeploymentResult(owner, daofundTx);
        dividends = getDeploymentResult(owner, dividendsTx);
        oracle = getDeploymentResult(owner, oracleTx);
        reserve = getDeploymentResult(owner, reserveTx);
        router = getDeploymentResult(owner, routerTx);
        stakedLp = getDeploymentResult(owner, stakedLpTx);
        sicx = getDeploymentResult(owner, sicxTx);
    }

    protected void setupAddresses() {
        BalancedAddresses balancedAddresses = new BalancedAddresses();
        balancedAddresses.loans = loans._address();
        balancedAddresses.dex = dex._address();
        balancedAddresses.staking = staking._address();
        balancedAddresses.rewards = rewards._address();
        balancedAddresses.reserve = reserve._address();
        balancedAddresses.dividends = dividends._address();
        balancedAddresses.daofund = daofund._address();
        balancedAddresses.oracle = oracle._address();
        balancedAddresses.sicx = sicx._address();
        balancedAddresses.bnUSD = bnusd._address();
        balancedAddresses.baln = baln._address();
        balancedAddresses.bwt = bwt._address();
        balancedAddresses.router = router._address();
        balancedAddresses.rebalancing = rebalancing._address();
        balancedAddresses.feehandler = feehandler._address();
        balancedAddresses.stakedLp = stakedLp._address();

        ownerClient.governance.setAddresses(balancedAddresses);
        ownerClient.governance.setAdmins();
        ownerClient.governance.setContractAddresses();
    }

    protected void setupContracts() {
        ownerClient.staking.setSicxAddress(sicx._address());

        ownerClient.bnUSD.setMinter(loans._address());
        ownerClient.governance.configureBalanced();
        ownerClient.governance.launchBalanced();
        ownerClient.staking.toggleStakingOn();

        ownerClient.daofund.addAddressToSetdb();
    }

    public BalancedClient newClient(BigInteger clientBalanace) throws Exception {
        return new BalancedClient(this, createWalletWithBalance(clientBalanace));
    }

    public BalancedClient newClient() throws Exception {
        return newClient(BigInteger.TEN.pow(24));
    }

    public void syncDistributions() {
        while (!checkDistributionsDone()) {
            Consumer<TransactionResult> distributeConsumer = result -> {};
            ownerClient.rewards.distribute(distributeConsumer);
        }
    }
    
    public boolean checkDistributionsDone() {
        BigInteger day = ownerClient.governance.getDay();
        Map<String, Object> status = ownerClient.rewards.distStatus();
        System.out.println("platform: " +hexObjectToInt(status.get("platform_day")) + "  <  " + day.intValue());
        if (hexObjectToInt(status.get("platform_day")) < day.intValue()) {
            return false;
        }

        Map<String, String> dataSourceStatus = (Map<String, String>) status.get("source_days");
        for (String sourceDay : dataSourceStatus.values()) {
            System.out.println("source: " +hexObjectToInt(sourceDay) + "  <  " + day.intValue());
            if (hexObjectToInt(sourceDay) < day.intValue()) {
                return false;
            }
        }

        return true;
    }

    public int hexObjectToInt(Object hexNumber) {
        return Integer.parseInt(((String)hexNumber).substring(2), 16);
    }
    
    public void increaseDay(int nrOfDays) {
        ownerClient.governance.setTimeOffset(ownerClient.governance.getTimeOffset().subtract(U_SECONDS_DAY.multiply(BigInteger.valueOf(nrOfDays))));
        ownerClient.baln.setTimeOffset();
    }
}
