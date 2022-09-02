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

    public static BigInteger getUserWeight(Address user, Address token) {
        return userWeight.at(user).getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger getTotalSupply() {
        return totalSupply.getOrDefault(BigInteger.ONE.multiply(EXA));
    }

    public static void setTotalSupply(BigInteger supply) {
        totalSupply.set(supply);
    }

    public static BigInteger getTotalWeight(Address token) {
        return totalWeight.getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger updateUserData(Address token, Address user, BigInteger prevBalance, boolean readOnlyContext) {
        if (!continuousDividendsActive()) {
            return BigInteger.ZERO;
        }

        BigInteger currentUserWeight = getUserWeight(user, token);
        BigInteger totalWeight = getTotalWeight(token);
        if (!readOnlyContext) {
            userWeight.at(user).set(token, totalWeight);
        }

        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    public static void updateTotalWeight(Address token, BigInteger amountReceived) {
        BigInteger previousTotalWeight = getTotalWeight(token);
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getTotalSupply());
        
        totalWeight.set(token, previousTotalWeight.add(addedWeight));
    }

    private static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}