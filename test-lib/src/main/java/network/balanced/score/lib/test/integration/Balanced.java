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

package network.balanced.score.lib.test.integration;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.Hash;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.utils.Names;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static network.balanced.score.lib.utils.Constants.EXA;
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
    public DefaultScoreClient bBaln;
    public DefaultScoreClient balancedOracle;
    public DefaultScoreClient assetManager;
    public DefaultScoreClient savings;
    public DefaultScoreClient xcall;
    public DefaultScoreClient xcallManager;
    public DefaultScoreClient iconBurner;
    public Governance governanceClient;

    public final String ICON_NID = "0x3.ICON";

    public String ETH_NID = "0x1.ETH";
    public String ETH_ASSET_MANAGER = "0x2";
    public String ETH_BNUSD_ADDRESS = "0x3";
    public String ETH_TOKEN_ADDRESS = "0x4";
    public String ETH_TOKEN_SYMBOL = "ETHA";
    public Address ethBaseAsset;

    public String BSC_NID = "0x3.BSC";
    public String BSC_ASSET_MANAGER = "0x5";
    public String BSC_BNUSD_ADDRESS = "0x6";
    public String BSC_TOKEN_ADDRESS = "0x7";
    public String BSC_TOKEN_SYMBOL = "BNBA";
    public Address bscBaseAsset;

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
        //String className = new Exception().getStackTrace()[1].getClassName();
        // no need to set up market in staking integration test case
