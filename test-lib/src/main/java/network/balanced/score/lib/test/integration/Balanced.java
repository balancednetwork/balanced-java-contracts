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
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.utils.Names;
import foundation.icon.jsonrpc.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createSingleTransaction;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

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
    public DefaultScoreClient stability;
    public DefaultScoreClient balancedOracle;
    public Governance governanceClient;

    public Map<score.Address, BalancedClient> balancedClients;

    public Balanced() throws Exception {
        balancedClients = new HashMap<>();
        owner = createWalletWithBalance(BigInteger.TEN.pow(25));
    }

    public void setupBalanced() throws Exception {
        registerPreps();
        deployContracts();
        setupAddresses();
        increaseDay(1);
        setupContracts();
        // delegate(adminWallet);
        String className = new Exception().getStackTrace()[1].getClassName();
        // no need to set up market in staking integration test case
        if (!className.equals("network.balanced.score.core.staking.StakingIntegrationTest")) {
            setupMarkets();
        }
    }

    public void deployContracts() {
        governance = deploy(owner, "Governance", null);
        governanceClient = new GovernanceScoreClient(chain.getEndpointURL(), chain.networkId, owner, governance._address());
        String governanceParam = new JsonArray()
            .add(createParameter(governance._address()))
            .toString();

        governanceClient.deploy(getContractData("BalancedToken"), governanceParam);
        governanceClient.deploy(getContractData("WorkerToken"), governanceParam);
        governanceClient.deploy(getContractData("Dex"), governanceParam);
        governanceClient.deploy(getContractData("FeeHandler"), governanceParam);
        governanceClient.deploy(getContractData("Loans"), governanceParam);
        governanceClient.deploy(getContractData("Rebalancing"), governanceParam);
        governanceClient.deploy(getContractData("Rewards"), governanceParam);
        governanceClient.deploy(getContractData("Staking"), "[]");
        governanceClient.deploy(getContractData("BalancedDollar"), governanceParam);
        governanceClient.deploy(getContractData("DAOfund"), governanceParam);
        governanceClient.deploy(getContractData("Dividends"), governanceParam);
        governanceClient.deploy(getContractData("Reserve"), governanceParam);
        governanceClient.deploy(getContractData("Router"), governanceParam);
        governanceClient.deploy(getContractData("StakedLP"), governanceParam);
        governanceClient.deploy(getContractData("BalancedOracle"), governanceParam);

        Hash oracleTx = deployAsync(owner, "DummyOracle", null);
        Hash stakingTx = deployAsync(owner, "Staking", null);

        baln = newScoreClient(owner, governanceClient.getAddress(Names.BALN));
        bnusd = newScoreClient(owner, governanceClient.getAddress(Names.BNUSD));
        bwt = newScoreClient(owner, governanceClient.getAddress(Names.WORKERTOKEN));
        dex = newScoreClient(owner, governanceClient.getAddress(Names.DEX));
        loans = newScoreClient(owner, governanceClient.getAddress(Names.LOANS));
        rebalancing = newScoreClient(owner, governanceClient.getAddress(Names.REBALANCING));
        rewards = newScoreClient(owner, governanceClient.getAddress(Names.REWARDS));
        daofund = newScoreClient(owner, governanceClient.getAddress(Names.DAOFUND));
        dividends = newScoreClient(owner, governanceClient.getAddress(Names.DIVIDENDS));
        reserve = newScoreClient(owner, governanceClient.getAddress(Names.RESERVE));
        router = newScoreClient(owner, governanceClient.getAddress(Names.ROUTER));
        stakedLp = newScoreClient(owner, governanceClient.getAddress(Names.STAKEDLP));
        balancedOracle = newScoreClient(owner, governanceClient.getAddress(Names.BALANCEDORACLE));
        feehandler = newScoreClient(owner, governanceClient.getAddress(Names.FEEHANDLER));

        oracle = getDeploymentResult(owner, oracleTx);
        staking = getDeploymentResult(owner, stakingTx);

        String StabilityParams = new JsonArray()
            .add(createParameter(feehandler._address()))
            .add(createParameter(bnusd._address()))
            .add(createParameter(BigInteger.TEN.pow(18)))
            .add(createParameter(BigInteger.TEN.pow(18)))
            .toString();

        governanceClient.deploy(getContractData("Stability"), StabilityParams);

        Hash sicxTx = deployAsync(owner, "Sicx", Map.of("_admin", staking._address()));
        
        stability = newScoreClient(owner, governanceClient.getAddress(Names.STABILITY));
        sicx = getDeploymentResult(owner, sicxTx);

        ownerClient = new BalancedClient(this, owner);
    }

    public void setupAddresses() {
        ownerClient.governance.addExternalContract(Names.ORACLE, oracle._address());
        ownerClient.governance.addExternalContract(Names.STAKING, staking._address());
        ownerClient.governance.addExternalContract(Names.SICX, sicx._address());
        ownerClient.governance.setAdmins();
        ownerClient.governance.setContractAddresses();
    }

    public void setupContracts() {
        ownerClient.balancedOracle.getPriceInLoop((txr) -> {}, "sICX");
        ownerClient.balancedOracle.getPriceInLoop((txr) -> {}, "USD");
        ownerClient.staking.setSicxAddress(sicx._address());
        ownerClient.sicx.setMinter(staking._address());

        ownerClient.governance.setAdmin(feehandler._address(), governance._address());

        ownerClient.governance.configureBalanced();
        ownerClient.governance.launchBalanced();
    }

    public void setupMarkets() {
        ownerClient.governance.createBnusdMarket(BigInteger.valueOf(40000).multiply(BigInteger.TEN.pow(18)));
        increaseDay(1);

        BigInteger balnBalance = ownerClient.rewards.getBalnHolding(governance._address());
        BigInteger initalPoolDepths = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        ownerClient.governance.createBalnMarket(initalPoolDepths, initalPoolDepths);
        ownerClient.staking.stakeICX(initalPoolDepths.multiply(BigInteger.TWO), null, null);
        ownerClient.sicx.transfer(governance._address(), initalPoolDepths, null);
        ownerClient.governance.createBalnSicxMarket(initalPoolDepths, initalPoolDepths);
    }

    public BalancedClient newClient(BigInteger clientBalance) throws Exception {
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

    public void syncDistributions() {
        Consumer<TransactionResult> distributeConsumer = result -> {};
        ownerClient.rewards.distribute(distributeConsumer);
        ownerClient.dividends.distribute(distributeConsumer);
    }

    public void increaseDay(int nrOfDays) {
        ownerClient.governance.setTimeOffset(ownerClient.governance.getTimeOffset().subtract(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(nrOfDays))));
        JsonArray setTimeOffset = createSingleTransaction(baln._address(), "setTimeOffset", new JsonArray());

        ownerClient.governance.execute(setTimeOffset.toString());
    }
}