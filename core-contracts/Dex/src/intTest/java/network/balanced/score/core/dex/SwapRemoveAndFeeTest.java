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

package network.balanced.score.core.dex;

import com.eclipsesource.json.JsonArray;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.Dex;
import network.balanced.score.lib.interfaces.DexScoreClient;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createTransaction;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SwapRemoveAndFeeTest {

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static Wallet userWallet;
    private static Wallet testOwnerWallet;
    private static DefaultScoreClient dexScoreClient;
    private static DefaultScoreClient governanceScoreClient;
    private static DefaultScoreClient feeHandlerScoreClient;
    private static DefaultScoreClient dexIntTestScoreClient;
    private static DefaultScoreClient dexTestBaseScoreClient;
    private static DefaultScoreClient dexTestThirdScoreClient;

    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens" +
            "/DexIntTestToken.jar");

    static {
        try {
            balanced = new Balanced();
            testOwnerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            Wallet tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            dexIntTestScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Token", "symbol", "TT"));
            dexTestBaseScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Base Token", "symbol", "TB"));
            dexTestThirdScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Third Token", "symbol", "TTD"));
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            feeHandlerScoreClient = balanced.feehandler;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: " + e.getMessage());
        }

    }

    private static final String dexTestScoreAddress = dexIntTestScoreClient._address().toString();
    private static final String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    private static final String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();

    private static final Address userAddress = Address.of(userWallet);

    private static final DexTestScoreClient ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestScoreAddress));
    private static final DexTestScoreClient ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));
    private static final DexTestScoreClient ownerDexTestThirdScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    DefaultScoreClient.address(dexTestThirdScoreAddress));
    private static final Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(),
            userWallet, dexScoreClient._address());

    private static final DexTestScoreClient userDexTestScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, DefaultScoreClient.address(dexTestScoreAddress));
    private static final DexTestScoreClient userDexTestBaseScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestBaseScoreAddress));
    private static final DexTestScoreClient userDexTestThirdScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestThirdScoreAddress));
    private static final GovernanceScoreClient governanceDexScoreClient =
            new GovernanceScoreClient(governanceScoreClient);

    @Test
    @Order(5)
    void testSwapTokensVerifySendsFeeAndRemove() {
        //check balance of fee handler in from token
        BigInteger feeBalanceOfTestToken = userDexTestScoreClient.balanceOf(feeHandlerScoreClient._address());
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress),
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true, BigInteger.valueOf(100));

        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress),
                Address.fromString(dexTestScoreAddress));
        assertNotNull(poolId);
        //governanceDexScoreClient.disable_fee_handler();
        String swapString = "{\"method\":\"_swap\",\"params\":{\"toToken\":\"" + dexTestBaseScoreAddress + "\"}}";
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA),
                swapString.getBytes());
        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertEquals(poolStats.get("base_token").toString(), dexTestBaseScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()).divide(EXA), BigInteger.valueOf(16));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()).divide(EXA), BigInteger.valueOf(149));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()).divide(EXA), BigInteger.valueOf(50));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()).divide(EXA), BigInteger.valueOf(8));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);
        BigInteger updatedFeeBalanceOfTestToken = userDexTestScoreClient.balanceOf(feeHandlerScoreClient._address());
        assert updatedFeeBalanceOfTestToken.compareTo(feeBalanceOfTestToken) > 0;
        assertEquals(BigInteger.valueOf(150).multiply(EXA).divide(BigInteger.valueOf(1000)),
                updatedFeeBalanceOfTestToken);

        waitForADay();
        balanced.syncDistributions();
        BigInteger withdrawAmount = BigInteger.valueOf(5);
        BigInteger balanceBefore = dexUserScoreClient.balanceOf(userAddress, poolId);
        dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
        BigInteger balanceAfter = dexUserScoreClient.balanceOf(userAddress, poolId);
        assert balanceAfter.equals(balanceBefore.subtract(withdrawAmount));
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit) {

        ownerDexTestScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestThirdScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        //deposit base token
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        //deposit quote token
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        userDexTestThirdScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestScoreAddress));
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestBaseScoreAddress));
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestThirdScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestThirdScoreAddress));
        }
    }

    void dexAddQuoteCoin(Address address) {
        JsonArray addQuoteCoinParameters = new JsonArray()
                .add(createParameter(address));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "addQuoteCoin", addQuoteCoinParameters));

        balanced.ownerClient.governance.execute(actions.toString());
    }

    void waitForADay() {
        balanced.increaseDay(1);
    }

    BigInteger hexToBigInteger(String hex) {
        return new BigInteger(hex.replace("0x", ""), 16);
    }
}