//        if (!className.equals("network.balanced.score.core.staking.StakingIntegrationTest")) {
//            setupMarkets();
//        }
    }

    public void deployContracts() {
        xcall = deploy(owner, "XCallMock", Map.of("networkId", ICON_NID));

        governance = deploy(owner, "Governance", null);
        governanceClient = new GovernanceScoreClient(chain.getEndpointURL(), chain.networkId, owner,
                governance._address());
        String governanceParam = new JsonArray()
                .add(createParameter(governance._address()))
                .toString();

        governanceClient.addExternalContract(Names.XCALL, xcall._address());
        governanceClient.deploy(getContractData("BalancedToken"), governanceParam);
        governanceClient.deploy(getContractData("WorkerToken"), governanceParam);
        governanceClient.deploy(getContractData("Dex"), governanceParam);
        governanceClient.deploy(getContractData("FeeHandler"), governanceParam);
        governanceClient.deploy(getContractData("Loans"), governanceParam);
        governanceClient.deploy(getContractData("Rebalancing"), governanceParam);
        governanceClient.deploy(getContractData("Rewards"), governanceParam);
        governanceClient.deploy(getContractData("BalancedDollar"), governanceParam);
        governanceClient.deploy(getContractData("DAOfund"), governanceParam);
        governanceClient.deploy(getContractData("Dividends"), governanceParam);
        governanceClient.deploy(getContractData("Reserve"), governanceParam);
        governanceClient.deploy(getContractData("Router"), governanceParam);
        governanceClient.deploy(getContractData("StakedLP"), governanceParam);
        governanceClient.deploy(getContractData("Savings"), governanceParam);
        governanceClient.deploy(getContractData("BalancedOracle"), governanceParam);
        governanceClient.deploy(getContractData("XCallManager"), governanceParam);
        governanceClient.deploy(getContractData("Burner"), governanceParam);
        governanceClient.deploy(getContractData("Stability"), governanceParam);

        String assetManagerParams = new JsonArray()
                .add(createParameter(governance._address()))
                .add(createParameter(getContractData("AssetToken")))
                .toString();
        governanceClient.deploy(getContractData("AssetManager"), assetManagerParams);

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
        assetManager = newScoreClient(owner, governanceClient.getAddress(Names.ASSET_MANAGER));
        xcallManager = newScoreClient(owner, governanceClient.getAddress(Names.XCALL_MANAGER));
        iconBurner = newScoreClient(owner, governanceClient.getAddress(Names.BURNER));
        stability = newScoreClient(owner, governanceClient.getAddress(Names.STABILITY));
        savings = newScoreClient(owner, governanceClient.getAddress(Names.SAVINGS));

        oracle = getDeploymentResult(owner, oracleTx);
        staking = getDeploymentResult(owner, stakingTx);

        JsonArray setStabilityFeeOut = new JsonArray()
            .add(createParameter(EXA));
        JsonArray setFeeOut = createSingleTransaction(stability._address(), "setFeeOut", setStabilityFeeOut);
        governanceClient.execute(setFeeOut.toString());

        String BoostedBalnParams = new JsonArray()
                .add(createParameter(governance._address()))
                .add(createParameter("bBaln"))
                .toString();

        governanceClient.deploy(getContractData("bBaln"), BoostedBalnParams);
        bBaln = newScoreClient(owner, governanceClient.getAddress(Names.BOOSTED_BALN));
        Hash sicxTx = deployAsync(owner, "Sicx", Map.of("_admin", staking._address(), "_governance", governance._address()));

        sicx = getDeploymentResult(owner, sicxTx);

        setupSpoke(BSC_NID, BSC_ASSET_MANAGER, BSC_BNUSD_ADDRESS, BSC_TOKEN_ADDRESS, BSC_TOKEN_SYMBOL, BigInteger.valueOf(18));
        setupSpoke(ETH_NID, ETH_ASSET_MANAGER, ETH_BNUSD_ADDRESS, ETH_TOKEN_ADDRESS, ETH_TOKEN_SYMBOL, BigInteger.valueOf(18));

        ownerClient = new BalancedClient(this, owner);
        bscBaseAsset = new Address(ownerClient.assetManager.getAssetAddress(new NetworkAddress(BSC_NID, BSC_TOKEN_ADDRESS).toString()).toString());
        ethBaseAsset = new Address(ownerClient.assetManager.getAssetAddress(new NetworkAddress(ETH_NID, ETH_TOKEN_ADDRESS).toString()).toString());
        transfer(daofund._address(), BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)));

        addXCallFeePermission(loans._address(), BSC_NID, true);
        addXCallFeePermission(loans._address(), ETH_NID, true);
        addXCallFeePermission(router._address(), BSC_NID, true);
        addXCallFeePermission(router._address(), ETH_NID, true);

        JsonArray setProtocolParams = new JsonArray()
            .add(createParameter(ICON_NID))
            .add(createParameter("String[]", new JsonArray()))
            .add(createParameter("String[]", new JsonArray()));
        JsonArray setProtocol = createSingleTransaction(xcallManager._address(), "configureProtocols", setProtocolParams);
        governanceClient.execute(setProtocol.toString());
    }

    public void addXCallFeePermission(Address contract, String net, boolean permission) {
        JsonArray params = new JsonArray()
            .add(createParameter(contract))
            .add(createParameter(net))
            .add(createParameter(permission));
        JsonArray tx = createSingleTransaction(daofund._address(), "setXCallFeePermission", params);
        governanceClient.execute(tx.toString());
    }

    public void setupSpoke(String nid, String spokeAssetManager, String spokeBnUSd, String tokenAddress, String tokenSymbol, BigInteger decimals) {
        JsonArray addAssetManagerParam = new JsonArray().add(createParameter(new NetworkAddress(nid, spokeAssetManager).toString()));
        JsonObject addAssetManager = createTransaction(assetManager._address(), "addSpokeManager", addAssetManagerParam);

        JsonArray addAssetParams = new JsonArray()
                .add(createParameter(new NetworkAddress(nid, tokenAddress).toString()))
                .add(createParameter(tokenSymbol))
                .add(createParameter(tokenSymbol))
                .add(createParameter(decimals));
        JsonObject addAsset = createTransaction(assetManager._address(), "deployAsset", addAssetParams);

        JsonArray addBnUSDParams = new JsonArray()
                .add(createParameter(new NetworkAddress(nid, spokeBnUSd).toString()))
                .add(createParameter(BigInteger.TEN.pow(28)));
        JsonObject addBnUSD = createTransaction(bnusd._address(), "addChain", addBnUSDParams);

        JsonArray setProtocolParams = new JsonArray()
            .add(createParameter(nid))
            .add(createParameter("String[]", new JsonArray()))
            .add(createParameter("String[]", new JsonArray()));
        JsonObject setProtocol = createTransaction(xcallManager._address(), "configureProtocols", setProtocolParams);

        JsonArray transactions = new JsonArray()
                .add(addAssetManager)
                .add(addAsset)
                .add(addBnUSD)
                .add(setProtocol);
        governanceClient.execute(transactions.toString());
    }

    public void setupAddresses() {
        ownerClient.governance.addExternalContract(Names.ORACLE, oracle._address());
        ownerClient.governance.addExternalContract(Names.STAKING, staking._address());
        ownerClient.governance.addExternalContract(Names.SICX, sicx._address());
        ownerClient.governance.setAdmins();
        ownerClient.governance.setContractAddresses();
    }

    public void setupContracts() {
        ownerClient.balancedOracle.getPriceInLoop((txr) -> {
        }, "sICX");
        ownerClient.balancedOracle.getPriceInLoop((txr) -> {
        }, "USD");
        ownerClient.staking.setSicxAddress(sicx._address());
        ownerClient.sicx.setMinter(staking._address());

        ownerClient.governance.configureBalanced();
        ownerClient.governance.launchBalanced();
    }

    public void setupMarkets() {
        ownerClient.governance.createBnusdMarket(BigInteger.valueOf(40000).multiply(BigInteger.TEN.pow(18)));
        increaseDay(1);

        BigInteger initialPoolDepths = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        ownerClient.governance.createBalnMarket(initialPoolDepths, initialPoolDepths);
        ownerClient.staking.stakeICX(initialPoolDepths.multiply(BigInteger.TWO), null, null);
        ownerClient.sicx.transfer(governance._address(), initialPoolDepths, null);
        ownerClient.governance.createBalnSicxMarket(initialPoolDepths, initialPoolDepths);
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
        Consumer<TransactionResult> distributeConsumer = result -> {
        };
        ownerClient.rewards.distribute(distributeConsumer);
        ownerClient.dividends.distribute(distributeConsumer);
    }

    public void increaseDay(int nrOfDays) {
        ownerClient.governance.setTimeOffset(ownerClient.governance.getTimeOffset().subtract(MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(nrOfDays))));
        JsonArray setTimeOffset = createSingleTransaction(baln._address(), "setTimeOffset", new JsonArray());

        ownerClient.governance.execute(setTimeOffset.toString());
    }
}
