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

package network.balanced.score.core.dividends;

import network.balanced.score.lib.structs.PrepDelegations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.lib.utils.Math.pow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

class DividendsImplTest extends DividendsImplTestBase {

    private final BigInteger batchSize = BigInteger.TWO;

    @BeforeEach
    void setup() throws Exception {
        sm.getBlock().increase(2 * DAY);

        setupBase();
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setDaofund", daoScore.getAddress());
        dividendScore.invoke(admin, "setBaln", balnScore.getAddress());

        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());
        dividendScore.invoke(admin, "setDex", dexScore.getAddress());
        dividendScore.invoke(admin, "addAcceptedTokens", bnUSDScore.getAddress());
        dividendScore.invoke(admin, "setDividendsBatchSize", batchSize);
        dividendScore.invoke(admin, "setDistributionActivationStatus", true);

        contextMock.when(() -> Context.call(eq(dexScore.getAddress()), eq("getTimeOffset"))).thenReturn(BigInteger.TWO);

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        asset.put("bnUSD", String.valueOf(bnUSDScore.getAddress()));

        contextMock.when(getAssetTokens).thenReturn(asset);

        dividendScore.invoke(owner, "distribute");

    }

    @Test
    void getBalance() {
        dividendScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        dividendScore.invoke(admin, "setLoans", loansScore.getAddress());

        Map<String, String> asset = new HashMap<>();
        asset.put("baln", String.valueOf(balnScore.getAddress()));
        contextMock.when(getAssetTokens).thenReturn(asset);
        contextMock.when(balanceOf).thenReturn(BigInteger.TEN.pow(4));

        Map<String, BigInteger> expectedResult = new HashMap<>();
        expectedResult.put("baln", BigInteger.TEN.pow(4));
        expectedResult.put("ICX", BigInteger.ZERO);

        assertEquals(expectedResult, dividendScore.call("getBalances"));
    }

    @Test
    void dividendsAt() {
        Map<String, BigInteger> expected_output = new HashMap<>();
        expected_output.put("daofund", BigInteger.valueOf(400000000000000000L));
        expected_output.put("baln_holders", BigInteger.valueOf(600000000000000000L));

        assertEquals(expected_output, dividendScore.call("dividendsAt", BigInteger.valueOf(2)));
    }

    @Test
    void delegate() {
        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking")).thenReturn(stakingScore.getAddress());
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn(
                "Staking delegate called");
        dividendScore.invoke(governanceScore, "delegate", (Object) preps);

        contextMock.verify(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking"));
        contextMock.verify(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any()));
    }

    @Test
    void setBBalnAddress() {
        dividendScore.invoke(owner, "setBBalnAddress", bBalnScore.getAddress());
        assertEquals(bBalnScore.getAddress(), dividendScore.call("getBBalnAddress"));
    }

    private void mockStake(Address user, BigInteger stake) {
        contextMock.when(() -> Context.call(BigInteger.class, balnScore.getAddress(), "stakedBalanceOf", user)).thenReturn(stake);
    }
}
