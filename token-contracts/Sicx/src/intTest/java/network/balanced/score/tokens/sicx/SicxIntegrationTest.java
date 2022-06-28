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
import network.balanced.score.lib.interfaces.SicxScoreClient;
import network.balanced.score.lib.interfaces.StakingScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.math.BigInteger;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SicxIntegrationTest implements ScoreIntegrationTest {
    static Balanced balanced;
    static Wallet tester;
    static Wallet owner;

    static StakingScoreClient stakingScore;
    static SicxScoreClient sicxScore;

    @BeforeAll
    static void setup() throws Exception {
        tester = createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.owner;

        stakingScore = new StakingScoreClient(balanced.staking);
        sicxScore = new SicxScoreClient(balanced.sicx);
    }


    @Test
    @Order(1)
    void name() {
        assertEquals("sICX", sicxScore.getPeg());
    }

    @Test
    @Order(2)
    void getSymbol() {
        assertEquals("sICX", sicxScore.symbol());
    }

    @Test
    @Order(3)
    void setAndGetStaking() {
        sicxScore.setStaking(stakingScore._address());
        assertEquals(stakingScore._address(), sicxScore.getStaking());

    }

    @Test
    @Order(4)
    void setMinterAddress() {
        sicxScore.setMinter(stakingScore._address());
        assertEquals(stakingScore._address(), sicxScore.getMinter());
    }

    @Test
    @Order(5)
    void mint() {
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousBalance = sicxScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        stakingScore.stakeICX(value, null, null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousBalance.add(value), sicxScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(6)
    void transfer() {
        BigInteger value = BigInteger.valueOf(5).multiply(EXA);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = sicxScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        sicxScore.transfer(Address.fromString(tester.getAddress().toString()), value, null);
        assertEquals(previousSupply, sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(7)
    void burn() {
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = sicxScore.balanceOf(Address.fromString(tester.getAddress().toString()));

        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        BigInteger value = BigInteger.TEN.multiply(EXA);
        sicxScore.transfer(stakingScore._address(), value, data.toString().getBytes());
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance, sicxScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(8)
    void mintTo() {
        sicxScore.setMinter(Address.fromString(owner.getAddress().toString()));
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = sicxScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        sicxScore.mintTo(Address.fromString(tester.getAddress().toString()), value, null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(9)
    void burnFrom() {
        sicxScore.setMinter(Address.fromString(owner.getAddress().toString()));
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = sicxScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        BigInteger value = BigInteger.TEN.multiply(EXA);
        sicxScore.burnFrom(Address.fromString(tester.getAddress().toString()), value);
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.subtract(value), sicxScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

}
