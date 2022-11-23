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
import network.balanced.score.lib.interfaces.*;
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
import static org.junit.jupiter.api.Assertions.assertNull;

public class MultipleAddTest {

    static StakingScoreClient staking;
    static LoansScoreClient loans;
    static RewardsScoreClient rewards;
    static SicxScoreClient sicx;
    static StakedLPScoreClient stakedLp;
    static BalancedTokenScoreClient baln;

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Wallet userWallet;
    private static Wallet testOwnerWallet;
    private static Balanced balanced;
    private static DefaultScoreClient dexScoreClient;
    private static DefaultScoreClient governanceScoreClient;
    private static DefaultScoreClient dexTestThirdScoreClient;
    private static DefaultScoreClient dexTestFourthScoreClient;


    static DefaultScoreClient daoFund;
    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens" +
            "/DexIntTestToken.jar");

    static {
        try {
            balanced = new Balanced();
            testOwnerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            Wallet tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            dexTestThirdScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Third Token", "symbol", "TTD"));
            dexTestFourthScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Fourth Token", "symbol", "TFD"));
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: " + e.getMessage());
        }

    }

    private static final String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();
    private static final String dexTestFourthScoreAddress = dexTestFourthScoreClient._address().toString();

    private static final Address userAddress = Address.of(userWallet);

    private static final DexTestScoreClient ownerDexTestThirdScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    DefaultScoreClient.address(dexTestThirdScoreAddress));
    private static final DexTestScoreClient ownerDexTestFourthScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    DefaultScoreClient.address(dexTestFourthScoreAddress));
    private static final DexScoreClient dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, dexScoreClient._address());
    private static final DexTestScoreClient userDexTestThirdScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestThirdScoreAddress));
    private static final DexTestScoreClient userDexTestFourthScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestFourthScoreAddress));
    private static final GovernanceScoreClient governanceDexScoreClient =
            new GovernanceScoreClient(governanceScoreClient);

    @Test
    @Order(4)
    void testMultipleAdd() {
        //testMultipleAdd
        BigInteger previousUserBalance = ownerDexTestFourthScoreClient.balanceOf(userAddress);
        BigInteger previousSecondUserBalance = ownerDexTestThirdScoreClient.balanceOf(userAddress);
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();

        this.mintAndTransferTestTokens(tokenDeposit);
        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress),
                Address.fromString(dexTestFourthScoreAddress), BigInteger.valueOf(50).multiply(EXA),
                BigInteger.valueOf(50).multiply(EXA), true);
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress),
                Address.fromString(dexTestFourthScoreAddress));
        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestFourthScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);

        // after lp is added to the pool, remaining balance is checked
        assertEquals(previousUserBalance.add(BigInteger.valueOf(150).multiply(EXA)),
                ownerDexTestFourthScoreClient.balanceOf(userAddress));
        assertEquals(previousSecondUserBalance.add(BigInteger.valueOf(150).multiply(EXA)),
                ownerDexTestThirdScoreClient.balanceOf(userAddress));

        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress),
                Address.fromString(dexTestFourthScoreAddress), BigInteger.valueOf(80).multiply(EXA),
                BigInteger.valueOf(60).multiply(EXA), true);

        // after lp is added to the pool, remaining balance is checked
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestFourthScoreClient.balanceOf(userAddress));
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestThirdScoreClient.balanceOf(userAddress));

        poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress),
                Address.fromString(dexTestFourthScoreAddress));
        poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestFourthScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);

        //change name and verify
        setMarketName(poolId, "DTT/DTBT");
        Map<String, Object> updatedPoolStats = dexUserScoreClient.getPoolStats(poolId);
        assertEquals(updatedPoolStats.get("name").toString(), "DTT/DTBT");
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit) {

        ownerDexTestThirdScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestFourthScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        userDexTestThirdScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        userDexTestFourthScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestThirdScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestThirdScoreAddress));
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestFourthScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestFourthScoreAddress));
        }
    }

    void dexAddQuoteCoin(Address address) {
        JsonArray addQuoteCoinParameters = new JsonArray()
                .add(createParameter(address));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "addQuoteCoin", addQuoteCoinParameters));

        balanced.ownerClient.governance.execute(actions.toString());
    }

    void setMarketName(BigInteger poolID, String name) {
        JsonArray setMarketNameParameters = new JsonArray()
                .add(createParameter(poolID))
                .add(createParameter(name));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "setMarketName", setMarketNameParameters));

        balanced.ownerClient.governance.execute(actions.toString());
    }

    BigInteger hexToBigInteger(String hex) {
        return new BigInteger(hex.replace("0x", ""), 16);
    }
}
