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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

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

    @Test
    @SuppressWarnings("unchecked")
    void externalRewards_simple() {
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
        System.out.println(mockBalanced.sicx.getAddress());
        List<Object> _data = new ArrayList<>(1);
        _data.add(Map.of("source", "sICX/ICX", "amount", sICXRewards));
        JSONArray data = new JSONArray(_data);

        rewardsScore.invoke(mockBalanced.sicx.account, "tokenFallback", externalRewardsProvider.getAddress(),
                sICXRewards, data.toString().getBytes());
        // starts after 1 day, fully distributed after 2
        sm.getBlock().increase(DAY * 2);

        // Assert
        Map<Address, BigInteger> rewards = (Map<Address, BigInteger>) rewardsScore.call("getRewards",
                account.getAddress().toString());
        rewardsScore.invoke(account, "claimRewards", getUserSources(account.getAddress()));
        verify(mockBalanced.sicx.mock).transfer(account.getAddress(), expectedRewards, new byte[0]);
        assertEquals(expectedRewards, rewards.get(mockBalanced.sicx.getAddress()));
    }
}