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

import com.iconloop.score.test.Account;

import score.Address;

import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RewardsTestExternalRewards extends RewardsTestBase {
    Account externalRewardsProvider = sm.createAccount();

    @BeforeEach
    void setup() throws Exception {
        super.setup();

        rewardsScore.invoke(owner, "configureExternalRewardProvider", externalRewardsProvider.getAddress(), true);
        rewardsScore.invoke(owner, "configureExternalReward", mockBalanced.sicx.getAddress(), true);
    }

    private void nextDay() {
        BigInteger offset = (BigInteger) rewardsScore.call("getTimeOffset");
        BigInteger blockTime = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(offset);
        BigInteger roundedBlockTime = blockTime.divide(MICRO_SECONDS_IN_A_DAY).multiply(MICRO_SECONDS_IN_A_DAY);
        BigInteger diff = blockTime.subtract(roundedBlockTime).divide(MICRO_SECONDS_IN_A_SECOND);
        sm.getBlock().increase(DAY - diff.divide(BigInteger.TWO).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_base() {
        // Arrange
        Account account = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger balance = BigInteger.TWO.multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger expectedRewards = BigInteger.valueOf(200).multiply(EXA);

        // Act
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply.subtract(balance),
                supplyAccount.getAddress().toString(), totalSupply.subtract(balance));

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply,
                account.getAddress().toString(), balance);
        BigInteger startTime = BigInteger.valueOf(sm.getBlock().getTimestamp());

        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), balance, totalSupply);

        List<Object> _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards));
        JSONArray data = new JSONArray(_data);

        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
             sICXRewards, data.toString().getBytes());
        BigInteger emission = (BigInteger) rewardsScore.call("getEmission", BigInteger.valueOf(-1));
        BigInteger dist = getVotePercentage("sICX/ICX").multiply(emission).divide(EXA);
        BigInteger userDist = dist.multiply(balance).divide(totalSupply);

        // Assert
        // starts after 1 day, fully distributed after 2
        nextDay();
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        assertEquals(BigInteger.ZERO, rewards.get(mockBalanced.sicx.getAddress()));

        nextDay();
        rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        BigInteger timeInUS = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger diff = timeInUS.subtract(startTime);
        BigInteger expectedBalnRewards = userDist.multiply(diff).divide(MICRO_SECONDS_IN_A_DAY);
        assertEquals(expectedRewards, rewards.get(mockBalanced.sicx.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account.getAddress(), expectedRewards, new byte[0]);
        verifyBalnReward(account.getAddress(), expectedBalnRewards.subtract(BigInteger.ONE));


        // No more is distributed
        nextDay();
        rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        assertEquals(BigInteger.ZERO, rewards.get(mockBalanced.sicx.getAddress()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_continuousRewards() {
        // Arrange
        Account account = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger balance = BigInteger.TWO.multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger expectedRewards = BigInteger.valueOf(200).multiply(EXA);

        // Act
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply.subtract(balance),
                supplyAccount.getAddress().toString(), totalSupply.subtract(balance));

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply,
                account.getAddress().toString(), balance);

        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), balance, totalSupply);

        List<Object> _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards));
        JSONArray data = new JSONArray(_data);

        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
                sICXRewards, data.toString().getBytes());

        // Assert
        // starts after 1 day
        nextDay();
        sm.getBlock().increase(DAY / 4);
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        assertEquals(expectedRewards.divide(BigInteger.valueOf(4)), rewards.get(mockBalanced.sicx.getAddress()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_MultipleSources() {
        // Arrange
        Account account = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger balance1 = BigInteger.TWO.multiply(EXA);
        BigInteger balance2 = BigInteger.valueOf(3).multiply(EXA);
        BigInteger totalSupply1 = BigInteger.TEN.multiply(EXA);
        BigInteger totalSupply2 = BigInteger.valueOf(30).multiply(EXA);
        BigInteger sICXRewards1 = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger sICXRewards2 = BigInteger.valueOf(1500).multiply(EXA);
        BigInteger expectedRewards1 = BigInteger.valueOf(200).multiply(EXA); // 20% of total
        BigInteger expectedRewards2 = BigInteger.valueOf(150).multiply(EXA); // 10% of total

        // Act
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply1.subtract(balance1),
                supplyAccount.getAddress().toString(), totalSupply1.subtract(balance1));
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/bnUSD", totalSupply2.subtract(balance2),
                supplyAccount.getAddress().toString(), totalSupply2.subtract(balance2));

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply1,
                account.getAddress().toString(), balance1);

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/bnUSD", totalSupply2,
                account.getAddress().toString(), balance2);

        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), balance1, totalSupply1);
        mockBalanceAndSupply(dex, "sICX/bnUSD", account.getAddress(), balance2, totalSupply2);

        List<Object> _data = new ArrayList<>(2);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards1));
        _data.add(Map.of("source", "sICX/bnUSD", "amount", sICXRewards2));
        JSONArray data = new JSONArray(_data);

        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
                sICXRewards1.add(sICXRewards2), data.toString().getBytes());
        // starts after 1 day, fully distributed after 2
        nextDay();
        nextDay();

        // Assert
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account.getAddress(), expectedRewards1.add(expectedRewards2),
                new byte[0]);
        assertEquals(expectedRewards1.add(expectedRewards2), rewards.get(mockBalanced.sicx.getAddress()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_MultipleUsers() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger balance1 = BigInteger.TWO.multiply(EXA);
        BigInteger balance2 = BigInteger.valueOf(3).multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger expectedRewards1 = BigInteger.valueOf(200).multiply(EXA); // 20% of total
        BigInteger expectedRewards2 = BigInteger.valueOf(300).multiply(EXA); // 30% of total

        // Act
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX",
                totalSupply.subtract(balance1.subtract(balance2)),
                supplyAccount.getAddress().toString(), totalSupply.subtract(balance1).subtract(balance2));

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply.subtract(balance2),
                account1.getAddress().toString(), balance1);

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply,
                account2.getAddress().toString(), balance2);

        mockBalanceAndSupply(dex, "sICX/ICX", account1.getAddress(), balance1, totalSupply);
        mockBalanceAndSupply(dex, "sICX/ICX", account2.getAddress(), balance2, totalSupply);

        List<Object> _data = new ArrayList<>(2);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards));
        JSONArray data = new JSONArray(_data);

        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
                sICXRewards, data.toString().getBytes());
        // starts after 1 day, fully distributed after 2
        nextDay();
        nextDay();

        // Assert
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account1.getAddress().toString());
        rewardsScore.invoke(account1, "claimRewards", getUserSources(account1.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account1.getAddress(), expectedRewards1, new byte[0]);
        assertEquals(expectedRewards1, rewards.get(mockBalanced.sicx.getAddress()));

        rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account2.getAddress().toString());
        rewardsScore.invoke(account2, "claimRewards", getUserSources(account2.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account2.getAddress(), expectedRewards2, new byte[0]);
        assertEquals(expectedRewards2, rewards.get(mockBalanced.sicx.getAddress()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_MultipleTokens() {
        // Arrange
        Account account = sm.createAccount();
        Account supplyAccount = sm.createAccount();
        BigInteger balance = BigInteger.TWO.multiply(EXA);
        BigInteger totalSupply = BigInteger.TEN.multiply(EXA);
        BigInteger sICXRewards = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger bnUSDRewards = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedSICXRewards = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedBnUSDRewards = BigInteger.valueOf(40).multiply(EXA);

        rewardsScore.invoke(owner, "configureExternalReward", mockBalanced.bnUSD.getAddress(), true);

        // Act
        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply.subtract(balance),
                supplyAccount.getAddress().toString(), totalSupply.subtract(balance));

        rewardsScore.invoke(dex.account, "updateBalanceAndSupply", "sICX/ICX", totalSupply,
                account.getAddress().toString(), balance);

        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), balance, totalSupply);

        List<Object> _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards));
        JSONArray data = new JSONArray(_data);
        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
                sICXRewards, data.toString().getBytes());
        _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", bnUSDRewards));
        data = new JSONArray(_data);
        rewardsScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", externalRewardsProvider.getAddress(),
                bnUSDRewards, data.toString().getBytes());

        // Assert
        // starts after 1 day, fully distributed after 2
        nextDay();
        nextDay();
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        assertEquals(expectedSICXRewards, rewards.get(mockBalanced.sicx.getAddress()));
        assertEquals(expectedBnUSDRewards, rewards.get(mockBalanced.bnUSD.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account.getAddress(), expectedSICXRewards, new byte[0]);
        verify(mockBalanced.bnUSD.mock).transfer(account.getAddress(), expectedBnUSDRewards, new byte[0]);

        // No more is distributed
        nextDay();
        rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        assertEquals(BigInteger.ZERO, rewards.get(mockBalanced.sicx.getAddress()));
    }

    @Test
    void externalRewards_verification() {
        Executable whiteListedTokens = () -> rewardsScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                externalRewardsProvider.getAddress(), BigInteger.ONE, new byte[0]);
        expectErrorMessage(whiteListedTokens, "Only whitelisted tokens is allowed as rewards tokens");

        Executable whiteListedProviders = () -> rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback",
                owner.getAddress(), BigInteger.ONE, new byte[0]);
        expectErrorMessage(whiteListedProviders, "Only whitelisted addresses is allowed to provider external rewards");

        List<Object> _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", BigInteger.TEN));
        JSONArray data = new JSONArray(_data);
        Executable invalidAmounts = () -> rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback",
                externalRewardsProvider.getAddress(),
                BigInteger.ONE, data.toString().getBytes());
        expectErrorMessage(invalidAmounts, "Total allocations do not match amount deposited");
    }
}