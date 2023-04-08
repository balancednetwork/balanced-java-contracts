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

package network.balanced.score.core.daofund;

import com.eclipsesource.json.JsonArray;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaofundIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);

        JsonArray feeProcessingIntervalParameters = new JsonArray()
                .add(createParameter(BigInteger.ZERO));

        JsonArray actions = createSingleTransaction(balanced.feehandler._address(), "setFeeProcessingInterval",
                feeProcessingIntervalParameters);
        owner.governance.execute(actions.toString());
    }

    @Test
    @Order(1)
    void protocolOwnedLiquidity_addLiquidity() {
        // Arrange
        BigInteger collateral = EXA.multiply(BigInteger.valueOf(10000));
        BigInteger quoteAmount = EXA.multiply(BigInteger.valueOf(100));
        BigInteger pid = reader.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        owner.stakeDepositAndBorrow(collateral, quoteAmount);

        BigInteger price = reader.dex.getPrice(pid);
        BigInteger baseAmount = quoteAmount.multiply(EXA).divide(price);
        owner.staking.stakeICX(baseAmount.multiply(BigInteger.TWO), null, null);
        owner.bnUSD.transfer(balanced.daofund._address(), quoteAmount, null);
        owner.sicx.transfer(balanced.daofund._address(), baseAmount, null);

        // Act
        JsonArray addLiquidityParameters = new JsonArray()
                .add(createParameter(balanced.sicx._address()))
                .add(createParameter(baseAmount))
                .add(createParameter(balanced.bnusd._address()))
                .add(createParameter(quoteAmount));
        JsonArray stakeLpTokensParameters = new JsonArray()
                .add(createParameter(pid));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.daofund._address(), "supplyLiquidity", addLiquidityParameters))
            .add(createTransaction(balanced.daofund._address(), "stakeLpTokens", stakeLpTokensParameters));

        owner.governance.execute(actions.toString());

        // Assert
        BigInteger lpBalance = reader.stakedLp.balanceOf(balanced.daofund._address(), pid);
        assertTrue(lpBalance.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    @Order(2)
    void protocolOwnedLiquidity_claimRewards() throws Exception {
        // Arrange
        BalancedClient claimer = balanced.newClient();
        balanced.increaseDay(3);
        owner.rewards.distribute((tx) -> {
        });

        // Act
        BigInteger rewards = reader.rewards.getBalnHolding(balanced.daofund._address());
        claimer.daofund.claimRewards();

        // Assert
        BigInteger earnings = reader.daofund.getBalnEarnings();
        Map<String, BigInteger> locked = reader.boostedBaln.getLocked(balanced.daofund._address());

        assertEquals(earnings, locked.get("amount"));
        assertEquals(earnings.divide(EXA), rewards.divide(EXA));

        BigInteger time = BigInteger.valueOf(Instant.now().getEpochSecond()).multiply(MICRO_SECONDS_IN_A_SECOND);
        // hard to get exact value a lock longer than 207 weeks is sufficient
        BigInteger expectedMinUnLockTime = time.add(WEEK_IN_MICRO_SECONDS.multiply(BigInteger.valueOf(207)));
        assertTrue(locked.get("end").compareTo(expectedMinUnLockTime) > 0);

        //Rerun to add to earning and increase lock instead of create

        // Arrange
        balanced.increaseDay(1);
        owner.rewards.distribute((tx) -> {
        });

        // Act
        rewards = reader.rewards.getBalnHolding(balanced.daofund._address());
        claimer.daofund.claimRewards();

        // Assert
        BigInteger newEarnings = reader.daofund.getBalnEarnings();
        locked = reader.boostedBaln.getLocked(balanced.daofund._address());

        assertEquals(newEarnings, locked.get("amount"));
        // Wont be exact the same
        assertTrue(newEarnings.compareTo(rewards.add(earnings)) >= 0);

    }

    @Test
    @Order(3)
    void protocolOwnedLiquidity_claimFees() throws Exception {
        // Arrange
        BalancedClient claimer = balanced.newClient();
        BigInteger collateral = EXA.multiply(BigInteger.valueOf(10000));
        BigInteger loan = EXA.multiply(BigInteger.valueOf(100));

        // generate fees
        owner.stakeDepositAndBorrow(collateral, loan);
        owner.sicx.transfer(balanced.feehandler._address(), EXA.multiply(BigInteger.valueOf(100)), null);

        // Act
        Map<String, BigInteger> fees = reader.dividends.getUnclaimedDividends(balanced.daofund._address());
        claimer.daofund.claimNetworkFees();

        // Assert
        Map<String, BigInteger> earnings = reader.daofund.getFeeEarnings();

        assertTrue(fees.get(balanced.sicx._address().toString()).compareTo(BigInteger.ZERO) > 0);
        assertTrue(fees.get(balanced.bnusd._address().toString()).compareTo(BigInteger.ZERO) > 0);
        assertEquals(fees.get(balanced.sicx._address().toString()), earnings.get(balanced.sicx._address().toString()));
        assertEquals(fees.get(balanced.bnusd._address().toString()),
                earnings.get(balanced.bnusd._address().toString()));
    }

    @Test
    @Order(4)
    void protocolOwnedLiquidity_withdraw_partial() {
        // Arrange
        BigInteger pid = reader.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger balance = reader.stakedLp.balanceOf(balanced.daofund._address(), pid);
        BigInteger withdrawAmount = balance.divide(BigInteger.TWO);

        BigInteger expectedBnUSDWithdrawn = getHoldingInPool(pid, balanced.bnusd._address(), withdrawAmount);
        BigInteger expectedSICXWithdrawn = getHoldingInPool(pid, balanced.sicx._address(), withdrawAmount);
        BigInteger bnUSDBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger sICXBalance = reader.sicx.balanceOf(balanced.daofund._address());

        // Act
        JsonArray parameters = new JsonArray()
            .add(createParameter(pid))
            .add(createParameter(withdrawAmount));
        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.daofund._address(), "unstakeLpTokens", parameters))
            .add(createTransaction(balanced.daofund._address(), "withdrawLiquidity", parameters));
        owner.governance.execute(actions.toString());


        // Assert
        assertEquals(balance.subtract(withdrawAmount), reader.stakedLp.balanceOf(balanced.daofund._address(), pid));
        assertEquals(bnUSDBalance.add(expectedBnUSDWithdrawn), reader.bnUSD.balanceOf(balanced.daofund._address()));
        assertEquals(sICXBalance.add(expectedSICXWithdrawn), reader.sicx.balanceOf(balanced.daofund._address()));
    }

    @Test
    @Order(5)
    void protocolOwnedLiquidity_withdraw_full() {
        // Arrange
        BigInteger pid = reader.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger balance = reader.stakedLp.balanceOf(balanced.daofund._address(), pid);

        BigInteger expectedBnUSDWithdrawn = getHoldingInPool(pid, balanced.bnusd._address(), balance);
        BigInteger expectedSICXWithdrawn = getHoldingInPool(pid, balanced.sicx._address(), balance);
        BigInteger bnUSDBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger sICXBalance = reader.sicx.balanceOf(balanced.daofund._address());

        // Act
        JsonArray parameters = new JsonArray()
                .add(createParameter(pid))
                .add(createParameter(balance));
        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.daofund._address(), "unstakeLpTokens", parameters))
            .add(createTransaction(balanced.daofund._address(), "withdrawLiquidity", parameters));
        owner.governance.execute(actions.toString());

        // Assert
        assertEquals(BigInteger.ZERO, reader.stakedLp.balanceOf(balanced.daofund._address(), pid));
        assertEquals(bnUSDBalance.add(expectedBnUSDWithdrawn), reader.bnUSD.balanceOf(balanced.daofund._address()));
        assertEquals(sICXBalance.add(expectedSICXWithdrawn), reader.sicx.balanceOf(balanced.daofund._address()));
    }

    @Test
    @Order(6)
    void protocolOwnedLiquidity_stakeLpTokens() throws Exception {
        // Arrange
        byte[] tokenDepositData = "{\"method\":\"_deposit\"}".getBytes();
        BigInteger pid = reader.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BalancedClient client = balanced.newClient();
        BigInteger collateral = EXA.multiply(BigInteger.valueOf(10000));
        BigInteger loan = EXA.multiply(BigInteger.valueOf(100));
        BigInteger lpAmount = loan;

        client.stakeDepositAndBorrow(collateral, loan);
        client.staking.stakeICX(lpAmount.multiply(BigInteger.TWO), null, null);
        client.bnUSD.transfer(balanced.dex._address(), lpAmount, tokenDepositData);
        client.sicx.transfer(balanced.dex._address(), lpAmount, tokenDepositData);
        client.dex.add(balanced.sicx._address(), balanced.bnusd._address(), lpAmount, lpAmount, true);
        BigInteger lpBalance = client.dex.balanceOf(client.getAddress(), pid);

        // Act
        client.dex.transfer(balanced.daofund._address(), lpBalance, pid, new byte[0]);
        JsonArray stakeLPTokensParameters = new JsonArray()
            .add(createParameter(pid));
        JsonArray actions = createSingleTransaction(balanced.daofund._address(), "stakeLpTokens",
            stakeLPTokensParameters);
        owner.governance.execute(actions.toString());


        // Assert
        assertEquals(BigInteger.ZERO, client.dex.balanceOf(client.getAddress(), pid));
        assertEquals(lpBalance, client.stakedLp.balanceOf(balanced.daofund._address(), pid));
    }

    @Test
    @Order(7)
    void disburse() throws Exception {
        // Arrange
        BigInteger collateral = EXA.multiply(BigInteger.valueOf(10000));
        BigInteger loan = EXA.multiply(BigInteger.valueOf(100));
        owner.stakeDepositAndBorrow(collateral, loan);
        BalancedClient recipient = balanced.newClient();
        owner.bnUSD.transfer(balanced.daofund._address(), loan, null);

        // Act
        JsonArray disburseParam = new JsonArray()
                .add(createJsonDisbursement(balanced.bnusd._address(), loan));

        JsonArray disburseParameters = new JsonArray()
                .add(createParameter(balanced.bnusd._address()))
                .add(createParameter(recipient.getAddress()))
                .add(createParameter(loan));

        JsonArray actions = createSingleTransaction(balanced.daofund._address(), "disburse",
                disburseParameters);

        owner.governance.execute(actions.toString());

        // Assert
        BigInteger balance = reader.bnUSD.balanceOf(recipient.getAddress());
        assertEquals(loan, balance);
    }

    private BigInteger getHoldingInPool(BigInteger pid, Address token, BigInteger lpTokens) {
        BigInteger totalTokens = reader.dex.getPoolTotal(pid, token);
        BigInteger totalLpTokens = reader.dex.totalSupply(pid);
        return lpTokens.multiply(totalTokens).divide(totalLpTokens);

    }
}
