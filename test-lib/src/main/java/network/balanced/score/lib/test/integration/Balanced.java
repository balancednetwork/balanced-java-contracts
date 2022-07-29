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
import network.balanced.score.lib.interfaces.DAOfundScoreClient;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.RewardsScoreClient;
import network.balanced.score.lib.interfaces.StakingScoreClient;
import network.balanced.score.lib.structs.BalancedAddresses;
import score.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

public class Balanced {

    public KeyWallet user;
    public KeyWallet testerWallet;
    public KeyWallet secondTesterWallet;

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
    public DefaultScoreClient stability;

    public GovernanceScoreClient governanceScore;
    public StakingScoreClient stakingScore;
    public DAOfundScoreClient daofundScore;
    public RewardsScoreClient rewardsScore;

    public Map<Address, BalancedClient> balancedClients;

    public Balanced() throws Exception {
        balancedClients = new HashMap<>();
        owner = createWalletWithBalance(BigInteger.TEN.pow(24));
        user = createWalletWithBalance(BigInteger.TEN.pow(24));
        testerWallet = createWalletWithBalance(BigInteger.TEN.pow(24));
        secondTesterWallet = createWalletWithBalance(BigInteger.TEN.pow(24));
    }

    public void setupBalanced() throws Exception {
        registerPreps();
        deployContracts();
        setupAddresses();
        increaseDay(1);
        setupContracts();
//         delegate(adminWallet);
        String className = new Exception().getStackTrace()[1].getClassName();
        // no need to set up market in staking integration test case
        if (!className.equals("network.balanced.score.core.staking.StakingIntegrationTest")) {
            setupMarkets();
        }
    }

    public void deployContracts() {
        governance = deploy(owner, "Governance", null);

        Hash balnTx = deployAsync(owner, "BalancedToken", Map.of("_governance", governance._address()));
        Hash bwtTx = deployAsync(owner, "WorkerToken", Map.of("_governance", governance._address()));
        Hash dexTx = deployAsync(owner, "Dex", Map.of("_governance", governance._address()));
        Hash feehandlerTx = deployAsync(owner, "FeeHandler", Map.of("_governance", governance._address()));
        Hash loansTx = deployAsync(owner, "Loans", Map.of("_governance", governance._address()));
        Hash rebalancingTx = deployAsync(owner, "Rebalancing", Map.of("_governance", governance._address()));
        Hash rewardsTx = deployAsync(owner, "Rewards", Map.of("_governance", governance._address()));
        Hash stakingTx = deployAsync(owner, "Staking", null);
        Hash bnusdTx = deployAsync(owner, "BalancedDollar", Map.of("_governance", governance._address()));
        Hash daofundTx = deployAsync(owner, "DAOfund", Map.of("_governance", governance._address()));
        Hash dividendsTx = deployAsync(owner, "Dividends", Map.of("_governance", governance._address()));
        Hash oracleTx = deployAsync(owner, "DummyOracle",null);
        Hash reserveTx = deployAsync(owner, "Reserve", Map.of("governance", governance._address()));
        Hash routerTx = deployAsync(owner, "Router", Map.of("_governance", governance._address()));
        Hash stakedLpTx = deployAsync(owner, "StakedLP", Map.of("governance", governance._address()));
        staking = getDeploymentResult(owner, stakingTx);
        feehandler = getDeploymentResult(owner, feehandlerTx);
        bnusd = getDeploymentResult(owner, bnusdTx);

        Hash stabilityTx = deployAsync(owner, "Stability", Map.of(
                "_feeHandler", feehandler._address(),
                "_bnusd", bnusd._address(),
                "_feeIn", BigInteger.TEN.pow(18),
                "_feeOut", BigInteger.TEN.pow(18)
        ));

        Hash sicxTx = deployAsync(owner, "Sicx", Map.of("_admin", staking._address()));

        baln = getDeploymentResult(owner, balnTx);
        bwt = getDeploymentResult(owner, bwtTx);
        dex = getDeploymentResult(owner, dexTx);
        loans = getDeploymentResult(owner, loansTx);
        rebalancing = getDeploymentResult(owner, rebalancingTx);
        rewards = getDeploymentResult(owner, rewardsTx);
        daofund = getDeploymentResult(owner, daofundTx);
        dividends = getDeploymentResult(owner, dividendsTx);
        oracle = getDeploymentResult(owner, oracleTx);
        reserve = getDeploymentResult(owner, reserveTx);
        router = getDeploymentResult(owner, routerTx);
        stakedLp = getDeploymentResult(owner, stakedLpTx);
        sicx = getDeploymentResult(owner, sicxTx);
        stability = getDeploymentResult(owner, stabilityTx);

        ownerClient = new BalancedClient(this, owner);
    }

