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
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.DexScoreClient;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.StakedLPScoreClient;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import org.junit.jupiter.api.*;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StakedlpIntegrationTest {
    static KeyWallet owner;
    static Balanced balanced;
    private static Wallet userWallet;
    static StakedLPScoreClient stakedlp;
    static StakedLPScoreClient stakedlpUser;
    static DexScoreClient dex;
    static GovernanceScoreClient governance;
    private static DefaultScoreClient tokenAClient;
    private static DefaultScoreClient tokenBClient;
    static DexScoreClient testerScoreDex;

    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/stakedlp/testtokens" +
            "/DexIntTestToken.jar");

    DexTestScoreClient ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, owner, tokenAClient._address());
    DexTestScoreClient ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, owner, tokenBClient._address());


    @BeforeAll
    static void setup() throws Exception {

        userWallet = createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
        balanced = new Balanced();
        balanced.setupBalanced();


        owner = balanced.owner;

        stakedlp = new StakedLPScoreClient(balanced.stakedLp);
        dex = new DexScoreClient(balanced.dex);
        governance = new GovernanceScoreClient(balanced.governance);
        DefaultScoreClient clientWithTester3 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), userWallet, balanced.dex._address());

        DefaultScoreClient clientWithTester4 = new DefaultScoreClient("http://localhost:9082/api/v3",
                BigInteger.valueOf(3), userWallet, balanced.stakedLp._address());
        tokenAClient = _deploy(chain.getEndpointURL(), chain.networkId, owner, jarfile.getPath(),
                Map.of("name", "Test Token", "symbol", "TT"));
        tokenBClient = _deploy(chain.getEndpointURL(), chain.networkId, owner, jarfile.getPath(),
                Map.of("name", "Test Base Token", "symbol", "TB"));
        testerScoreDex = new DexScoreClient(clientWithTester3);
        stakedlpUser = new StakedLPScoreClient(clientWithTester4);

    }

    Address userAddress = Address.of(userWallet);


    @Test
    @Order(1)
    void testStakeAndUnstake() {

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
        testerScoreDex.add(tokenAClient._address(), tokenBClient._address(), BigInteger.valueOf(190).multiply(EXA),
                BigInteger.valueOf(190).multiply(EXA), false);

        BigInteger poolId = dex.getPoolId(tokenAClient._address(), tokenBClient._address());
        BigInteger balance = dex.balanceOf(userAddress, poolId);

        // init rewards weight controller
        BigInteger day = balanced.ownerClient.governance.getDay();
        JsonArray setMigrateToVotingDayParameters = new JsonArray()
                .add(createParameter(day.add(BigInteger.TEN)));
        JsonArray actions = createSingleTransaction(balanced.rewards._address(), "setMigrateToVotingDay",
                setMigrateToVotingDayParameters);
        balanced.ownerClient.governance.execute(actions.toString());

        //set name
        addNewDataSource("test", poolId, BigInteger.ONE);

        assertEquals(dex.balanceOf(userAddress, poolId), balance);
        assertEquals(stakedlp.balanceOf(userAddress, poolId), BigInteger.ZERO);
        assertEquals(stakedlp.totalStaked(BigInteger.valueOf(5)), BigInteger.ZERO);

        // stake lp to stakedlp contract
        testerScoreDex.transfer(Address.fromString(stakedlp._address().toString()), balance,
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
        testerScoreDex.transfer(Address.fromString(stakedlp._address().toString()), remaining,
                poolId, null);

        assertEquals(dex.balanceOf(userAddress, poolId), balance.subtract(remaining));
        assertEquals(stakedlp.balanceOf(userAddress, poolId), remaining);
        assertEquals(stakedlp.totalStaked(poolId), remaining);

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

    private void dexAddQuoteCoin(Address address) {
        JsonArray params = new JsonArray()
                .add(createParameter(address));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "addQuoteCoin", params));

        balanced.ownerClient.governance.execute(actions.toString());
    }
}
