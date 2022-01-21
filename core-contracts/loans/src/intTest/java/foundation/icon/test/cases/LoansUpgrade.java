/*
 * Copyright 2019 ICON Foundation
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

package network.balanced.test.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcArray;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import network.balanced.test.Constants;
import network.balanced.test.Env;
import network.balanced.test.TestBase;
import network.balanced.test.TransactionHandler;
import network.balanced.test.score.Score;
import network.balanced.test.score.ChainScore;
import static network.balanced.test.util.Helpers.*;

import network.balanced.test.score.LoansScoreInTest;
import network.balanced.test.score.GovernanceInTest;
import network.balanced.test.score.RebalanceMockScore;
import network.balanced.test.score.DexMockScore;

import network.balanced.test.contracts.base.LoansScore;
import network.balanced.test.contracts.base.Governance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.io.IOException;
import java.math.BigInteger;

import static network.balanced.test.Env.LOG;
/**
 * Loans Migration test
 * 
 * Mocked contracts:
 *  Rebalance contract: is mocked so we can rebalance position without any constraints.
 *    this allows us to force postions into the different standings
 * 
 *  Dex contract: is mocked so we can trade with very smooth setup aswell as simple and predictable math.
 * 
 * Tests Structure:
 *  AdminWallet owns all contracts 
 *  The reference contracts all refeer to the ones that should be on mainnet currently Excluding the mocks
 *  All test are done in a mirrored fashion so that we can verify that all methods that are supposed
 *  to stay the same stays the same.
 * 
 *  After all mirrored test we run all external methods on the reference contracts and Save.
 *  We then upgrade it to the New contract and recheck compare all external methods to verify all data where kept.
 * 
 */
class LoansUpgrade extends TestBase {
    private static TransactionHandler txHandler;
    private static KeyWallet[] wallets;
    private static KeyWallet adminWallet;

    private static ChainScore chainScore;

    private static final BigInteger intialDexLiquiditySICX = BigInteger.valueOf(50000).multiply(ICX);
    private static final BigInteger intialDexLiquidityBnusd = BigInteger.valueOf(25000).multiply(ICX);

    private List<ExternalCall> methods;
    private Map<Integer, RpcItem> results;

    private class ExternalCall {
        public String name;
        public String compareType;
        public RpcObject params;

        public ExternalCall(String name, String compareType ) {
            this.name = name;
            this.compareType = compareType;
            this.params = null;
         }

        public ExternalCall(String name, String compareType, RpcObject params) {
           this.name = name;
           this.compareType = compareType;
           this.params = params;
        }
    }

    private static void createBalancedInstance(Governance gov) throws Exception {
        gov.setupBalanced(txHandler, adminWallet);
        Address loansAddress = gov.getLoans().getAddress();
        RebalanceMockScore rebalanceScore = RebalanceMockScore.deploy(txHandler, adminWallet, loansAddress);
        DexMockScore dexScore = DexMockScore.deploy(txHandler, adminWallet, gov.getSicx().getAddress(), gov.getBnUSD().getAddress());
       
        gov.rebalancing = rebalanceScore;
        gov.dex = dexScore;
        gov.setLoansRebalance(adminWallet, rebalanceScore.getAddress());
        gov.setLoansDex(adminWallet, dexScore.getAddress());
    } 

    private static void setupDex(Governance gov) throws Exception {
        gov.getStaking().stakeICX(adminWallet, intialDexLiquiditySICX.multiply(BigInteger.valueOf(2)));
        gov.getLoans().depositAndBorrow(adminWallet, "bnUSD", intialDexLiquidityBnusd, intialDexLiquidityBnusd.multiply(BigInteger.TEN));
        gov.getBnUSD().transfer(adminWallet, gov.getDex().getAddress(), intialDexLiquidityBnusd);
        gov.getSicx().transfer(adminWallet, gov.getDex().getAddress(), intialDexLiquiditySICX);
    }

    static void setup() throws Exception {
        Env.Chain chain = Env.getDefaultChain();
        IconService iconService = new IconService(new HttpProvider(chain.getEndpointURL(3)));
        
        txHandler = new TransactionHandler(iconService, chain);
        chainScore = new ChainScore(txHandler);

        BigInteger initalBalance =  ICX.multiply(BigInteger.TEN.pow(5));
        wallets = setupWallets(txHandler, 5, ICX.multiply(BigInteger.TEN.pow(5)));
        for (KeyWallet wallet : wallets) {
            ensureIcxBalance(txHandler, wallet.getAddress(), BigInteger.ZERO, initalBalance);
        }

        adminWallet = wallets[0];
        BigInteger icxNeededForDexSetup = BigInteger.valueOf(4).multiply(intialDexLiquiditySICX.add(intialDexLiquidityBnusd.multiply(BigInteger.TEN)));
        txHandler.transfer(adminWallet.getAddress(), icxNeededForDexSetup);
        ensureIcxBalance(txHandler, adminWallet.getAddress(), initalBalance, icxNeededForDexSetup.add(initalBalance));

        //TODO check if needed also move to governace score
        chainScore.registerPrep(adminWallet);
    }

