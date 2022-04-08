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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;


public class RewardsTest extends RewardsTestBase {

    @BeforeEach
    void setup() throws Exception{
        super.setup();
    }

    @Test
    void name() {
        assertEquals("Balanced Rewards", rewardsScore.call("name"));
    }

    @Test
    void getEmission_first60Days() {
        // Arrange
        BigInteger expectedEmission = BigInteger.TEN.pow(23);

        // Act & Assert 
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.ONE));
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(60)));
    }

    @Test
    void getEmission_after60Days() {
        // Arrange
        BigInteger baseEmssion = BigInteger.TEN.pow(23);
        BigInteger percent = BigInteger.valueOf(10);
        BigInteger percent100 = percent.multiply(BigInteger.valueOf(100));
        BigInteger decay = percent100.subtract(BigInteger.valueOf(5));

        BigInteger expectedEmission = baseEmssion.multiply(decay).divide(percent100);
        BigInteger expectedEmissionDayAfter = expectedEmission.multiply(decay).divide(percent100);

         // Act & Assert 
        assertEquals(expectedEmission, rewardsScore.call("getEmission", BigInteger.valueOf(61)));
        assertEquals(expectedEmissionDayAfter, rewardsScore.call("getEmission", BigInteger.valueOf(62)));
    }

    @Test
    void getEmission_after935Days() {
        // Arrange
        int day = 935;
        BigInteger minEmssion = BigInteger.valueOf(1250).multiply(ICX);

         // Act & Assert 
         assertNotEquals(minEmssion, rewardsScore.call("getEmission", BigInteger.valueOf(day-1)));
         assertEquals(minEmssion, rewardsScore.call("getEmission", BigInteger.valueOf(day)));
    }

    @Test
    void getBalnHolding() {
        // Arrange
        Account account = sm.createAccount();

        mockBalanceAndSupply(loans, "Loans", account.getAddress(), BigInteger.ONE, BigInteger.TEN);
        mockBalanceAndSupply(dex, "sICX/ICX", account.getAddress(), BigInteger.valueOf(5), BigInteger.valueOf(100));
        
        //Act 
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(account, "claimRewards");
        sm.getBlock().increase(DAY);
        rewardsScore.invoke(account, "claimRewards");

        sm.getBlock().increase(DAY);
        System.out.println(rewardsScore.call("getBalnHolding", account.getAddress()));
        sm.getBlock().increase(DAY);
        System.out.println(rewardsScore.call("getBalnHolding", account.getAddress()));
        
        // Assert 
       
    }

}


