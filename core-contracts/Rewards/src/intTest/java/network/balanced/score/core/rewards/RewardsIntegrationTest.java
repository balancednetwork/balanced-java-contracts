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

package network.balanced.score.core.rewards;


import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient tester1;
    private static BalancedClient tester2;

    private static String dexJavaPath;

    @BeforeAll    
    static void setup() throws Exception {
        System.setProperty("Rewards", System.getProperty("python"));
        dexJavaPath = System.getProperty("Dex");
        System.setProperty("Dex", System.getProperty("dexPython"));
        
        balanced = new Balanced();
        balanced.deployBalanced();
        owner = balanced.ownerClient;

        KeyWallet tester1Wallet = createWalletWithBalance(BigInteger.TEN.pow(24));
        KeyWallet tester2Wallet = createWalletWithBalance(BigInteger.TEN.pow(24));
        tester1 = new BalancedClient(balanced, tester1Wallet);
        tester2 = new BalancedClient(balanced, tester2Wallet);

        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setVoteDefinitionFee(BigInteger.TEN.pow(10));
        owner.governance.setQuorum(BigInteger.ONE);
        owner.baln.toggleEnableSnapshot();
    }

    @Test
    void update() {
        // Add postions that generate baln
        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));

        BigInteger tester1Loan = BigInteger.TEN.pow(21);
        BigInteger tester2Loan = BigInteger.TEN.pow(21);
        tester1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester1Loan, null, null);        
        tester2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", tester2Loan, null, null);
        
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        tester1.bnUSD.transfer(balanced.dex._address(), tester1Loan, depositData.toString().getBytes());
        tester1.staking.stakeICX(BigInteger.TEN.pow(22), null, null);
        BigInteger tester1SicxDeposit = tester1.sicx.balanceOf(tester1.getAddress());

        tester1.sicx.transfer(balanced.dex._address(), tester1SicxDeposit, depositData.toString().getBytes());
        tester1.dex.add(balanced.sicx._address(), balanced.bnusd._address(), tester1SicxDeposit, tester1Loan, false);

        // Do one distribtion
        balanced.increaseDay(1);
        balanced.syncDistributions();
        
        //verify dao/reserver/bwt

        tester1.rewards.claimRewards();
        BigInteger balance = tester1.baln.balanceOf(tester1.getAddress());

        balanced.rewards._update(System.getProperty("java"), Map.of("_governance", balanced.governance._address()));
        
        // try reclaim after upgrade
        tester1.rewards.claimRewards();
        assertEquals(balance, tester1.baln.balanceOf(tester1.getAddress()));

        // claim unclaimed
        assertEquals(BigInteger.ZERO, tester2.baln.balanceOf(tester2.getAddress()));
        tester2.rewards.claimRewards();

        // assert tester 1 has more rewards from LP and loan
        // assertTrue(tester1.baln.balanceOf(tester1.getAddress()).compareTo(tester2.baln.balanceOf(tester2.getAddress())) > 0);

        // Do one distribtion
        balanced.increaseDay(1);
        balanced.syncDistributions();

        BigInteger tester1Balance = tester1.baln.balanceOf(tester1.getAddress());
        BigInteger tester2Balance = tester2.baln.balanceOf(tester2.getAddress());
        tester1.rewards.claimRewards();
        tester2.rewards.claimRewards();
        assertTrue(tester1Balance.compareTo(tester1.baln.balanceOf(tester1.getAddress())) < 0);
        assertTrue(tester2Balance.compareTo(tester2.baln.balanceOf(tester2.getAddress())) < 0);
        
        System.out.println("balnance1 " + tester1.baln.balanceOf(tester1.getAddress()));
        System.out.println("balnance1 " + tester2.baln.balanceOf(tester2.getAddress()));
        //verify dao/reserver/bwt

        // upgrade to continous
        // BigInteger day = owner.governance.getDay();

        // owner.governance.setContinuousRewardsDay(day.add(BigInteger.ONE));
    }


}