    public void setupAddresses() {
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

    public void setupContracts() {
        ownerClient.staking.setSicxAddress(sicx._address());

        ownerClient.bnUSD.setMinter(loans._address());
        ownerClient.sicx.setMinter(staking._address());
        ownerClient.baln.setMinter(rewards._address());
        ownerClient.bnUSD.setMinter2(stability._address());

        ownerClient.governance.configureBalanced();
        ownerClient.governance.launchBalanced();
        ownerClient.staking.toggleStakingOn();

        ownerClient.daofund.addAddressToSetdb();

        ownerClient.governance.setAdmin(feehandler._address(), governance._address());
        ownerClient.governance.enable_fee_handler();

        ownerClient.rewards.addDataProvider(stakedLp._address());
        ownerClient.rewards.addDataProvider(dex._address());
        ownerClient.rewards.addDataProvider(loans._address());

        ownerClient.governance.setFeeProcessingInterval(BigInteger.ONE);

        Address[] acceptedAddress=new Address[]{
                bnusd._address(), sicx._address(),baln._address()
        };
        ownerClient.governance.setAcceptedDividendTokens(acceptedAddress);
        ownerClient.governance.addAcceptedTokens(String.valueOf(bnusd._address()));
        ownerClient.governance.addAcceptedTokens(String.valueOf(sicx._address()));
        ownerClient.governance.addAcceptedTokens(String.valueOf(baln._address()));
    }

    public void setupMarkets() {
        ownerClient.governance.createBnusdMarket(BigInteger.valueOf(400000).multiply(BigInteger.TEN.pow(18)));
        increaseDay(2);
//        syncDistributions();
        BigInteger balnBalance = ownerClient.rewards.getBalnHolding(governance._address());
        BigInteger initialPoolDepths = balnBalance.divide(BigInteger.TWO);
        ownerClient.governance.createBalnMarket(initialPoolDepths, initialPoolDepths);
        ownerClient.staking.stakeICX(initialPoolDepths.multiply(BigInteger.TWO), null, null);
        ownerClient.sicx.transfer(governance._address(), initialPoolDepths, null);
        ownerClient.governance.createBalnSicxMarket(initialPoolDepths, initialPoolDepths);
    }

    private BalancedClient newClient(BigInteger clientBalance) throws Exception {
        BalancedClient client = new BalancedClient(this, createWalletWithBalance(clientBalance));
        balancedClients.put(client.getAddress(), client);
        return client;
    }

    public BalancedClient newClient() throws Exception {
        return newClient(BigInteger.TEN.pow(24));
    }

    public BalancedClient getClient(Address address) {
        return balancedClients.get(address);
    }

    // deprecated after continuous migration
    public void syncDistributions() {
        if (ownerClient.dex.getContinuousRewardsDay() == null) {
            Consumer<TransactionResult> distributeConsumer = result -> {};
            while (!checkDistributionsDone()) {
                ownerClient.rewards.distribute(distributeConsumer);
                ownerClient.dividends.distribute(distributeConsumer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public boolean checkDistributionsDone() {
        BigInteger day = ownerClient.governance.getDay();
        Map<String, Object> status = ownerClient.rewards.distStatus();
        if (hexObjectToBigInteger(status.get("platform_day")).intValue() < day.intValue()) {
            return false;
        }

        Map<String, String> dataSourceStatus = (Map<String, String>) status.get("source_days");
        for (String sourceDay : dataSourceStatus.values()) {
            if (hexObjectToBigInteger(sourceDay).intValue() < day.intValue()) {
                return false;
            }
        }

        return true;
    }

    public void increaseDay(int nrOfDays) {
        ownerClient.governance.setTimeOffset(ownerClient.governance.getTimeOffset().subtract(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(nrOfDays))));
        ownerClient.baln.setTimeOffset();
    }
}