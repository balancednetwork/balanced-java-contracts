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

package network.balanced.score.tokens.balancedtoken;

import com.eclipsesource.json.JsonArray;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import network.balanced.score.lib.interfaces.BalancedTokenScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createSingleTransaction;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BalancedTokenImplIntTest {

    private static Balanced balanced;
    private static Wallet tester;
    private static Wallet owner;

    private static BalancedTokenScoreClient balnScore;

    @BeforeAll
    static void setup() throws Exception {
        tester = ScoreIntegrationTest.createWalletWithBalance(BigInteger.TEN.pow(24));
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.owner;

        balnScore = new BalancedTokenScoreClient(balanced.baln);
    }

    @Test
    @Order(1)
    void testName() {
        assertEquals("Balance Token", balnScore.name());
    }

    @Test
    @Order(2)
    void ShouldAUserMintAndTransferAndMakeStake() {
        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        balanced.ownerClient.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        balanced.increaseDay(1);
        balanced.syncDistributions();

        // balance token is minted in owner address
        balanced.ownerClient.rewards.claimRewards(null);
        BigInteger amountToMint = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));

        //transfer some tokens to another user
        BigInteger amountToTransferToReceiver = amountToMint.divide(BigInteger.TWO);
        BigInteger amountRemaining = amountToMint.subtract(amountToTransferToReceiver);

        balnScore.transfer(Address.fromString(tester.getAddress().toString()), amountToTransferToReceiver,
                "mole".getBytes());
        assertEquals(amountRemaining,
                balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
        assertEquals(amountToTransferToReceiver,
                balnScore.balanceOf(Address.fromString(tester.getAddress().toString())));

        BigInteger amountToStake = amountRemaining.divide(BigInteger.TWO);
        BigInteger unstakedBalance = amountRemaining.subtract(amountToStake);

        // stake some token
        balnScore.stake(amountToStake);

        Map<String, BigInteger> detailsBalanceOf =
                balnScore.detailsBalanceOf(Address.fromString(owner.getAddress().toString()));

        // assert if balance is staked or not.
        assertEquals(detailsBalanceOf.get("Staked balance"), amountToStake);
        assertEquals(detailsBalanceOf.get("Available balance"), unstakedBalance);
        assertEquals(detailsBalanceOf.get("Unstaking balance"), BigInteger.ZERO);

        //unstake some tokens
        BigInteger remainingStake = amountToStake.divide(BigInteger.TWO);
        BigInteger unstakeAmount = amountToStake.subtract(remainingStake);
        balnScore.stake(remainingStake);

        detailsBalanceOf = balnScore.detailsBalanceOf(Address.fromString(owner.getAddress().toString()));

        // assert if balance is unstaked or not.
        assertEquals(detailsBalanceOf.get("Staked balance"), remainingStake);
        assertEquals(detailsBalanceOf.get("Available balance"), unstakedBalance);
        assertEquals(detailsBalanceOf.get("Unstaking balance"), unstakeAmount);

        BigInteger totalBalance = balnScore.totalStakedBalance();
        // assert totalStakedBalance
        assertEquals(amountToStake.divide(BigInteger.TWO), totalBalance);

        // unstake completely

        // wait for few days
        balanced.increaseDay(4);
        balanced.syncDistributions();

        // unstake completely
        balnScore.stake(BigInteger.ZERO);

        detailsBalanceOf = balnScore.detailsBalanceOf(Address.fromString(owner.getAddress().toString()));

        // assert if balance is unstaked or not.
        assertEquals(detailsBalanceOf.get("Staked balance"), BigInteger.ZERO);
        assertEquals(detailsBalanceOf.get("Unstaking balance"), amountToStake);
    }

    @Test
    @Order(3)
    void mint() {
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        BigInteger previousSupply = balnScore.totalSupply();
        BigInteger previousBalance = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));

        setMinter(owner.getAddress());

        balnScore.mint(value, "mole".getBytes());
        assertEquals(previousSupply.add(value), balnScore.totalSupply());
        assertEquals(previousBalance.add(value),
                balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(4)
    void transfer() {
        BigInteger value = BigInteger.valueOf(5).multiply(EXA);
        BigInteger previousSupply = balnScore.totalSupply();
        BigInteger previousOwnerBalance = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = balnScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        balnScore.transfer(Address.fromString(tester.getAddress().toString()), value, null);
        assertEquals(previousSupply, balnScore.totalSupply());
        assertEquals(previousTesterBalance.add(value),
                balnScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance.subtract(value),
                balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(5)
    void burn() {
        BigInteger previousSupply = balnScore.totalSupply();
        BigInteger previousOwnerBalance = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = balnScore.balanceOf(Address.fromString(tester.getAddress().toString()));

        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        BigInteger value = BigInteger.TEN.multiply(EXA);
        balnScore.burn(value);
        assertEquals(previousSupply.subtract(value), balnScore.totalSupply());
        assertEquals(previousTesterBalance, balnScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance.subtract(value),
                balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(6)
    void mintTo() {
        setMinter(owner.getAddress());
        BigInteger previousSupply = balnScore.totalSupply();
        BigInteger previousOwnerBalance = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = balnScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        BigInteger value = BigInteger.valueOf(20).multiply(EXA);
        balnScore.mintTo(Address.fromString(tester.getAddress().toString()), value, null);
        assertEquals(previousSupply.add(value), balnScore.totalSupply());
        assertEquals(previousTesterBalance.add(value),
                balnScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance, balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    @Test
    @Order(7)
    void burnFrom() {
        setMinter(owner.getAddress());
        BigInteger previousSupply = balnScore.totalSupply();
        BigInteger previousOwnerBalance = balnScore.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger previousTesterBalance = balnScore.balanceOf(Address.fromString(tester.getAddress().toString()));
        BigInteger value = BigInteger.TEN.multiply(EXA);
        balnScore.burnFrom(Address.fromString(tester.getAddress().toString()), value);
        assertEquals(previousSupply.subtract(value), balnScore.totalSupply());
        assertEquals(previousTesterBalance.subtract(value),
                balnScore.balanceOf(Address.fromString(tester.getAddress().toString())));
        assertEquals(previousOwnerBalance, balnScore.balanceOf(Address.fromString(owner.getAddress().toString())));
    }

    private void setMinter(foundation.icon.icx.data.Address address) {
        JsonArray setMinterParams = new JsonArray()
                .add(createParameter(new Address(address.toString())));

        JsonArray setMinter = createSingleTransaction(
                balanced.baln._address(),
                "setMinter",
                setMinterParams
        );

        balanced.ownerClient.governance.execute(setMinter.toString());
    }
}

