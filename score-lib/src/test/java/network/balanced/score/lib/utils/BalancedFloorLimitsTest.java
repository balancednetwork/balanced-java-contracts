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

package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;

import network.balanced.score.lib.interfaces.tokens.IRC2;
import network.balanced.score.lib.interfaces.tokens.IRC2ScoreInterface;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import score.ArrayDB;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;

public class BalancedFloorLimitsTest extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private Score score;
    private MockContract<IRC2> token1;
    private MockContract<IRC2> token2;

    private static final long BLOCKS_IN_A_DAY = 86400/2;

    public static class FloorLimitedTest extends BalancedFloorLimits {

        public static void setLegacyPercentage(BigInteger percent) {
            BalancedFloorLimits.percentage.set(percent);
        }

        public static void setLegacyDelay(BigInteger us) {
            BalancedFloorLimits.delay.set(us);
        }

        public static void setLegacyDisable(Address token, boolean _disabled) {
            BalancedFloorLimits.disabled.set(token, _disabled);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        token1 = new MockContract<IRC2>(IRC2ScoreInterface.class, sm, owner);
        token2 = new MockContract<IRC2>(IRC2ScoreInterface.class, sm, owner);
        score = sm.deploy(owner, FloorLimitedTest.class);
    }

    @Test
    public void initialFloorLimit(){
        // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Act
        Executable withdrawMoreThanAllowed =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(101));
        Executable withdrawAllowed =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());

        // Assert
        assertEquals(BigInteger.valueOf(900), floor);
        expectErrorMessage(withdrawMoreThanAllowed, BalancedFloorLimits.getErrorMessage());
        assertDoesNotThrow(withdrawAllowed);
    }

    @Test
    public void floorLimitReturnsToMin(){
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Act
        score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance.subtract(BigInteger.valueOf(100)));
        sm.getBlock().increase(BLOCKS_IN_A_DAY/2);
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertTrue(floor.compareTo(BigInteger.valueOf(900)) < 0);

        // Assert
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(BigInteger.valueOf(2000));
        floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertEquals(BigInteger.valueOf(1800), floor);
    }

    @Test
    public void nativeFloorLimit(){
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", EOA_ZERO, percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", EOA_ZERO, MICRO_SECONDS_IN_A_DAY);
        score.getAccount().addBalance("ICX", balance);

        // Act
        score.invoke(owner, "verifyNativeWithdraw", BigInteger.valueOf(100));
        score.getAccount().subtractBalance("ICX", BigInteger.valueOf(100));
        sm.getBlock().increase(BLOCKS_IN_A_DAY/2);
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", EOA_ZERO);
        System.out.println(floor);
        assertTrue(floor.compareTo(BigInteger.valueOf(900)) < 0);

        // Assert
        score.getAccount().addBalance("ICX", BigInteger.valueOf(1100));

        floor = (BigInteger) score.call("getCurrentFloor", EOA_ZERO);
        assertEquals(BigInteger.valueOf(1800), floor);
    }

    @Test
    public void decay() {
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Act
        score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance.subtract(BigInteger.valueOf(100)));

        // Assert
        sm.getBlock().increase(BLOCKS_IN_A_DAY/2);
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertTrue(floor.compareTo(BigInteger.valueOf(860)) < 0);
        assertTrue(floor.compareTo(BigInteger.valueOf(850)) > 0);

        sm.getBlock().increase(BLOCKS_IN_A_DAY/2);
        floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertEquals(BigInteger.valueOf(810), floor);
    }

    @Test
    public void notConfigured() {
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Assert
        Executable withdrawNonConfigured =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        assertDoesNotThrow(withdrawNonConfigured);
    }

    @Test
    public void disableFloorLimit() {
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);
        score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance.subtract(BigInteger.valueOf(100)));

        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertTrue(floor.compareTo(BigInteger.ZERO) > 0);

        // Act
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), BigInteger.ZERO);

        // Assert
        floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertEquals(floor, BigInteger.ZERO);
    }

    @Test
    public void multipleTokens() {
        // Arrange
        BigInteger balance1 = BigInteger.valueOf(1000);
        BigInteger balance2 = BigInteger.valueOf(5000);
        BigInteger percentage = BigInteger.valueOf(2500);// 25%

        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.TWO));
        score.invoke(owner, "setFloorPercentage", token2.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token2.getAddress(), MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.TWO));

        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance1);
        when(token2.mock.balanceOf(score.getAddress())).thenReturn(balance2);

        score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.ZERO);
        score.invoke(owner, "verifyWithdraw", token2.getAddress(), BigInteger.ZERO);
        BigInteger floor1 = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        BigInteger floor2 = (BigInteger) score.call("getCurrentFloor", token2.getAddress());
        assertEquals(BigInteger.valueOf(750), floor1);
        assertEquals(BigInteger.valueOf(3750), floor2);

        // Assert
        Executable withdrawMoreThanAllowed1 =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(251));
        Executable withdrawAllowed1 =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(250));
        expectErrorMessage(withdrawMoreThanAllowed1, BalancedFloorLimits.getErrorMessage());
        assertDoesNotThrow(withdrawAllowed1);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance1.subtract(BigInteger.valueOf(250)));

        Executable withdrawMoreThanAllowed2 =  () -> score.invoke(owner, "verifyWithdraw", token2.getAddress(), BigInteger.valueOf(1251));
        Executable withdrawAllowed2 =  () -> score.invoke(owner, "verifyWithdraw", token2.getAddress(), BigInteger.valueOf(1250));
        expectErrorMessage(withdrawMoreThanAllowed2, BalancedFloorLimits.getErrorMessage());
        assertDoesNotThrow(withdrawAllowed2);
        when(token2.mock.balanceOf(score.getAddress())).thenReturn(balance2.subtract(BigInteger.valueOf(1250)));

        sm.getBlock().increase(BLOCKS_IN_A_DAY);
        floor1 = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        // 750 - 25%/2 ~=656
        System.out.println(floor1);
        assertTrue(floor1.compareTo(BigInteger.valueOf(660)) < 0);
        assertTrue(floor1.compareTo(BigInteger.valueOf(650)) > 0);

        floor2 = (BigInteger) score.call("getCurrentFloor", token2.getAddress());
        // 3750 - 25%/2 ~=3281.25
        assertTrue(floor2.compareTo(BigInteger.valueOf(3285)) < 0);
        assertTrue(floor2.compareTo(BigInteger.valueOf(3275)) > 0);
    }

    @Test
    public void minimumFloor_native(){
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", EOA_ZERO, percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", EOA_ZERO, MICRO_SECONDS_IN_A_DAY);
        score.getAccount().addBalance("ICX", balance);

        // Act
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", EOA_ZERO);
        assertNotEquals(BigInteger.ZERO, floor);
        score.invoke(owner, "setMinimumFloor", EOA_ZERO, balance.add(BigInteger.ONE));

        // Assert
        floor = (BigInteger) score.call("getCurrentFloor", EOA_ZERO);
        assertEquals(BigInteger.ZERO, floor);

        // Act
        score.getAccount().addBalance("ICX", balance);

        // Assert
        floor = (BigInteger) score.call("getCurrentFloor", EOA_ZERO);
        assertEquals(BigInteger.valueOf(1800), floor);
    }

    @Test
    public void minimumFloor(){
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setFloorPercentage", token1.getAddress(), percentage);
        score.invoke(owner, "setTimeDelayMicroSeconds", token1.getAddress(), MICRO_SECONDS_IN_A_DAY);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Act
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertNotEquals(BigInteger.ZERO, floor);
        score.invoke(owner, "setMinimumFloor", token1.getAddress(), balance.add(BigInteger.ONE));

        // Assert
        floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertEquals(BigInteger.ZERO, floor);

        // Act
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance.multiply(BigInteger.TWO));

        // Assert
        floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());
        assertEquals(BigInteger.valueOf(1800), floor);
    }

    @Test
    public void migrate(){
         // Arrange
        BigInteger balance = BigInteger.valueOf(1000);
        BigInteger percentage = BigInteger.valueOf(1000);// 10%
        score.invoke(owner, "setLegacyPercentage", percentage);
        score.invoke(owner, "setLegacyDelay", MICRO_SECONDS_IN_A_DAY);
        score.invoke(owner, "setLegacyDisable", token1.getAddress(), false);
        when(token1.mock.balanceOf(score.getAddress())).thenReturn(balance);

        // Act
        Executable withdrawMoreThanAllowed =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(101));
        Executable withdrawAllowed =  () -> score.invoke(owner, "verifyWithdraw", token1.getAddress(), BigInteger.valueOf(100));
        BigInteger floor = (BigInteger) score.call("getCurrentFloor", token1.getAddress());

        // Assert
        assertEquals(BigInteger.valueOf(900), floor);
        expectErrorMessage(withdrawMoreThanAllowed, BalancedFloorLimits.getErrorMessage());
        assertDoesNotThrow(withdrawAllowed);
    }

}
