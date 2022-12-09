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

public class LpTransferableOnContinuousModeTest {

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static Wallet userWallet;
    private static Wallet tUserWallet;
    private static Wallet testOwnerWallet;
    private static DefaultScoreClient dexScoreClient;
    private static DefaultScoreClient governanceScoreClient;
    private static DefaultScoreClient dexTestBaseScoreClient;
    private static DefaultScoreClient dexTestFourthScoreClient;


    static DefaultScoreClient daoFund;
    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens" +
            "/DexIntTestToken.jar");

    static {
        try {
            balanced = new Balanced();
            testOwnerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            dexTestBaseScoreClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    jarfile.getPath(), Map.of("name", "Test Base Token", "symbol", "TB"));
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

    private static final String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    private static final String dexTestFourthScoreAddress = dexTestFourthScoreClient._address().toString();

    private static final Address userAddress = Address.of(userWallet);
    private static final Address tUserAddress = Address.of(tUserWallet);

    private static final DexTestScoreClient ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));
    private static final DexTestScoreClient ownerDexTestFourthScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet,
                    DefaultScoreClient.address(dexTestFourthScoreAddress));
    private static final DexScoreClient dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, dexScoreClient._address());

    private static final DexTestScoreClient userDexTestBaseScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestBaseScoreAddress));
    private static final DexTestScoreClient userDexTestFourthScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    DefaultScoreClient.address(dexTestFourthScoreAddress));
    private static final GovernanceScoreClient governanceDexScoreClient =
            new GovernanceScoreClient(governanceScoreClient);


    @Test
    @Order(4)
    void testBalnPoolTokenTransferableOnContinuousRewards() {
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        mintAndTransferTestTokens(tokenDeposit);
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress),
                Address.fromString(dexTestFourthScoreClient._address().toString()),
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), false);
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress),
                Address.fromString(dexTestFourthScoreAddress));
        //assert pool id is less than 5
        assert poolId.compareTo(BigInteger.valueOf(6)) < 0;
        BigInteger liquidity =
                (BigInteger.valueOf(50).multiply(EXA).multiply(BigInteger.valueOf(50).multiply(EXA))).sqrt();
        BigInteger balance = dexUserScoreClient.balanceOf(userAddress, poolId);
        BigInteger tUsersPrevBalance = dexUserScoreClient.balanceOf(tUserAddress, poolId);

        assertEquals(balance, liquidity);
        dexUserScoreClient.transfer(tUserAddress, BigInteger.valueOf(5).multiply(EXA), poolId, new byte[0]);
        BigInteger tUsersBalance = dexUserScoreClient.balanceOf(tUserAddress, poolId);
        assertEquals(tUsersPrevBalance.add(BigInteger.valueOf(5).multiply(EXA)), tUsersBalance);
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit) {

        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestFourthScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        //deposit base token
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        //deposit quote token
        userDexTestFourthScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added

        if (!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            dexAddQuoteCoin(new Address(dexTestBaseScoreAddress));
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

    void waitForADay() {
        balanced.increaseDay(1);
    }
}
