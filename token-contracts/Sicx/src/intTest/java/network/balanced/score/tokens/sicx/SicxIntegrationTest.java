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

package network.balanced.score.tokens.sicx;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.SicxScoreClient;
import network.balanced.score.lib.interfaces.Staking;
import network.balanced.score.lib.interfaces.StakingScoreClient;
import network.balanced.score.lib.test.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SicxIntegrationTest implements ScoreIntegrationTest {
    private static final Wallet tester = ScoreIntegrationTest.getOrGenerateWallet(null);
    private static final Address testerAddress = Address.of(tester);

    private static final Wallet owner = ScoreIntegrationTest.getOrGenerateWallet(System.getProperties());
    private static final Address ownerAddress = Address.of(owner);

    private static final DefaultScoreClient sicxClient = DefaultScoreClient.of(System.getProperties(), Map.of("_admin"
            , ownerAddress));
    private static final DefaultScoreClient stakingClient = DefaultScoreClient.of("staking.", System.getProperties());

    @ScoreClient
    private static final Staking stakingScore = new StakingScoreClient(stakingClient);

    @ScoreClient
    private final Sicx sicxScore = new SicxScoreClient(sicxClient);

    @Test
    @Order(1)
    void name() {
        System.setProperty("scoreFilePath", "../../token-contracts/sicx/build/libs/sicx-0.1.0-optimized.jar");
        System.setProperty("isUpdate", "true");
        System.setProperty("address", String.valueOf(sicxClient._address()));
        DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));
        assertEquals("sICX", sicxScore.getPeg());
        stakingScore.toggleStakingOn();
        stakingScore.setSicxAddress(sicxClient._address());
    }

    @Test
    @Order(2)
    void getSymbol() {
        assertEquals("sICX", sicxScore.symbol());
    }

    @Test
    @Order(3)
    void setAndGetStaking() {
        sicxScore.setStaking(stakingClient._address());
        assertEquals(stakingClient._address(), sicxScore.getStaking());

    }

    @Test
    @Order(4)
    void setMinterAddress() {
        sicxScore.setMinter(stakingClient._address());
        assertEquals(stakingClient._address(), sicxScore.getMinter());
    }

    @Test
    @Order(5)
    void mint() {
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousBalance = sicxScore.balanceOf(ownerAddress);
        ((StakingScoreClient) stakingScore).stakeICX(value, null, null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousBalance.add(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(6)
    void transfer() {
        BigInteger value = BigInteger.valueOf(5).multiply(EXA);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        sicxScore.transfer(testerAddress, value, null);
        assertEquals(previousSupply, sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(7)
    void burn() {
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);

        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        BigInteger value = BigInteger.TEN.multiply(EXA);
        sicxScore.transfer(stakingClient._address(), value, data.toString().getBytes());
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance, sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(8)
    void mintTo() {
        sicxScore.setMinter(ownerAddress);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        sicxScore.mintTo(testerAddress, value, null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(9)
    void burnFrom() {
        sicxScore.setMinter(ownerAddress);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        BigInteger value = BigInteger.TEN.multiply(EXA);
        sicxScore.burnFrom(testerAddress, value);
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.subtract(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(ownerAddress));
    }

}
