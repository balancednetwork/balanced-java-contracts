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

import static network.balanced.score.core.dividends.Check.continuousDividendsActive;
import static network.balanced.score.lib.utils.Constants.EXA;

import java.math.BigInteger;

import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;

public class DividendsTracker {
    private static final BranchDB<Address, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private static final  VarDB<BigInteger> totalSupply = Context.newVarDB("balnSupply", BigInteger.class);
    private static final  DictDB<Address, BigInteger> totalWeight = Context.newDictDB("running_total",
            BigInteger.class);

    private static final BranchDB<Address, DictDB<Address, BigInteger>> boostedUserWeight = Context.newBranchDB("bossted_user_weight",
            BigInteger.class);
    private static final  VarDB<BigInteger> boostedTotalSupply = Context.newVarDB("boostedbalnSupply", BigInteger.class);
    private static final  DictDB<Address, BigInteger> boostedTotalWeight = Context.newDictDB("boosted_running_total",
            BigInteger.class);
    private static final BigInteger boostedDay = BigInteger.valueOf(1000000000000000000L);

    public static BigInteger getUserWeight(Address user, Address token, BigInteger day) {
        if (day.compareTo(boostedDay) >= 0) {
            return boostedUserWeight.at(user).getOrDefault(token, BigInteger.ZERO);
        } else {
            return boostedUserWeight.at(user).getOrDefault(token, BigInteger.ZERO);
        }
    }

    public static BigInteger getTotalSupply(BigInteger day) {
        if (day.compareTo(boostedDay) >= 0) {
            return boostedTotalSupply.getOrDefault(BigInteger.ZERO);
        } else {
            return totalSupply.getOrDefault(BigInteger.ZERO);
        }
    }

    public static void setTotalSupply(BigInteger supply, BigInteger day) {
        if (day.compareTo(boostedDay) >= 0) {
            boostedTotalSupply.set(supply);
        } else {
            totalSupply.set(supply);
        }
    }

    public static BigInteger getTotalWeight(Address token, BigInteger day) {
        if (day.compareTo(boostedDay) >= 0) {
            return boostedTotalWeight.getOrDefault(token, BigInteger.ZERO);
        } else {
            return totalWeight.getOrDefault(token, BigInteger.ZERO);
        }
    }

    public static BigInteger updateUserData(Address token, Address user, BigInteger prevBalance, boolean readOnlyContext, BigInteger day) {
        if (!continuousDividendsActive()) {
            return BigInteger.ZERO;
        }

        BigInteger currentUserWeight = getUserWeight(user, token, day);
        BigInteger totalWeight = getTotalWeight(token, day);
        if (!readOnlyContext) {
            if (day.compareTo(boostedDay) >= 0) {
                boostedUserWeight.at(user).set(token, totalWeight);
            } else {
                userWeight.at(user).set(token, totalWeight);
            }
        }

        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    public static void updateTotalWeight(Address token, BigInteger amountReceived, BigInteger day) {
        BigInteger previousTotalWeight = getTotalWeight(token, day);
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getTotalSupply(day));
        if (day.compareTo(boostedDay) >= 0) {
            boostedTotalWeight.set(token, previousTotalWeight.add(addedWeight));
        } else {
            totalWeight.set(token, previousTotalWeight.add(addedWeight));
        }
    }

    private static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}