/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;

import java.math.BigInteger;

public class BalancedFloorLimits {
    private static final String TAG = "BalancedFloorLimits";
    static final DictDB<Address, BigInteger> floor = Context.newDictDB(TAG + "floor",BigInteger.class);
    static final DictDB<Address, BigInteger> lastUpdate = Context.newDictDB(TAG + "last_update",BigInteger.class);
    static final DictDB<Address, Boolean> disabled = Context.newDictDB(TAG + "disabled",Boolean.class);
    static final VarDB<BigInteger> percentage = Context.newVarDB(TAG + "percentage",BigInteger.class);
    static final VarDB<BigInteger> delay = Context.newVarDB(TAG + "delay",BigInteger.class);

    public static void setFloorPercentage(BigInteger points) {
        Context.require(POINTS.compareTo(points) >= 0, TAG + ": points value must be between 0 and " + POINTS);
        Context.require(BigInteger.ZERO.compareTo(points) < 0, TAG + ": points value must be between 0 and " + POINTS);
        percentage.set(points);
    }

    public static BigInteger getFloorPercentage() {
        return percentage.get();
    }

    public static void setTimeDelayMicroSeconds(BigInteger us) {
        delay.set(us);
    }

    public static BigInteger getTimeDelayMicroSeconds() {
        return delay.get();
    }

    public static void setDisabled(Address token, boolean _disabled) {
        disabled.set(token, _disabled);
    }

    public static Boolean isDisabled(Address token) {
        return disabled.getOrDefault(token, false);
    }

    public static BigInteger getCurrentFloor(Address tokenAddress) {
        BigInteger balance;
        if (tokenAddress.equals(EOA_ZERO)) {
            balance = Context.getBalance(Context.getAddress());
        } else {
            balance = Context.call(BigInteger.class, tokenAddress, "balanceOf", Context.getAddress());

        }
        return updateFloor(tokenAddress, balance, true);
    }

    private static BigInteger updateFloor(Address address, BigInteger balance, boolean readonly) {
        if (disabled.getOrDefault(address, false)) {
            return BigInteger.ZERO;
        }

        BigInteger percentageInPoints = percentage.get();
        BigInteger delayInUs = delay.get();
        BigInteger lastUpdateUs = lastUpdate.getOrDefault(address, BigInteger.ZERO);
        BigInteger lastFloor = floor.getOrDefault(address, BigInteger.ZERO);

        if (percentageInPoints == null || delayInUs == null) {
            return BigInteger.ZERO;
        }

        BigInteger minFloor = balance.multiply(POINTS.subtract(percentageInPoints)).divide(POINTS);
        BigInteger currentTime = currentTime();

        if (lastFloor.equals(BigInteger.ZERO)) {
            if (!readonly) {
                lastUpdate.set(address, currentTime);
                floor.set(address, minFloor);
            }

            return minFloor;
        }

        BigInteger maxWithdraw = balance.multiply(percentageInPoints).divide(POINTS);
        BigInteger timePassed = currentTime.subtract(lastUpdateUs);
        BigInteger floorRemoved = maxWithdraw.multiply(timePassed).divide(delayInUs);

        BigInteger newFloor = lastFloor.subtract(floorRemoved);
        newFloor = newFloor.max(minFloor);
        if (!readonly) {
            lastUpdate.set(address, currentTime);
            floor.set(address, newFloor);
        }

        return newFloor;
    }

    public static void verifyWithdraw(Address tokenAddress, BigInteger amount) {
        BigInteger balance = Context.call(BigInteger.class, tokenAddress, "balanceOf", Context.getAddress());
        BigInteger floor = updateFloor(tokenAddress, balance, false);
        if (floor.equals(BigInteger.ZERO) ){
            return;
        }

        Context.require(balance.subtract(amount).compareTo(floor) >= 0, getErrorMessage());
    }

    public static void verifyNativeWithdraw(BigInteger amount) {
        BigInteger balance = Context.getBalance(Context.getAddress());
        BigInteger floor = updateFloor(EOA_ZERO, balance, false);
        if (floor.equals(BigInteger.ZERO)) {
            return;
        }

        Context.require(balance.subtract(amount).compareTo(floor) >= 0, getErrorMessage());
    }

    public static String getErrorMessage() {
        return TAG + ": Failed to withdraw, the contracts balance floor has been reached. Try again later or with a smaller amount";
    }

    public static BigInteger currentTime() {
        return BigInteger.valueOf(Context.getBlockTimestamp());
    }


}