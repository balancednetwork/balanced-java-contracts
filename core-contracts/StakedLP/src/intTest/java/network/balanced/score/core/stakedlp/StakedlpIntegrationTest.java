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

package network.balanced.score.core.stakedlp;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.DexScoreClient;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.StakedLPScoreClient;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.*;
import score.ByteArrayObjectWriter;
import score.Context;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class StakedlpIntegrationTest {
    private static BalancedClient owner;
    static Balanced balanced;
    private static Wallet userWallet;
    static StakedLPScoreClient stakedlp;
    static StakedLPScoreClient stakedlpUser;
    static DexScoreClient dex;
    static GovernanceScoreClient governance;
    private static DefaultScoreClient tokenAClient;
    private static DefaultScoreClient tokenBClient;
    static DexScoreClient dexScoreClient;

    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/stakedlp/testtokens" +
            "/DexIntTestToken.jar");

    static DexTestScoreClient ownerDexTestScoreClient;
    static DexTestScoreClient ownerDexTestBaseScoreClient;


    static {
        try {
            userWallet = createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            balanced = new Balanced();
            balanced.setupBalanced();
            owner = balanced.ownerClient;

            stakedlp = new StakedLPScoreClient(balanced.stakedLp);
            dex = new DexScoreClient(balanced.dex);
            governance = new GovernanceScoreClient(balanced.governance);
            DefaultScoreClient clientWithTester3 = new DefaultScoreClient("http://localhost:9082/api/v3",
                    BigInteger.valueOf(3), userWallet, balanced.dex._address());

            DefaultScoreClient clientWithTester4 = new DefaultScoreClient("http://localhost:9082/api/v3",
                    BigInteger.valueOf(3), userWallet, balanced.stakedLp._address());
            tokenAClient = _deploy(chain.getEndpointURL(), chain.networkId, balanced.owner, jarfile.getPath(),
                    Map.of("name", "Test Token", "symbol", "TT"));
            tokenBClient = _deploy(chain.getEndpointURL(), chain.networkId, balanced.owner, jarfile.getPath(),
                    Map.of("name", "Test Base Token", "symbol", "TB"));
            dexScoreClient = new DexScoreClient(clientWithTester3);
            stakedlpUser = new StakedLPScoreClient(clientWithTester4);

            ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
                    chain.networkId, balanced.owner, tokenAClient._address());
            ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
                    chain.networkId, balanced.owner, tokenBClient._address());
        }catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    void testStakeAndUnstake() {
        Address userAddress = Address.of(userWallet);
        DexTestScoreClient userDexTestScoreClient = new DexTestScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), userWallet, tokenAClient._address());
        DexTestScoreClient userDexTestBaseScoreClient = new DexTestScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), userWallet, tokenBClient._address());

        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();

        // mint test token to userAddress
        ownerDexTestScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));

        // depositing test token to dex
        userDexTestBaseScoreClient.transfer(dex._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        userDexTestScoreClient.transfer(dex._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);

        // add quote coins
        dexAddQuoteCoin(tokenAClient._address());
        dexAddQuoteCoin(tokenBClient._address());

        // add tokens on dex and receive lp
        dexScoreClient.add(tokenAClient._address(), tokenBClient._address(), BigInteger.valueOf(190).multiply(EXA),
                BigInteger.valueOf(190).multiply(EXA), false, BigInteger.valueOf(100));

        BigInteger poolId = dex.getPoolId(tokenAClient._address(), tokenBClient._address());
        BigInteger balance = dex.balanceOf(userAddress, poolId);

        //set name
        addNewDataSource("test", poolId, BigInteger.ONE);

        assertEquals(dex.balanceOf(userAddress, poolId), balance);
        assertEquals(stakedlp.balanceOf(userAddress, poolId), BigInteger.ZERO);
        assertEquals(stakedlp.totalStaked(BigInteger.valueOf(5)), BigInteger.ZERO);

        // stake lp to stakedlp contract
        dexScoreClient.transfer(Address.fromString(stakedlp._address().toString()), balance,
                poolId, null);

        assertEquals(dex.balanceOf(userAddress, poolId), BigInteger.ZERO);
        assertEquals(stakedlp.balanceOf(userAddress, poolId), balance);
        assertEquals(stakedlp.totalStaked(poolId), balance);

        BigInteger toUnstake = BigInteger.valueOf(100).multiply(EXA);
        BigInteger remaining = balance.subtract(toUnstake);

        // unstakes lp token partially
        stakedlpUser.unstake(poolId, toUnstake);

        assertEquals(dex.balanceOf(userAddress, poolId), toUnstake);
        assertEquals(stakedlp.balanceOf(userAddress, poolId), remaining);
        assertEquals(stakedlp.totalStaked(poolId), remaining);

        // unstakes lp token completely
        stakedlpUser.unstake(poolId, remaining);

        assertEquals(dex.balanceOf(userAddress, poolId), balance);
        assertEquals(stakedlp.balanceOf(userAddress, poolId), BigInteger.ZERO);
        assertEquals(stakedlp.totalStaked(poolId), BigInteger.ZERO);

        // stakes lp to stakedlp contract
        dexScoreClient.transfer(Address.fromString(stakedlp._address().toString()), remaining,
                poolId, null);

        assertEquals(dex.balanceOf(userAddress, poolId), balance.subtract(remaining));
        assertEquals(stakedlp.balanceOf(userAddress, poolId), remaining);
        assertEquals(stakedlp.totalStaked(poolId), remaining);
    }

    @Test
    @Order(2)
    void crossChainStakeAndUnstake() {
        // Arrange
        NetworkAddress ethBaseAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        NetworkAddress ethQuoteAssetAddress = new NetworkAddress(balanced.ETH_NID, "ox100");
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.valueOf(100).multiply(EXA);
        String toNetworkAddress = new NetworkAddress(balanced.ICON_NID, dexScoreClient._address()).toString();
        String stakingAddress = new NetworkAddress(balanced.ICON_NID, stakedlp._address()).toString();

        // Arrange - deploy a new token
        JsonArray addAssetParams = new JsonArray()
                .add(createParameter(ethQuoteAssetAddress.toString()))
                .add(createParameter("ETHZ"))
                .add(createParameter("ETHZ"))
                .add(createParameter(BigInteger.valueOf(18)));
        JsonObject addAsset = createTransaction(balanced.assetManager._address(), "deployAsset", addAssetParams);
        JsonArray transactions = new JsonArray()
                .add(addAsset);
        balanced.governanceClient.execute(transactions.toString());

        score.Address baseAssetAddress = owner.assetManager.getAssetAddress(ethBaseAssetAddress.toString());
        score.Address quoteAssetAddress = owner.assetManager.getAssetAddress(ethQuoteAssetAddress.toString());

        // Arrange - deposits
        byte[] deposit1 = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), toNetworkAddress,  amount, tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString())));
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit1);

        byte[] deposit2 = AssetManagerMessages.deposit("ox100", ethAccount.account(), toNetworkAddress,  amount, tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString())));
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit2);

        // Arrange add quote token
        dexAddQuoteCoin((Address) quoteAssetAddress);

        // Arrange - add pool
        byte[] xaddMessage = getAddLPData(baseAssetAddress, quoteAssetAddress, amount, amount, false, BigInteger.valueOf(5) );
        owner.xcall.recvCall(dexScoreClient._address(), ethAccount.toString(), xaddMessage);

        // Act - stake
        BigInteger poolId = dexScoreClient.getPoolId(baseAssetAddress,
                quoteAssetAddress);
        try{
            addNewDataSource("test1", poolId, BigInteger.ONE);
        } catch (Exception ignored){}
        BigInteger balance = dexScoreClient.xBalanceOf(ethAccount.toString(), poolId);
        byte[] stakeData = getStakeData(stakingAddress, balance, poolId, "stake".getBytes());
        owner.xcall.recvCall(owner.dex._address(), ethAccount.toString(), stakeData);

        // Verify stake
        assertEquals(dex.xBalanceOf(ethAccount.toString(), poolId), BigInteger.ZERO);
        assertEquals(stakedlp.xBalanceOf(ethAccount.toString(), poolId), balance);
        assertEquals(stakedlp.totalStaked(poolId), balance);

        //act - unstake
        BigInteger remainingStake = balance.divide(BigInteger.TWO);
        byte[] unstakeData = getUnStakeData(poolId, remainingStake);
        owner.xcall.recvCall(owner.stakedLp._address(), ethAccount.toString(), unstakeData);

        // Verify unstake
        assertEquals(dex.xBalanceOf(ethAccount.toString(), poolId), remainingStake);
        assertEquals(stakedlp.xBalanceOf(ethAccount.toString(), poolId), remainingStake);
        assertEquals(stakedlp.totalStaked(poolId), remainingStake);
    }

    static byte[] getStakeData(String to, BigInteger amount, BigInteger poolId, byte[] data) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(5);
        writer.write("xhubtransfer");
        writer.write(to);
        writer.write(amount);
        writer.write(poolId);
        writer.write(data);
        writer.end();
        return writer.toByteArray();
    }

    static byte[] getUnStakeData(BigInteger poolId, BigInteger amount) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(3);
        writer.write("xunstake");
        writer.write(poolId);
        writer.write(amount);
        writer.end();
        return writer.toByteArray();
    }

    private void addNewDataSource(String name, BigInteger id, BigInteger type) {
        JsonArray lpSourceParameters = new JsonArray()
                .add(createParameter(id))
                .add(createParameter(name));

        JsonArray rewardsSourceParameters = new JsonArray()
                .add(createParameter(name))
                .add(createParameter(balanced.stakedLp._address()))
                .add(createParameter(type));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.stakedLp._address(), "addDataSource", lpSourceParameters))
                .add(createTransaction(balanced.rewards._address(), "createDataSource", rewardsSourceParameters));

        balanced.ownerClient.governance.execute(actions.toString());
    }

    static byte[] getAddLPData(score.Address baseToken, score.Address quoteToken, BigInteger baseValue, BigInteger quoteValue, Boolean withdraw_unused, BigInteger slippagePercentage) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(7);
        writer.write("xadd");
        writer.write(baseToken);
        writer.write(quoteToken);
        writer.write(baseValue);
        writer.write(quoteValue);
        writer.write(withdraw_unused);
        writer.write(slippagePercentage);
        writer.end();
        return writer.toByteArray();
    }

    public static byte[] tokenData(String method, JsonObject params) {
        JsonObject data = new JsonObject();
        data.set("method", method);
        data.set("params", params);
        return data.toString().getBytes();
    }

    private void dexAddQuoteCoin(Address address) {
        JsonArray params = new JsonArray()
                .add(createParameter(address));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "addQuoteCoin", params));

        balanced.ownerClient.governance.execute(actions.toString());
    }
}