    @Test
    void MirrorTest() throws Exception {
        setup();
        methods = new ArrayList<ExternalCall>();
        GovernanceInTest governance = GovernanceInTest.deploy(txHandler, adminWallet);
        Governance referenceGovernance = Governance.deploy(txHandler, adminWallet);

        LOG.infoEntering("Setup Balanced");
        createBalancedInstance(governance);
        LOG.infoExiting();

        LOG.infoEntering("Setup Reference Balanced");
        createBalancedInstance(referenceGovernance);
        LOG.infoExiting();

        LOG.infoEntering("Setup DEX");
        setupDex(governance);
        setupDex(referenceGovernance);
        LOG.infoExiting();

        addExternalCallsForMirror(governance.getLoans().getDay());

        LOG.infoEntering("Take Loans");
        depositAndBorrow(governance);
        depositAndBorrow(referenceGovernance);
        validateData(governance, referenceGovernance);
        LOG.infoExiting();

        LOG.infoEntering("Return Assets");
        returnAssets(governance);
        returnAssets(referenceGovernance);
        validateData(governance, referenceGovernance);
        LOG.infoExiting();

        LOG.infoEntering("Withdraw Collateral");
        withdrawCollateral(governance);
        withdrawCollateral(referenceGovernance);
        validateData(governance, referenceGovernance);
        LOG.infoExiting();

        LOG.infoEntering("Rebalance");
        rebalance(governance);
        rebalance(referenceGovernance);
        validateData(governance, referenceGovernance);
        LOG.infoExiting();

        LOG.infoEntering("Upgrade Contract");
        testUpgradeContract(referenceGovernance);
        LOG.infoExiting();

    }

    public void testUpgradeContract(Governance gov) throws Exception {
        addExternalCallsForUpgrade();
        callAllMethods(gov.getLoans());
        LoansScore newLoans = LoansScoreInTest.upgradeToJava(txHandler, adminWallet, gov.getAddress(), gov.getLoans().getAddress());
        callAllMethodsAndCompare(newLoans);
    }

    public void depositAndBorrow(Governance gov) throws Exception {
        takeLoan(gov, wallets[1], 500, 5000);
        takeLoan(gov, wallets[2], 600, 5000);
        takeLoan(gov, wallets[3], 700, 5000);
        takeLoan(gov, wallets[4], 800, 5000);

        addMethodsForPosition(gov, wallets[1]);
        addMethodsForPosition(gov, wallets[2]);
        addMethodsForPosition(gov, wallets[3]);
        addMethodsForPosition(gov, wallets[4]);
    }

    void returnAssets(Governance gov) throws Exception {
        gov.getLoans().returnAsset(wallets[1], "bnUSD", ICX.multiply(BigInteger.valueOf(100)), true);
        gov.getLoans().returnAsset(wallets[2], "bnUSD", BigInteger.ZERO, true);
        gov.getLoans().returnAsset(wallets[2], "sICX", ICX.multiply(BigInteger.valueOf(100)), true);
        gov.getLoans().returnAsset(wallets[2], "bnUSD", ICX.multiply(BigInteger.valueOf(1000)), true);
    }

    void withdrawCollateral(Governance gov) throws Exception {
        gov.getLoans().withdrawCollateral(wallets[3], ICX.multiply(BigInteger.valueOf(100)));
    }

    void rebalance(Governance gov) throws Exception {
        RebalanceMockScore rebalance = ((RebalanceMockScore) gov.getRebalance());
        rebalance.raisePrice(adminWallet, ICX.multiply(BigInteger.valueOf(1000)));
     
    }

    @AfterAll
    static void shutdown() throws Exception {
        //TODO cleanupPreps
        for (KeyWallet wallet : wallets) {
            txHandler.refundAll(wallet);
        }
    }

    private void takeLoan(Governance gov, KeyWallet wallet, int loan, int value) throws Exception {
        gov.getLoans().depositAndBorrow(wallet, "bnUSD", ICX.multiply(BigInteger.valueOf(loan)), ICX.multiply(BigInteger.valueOf(value)));
    }  
    
    private void addMethodsForPosition(Governance gov, KeyWallet wallet) throws Exception {
        RpcObject _ownerParams = new RpcObject.Builder()
            .put("_owner", new RpcValue(wallet.getAddress()))
            .build();
        methods.add(new ExternalCall("getAccountPositions", "asObject", _ownerParams));
        methods.add(new ExternalCall("hasDebt", "asValue", _ownerParams));

        RpcObject _addressParams = new RpcObject.Builder()
            .put("_address", new RpcValue(wallet.getAddress()))
            .build();
        methods.add(new ExternalCall("getPositionStanding", "asObject", _addressParams));

        BigInteger positionIndex =  gov.getLoans().getPositionIndex(wallet.getAddress());
        RpcObject _indexParams = new RpcObject.Builder()
            .put("_index", new RpcValue(positionIndex))
            .build();
        methods.add(new ExternalCall("getPositionAddress", "asValue", _indexParams));

        RpcObject _index_dayParams = new RpcObject.Builder()
            .put("_index", new RpcValue(positionIndex))
            .put("_day", new RpcValue(BigInteger.valueOf(-1)))
            .build();
        methods.add(new ExternalCall("getPositionByIndex", "asObject", _index_dayParams));
    }

    private void addExternalCallsForMirror(BigInteger day) throws Exception {
        methods.add(new ExternalCall("getDay", "asValue"));
        methods.add(new ExternalCall("name", "asValue"));
        methods.add(new ExternalCall("getDistributionsDone", "asObject"));
        methods.add(new ExternalCall("checkDeadMarkets", "asArray"));
        methods.add(new ExternalCall("getNonzeroPositionCount", "asValue"));
        methods.add(new ExternalCall("getTotalCollateral", "asValue"));
        methods.add(new ExternalCall("assetCount", "asValue"));
        methods.add(new ExternalCall("borrowerCount", "asValue"));

        RpcObject getSnapshotParams = new RpcObject.Builder()
            .put("_snap_id", new RpcValue(day))
            .build();
        methods.add(new ExternalCall("getSnapshot", "asObject", getSnapshotParams));

        RpcObject getTotalValueParams = new RpcObject.Builder()
            .put("_name", new RpcValue(""))
            .put("_snapshot_id", new RpcValue(day))
            .build();
        methods.add(new ExternalCall("getTotalValue", "asValue", getTotalValueParams));

        RpcObject batchParams = new RpcObject.Builder()
            .put("_name", new RpcValue("loans"))
            .put("_snapshot_id", new RpcValue(BigInteger.valueOf(-1)))
            .put("_limit", new RpcValue(BigInteger.valueOf(10)))
            .build();
        methods.add(new ExternalCall("getDataBatch", "asObject", batchParams));
    }

    private void addExternalCallsForUpgrade() throws Exception {
        methods.add(new ExternalCall("getParameters", "asObject"));
        methods.add(new ExternalCall("getDay", "asValue"));
        methods.add(new ExternalCall("getAssetTokens", "asObject"));
        methods.add(new ExternalCall("getCollateralTokens", "asObject"));
        methods.add(new ExternalCall("getAvailableAssets", "asObject"));
    }

    private void validateData(Governance gov, Governance referenceGov) throws Exception {
        for (ExternalCall method : methods) {
            try {
                RpcItem result = gov.getLoans().call(method.name, method.params);
                RpcItem referenceResult = referenceGov.getLoans().call(method.name, method.params);
                compareItems(referenceResult, result, method.compareType);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.info("failed with method "+ method.name +" " + e);
                assertTrue(false);
            }
        }
    }

    private void callAllMethods(LoansScore loans) throws Exception {
        results = new HashMap<>();
        for (ExternalCall method : methods) {
            try {
                RpcItem res = loans.call(method.name, method.params);
                results.put(method.hashCode(), res);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.info("failed with method "+ method.name +" " + e);
                assertTrue(false);
            }
        }
    }

    private void callAllMethodsAndCompare(LoansScore loans) throws Exception {
        for (ExternalCall method : methods) {
            LOG.info("Compare method: " + method.name);
            try {
                RpcItem res = loans.call(method.name, method.params);
                compareItems(results.get(method.hashCode()), res, method.compareType);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.info("failed with method "+ method.name +" " + e);
                assertTrue(false);
            }
        }
    }

    private void compareItems(RpcItem item1, RpcItem item2, String type) {
        switch (type) {
            case "asObject":
                assertEquals(
                    stripTimeData(item1.asObject().toString()), 
                    stripTimeData(item2.asObject().toString())
                );
                break;
            case "asValue":
                assertEquals(
                    item1.asValue().toString(), 
                    item2.asValue().toString()
                );
                break;
            case "asArray":
                assertEquals(
                    item1.asArray().toString(), 
                    item2.asArray().toString()
                );
                break;
            default:
                assertTrue(false);
                break;
        }
    }
    private String stripTimeData(String message) {
        String regexp = "created=([a-z0-9]*),";
        return message.replaceAll(regexp, "");
    }
}
